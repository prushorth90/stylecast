# StyleCast Product Specification

## Product overview

StyleCast is an event-aware fashion recommendation application.

A user connects Google Calendar, selects an upcoming event, and receives
complete outfit recommendations based on:

- Event type
- Dress code
- Event location
- Event date and time
- Expected weather during the event
- User budget
- Clothing and shoe sizes
- Style preferences
- Product availability

Each outfit is presented as a Pinterest-inspired visual mood board.

The products shown inside the outfit boards come from StyleCast's product
catalog. The application may later include legitimate outbound retailer or
affiliate links, but the MVP does not use unofficial Nordstrom APIs or scrape
Nordstrom.

## Product positioning

StyleCast is inspired by the type of styling and retail experience that a
department store such as Nordstrom might provide.

The application must not claim:

- To be operated by Nordstrom
- To be officially affiliated with Nordstrom
- To have access to Nordstrom internal APIs
- To have live Nordstrom inventory unless legitimate access is obtained

## Primary user flow

1. The user opens StyleCast.
2. The user connects Google Calendar or creates an event manually.
3. StyleCast displays a calendar and a list of upcoming events.
4. The user selects one event.
5. StyleCast opens a dedicated event styling page.
6. The application interprets the occasion and dress code.
7. The application obtains weather for the event location and time.
8. The user confirms budget, clothing size, shoe size, and style preference.
9. The application generates up to three complete outfits.
10. Each outfit appears as a visual mood board.
11. Products are displayed inside their respective outfit board.
12. The user can save, reject, or customize an outfit.
13. The user can open individual product details or outbound shopping links.

## Main user interface

### Events page

Route:

`/events`

The Events page contains:

- Google Calendar connection status
- Upcoming-events list
- Month or week calendar
- Manual event creation button
- Event cards
- Event selection

The calendar is used primarily to select an occasion.

### Event styling page

Route:

`/events/{eventId}/style`

The Event Styling page contains:

- Event title
- Date
- Start and end time
- Location
- Indoor or outdoor status
- Interpreted occasion
- Interpreted dress code
- Weather during the event
- User budget
- Clothing size
- Shoe size
- Preferred style
- Up to three outfit mood boards

### Outfit mood board

Each outfit board contains:

- Outfit title
- Hero image or visual composition
- Individual product images
- Product names
- Product brands
- Product prices
- Selected sizes and colors
- Total outfit price
- Weather suitability score
- Occasion suitability score
- Explanation
- Save action
- Reject action
- Customize action
- Shop-items action

Products must appear inside the outfit board. Do not add a duplicate generic
product grid beneath every outfit unless the user explicitly opens a catalog
or product-selection interface.

## MVP scope

The MVP includes:

- React and TypeScript frontend
- Java Spring Boot backend
- PostgreSQL database
- Docker Compose local environment
- GitHub Actions pull-request validation
- Manual event creation
- Seeded fashion catalog
- Weather integration
- Structured occasion classification
- User preferences
- Deterministic outfit recommendation rules
- Pinterest-inspired mood boards
- Saved outfits
- Google Calendar import

## MVP exclusions

The MVP does not include:

- Real checkout
- Payment processing
- Live Nordstrom inventory
- Unofficial Nordstrom API access
- Nordstrom scraping
- Pinterest API integration
- Virtual try-on
- Native mobile applications
- Microservices
- Kafka
- Kubernetes
- Multi-region deployment

## Technology

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
- Spring Web
- Spring Validation
- Spring Data JPA
- PostgreSQL
- Flyway
- Spring Security
- Spring WebClient
- Spring Boot Actuator
- Springdoc OpenAPI
- JUnit 5
- Mockito

### Infrastructure

- Docker
- Docker Compose
- Nginx
- GitHub Actions

### Integrations

- Google Calendar API
- Weather API
- LLM API
- Locally seeded product catalog

## AI responsibilities

The LLM may:

- Interpret event titles and descriptions
- Classify an occasion
- Infer a likely dress code
- Extract structured style requirements
- Explain why an outfit is suitable
- Rerank already-valid recommendations

The LLM must not:

- Invent weather
- Invent calendar details
- Invent product IDs
- Invent inventory
- Bypass budget restrictions
- Bypass sizing restrictions
- Bypass availability restrictions
- Directly persist unvalidated structured output

## Weather responsibilities

Weather must come from a weather provider.

The weather lookup should use:

- Event location
- Event date
- Event start time
- Event end time
- Location time zone

If a reliable event-time forecast is unavailable, the application must state
that clearly. It must not present fabricated forecast data as real.

## Recommendation responsibilities

The backend recommendation engine must enforce:

- Budget
- Size
- Product availability
- Required clothing categories
- Occasion formality
- Color restrictions
- Weather suitability
- Indoor or outdoor suitability
- Delivery or availability requirements when available

It is acceptable to return no outfit when no valid outfit satisfies the hard
constraints.

## Security requirements

- Never commit secrets.
- Store local secrets in ignored environment files.
- Provide `.env.example` without real credentials.
- Do not expose OAuth tokens to the frontend.
- Do not log OAuth tokens.
- Validate OAuth state.
- Request minimum required OAuth scopes.
- Validate all API request bodies.
- Validate structured LLM responses.