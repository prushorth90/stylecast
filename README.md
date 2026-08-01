# StyleCast

StyleCast is an event-aware fashion recommendation application.

Users select an event from Google Calendar or create an event manually.
StyleCast combines the occasion, dress code, event location, event time,
weather, budget, sizing, and style preferences to recommend complete outfits.

Live Nordstrom recommendations are presented as compact, link-based
recommended product sets - product images, current prices, sizes, colors,
and availability are confirmed directly on nordstrom.com, not shown in the
app (no authorized live product-image/price feed is available yet). The
separate demo catalog (fictional, locally seeded products) may include
richer, image-based cards with locally-controlled metadata.

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
  a structured occasion interpretation (see "Occasion interpretation"
  below), a placeholder for recommended looks (implemented in a later
  task), and a preferences form (outfit request, maximum budget, clothing
  size, shoe size, preferred style, and optional preferred colors / colors
  to avoid). Preferences are persisted per event in PostgreSQL; saving
  again updates the same record instead of creating a duplicate.
- Event-time weather (`com.stylecast.weather`): geocodes the event's
  location and retrieves an hourly forecast covering the event's start and
  end time from [Open-Meteo](https://open-meteo.com) (no API key required),
  storing the latest result per event in PostgreSQL. See "Event weather"
  below.
- Structured occasion interpretation (`com.stylecast.occasion`): classifies
  an event's occasion, dress code, formality, product categories, colors,
  and special requirements from its title, description, setting, dress
  code, and saved preferences - never from live weather or invented
  products. See "Occasion interpretation" below.
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
  domain. It is exercised directly through a temporary development
  endpoint, `POST /api/dev/retail-products/search` - **not** a
  customer-facing search page - and is also called automatically by the
  live outfit-recommendation flow (see below). See "Live retail product
  search (development)" below.
- A deterministic, local-catalog-only outfit-recommendation engine
  (`com.stylecast.recommendation`, Task 7A) that assembles up to three
  complete, ranked outfits for an event from the local product catalog -
  budget, sizes, stock, active status, avoided colors, formality, and
  weather are all enforced as hard constraints, with deterministic 0-100
  scores for occasion/weather/style/color fit, budget efficiency, and
  completeness used to rank results. Still available at
  `POST /api/events/{eventId}/recommendations/generate`, but no longer
  wired to the styling page's "Generate Looks" button. See "Outfit
  recommendations" below.
- A live-Nordstrom outfit-recommendation flow (`com.stylecast.recommendation`,
  Task 8) that is what the event styling page's "Generate Looks" button
  now calls: it derives the event's required garment categories, issues
  one targeted, automatically-built `com.stylecast.retail` search per
  category, and assembles up to three outfits from the real `nordstrom.com`
  candidates found - never from the local catalog. See "Live outfit
  recommendations" below.
- Explicit requested-item extraction (`com.stylecast.occasion`, Task 8.5):
  preserves specific product phrases a user names in their outfit request
  (e.g. "USA soccer jersey", "swim goggles", "hiking boots") instead of
  collapsing them into broad categories like `SHIRT`/`TROUSERS`/`SHOES` -
  these take priority over the occasion interpretation's broad required
  categories when generating live searches, activity-agnostically (no new
  enum per sport). See "Explicit requested items" below.
- Live Nordstrom recommendations are link-based (Task 9): the event styling
  page presents live-Nordstrom recommendations as compact, text-based
  recommended product sets (complete/partial/no-results/provider-
  unavailable/stale states, a single Nordstrom verification notice, and a
  "View on Nordstrom" link per product - no product images) rather than
  the earlier temporary summary cards. See "Live Nordstrom recommendations
  are link-based" below.

### Live retail product search (development)

`POST /api/dev/retail-products/search` is a temporary, development-only
endpoint for exercising the live Nordstrom product-search provider directly.
It is not linked from any user-facing page. The same provider now also
powers the live outfit-recommendation flow described in "Live outfit
recommendations" below - this endpoint remains useful for testing a single
search request in isolation.

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
4. This same `OPENAI_API_KEY` is also used by occasion interpretation (see
   "Occasion interpretation" below) - the styling page works fine without
   it too, automatically using its rule-based fallback classifier instead.

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

### Occasion interpretation

Occasion interpretation (`com.stylecast.occasion`) converts an event's
title, description, indoor/outdoor setting, manually entered dress code,
and saved styling preferences (outfit request, preferred style, preferred
colors, colors to avoid) into a structured, validated interpretation:
occasion, dress code, formality level (1-10), required/optional product
categories, preferred colors, colors to avoid, special requirements,
assumptions, and confidence (0-1). **This task only classifies the
occasion - it never searches for or selects products, assembles outfits,
or invents product names, URLs, prices, or inventory.**

- `GET /api/events/{eventId}/interpretation` loads the interpretation
  **automatically** - the event styling page never requires a manual click
  before it appears. On first call it classifies and persists the result;
  every later call returns the same saved interpretation as-is (it does not
  re-classify on its own). Returns 404 for an unknown event id, 400 for a
  malformed event id.
- `POST /api/events/{eventId}/interpretation/regenerate` always re-runs
  classification against the event's current details and preferences and
  overwrites the existing interpretation (same id, new `generatedAt`) -
  it never creates a duplicate row. The event styling page's "Regenerate
  Interpretation" button calls this endpoint.
- **AI classification:** when `OPENAI_API_KEY` is configured, an
  OpenAI Responses API call (structured JSON-schema output, low
  temperature) classifies the occasion. The response is validated before
  it is ever persisted - unknown enum values, an out-of-range formality
  level (must be 1-10), or an out-of-range confidence (must be 0-1) are all
  rejected, never saved.
- **Rule-based fallback:** if `OPENAI_API_KEY` is not configured, or the AI
  call fails, times out, or returns output that fails validation, a
  deterministic keyword-based classifier is used instead (recognizing
  keywords like wedding, interview, dinner, networking, conference,
  concert, cocktail, formal, and black tie in the event's own text). This
  is marked with `source: "RULE_BASED_FALLBACK"` in the response and always
  reports a lower `confidence` than a successful AI classification. **The
  application starts and this endpoint works normally with no
  `OPENAI_API_KEY` set at all** - every classification just uses the
  rule-based fallback.
- **No live weather is ever used or invented:** the classifier's input
  never includes a weather forecast, and its prompt explicitly instructs
  the model not to invent current or forecasted conditions. A
  weather-related special requirement (e.g. rain, heat, or cold
  suitability) can only come from the event's own explicit text (e.g. an
  outdoor setting, or a season/condition mentioned in the title or
  description) - never from `com.stylecast.weather` or a model guess.
- Automated tests never call the real OpenAI API - they use a local fake
  HTTP server, fake classifier beans, or exercise the deterministic
  rule-based classifier directly (see backend test classes under
  `com.stylecast.occasion`).

### Explicit requested items

Occasion interpretation also extracts **explicit product phrases** the
user names in their saved outfit request (e.g. *"I want a USA soccer
jersey with shorts and football boots"*), preserving each phrase exactly
as written instead of collapsing it into a broad catalog category like
`SHIRT`/`TROUSERS`/`SHOES`. Without this, a soccer jersey request could be
(mis)interpreted as a dress shirt, football boots as loafers, or swim
goggles as sunglasses - this feature exists specifically to prevent that.

- Each requested item is a small structured record: `originalPhrase` (the
  user's own words, never rewritten), a broad `genericCategory`,
  `searchTerms` (normalized keyword variants used to drive live search),
  `required`, and an optional free-text `activityContext` (e.g. "soccer",
  "swimming", "hiking").
- **`genericCategory` is intentionally broad and activity-agnostic:**
  `TOP`, `BOTTOM`, `ONE_PIECE`, `FOOTWEAR`, `OUTERWEAR`, `ACCESSORY`,
  `EQUIPMENT`, `OTHER`. There is no per-sport or per-garment category
  (no `JERSEY`, no `CLEATS`, no `GOGGLES`) - a new activity is supported
  purely through free-text `originalPhrase`/`searchTerms`/`activityContext`,
  never by adding a new enum value.
- **Explicit items always take priority** over the occasion
  interpretation's broad `requiredCategories` when generating live
  searches - see "Live outfit recommendations" below.
- A requested item that Nordstrom search cannot find stays **missing** -
  it is never silently swapped for an unrelated product that happens to
  share the same broad category (e.g. an unavailable pair of football
  boots is never replaced with loafers just because both are `FOOTWEAR`).
- Both the AI classifier (validated, strict JSON schema) and the
  deterministic rule-based fallback extract requested items - an event
  with no explicit product phrases in its outfit request simply has an
  empty list, and interpretations generated before this feature existed
  are fully compatible (they load with an empty list, no migration
  backfill needed).

Examples:

| Activity | Outfit request | Preserved requested items |
| --- | --- | --- |
| Soccer | "I want a USA soccer jersey with shorts and football boots." | USA soccer jersey (Top), soccer shorts (Bottom), football boots (Footwear, search also includes "soccer cleats") |
| Swimming | "I need swim trunks, swimming goggles, and a swim cap." | swim trunks (Bottom), swimming goggles (Equipment), swim cap (Accessory) |
| Hiking | "I need a hiking shirt, hiking trousers, hiking boots, and a rain shell." | hiking shirt (Top), hiking trousers (Bottom), hiking boots (Footwear), rain shell (Outerwear) |

### Live outfit recommendations

`com.stylecast.recommendation` also assembles up to three outfits for an
event from **live `nordstrom.com` search results** (via
`com.stylecast.retail`) instead of the local catalog. This is what the
event styling page's "Generate Looks" button calls.

**Required prerequisites** are the same as the local-catalog engine below:
saved styling preferences, an occasion interpretation, and (optionally) the
latest weather snapshot. Calling generate before preferences or an
interpretation exist returns HTTP 409.

- `POST /api/events/{eventId}/recommendations/live/generate`: **when the
  event's occasion interpretation has explicit requested items (see
  "Explicit requested items" above), those take priority** - one targeted
  Nordstrom search per requested item (using its own `originalPhrase` and
  `searchTerms`, never a generic category name), independently. Only when
  there are **no** explicit requested items does generation fall back to
  deriving the event's required garment categories (from the occasion
  interpretation, always including shoes) and searching **each one
  independently** - a targeted Nordstrom search per category, automatically
  built (keywords, deterministic per-category synonyms - e.g. `TROUSERS`
  also searches "dress pants"/"pants"/"chinos" - size hint, and an even
  budget split across items/categories; **the user never constructs a
  search themselves**). A search failure for one item/category never
  discards candidates already found for another. Up to three outfits are
  assembled from whichever items/categories found candidates and
  persisted as a new, versioned "generation", the same versioning scheme
  the local-catalog engine uses.
- `POST /api/events/{eventId}/recommendations/live/retry-missing`
  re-searches **only** the items/categories the latest generation was
  missing, reusing the candidates already found for every other one (no
  repeated search calls for items/categories that already succeeded) -
  bounds the added API cost of a retry to just the gap. A no-op (no search
  calls at all) when nothing was missing.
- `GET /api/events/{eventId}/recommendations/live` returns the event's
  current (latest generation) live recommendations. **It never triggers a
  live search on its own** - only `generate`/`retry-missing` do.
- **When explicit requested items exist, the response includes
  `foundRequestedItems`/`missingRequestedItems`** (each item's
  `originalPhrase`, `genericCategory`, and `activityContext`) instead of
  `foundCategories`/`missingCategories`. The event styling page shows these
  under "Found" (with a working Nordstrom link) and "Missing" (a clear "No
  matching Nordstrom product found" message) - a missing explicit item is
  never silently dropped or replaced with an unrelated product.
- **Every generation reports a `status`:**
  - `COMPLETE` - every required category (or every explicit requested
    item) found candidates.
  - `PARTIAL` - some found candidates and some did not; valid Nordstrom
    candidates (with working product links) are still returned for the
    ones that succeeded, alongside `foundCategories`/`missingCategories`
    (or `foundRequestedItems`/`missingRequestedItems`) and a clear message
    (e.g. *"We found items for Shirt and Shoes, but no matching
    Trousers."*) - **never presented as a complete outfit**. The styling
    page offers a "Retry Missing Items" action for this case.
  - `NO_RESULTS` - every required category was searched successfully but
    none found anything; a normal, non-error outcome, never a fabricated
    outfit.
  - `PROVIDER_UNAVAILABLE` - every attempted category search failed at the
    provider level (a transient outage, not a genuine "nothing found"
    result) - shown as a distinct, clear message from `NO_RESULTS`.
- **Candidates never mix departments unless the user explicitly allows
  it:** each event's saved styling preferences include a required `shoppingDepartment`
  ("Shop from": Men's / Women's / Gender-neutral / Unisex / No preference),
  which is the sole source of the live search's department constraint
  (`men's`/`mens`, `women's`/`womens`, or `unisex`/`gender-neutral` keywords
  are added to the generated search accordingly; "No preference" adds none).
  Each candidate's title (and, once independently confirmed, an explicit
  breadcrumb/taxonomy/department label from product-detail enrichment - never
  an image) is classified into a normalized department/audience, and any
  candidate whose classification is the explicit opposite department of a
  men's- or women's-constrained search is rejected - this is what prevents a
  men's outfit from including women's ballet flats, or a women's outfit from
  including men's dress shoes.
- **Price, size, and availability are only ever shown when independently
  confirmed:** after finding a valid `nordstrom.com` product URL, StyleCast
  attempts a bounded, narrowly-scoped **product-detail enrichment** step -
  one OpenAI Responses API call (still using the same `web_search` tool
  restricted to `nordstrom.com`, never a direct fetch of the Nordstrom
  page - see "Retail boundaries" below) that reports brand, name,
  current/original price, currency, color, sizes, stock text, and
  department **only if the response actually cites the exact requested
  product URL** and the value passes basic sanity validation.
  **Live Nordstrom products never have an image**: no authorized product
  feed is available yet (see docs/ROADMAP.md), so StyleCast does not fetch
  Nordstrom product pages, parse JSON-LD/`og:image`/Twitter Card/gallery
  image metadata, or make a second OpenAI request to find one - `imageUrl`
  stays `null` for every new live item and the frontend never renders an
  image or a placeholder for one (see "Live Nordstrom recommendations are
  link-based" below). `imageUrl` remains a nullable field on the entity/DTO
  purely for backward/database compatibility - no schema migration is
  warranted just to drop it, and the demo catalog's own images are
  unaffected.
  `priceVerified`/`sizeVerified`/`availabilityVerified` are only ever
  `true` when a field survived that check; otherwise the field stays
  `null`/empty and its flag stays `false` - none of these fields (or their
  verification flags), nor color, are ever rendered on the live styling
  page at all; a department/audience label is shown when known, and a
  single top-level notice ("Confirm current product details, sizes,
  prices, and availability on Nordstrom.") appears once per recommendation
  section rather than repeating it per product. A failed enrichment
  attempt never discards the underlying candidate, and the number of
  enrichment calls per search is bounded
  (`stylecast.retail-search.enrichment-max-candidates`).
- **Automated tests use mocked providers and local fixtures only. They do
  not make real OpenAI, Nordstrom, weather, or geocoding requests.** A fake
  `RetailProductSearchProvider`/`ProductDetailEnricher` bean, or hand-built
  JSON fixtures shaped like the OpenAI Responses API, are used everywhere
  (see backend test classes under `com.stylecast.recommendation` whose name
  starts with `Live`, and `com.stylecast.retail.OpenAiProductDetailEnricherTest`/
  `CandidateAudienceClassifierTest`). See also `backend/src/test/resources/application-test.yml`
  and `com.stylecast.testsupport.NoExternalNetworkGuardConfig`.

### Live Nordstrom recommendations are link-based

The event styling page presents live-Nordstrom recommendations as compact,
text-based recommended product sets (`LiveRecommendationSection` and the
components under `frontend/src/components/styling/recommendations/`) - not
image-based mood boards. No authorized live Nordstrom product-image feed is
available yet (see docs/ROADMAP.md), so **product images, current prices,
sizes, colors, and availability are never shown for a live Nordstrom
product - they are confirmed directly on nordstrom.com** via each "View on
Nordstrom" link. **Demo catalog products may continue to use richer,
locally-controlled, image-based cards** (see `frontend/src/components/catalog/ProductCard.tsx`) -
this only applies to `LIVE_NORDSTROM` recommendations.

- **Complete product sets** show a green "Complete" badge and a "Live
  Nordstrom" source badge. **Partial product sets** show an amber
  "Partial" badge instead and only ever contain the items that were
  actually found - a partial result is never presented as a complete
  outfit.
- **Found and missing items are always shown separately**, above the
  product sets: a "Found" list (each entry linking to its real Nordstrom
  product page when known) and a "Missing" list ("No matching Nordstrom
  product found" per entry). Requested items always use the user's own
  `originalPhrase` (e.g. "USA soccer jersey", "football boots"), never a
  raw generic category name; category-only generations show the same
  found/missing structure using formatted category labels.
- **Each product renders as a compact, text-based card**: the requested
  item phrase (when the item fulfills an explicit requested item), the
  product title, a readable generic category, an audience/department chip
  when known, and a "View on Nordstrom" link - never an image, image
  placeholder, price, color, sizes, stock text, or a
  price/size/availability verification chip. Every "View on Nordstrom"
  link opens in a new tab with `target="_blank" rel="noopener noreferrer"`
  and a descriptive accessible name.
- **One verification notice** ("Confirm current product details, sizes,
  prices, and availability on Nordstrom.") appears once per recommendation
  section, never repeated per product set or per product.
- **No-results** (`status: 'NO_RESULTS'`) and **provider-unavailable**
  (`status: 'PROVIDER_UNAVAILABLE'`) are shown as distinct messages - the
  live section never falls back to fictional demo products.
- **Stale recommendations:** if the event's saved styling preferences or
  occasion interpretation change after a generation was produced (see
  `POST .../recommendations/live/invalidate-stale`, called by the event
  setup modal), the response's `stale: true` flag is shown as a clear
  warning ("These recommendations were generated from older preferences.")
  with a "Generate updated looks" action - the previous product sets stay
  visible (never cleared) until a new generation actually succeeds.
- **Live vs. demo labeling:** every live product set is always labeled
  "Live Nordstrom"; the source-badge mapping also supports a "Demo
  catalog" label for the separate local-catalog engine below, so the two
  are never visually or textually conflated if both are ever shown
  together.
- A structured, text-only loading skeleton (matching the eventual card
  shape - never an image-shaped skeleton) is shown while recommendations
  are loading or being generated - previously loaded, valid product sets
  are never cleared while a new generate/retry request is in flight, only
  replaced once it succeeds.



`com.stylecast.recommendation` also deterministically assembles up to
three complete outfits for an event from **the local product catalog
only**, entirely independently of the live flow above. It never calls the
live Nordstrom search provider, OpenAI, or any other LLM - every product,
price, and variant it returns comes directly from the catalog seeded into
PostgreSQL, and **all catalog products are fictional demo data**, not real
Nordstrom (or any other retailer's) inventory. This engine remains fully
functional at the endpoints below, but is no longer wired to the styling
page's "Generate Looks" button (see "Live outfit recommendations" above).

**Required prerequisites.** Generating recommendations for an event
requires the event to already have saved styling preferences
(`com.stylecast.event.styling`) and an occasion interpretation
(`com.stylecast.occasion`); the latest weather snapshot
(`com.stylecast.weather`) is used automatically when available, but is
optional. Calling generate before preferences or an interpretation exist
returns HTTP 409 with a clear message instead of guessing.

- `POST /api/events/{eventId}/recommendations/generate` builds candidate
  outfits from one or more outfit templates (e.g. formal/wedding menswear,
  business/interview, smart casual, or a dress/skirt-based outfit),
  filters every candidate against hard constraints (budget, required
  categories, clothing/shoe size, in-stock, active, avoided colors,
  formality, and weather where data exists), scores the valid ones
  deterministically, and persists up to three ranked outfits as a new,
  versioned "generation" for the event - the previous generation's rows
  are kept and marked superseded rather than deleted.
- `GET /api/events/{eventId}/recommendations` returns the event's current
  (latest generation) recommendations. **It never generates anything on
  its own** - repeating `GET` never re-runs generation; only the explicit
  `generate` call does.
- **No-results is not an error:** when no valid combination of catalog
  products satisfies every hard constraint (e.g. the budget is too low),
  both endpoints return HTTP 200 with `hasResults: false` and a
  human-readable `noResultReason` - never a 4xx/5xx error and never a
  fabricated outfit.
- This engine's recommendation summary cards (showing item names/
  categories/sizes/colors/prices, total price, and occasion/weather/
  overall fit scores) were previously a **temporary**
  integration for this task, clearly labeled "Demo catalog recommendations"
  - not the final Pinterest-style mood board.
- Automated tests never call a live retail provider or the OpenAI API -
  the engine has no dependency on `com.stylecast.retail`, `WebClient`, or
  any AI classifier (see backend test classes under
  `com.stylecast.recommendation`).

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

**Automated tests use mocked providers and local fixtures only. They do not
make real OpenAI, Nordstrom, weather, or geocoding requests.** Backend
HTTP-layer tests point at a local `com.sun.net.httpserver.HttpServer` fake
(never `api.openai.com`/`nordstrom.com`/`open-meteo.com`); full-context
Spring tests use `src/test/resources/application-test.yml` (an empty API
key and unreachable-localhost base URLs by default) plus fake provider
beans where a specific test needs one. `com.stylecast.testsupport.NoExternalNetworkGuardConfig`
fails a test immediately if that safe configuration is ever lost, and the
Maven Surefire plugin forces every test JVM to use safe environment
variable values regardless of the launching shell's own environment (see
`backend/pom.xml`). Frontend tests stub `fetch` globally in every test file
and never start or depend on a real backend. No test requires
`OPENAI_API_KEY`, other real credentials, or internet access.

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