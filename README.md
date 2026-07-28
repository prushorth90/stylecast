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

### Manual event validation rules

- `title` and `location` are required and must not be blank.
- `startTime` and `endTime` are required, and `endTime` must be strictly
  after `startTime`.
- `setting` is required and must be `INDOOR` or `OUTDOOR`.
- `description` and `dressCode` are optional.
- The events list only shows events whose `endTime` hasn't already passed,
  ordered chronologically by `startTime`.
- Invalid requests return HTTP 400 with a structured error body (including
  per-field messages where applicable); an unknown event id returns 404.

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