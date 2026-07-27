# StyleCast Architecture

## Architecture style

StyleCast uses a modular monolith.

The MVP must not be split into microservices.

The application consists of:

- React TypeScript frontend
- Spring Boot backend
- PostgreSQL database
- Nginx frontend server
- Docker Compose local environment

## Repository structure

The target repository structure is:

stylecast/
├── backend/
├── frontend/
├── docs/
├── .github/
├── docker-compose.yml
├── .env.example
└── README.md

## Backend modules

The Spring Boot backend should eventually contain these modules:

- common
- event
- calendar
- weather
- catalog
- user
- occasion
- recommendation
- outfit

Modules may initially be added only when required by a task.

## Module responsibilities

### common

Contains:

- Shared error responses
- Global exception handling
- Shared validation
- Shared configuration
- Utility code that is genuinely cross-cutting

### event

Contains:

- Event domain model
- Manual event creation
- Event retrieval
- Event source information
- Selected-event workflow

### calendar

Contains:

- Google OAuth integration
- Google Calendar API adapter
- Google event mapping
- Calendar connection status
- Duplicate-import prevention

### weather

Contains:

- WeatherProvider interface
- External weather implementation
- Event weather snapshots
- Forecast availability handling
- Time-zone-aware event weather lookup

### catalog

Contains:

- Products
- Product variants
- Categories
- Sizes
- Colors
- Prices
- Inventory
- Style tags
- Weather tags
- Occasion tags

### occasion

Contains:

- OccasionClassifier interface
- LLM-backed classifier
- Deterministic fallback classifier
- Structured occasion interpretation
- Output validation

### recommendation

Contains:

- Recommendation requests
- Hard constraints
- Soft scoring
- Outfit construction
- Outfit validation
- Weather scoring
- Occasion scoring

### outfit

Contains:

- Generated outfits
- Outfit items
- Saved outfits
- User feedback
- Customization state

## Primary request flow

Google Calendar or manual event
→ Event domain
→ Occasion interpretation
→ Event-time weather
→ User preferences
→ Eligible catalog products
→ Deterministic recommendation engine
→ Valid outfit combinations
→ Optional AI explanation
→ Mood-board response

## External integration boundaries

Every external integration must be behind an interface.

Examples:

- CalendarProvider
- WeatherProvider
- OccasionClassifier
- RecommendationExplanationProvider

Controllers must not call third-party APIs directly.

External API response classes must not be reused as database entities or
public StyleCast API contracts.

## API layering

The expected flow is:

Controller
→ Application service
→ Domain or business rules
→ Repository or external provider

Controllers should handle:

- HTTP request mapping
- Request validation
- Authentication context
- Response mapping

Controllers should not contain substantial business logic.

## Database

PostgreSQL is the source of truth.

Flyway manages database migrations.

Hibernate automatic schema creation must not be used as the production schema
management strategy.

Expected tables eventually include:

- users
- calendar_connections
- events
- event_interpretations
- event_weather_snapshots
- products
- product_variants
- inventory_records
- style_tags
- product_style_tags
- outfits
- outfit_items
- saved_outfits
- recommendation_feedback

## Docker architecture

Docker Compose runs three services:

### postgres

- PostgreSQL 16
- Named persistent volume
- Health check
- Runtime configuration from environment variables

### backend

- Multi-stage Java 21 image
- Runs Spring Boot JAR
- Connects to the postgres service
- Runs Flyway migrations on startup
- Exposes port 8080
- Has an application health check

### frontend

- Multi-stage Node build
- Nginx runtime image
- Serves built React files
- Proxies `/api` to the backend Compose service
- Exposes port 80 inside the container

## Local ports

Recommended host ports:

- Frontend: 3000
- Backend: 8080
- PostgreSQL: 5433

The PostgreSQL container continues to use internal port 5432.

## GitHub Actions

Every pull request targeting `main` must run:

1. Backend tests
2. Frontend lint
3. Frontend type checking
4. Frontend tests
5. Frontend production build
6. Docker Compose validation
7. Backend Docker image build
8. Frontend Docker image build
9. Full Docker Compose integration test
10. Backend health request
11. Frontend request
12. Frontend-to-backend proxy request

## Pull-request policy

All implementation work must be completed through pull requests.

The intended workflow is:

Issue
→ Copilot cloud agent
→ Remote branch
→ Pull request
→ GitHub Actions
→ Human review
→ Corrections
→ Squash merge

Copilot must never merge its own pull request.

## Testing strategy

### Backend

- Unit tests for business rules
- Repository tests where useful
- Controller or API integration tests
- Fake implementations for external providers
- Minimal live external API dependency during automated tests

### Frontend

- Component tests
- Form-validation tests
- Loading-state tests
- Error-state tests
- Empty-state tests
- API mocking
- Production build validation

### Full stack

Docker Compose integration testing should verify:

- PostgreSQL becomes healthy
- Backend becomes healthy
- Frontend serves HTML
- `/api` proxy reaches the backend