# StyleCast Copilot Instructions

Before making changes, read:

- docs/PRODUCT_SPEC.md
- docs/ARCHITECTURE.md
- docs/ROADMAP.md
- README.md
- Existing tests
- Existing build and runtime configuration

## Scope

- Work only on the assigned GitHub issue.
- Do not implement later roadmap tasks.
- Do not add adjacent functionality unless required by the issue.
- Keep each pull request small and reviewable.
- Do not refactor unrelated code.
- Preserve existing public API contracts unless the issue explicitly changes
  them.
- Do not merge pull requests.

## Pull-request workflow

- Create one pull request per issue.
- Target the `main` branch.
- Link the issue using `Closes #<issue-number>`.
- Include a clear summary.
- List important files changed.
- List all commands run.
- Report test results accurately.
- Identify limitations and follow-up work.
- Never claim that a test passed unless it was executed successfully.

## Architecture

- Use a modular Spring Boot monolith.
- Do not introduce microservices during the MVP.
- Do not introduce Kafka during the MVP.
- Use PostgreSQL as the source of truth.
- Use Flyway for schema migrations.
- Keep external integrations behind interfaces.
- Keep controllers thin.
- Put business logic in services and domain rules.
- Use DTOs at HTTP boundaries.
- Do not reuse third-party API response models as domain entities.

## Backend

- Use Java 21.
- Use Maven and include the Maven wrapper.
- Use constructor injection.
- Validate request bodies.
- Use a consistent API error format.
- Add tests for every behavior change.
- Mock or fake external providers in automated tests.
- Avoid static mutable state.
- Do not log secrets or tokens.
- Do not use Hibernate schema generation as a replacement for Flyway.

## Frontend

- Use React and TypeScript.
- Enable strict TypeScript.
- Do not use `any` without a documented reason.
- Use TanStack Query for server state.
- Keep API calls outside React components.
- Include loading, empty, and error states.
- Use accessible labels and semantic controls.
- Support keyboard interaction.
- Keep products within their recommended outfit mood boards.
- Do not duplicate every product in an unrelated catalog below the boards.

## Docker

- The complete application must run through Docker Compose.
- Keep one Dockerfile in `backend/`.
- Keep one Dockerfile in `frontend/`.
- Use multi-stage Docker builds.
- Use runtime images that do not contain unnecessary build tools.
- Do not commit or copy secret environment files into images.
- Use environment variables for runtime configuration.
- Add `.dockerignore` files.
- Add health checks.
- Use Compose service names for container-to-container communication.
- Do not use `localhost` to connect from one container to another.
- Keep development and Docker runtime configuration distinct.
- Do not add unnecessary infrastructure services.

## GitHub Actions

- Every pull request targeting `main` must run CI.
- Backend CI must run Maven tests.
- Frontend CI must run lint, type checking, tests, and production build.
- CI must validate Docker Compose configuration.
- CI must build backend and frontend images.
- CI must start the complete Compose stack.
- CI must verify backend health.
- CI must verify the frontend.
- CI must verify the frontend `/api` proxy reaches the backend.
- Always tear down containers and volumes after integration tests.
- Print relevant container logs when integration tests fail.
- Keep workflow permissions minimal.
- Never print secrets in workflow logs.
- Do not disable tests to make CI pass.

## AI boundaries

- Weather comes from a weather API, not the LLM.
- Calendar details come from Google Calendar or manual user input.
- The LLM may classify occasions and explain recommendations.
- The LLM must not invent product IDs.
- The LLM must not invent inventory.
- Validate all structured LLM output.
- Deterministic backend rules enforce budget, size, availability, and other
  hard constraints.

## Retail boundaries

- Do not use unofficial Nordstrom APIs.
- Do not scrape Nordstrom.
- Do not imply an official Nordstrom affiliation.
- Use a locally seeded catalog until legitimate product access exists.

## Security

- Never commit credentials.
- Never commit real OAuth client secrets.
- Use environment variables.
- Provide `.env.example` files with non-sensitive placeholders.
- Do not expose OAuth access or refresh tokens to the browser.
- Validate OAuth state.
- Request minimum required scopes.