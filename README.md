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