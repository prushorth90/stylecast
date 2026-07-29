# StyleCast

StyleCast is an event-aware fashion recommendation application.

Users select an event from Google Calendar or create an event manually.
StyleCast combines the occasion, dress code, event location, event time,
weather, budget, sizing, and style preferences to recommend complete outfits.

Outfits are displayed as Pinterest-inspired mood boards containing products
from the StyleCast product catalog.

## Current status

The repository is currently in initial setup.

Application code will be implemented through scoped GitHub issues assigned to
GitHub Copilot cloud agent.

Implemented so far:

- Manual event creation and an upcoming-events list at `/events`, with an
  event detail page at `/events/{eventId}`. Events are persisted in
  PostgreSQL via Flyway-managed schema, so they survive a browser refresh
  or backend restart.
- An event styling workspace at `/events/{eventId}/style`, reachable via a
  "Style this event" button on the event detail page. It shows the event's
  details, a real event-time weather forecast (see "Event weather" below),
  placeholders for occasion analysis and recommended looks (implemented in
  later tasks), and a preferences form (outfit request, maximum budget,
  clothing size, shoe size, preferred style, and optional preferred colors
  / colors to avoid). Preferences are persisted per event in PostgreSQL;
  saving again updates the same record instead of creating a duplicate.
- Event-time weather (`com.stylecast.weather`): geocodes the event's
  location and retrieves an hourly forecast covering the event's start and
  end time from [Open-Meteo](https://open-meteo.com) (no API key required),
  storing the latest result per event in PostgreSQL. See "Event weather"
  below.
- A deterministic, locally seeded product catalog (products, size/color
  variants, and per-variant inventory), with a temporary development
  catalog browser at `/catalog` for listing, filtering, and inspecting
  products. **The catalog is demo data: all brands and products are
  fictional, seeded directly into PostgreSQL via Flyway, and are not
  real Nordstrom (or any other retailer's) inventory.** `/catalog` is not
  part of the outfit-recommendation flow; it exists to exercise and
  showcase the catalog API during development.
- A live retail product-search provider (`com.stylecast.retail`) that finds
  real, currently live `nordstrom.com` product pages using the OpenAI
  Responses API's `web_search` tool, restricted to the `nordstrom.com`
  domain. It is exercised through a temporary development endpoint,
  `POST /api/dev/retail-products/search` - **not** a customer-facing search
  page - and will be called automatically by the outfit-recommendation
  engine in a later task. See "Live retail product search (development)"
  below.

### Live retail product search (development)

`POST /api/dev/retail-products/search` is a temporary, development-only
endpoint for exercising the live Nordstrom product-search provider directly.
It is not linked from any user-facing page and does not power outfit
recommendations yet.

**Results are search-derived, not verified inventory.** The provider only
returns real `nordstrom.com` product-page URLs it found via web search;
title comes from the search result itself, and price, size availability,
and stock are **not** confirmed by StyleCast. Always confirm current price,
size availability, and stock directly on nordstrom.com before relying on a
result. Zero matches is a normal, valid outcome (HTTP 200 with an empty
`candidates` list) - it does not mean an error occurred.

#### Configure `OPENAI_API_KEY` locally

1. Get an API key from <https://platform.openai.com/api-keys>.
2. Copy `.env.example` to `.env` if you haven't already, and set
   `OPENAI_API_KEY=sk-...` in your local `.env` (never commit a real key -
   `.env` is gitignored).
3. The application starts and runs normally with `OPENAI_API_KEY` unset or
   blank; only calls to the endpoint above are affected, returning HTTP 503
   with a clear "not configured" message until a real key is provided.

#### Test the endpoint

Locally (`./mvnw spring-boot:run -Dspring-boot.run.profiles=local`), either:

- Open Swagger UI at <http://localhost:8080/swagger-ui.html> and try
  `POST /api/dev/retail-products/search`, or
- Use curl:

  ```bash
  curl -X POST http://localhost:8080/api/dev/retail-products/search \
    -H "Content-Type: application/json" \
    -d '{
      "retailer": "NORDSTROM",
      "category": "SUIT",
      "keywords": ["navy", "wedding"],
      "maxPrice": 600,
      "clothingSize": "40R",
      "limit": 5
    }'
  ```

In Docker Compose, replace `localhost:8080` with the same host port the
backend is published on (see "Docker" below); export `OPENAI_API_KEY` in
your local `.env` before running `docker compose up` so the container picks
it up.



- `title` and `location` are required and must not be blank.
- `startTime` and `endTime` are required, and `endTime` must be strictly
  after `startTime`.
- `setting` is required and must be `INDOOR` or `OUTDOOR`.
- `description` and `dressCode` are optional.
- The events list only shows events whose `endTime` hasn't already passed,
  ordered chronologically by `startTime`.
- Invalid requests return HTTP 400 with a structured error body (including
  per-field messages where applicable); an unknown event id returns 404.

### Styling preferences validation rules

- `outfitRequest`, `clothingSize`, `shoeSize`, and `preferredStyle` are
  required; `preferredColors` and `colorsToAvoid` are optional.
- `maxBudget` is required and must be greater than zero.
- `preferredStyle` must be one of `CLASSIC`, `MODERN`, `MINIMAL`, `BOLD`,
  `CASUAL`, `FORMAL`.
- `GET`/`PUT /api/events/{eventId}/preferences` return 404 for an unknown
  event id and 400 for a malformed event id; `GET` also returns 404 when the
  event exists but has no saved preferences yet.

### Event weather

Event-time weather is retrieved from [Open-Meteo](https://open-meteo.com),
a free weather API that **requires no API key** - the feature works out of
the box with no extra secrets, in every environment (local, Docker, CI).
Weather always comes from this provider, never from the LLM.

- `GET /api/events/{eventId}/weather` loads weather **automatically** - the
  event styling page never requires a manual click before weather appears.
  On first call it fetches from the provider and persists the result; while
  the saved snapshot is still fresh (see below) it's returned as-is with no
  provider call; once it's stale, `GET` transparently refreshes it. Returns
  404 only for an unknown event id, 400 for a malformed event id.
- **Freshness:** a saved snapshot is trusted for a configurable window
  (`WEATHER_FRESHNESS_MINUTES`, default 180 = 3 hours) before `GET`
  refreshes it again automatically.
- **Stale fallback:** if an automatic refresh fails but a previous snapshot
  exists, `GET` returns that previous snapshot with `stale: true` and a
  `staleWarning` explaining why, instead of an error - the styling page
  keeps showing the last known forecast rather than going blank. If refresh
  fails and there is no previous snapshot, the provider error is returned
  instead (see below).
- `POST /api/events/{eventId}/weather/refresh` geocodes the event's
  location, retrieves an hourly forecast covering the event's start and end
  time, and always saves it as the event's latest snapshot (replacing any
  previous one), regardless of freshness. The event styling page's
  "Refresh Weather" button calls this endpoint for an explicit manual
  update; unlike the automatic `GET` refresh, a failure here always
  returns an error rather than falling back to stale data.
- **Forecast horizon:** Open-Meteo can only forecast a limited number of
  days ahead (16 by default, configurable via `WEATHER_FORECAST_HORIZON_DAYS`).
  For an event further out than that, both endpoints return
  `status: "FORECAST_UNAVAILABLE"` with no fabricated temperature/wind/
  precipitation values (all `null`) - the styling page shows a clear
  "Forecast not yet available" message instead.
- **Unresolvable location:** if the event's location text can't be geocoded,
  and there is no previous snapshot to fall back to, refreshing returns
  HTTP 422 with a clear message instead of guessing coordinates.
- **Provider failure:** a network/timeout error or non-success response
  from Open-Meteo, with no previous snapshot to fall back to, returns
  HTTP 503 rather than silently fabricating data.
- Automated tests never call the real Open-Meteo API - they use a local
  fake HTTP server or fake provider beans instead (see backend test
  classes under `com.stylecast.weather`).


## Planned stack

### Frontend

- React
- TypeScript
- Vite
- Material UI
- React Router
- TanStack Query
- Vitest
- React Testing Library

### Backend

- Java 21
- Spring Boot
- Maven
- PostgreSQL
- Flyway
- JUnit 5

### Infrastructure

- Docker
- Docker Compose
- Nginx
- GitHub Actions

## Documentation

- `docs/PRODUCT_SPEC.md`
- `docs/ARCHITECTURE.md`
- `docs/ROADMAP.md`

## Local development

### Backend

```bash
cd backend
./mvnw test                  # run backend tests
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile expects PostgreSQL to be reachable on `localhost` (for
example, the `postgres` service from `docker-compose.yml`, published on host
port 5433). Start just the database with `docker compose up -d postgres`
before running the backend this way.

### Frontend

```bash
cd frontend
npm install
npm run dev          # start the Vite dev server (proxies /api to localhost:8080)
npm run lint         # ESLint
npm run typecheck    # TypeScript project build/check
npm run test         # Vitest + React Testing Library
npm run build        # production build
```

## Docker

Copy `.env.example` to `.env` and adjust values if needed, then start the
full stack:

```bash
docker compose up --build
```

Service URLs once the stack is running:

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Backend health: http://localhost:8080/actuator/health
- PostgreSQL: localhost:5433 (internally 5432 inside the `postgres` container)

Useful commands:

```bash
docker compose config         # validate the Compose configuration
docker compose ps             # view service status and health
docker compose logs -f        # follow logs for all services
docker compose logs -f backend
docker compose down           # stop and remove containers (keeps the database volume)
docker compose down -v        # stop and remove containers AND the database volume
```

**Warning:** `docker compose down -v` deletes the named PostgreSQL volume,
which permanently deletes all local database data. Use plain `docker compose
down` (without `-v`) if you want to keep your local data between runs.

## Development workflow

1. Create a scoped GitHub issue.
2. Assign the issue to Copilot cloud agent.
3. Copilot creates a branch and pull request.
4. GitHub Actions validates the pull request.
5. Review the code and test it locally.
6. Request corrections with a precise `@copilot` comment.
7. Squash and merge only after all checks pass.

## Product disclaimer

StyleCast is an independent portfolio project.

It is not operated by, affiliated with, or endorsed by Nordstrom.
The MVP does not use unofficial Nordstrom APIs and does not scrape Nordstrom.