import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EventStylePage } from './EventStylePage';

const EVENT_ID = '11111111-1111-1111-1111-111111111111';

function eventResponse() {
  return new Response(
    JSON.stringify({
      id: EVENT_ID,
      title: 'Birthday party',
      description: 'Casual celebration',
      location: '123 Main St',
      startTime: '2026-08-01T18:00:00Z',
      endTime: '2026-08-01T21:00:00Z',
      setting: 'OUTDOOR',
      dressCode: 'Smart casual',
      createdAt: '2026-07-28T00:00:00Z',
    }),
    { status: 200 },
  );
}

function notFoundResponse() {
  return new Response(JSON.stringify({ message: 'Style preferences not found', fieldErrors: null }), {
    status: 404,
  });
}

function weatherAvailableResponse(overrides: Record<string, unknown> = {}) {
  return new Response(
    JSON.stringify({
      id: '33333333-3333-3333-3333-333333333333',
      eventId: EVENT_ID,
      status: 'AVAILABLE',
      resolvedLocation: 'Springfield, Illinois, United States',
      latitude: 39.78,
      longitude: -89.65,
      temperatureAtStart: 21.5,
      temperatureAtEnd: 18.0,
      precipitationProbability: 20,
      windSpeed: 9.5,
      condition: 'Partly cloudy',
      forecastStart: '2026-08-01T18:00:00Z',
      forecastEnd: '2026-08-01T21:00:00Z',
      retrievedAt: '2026-07-28T12:00:00Z',
      providerName: 'OPEN_METEO',
      message: null,
      stale: false,
      staleWarning: null,
      ...overrides,
    }),
    { status: 200 },
  );
}

function weatherUnavailableResponse() {
  return new Response(
    JSON.stringify({
      id: '33333333-3333-3333-3333-333333333333',
      eventId: EVENT_ID,
      status: 'FORECAST_UNAVAILABLE',
      resolvedLocation: null,
      latitude: null,
      longitude: null,
      temperatureAtStart: null,
      temperatureAtEnd: null,
      precipitationProbability: null,
      windSpeed: null,
      condition: null,
      forecastStart: null,
      forecastEnd: null,
      retrievedAt: '2026-07-28T12:00:00Z',
      providerName: null,
      message: 'Event start time is beyond the 16-day forecast horizon',
      stale: false,
      staleWarning: null,
    }),
    { status: 200 },
  );
}

function weatherErrorResponse() {
  return new Response(JSON.stringify({ message: 'Weather provider unavailable', fieldErrors: null }), {
    status: 503,
  });
}

function interpretationResponse(overrides: Record<string, unknown> = {}) {
  return new Response(
    JSON.stringify({
      id: '44444444-4444-4444-4444-444444444444',
      eventId: EVENT_ID,
      occasion: 'WEDDING',
      dressCode: 'GARDEN_COCKTAIL',
      formalityLevel: 8,
      requiredCategories: ['SUIT', 'SHOES'],
      optionalCategories: ['ACCESSORY'],
      preferredColors: ['navy'],
      colorsToAvoid: ['neon green'],
      specialRequirements: ['OUTDOOR_SUITABLE'],
      assumptions: ['Outdoor garden wedding implies cocktail-adjacent formality.'],
      confidence: 0.88,
      source: 'AI',
      generatedAt: '2026-07-28T12:00:00Z',
      createdAt: '2026-07-28T12:00:00Z',
      updatedAt: '2026-07-28T12:00:00Z',
      ...overrides,
    }),
    { status: 200 },
  );
}

function ruleBasedInterpretationResponse(overrides: Record<string, unknown> = {}) {
  return interpretationResponse({
    formalityLevel: 8,
    confidence: 0.45,
    source: 'RULE_BASED_FALLBACK',
    assumptions: ['Classified using keyword matching against event text; no live weather data was used.'],
    ...overrides,
  });
}

function interpretationErrorResponse() {
  return new Response(
    JSON.stringify({ message: 'Occasion interpretation is temporarily unavailable', fieldErrors: null }),
    { status: 503 },
  );
}

function preferencesResponse(overrides: Record<string, unknown> = {}) {
  return new Response(
    JSON.stringify({
      id: '22222222-2222-2222-2222-222222222222',
      eventId: EVENT_ID,
      outfitRequest: 'I want a navy suit and tie, but not too formal.',
      maxBudget: 500,
      clothingSize: 'M',
      shoeSize: '10',
      preferredStyle: 'CLASSIC',
      preferredColors: ['navy', 'cream'],
      colorsToAvoid: ['bright red'],
      shoppingDepartment: 'MEN',
      createdAt: '2026-07-28T00:00:00Z',
      updatedAt: '2026-07-28T00:00:00Z',
      ...overrides,
    }),
    { status: 200 },
  );
}

function recommendationsNotGeneratedResponse() {
  return new Response(
    JSON.stringify({
      eventId: EVENT_ID,
      generation: 0,
      generatedAt: null,
      status: 'NO_RESULTS',
      foundCategories: [],
      missingCategories: [],
      message: 'Recommendations have not been generated yet for this event.',
      recommendations: [],
    }),
    { status: 200 },
  );
}

function recommendationsNoResultsResponse() {
  return new Response(
    JSON.stringify({
      eventId: EVENT_ID,
      generation: 1,
      generatedAt: '2026-07-28T12:00:00Z',
      status: 'NO_RESULTS',
      foundCategories: [],
      missingCategories: ['SUIT', 'SHOES'],
      message: 'No live Nordstrom products were found for required categories: Suit and Shoes.',
      recommendations: [],
    }),
    { status: 200 },
  );
}

function liveRecommendationsWithResultsResponse(overrides: Record<string, unknown> = {}) {
  return new Response(
    JSON.stringify({
      eventId: EVENT_ID,
      generation: 1,
      generatedAt: '2026-07-28T12:00:00Z',
      status: 'COMPLETE',
      foundCategories: ['SUIT', 'SHOES'],
      missingCategories: [],
      message: null,
      recommendations: [
        {
          id: '55555555-5555-5555-5555-555555555555',
          eventId: EVENT_ID,
          generation: 1,
          rank: 1,
          name: 'Live Look 1',
          status: 'ACTIVE',
          source: 'LIVE_NORDSTROM',
          explanation: 'Includes 2 live nordstrom.com product(s) (SUIT, SHOES).',
          generatedAt: '2026-07-28T12:00:00Z',
          items: [
            {
              id: '66666666-6666-6666-6666-666666666666',
              category: 'SUIT',
              retailer: 'NORDSTROM',
              title: 'Navy Wool Suit',
              brand: null,
              productUrl: 'https://www.nordstrom.com/s/navy-wool-suit/1111111',
              imageUrl: null,
              price: null,
              originalPrice: null,
              currency: null,
              priceVerified: false,
              color: null,
              requestedSize: 'M',
              availableSizes: [],
              sizeVerified: false,
              stockText: null,
              availabilityVerified: false,
              audience: 'UNKNOWN',
              sourceCitation: 'OpenAI web_search url_citation',
              displayOrder: 0,
            },
            {
              id: '99999999-9999-9999-9999-999999999999',
              category: 'SHOES',
              retailer: 'NORDSTROM',
              title: 'Leather Oxford Shoes',
              brand: null,
              productUrl: 'https://www.nordstrom.com/s/leather-oxford-shoes/2222222',
              imageUrl: null,
              price: null,
              originalPrice: null,
              currency: null,
              priceVerified: false,
              color: null,
              requestedSize: '10',
              availableSizes: [],
              sizeVerified: false,
              stockText: null,
              availabilityVerified: false,
              audience: 'UNKNOWN',
              sourceCitation: 'OpenAI web_search url_citation',
              displayOrder: 1,
            },
          ],
        },
      ],
      ...overrides,
    }),
    { status: 200 },
  );
}

function liveRecommendationsPartialResponse() {
  return new Response(
    JSON.stringify({
      eventId: EVENT_ID,
      generation: 1,
      generatedAt: '2026-07-28T12:00:00Z',
      status: 'PARTIAL',
      foundCategories: ['SUIT'],
      missingCategories: ['SHOES'],
      message: 'We found items for Suit, but no matching Shoes.',
      recommendations: [
        {
          id: '55555555-5555-5555-5555-555555555555',
          eventId: EVENT_ID,
          generation: 1,
          rank: 1,
          name: 'Live Look 1',
          status: 'ACTIVE',
          source: 'LIVE_NORDSTROM',
          explanation: 'Includes 1 live nordstrom.com product(s) (SUIT).',
          generatedAt: '2026-07-28T12:00:00Z',
          items: [
            {
              id: '66666666-6666-6666-6666-666666666666',
              category: 'SUIT',
              retailer: 'NORDSTROM',
              title: 'Navy Wool Suit',
              brand: null,
              productUrl: 'https://www.nordstrom.com/s/navy-wool-suit/1111111',
              imageUrl: null,
              price: null,
              originalPrice: null,
              currency: null,
              priceVerified: false,
              color: null,
              requestedSize: 'M',
              availableSizes: [],
              sizeVerified: false,
              stockText: null,
              availabilityVerified: false,
              audience: 'UNKNOWN',
              sourceCitation: 'OpenAI web_search url_citation',
              displayOrder: 0,
            },
          ],
        },
      ],
    }),
    { status: 200 },
  );
}

function liveRecommendationsWithVerifiedResultsResponse() {
  return new Response(
    JSON.stringify({
      eventId: EVENT_ID,
      generation: 1,
      generatedAt: '2026-07-28T12:00:00Z',
      status: 'COMPLETE',
      foundCategories: ['SUIT'],
      missingCategories: [],
      message: null,
      recommendations: [
        {
          id: '55555555-5555-5555-5555-555555555555',
          eventId: EVENT_ID,
          generation: 1,
          rank: 1,
          name: 'Live Look 1',
          status: 'ACTIVE',
          source: 'LIVE_NORDSTROM',
          explanation: 'Includes 1 live nordstrom.com product(s) (SUIT).',
          generatedAt: '2026-07-28T12:00:00Z',
          items: [
            {
              id: '66666666-6666-6666-6666-666666666666',
              category: 'SUIT',
              retailer: 'NORDSTROM',
              title: 'Verified Navy Suit',
              brand: 'Acme',
              productUrl: 'https://www.nordstrom.com/s/navy-wool-suit/1111111',
              imageUrl: null,
              price: 299.99,
              originalPrice: 349.99,
              currency: 'USD',
              priceVerified: true,
              color: 'Navy',
              requestedSize: 'M',
              availableSizes: ['40R', '42R'],
              sizeVerified: true,
              stockText: 'In stock',
              availabilityVerified: true,
              audience: 'MEN',
              sourceCitation: 'OpenAI web_search url_citation',
              displayOrder: 0,
            },
          ],
        },
      ],
    }),
    { status: 200 },
  );
}

function liveSearchProviderUnavailableResponse() {
  return new Response(
    JSON.stringify({
      eventId: EVENT_ID,
      generation: 1,
      generatedAt: '2026-07-28T12:00:00Z',
      status: 'PROVIDER_UNAVAILABLE',
      foundCategories: [],
      missingCategories: ['SUIT', 'SHOES'],
      message: 'Live Nordstrom search is temporarily unavailable. Please try again shortly.',
      recommendations: [],
    }),
    { status: 200 },
  );
}

function missingPreferencesErrorResponse() {
  return new Response(
    JSON.stringify({
      message: 'Cannot generate outfit recommendations for event ' + EVENT_ID + ': styling preferences have not been saved yet',
      fieldErrors: null,
    }),
    { status: 409 },
  );
}



/** Default routing shared by most tests: real per-endpoint fixtures unless a test overrides them. */
function defaultFetchRouter(input: RequestInfo | URL): Promise<Response> {
  const url = input.toString();
  if (url.includes('/interpretation')) {
    return Promise.resolve(interpretationResponse());
  }
  if (url.includes('/weather')) {
    return Promise.resolve(weatherAvailableResponse());
  }
  if (url.includes('/recommendations')) {
    return Promise.resolve(recommendationsNotGeneratedResponse());
  }
  if (url.includes('/preferences')) {
    return Promise.resolve(notFoundResponse());
  }
  return Promise.resolve(eventResponse());
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/events/${EVENT_ID}/style`]}>
        <Routes>
          <Route path="/events/:eventId/style" element={<EventStylePage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function labelMatcher(label: string): RegExp {
  return new RegExp(`^${label}`);
}

async function selectOption(user: ReturnType<typeof userEvent.setup>, label: string, option: string) {
  await user.click(screen.getByLabelText(labelMatcher(label)));
  await user.click(await screen.findByText(option));
}

async function fillRequiredFields(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(labelMatcher('Outfit request')), 'A navy suit and tie.');
  await user.type(screen.getByLabelText(labelMatcher('Maximum budget')), '500');
  await selectOption(user, 'Clothing size', 'M');
  await selectOption(user, 'Shoe size', '10');
  await selectOption(user, 'Preferred style', 'Classic');
}

describe('EventStylePage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    cleanup();
  });

  it('shows a loading state before the event request resolves', () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}));

    renderPage();

    expect(screen.getByRole('status')).toHaveTextContent('Loading event');
  });

  it('shows an error state when the event cannot be loaded', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ message: 'Event not found: unknown-id', fieldErrors: null }), {
        status: 404,
      }),
    );

    renderPage();

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Event not found'));
  });

  it('shows a blank form when the event has no saved preferences yet', async () => {
    vi.mocked(fetch).mockImplementation((input) => defaultFetchRouter(input));

    renderPage();

    expect(await screen.findByLabelText(labelMatcher('Outfit request'))).toHaveValue('');
    expect(screen.getByLabelText(labelMatcher('Maximum budget'))).toHaveValue(null);
  });

  it('populates the form with existing preferences', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(preferencesResponse());
      }
      return Promise.resolve(eventResponse());
    });

    renderPage();

    expect(await screen.findByLabelText(labelMatcher('Outfit request'))).toHaveValue(
      'I want a navy suit and tie, but not too formal.',
    );
    expect(screen.getByLabelText(labelMatcher('Maximum budget'))).toHaveValue(500);
    expect(screen.getByLabelText(labelMatcher('Preferred colors'))).toHaveValue('navy, cream');
    expect(screen.getByLabelText(labelMatcher('Colors to avoid'))).toHaveValue('bright red');
  });

  it('shows validation errors when required fields are missing', async () => {
    vi.mocked(fetch).mockImplementation((input) => defaultFetchRouter(input));

    const user = userEvent.setup();
    renderPage();

    await screen.findByLabelText(labelMatcher('Outfit request'));
    await user.click(screen.getByText('Save Preferences'));

    expect(await screen.findByText('Outfit request is required.')).toBeInTheDocument();
    expect(screen.getByText('Maximum budget is required.')).toBeInTheDocument();
    expect(screen.getByText('Clothing size is required.')).toBeInTheDocument();
    expect(screen.getByText('Shoe size is required.')).toBeInTheDocument();
    expect(screen.getByText('Preferred style is required.')).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalledWith(expect.stringContaining('/preferences'), expect.anything());
  });

  it('shows a validation error for a zero or negative budget', async () => {
    vi.mocked(fetch).mockImplementation((input) => defaultFetchRouter(input));

    const user = userEvent.setup();
    renderPage();

    await screen.findByLabelText(labelMatcher('Outfit request'));
    await user.type(screen.getByLabelText(labelMatcher('Outfit request')), 'A navy suit and tie.');
    await user.type(screen.getByLabelText(labelMatcher('Maximum budget')), '0');
    await selectOption(user, 'Clothing size', 'M');
    await selectOption(user, 'Shoe size', '10');
    await selectOption(user, 'Preferred style', 'Classic');

    await user.click(screen.getByText('Save Preferences'));

    expect(await screen.findByText('Must be greater than zero.')).toBeInTheDocument();
  });

  it('saves preferences for the first time and shows a success message', async () => {
    vi.mocked(fetch).mockImplementation((input, init) => {
      const url = input.toString();
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/preferences') && init?.method === 'PUT') {
        return Promise.resolve(preferencesResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    const user = userEvent.setup();
    renderPage();

    await screen.findByLabelText(labelMatcher('Outfit request'));
    await fillRequiredFields(user);
    await user.click(screen.getByText('Save Preferences'));

    expect(await screen.findByText('Preferences saved.')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/events/${EVENT_ID}/preferences`),
      expect.objectContaining({ method: 'PUT' }),
    );
  });

  it('updates existing preferences and reflects the new value after saving', async () => {
    let putCallCount = 0;
    vi.mocked(fetch).mockImplementation((input, init) => {
      const url = input.toString();
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/preferences') && init?.method === 'PUT') {
        putCallCount += 1;
        return Promise.resolve(preferencesResponse({ maxBudget: 650 }));
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(preferencesResponse());
      }
      return Promise.resolve(eventResponse());
    });

    const user = userEvent.setup();
    renderPage();

    const budgetField = await screen.findByLabelText(labelMatcher('Maximum budget'));
    expect(budgetField).toHaveValue(500);

    await user.clear(budgetField);
    await user.type(budgetField, '650');
    await user.click(screen.getByText('Save Preferences'));

    await waitFor(() =>
      expect(screen.getByLabelText(labelMatcher('Maximum budget'))).toHaveValue(650),
    );
    expect(await screen.findByText('Preferences saved.')).toBeInTheDocument();
    expect(putCallCount).toBe(1);
  });

  it('shows a loading state for weather while it is being fetched', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return new Promise(() => {});
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    renderPage();

    expect(await screen.findByText('Loading weather…')).toBeInTheDocument();
  });

  it('shows the available forecast details automatically, without any user click', async () => {
    vi.mocked(fetch).mockImplementation((input) => defaultFetchRouter(input));

    renderPage();

    expect(await screen.findByText('21.5°C')).toBeInTheDocument();
    expect(screen.getByText('18°C')).toBeInTheDocument();
    expect(screen.getByText('20%')).toBeInTheDocument();
    expect(screen.getByText('9.5 km/h')).toBeInTheDocument();
    expect(screen.getByText('Partly cloudy')).toBeInTheDocument();
    expect(screen.getByText(/Last updated/)).toBeInTheDocument();
    // Auto-loaded via GET - no manual refresh click happened.
    expect(fetch).not.toHaveBeenCalledWith(
      expect.stringContaining('/weather/refresh'),
      expect.anything(),
    );
  });

  it('shows a forecast-unavailable state for a distant event', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherUnavailableResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    renderPage();

    expect(await screen.findByText(/Forecast not yet available/)).toBeInTheDocument();
  });

  it('shows an error state when the initial automatic weather load fails, without breaking the rest of the page', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherErrorResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    renderPage();

    expect(await screen.findByText('Unable to load weather.')).toBeInTheDocument();
    // The rest of the page (event details, preferences form) must still work.
    expect(screen.getByText(/Birthday party/)).toBeInTheDocument();
    expect(await screen.findByLabelText(labelMatcher('Outfit request'))).toBeInTheDocument();
  });

  it('shows a provider-error state when refreshing weather fails, without breaking the rest of the page', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather/refresh')) {
        return Promise.resolve(weatherErrorResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Partly cloudy');
    await user.click(screen.getByText('Refresh Weather'));

    expect(await screen.findByText('Weather provider unavailable')).toBeInTheDocument();
    // The previously loaded weather must remain visible alongside the error.
    expect(screen.getByText('Partly cloudy')).toBeInTheDocument();
    expect(screen.getByText(/Birthday party/)).toBeInTheDocument();
  });

  it('disables the refresh button while a refresh is in progress', async () => {
    let resolveRefresh: (value: Response) => void = () => {};
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather/refresh')) {
        return new Promise((resolve) => {
          resolveRefresh = resolve;
        });
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    const user = userEvent.setup();
    renderPage();

    await screen.findByText('Partly cloudy');
    await user.click(screen.getByText('Refresh Weather'));

    const refreshingButton = await screen.findByText('Refreshing…');
    expect(refreshingButton.closest('button')).toBeDisabled();

    resolveRefresh(weatherAvailableResponse({ condition: 'Sunny' }));
    await screen.findByText('Sunny');
  });

  it('refreshes weather via POST and replaces the previously displayed snapshot', async () => {
    let refreshCallCount = 0;
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather/refresh')) {
        refreshCallCount += 1;
        return Promise.resolve(weatherAvailableResponse({ temperatureAtStart: 30, condition: 'Sunny' }));
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherUnavailableResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    const user = userEvent.setup();
    renderPage();

    await screen.findByText(/Forecast not yet available/);
    await user.click(screen.getByText('Refresh Weather'));

    expect(await screen.findByText('30°C')).toBeInTheDocument();
    expect(screen.getByText('Sunny')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/events/${EVENT_ID}/weather/refresh`),
      expect.objectContaining({ method: 'POST' }),
    );
    expect(refreshCallCount).toBe(1);
  });

  // --- Occasion interpretation ------------------------------------------------

  it('shows a loading state for the occasion interpretation while it is being generated', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/interpretation')) {
        return new Promise(() => {});
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    renderPage();

    expect(await screen.findByText('Interpreting occasion…')).toBeInTheDocument();
  });

  it('shows a successful AI interpretation automatically, without any user click', async () => {
    vi.mocked(fetch).mockImplementation((input) => defaultFetchRouter(input));

    renderPage();

    expect(await screen.findByText('AI interpretation')).toBeInTheDocument();
    expect(screen.getByText('Wedding')).toBeInTheDocument();
    expect(screen.getByText('Garden Cocktail')).toBeInTheDocument();
    expect(screen.getByText('8 / 10')).toBeInTheDocument();
    expect(screen.getByText('88%')).toBeInTheDocument();
    expect(screen.getByText(/Generated/)).toBeInTheDocument();
    // Auto-loaded via GET - no manual regenerate click happened.
    expect(fetch).not.toHaveBeenCalledWith(
      expect.stringContaining('/interpretation/regenerate'),
      expect.anything(),
    );
  });

  it('shows a rule-based fallback source label', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/interpretation')) {
        return Promise.resolve(ruleBasedInterpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    renderPage();

    expect(await screen.findByText('Rule-based fallback')).toBeInTheDocument();
  });

  it('displays required and optional categories', async () => {
    vi.mocked(fetch).mockImplementation((input) => defaultFetchRouter(input));

    renderPage();

    await screen.findByText('AI interpretation');
    expect(screen.getByText('Suit')).toBeInTheDocument();
    expect(screen.getByText('Shoes')).toBeInTheDocument();
    expect(screen.getByText('Accessory')).toBeInTheDocument();
  });

  it('displays assumptions and confidence', async () => {
    vi.mocked(fetch).mockImplementation((input) => defaultFetchRouter(input));

    renderPage();

    expect(await screen.findByText('88%')).toBeInTheDocument();
    expect(
      screen.getByText('Outdoor garden wedding implies cocktail-adjacent formality.'),
    ).toBeInTheDocument();
  });

  it('regenerates the interpretation via POST and replaces the previously displayed data', async () => {
    let regenerateCallCount = 0;
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/interpretation/regenerate')) {
        regenerateCallCount += 1;
        return Promise.resolve(interpretationResponse({ formalityLevel: 9, confidence: 0.93 }));
      }
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    const user = userEvent.setup();
    renderPage();

    await screen.findByText('8 / 10');
    await user.click(screen.getByText('Regenerate Interpretation'));

    expect(await screen.findByText('9 / 10')).toBeInTheDocument();
    expect(screen.getByText('93%')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/events/${EVENT_ID}/interpretation/regenerate`),
      expect.objectContaining({ method: 'POST' }),
    );
    expect(regenerateCallCount).toBe(1);
  });

  it('disables the regenerate button while regeneration is in progress', async () => {
    let resolveRegenerate: (value: Response) => void = () => {};
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/interpretation/regenerate')) {
        return new Promise((resolve) => {
          resolveRegenerate = resolve;
        });
      }
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    const user = userEvent.setup();
    renderPage();

    await screen.findByText('8 / 10');
    await user.click(screen.getByText('Regenerate Interpretation'));

    const regeneratingButton = await screen.findByText('Regenerating…');
    expect(regeneratingButton.closest('button')).toBeDisabled();

    resolveRegenerate(interpretationResponse({ formalityLevel: 9 }));
    await screen.findByText('9 / 10');
  });

  it('shows an error state when the interpretation fails, without breaking the rest of the page', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationErrorResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    renderPage();

    expect(await screen.findByText(/Unable to load the occasion interpretation/)).toBeInTheDocument();
    // The rest of the page (event details, weather, preferences form) must still work.
    expect(screen.getByText(/Birthday party/)).toBeInTheDocument();
    expect(await screen.findByText('Partly cloudy')).toBeInTheDocument();
    expect(await screen.findByLabelText(labelMatcher('Outfit request'))).toBeInTheDocument();
  });

  // --- Live outfit recommendations (Task 8) -----------------------------------

  it('renders a clearly labeled "Generate Live Nordstrom Looks" button on the styling page', async () => {
    vi.mocked(fetch).mockImplementation((input) => defaultFetchRouter(input));

    renderPage();

    const button = await screen.findByText('Generate Live Nordstrom Looks');
    expect(button).toBeInTheDocument();
    expect(button.closest('button')).toBeEnabled();
  });

  it('loads existing recommendations automatically and labels them as a live nordstrom.com search', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/recommendations')) {
        return Promise.resolve(liveRecommendationsWithResultsResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    renderPage();

    expect(await screen.findByText('Live Look 1')).toBeInTheDocument();
    expect(screen.getByText('Live Nordstrom search - temporary integration')).toBeInTheDocument();
    expect(screen.getByText('Live Nordstrom')).toBeInTheDocument();
    // Auto-loaded via GET - no generate click happened.
    expect(fetch).not.toHaveBeenCalledWith(
      expect.stringContaining('/recommendations/live/generate'),
      expect.anything(),
    );
  });

  it('shows a loading state while recommendations are being fetched', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/recommendations')) {
        return new Promise(() => {});
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    renderPage();

    expect(await screen.findByText('Loading live Nordstrom recommendations…')).toBeInTheDocument();
  });

  it('shows a no-results state when nothing has been generated yet', async () => {
    vi.mocked(fetch).mockImplementation((input) => defaultFetchRouter(input));

    renderPage();

    expect(
      await screen.findByText('Recommendations have not been generated yet for this event.'),
    ).toBeInTheDocument();
  });

  it('generates recommendations via POST when clicking Generate Looks, showing a loading state and the resulting summary cards with unverified price/size', async () => {
    let resolveGenerate: (value: Response) => void = () => {};
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/recommendations/live/generate')) {
        return new Promise((resolve) => {
          resolveGenerate = resolve;
        });
      }
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/recommendations')) {
        return Promise.resolve(recommendationsNotGeneratedResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    const user = userEvent.setup();
    renderPage();

    const generateButton = await screen.findByText('Generate Live Nordstrom Looks');
    await user.click(generateButton);

    expect(await screen.findByText('Searching nordstrom.com…')).toBeInTheDocument();
    expect(screen.getByText('Generating…').closest('button')).toBeDisabled();

    resolveGenerate(liveRecommendationsWithResultsResponse());

    expect(await screen.findByText('Live Look 1')).toBeInTheDocument();
    expect(screen.getByText('Live Nordstrom')).toBeInTheDocument();
    expect(screen.getByText(/Navy Wool Suit/)).toBeInTheDocument();
    expect(screen.getByText(/Leather Oxford Shoes/)).toBeInTheDocument();
    // Unverified price/size/availability are never shown as a generic "unverified" badge -
    // no chip is rendered for them at all.
    expect(screen.queryByText(/^\$/)).not.toBeInTheDocument();
    expect(screen.queryByText(/^Sizes:/)).not.toBeInTheDocument();
    expect(
      screen.getByText('Confirm current product details, sizes, prices, and availability on Nordstrom.'),
    ).toBeInTheDocument();
    const productLinks = screen.getAllByText('View on Nordstrom');
    expect(productLinks).toHaveLength(2);
    productLinks.forEach((link) => {
      expect(link.closest('a')).toHaveAttribute('target', '_blank');
      expect(link.closest('a')).toHaveAttribute('rel', 'noopener noreferrer');
    });
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/events/${EVENT_ID}/recommendations/live/generate`),
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('shows extracted price, sizes, and availability when the backend independently verified them', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/recommendations')) {
        return Promise.resolve(liveRecommendationsWithVerifiedResultsResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    renderPage();

    expect(await screen.findByText(/Verified Navy Suit/)).toBeInTheDocument();
    expect(screen.getByText('$299.99 USD (was $349.99)')).toBeInTheDocument();
    expect(screen.getByText('Sizes: 40R, 42R')).toBeInTheDocument();
    expect(screen.getByText('In stock')).toBeInTheDocument();
    // A known audience/department is shown once independently confirmed.
    expect(screen.getByText('Men')).toBeInTheDocument();
    // The old generic "unverified" placeholders must never appear.
    expect(screen.queryByText('Check price on Nordstrom')).not.toBeInTheDocument();
    expect(screen.queryByText('Check size on Nordstrom')).not.toBeInTheDocument();
    expect(screen.queryByText('Check availability on Nordstrom')).not.toBeInTheDocument();
    // The single top-level notice is always shown regardless of verification status.
    expect(
      screen.getByText('Confirm current product details, sizes, prices, and availability on Nordstrom.'),
    ).toBeInTheDocument();
  });

  it('shows a no-results message when generation finds no suitable products', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/recommendations/live/generate')) {
        return Promise.resolve(recommendationsNoResultsResponse());
      }
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/recommendations')) {
        return Promise.resolve(recommendationsNotGeneratedResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByText('Generate Live Nordstrom Looks'));

    expect(
      await screen.findByText('No live Nordstrom products were found for required categories: Suit and Shoes.'),
    ).toBeInTheDocument();
  });

  it('shows a live-search-unavailable warning (not a no-results message) when the provider fails', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/recommendations/live/generate')) {
        return Promise.resolve(liveSearchProviderUnavailableResponse());
      }
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/recommendations')) {
        return Promise.resolve(recommendationsNotGeneratedResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByText('Generate Live Nordstrom Looks'));

    expect(
      await screen.findByText('Live Nordstrom search is temporarily unavailable. Please try again shortly.'),
    ).toBeInTheDocument();
  });

  // --- Partial results: valid links shown even when one category is missing -------

  it('shows valid product links and a clear found/missing message for a partial result', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/recommendations/live/generate')) {
        return Promise.resolve(liveRecommendationsPartialResponse());
      }
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/recommendations')) {
        return Promise.resolve(recommendationsNotGeneratedResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByText('Generate Live Nordstrom Looks'));

    // The message is clear about what was found vs. missing - never claims completeness.
    expect(await screen.findByText('We found items for Suit, but no matching Shoes.')).toBeInTheDocument();
    // Valid candidates for the category that was found are still shown, with a working link.
    expect(screen.getByText(/Navy Wool Suit/)).toBeInTheDocument();
    const productLink = screen.getByText('View on Nordstrom');
    expect(productLink.closest('a')).toHaveAttribute('href', 'https://www.nordstrom.com/s/navy-wool-suit/1111111');
    expect(productLink.closest('a')).toHaveAttribute('target', '_blank');
    expect(productLink.closest('a')).toHaveAttribute('rel', 'noopener noreferrer');
    // A "Retry Missing Items" action is offered for a partial result.
    expect(screen.getByText('Retry Missing Items')).toBeInTheDocument();
  });

  it('retries only the missing categories when clicking "Retry Missing Items"', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/recommendations/live/retry-missing')) {
        return Promise.resolve(liveRecommendationsWithResultsResponse({ generation: 2 }));
      }
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/recommendations')) {
        return Promise.resolve(liveRecommendationsPartialResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    const user = userEvent.setup();
    renderPage();

    const retryButton = await screen.findByText('Retry Missing Items');
    await user.click(retryButton);

    expect(await screen.findByText('Leather Oxford Shoes', { exact: false })).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining(`/api/events/${EVENT_ID}/recommendations/live/retry-missing`),
      expect.objectContaining({ method: 'POST' }),
    );
    // Retry must never call the full "generate" endpoint - only the targeted retry one.
    expect(fetch).not.toHaveBeenCalledWith(
      expect.stringContaining(`/api/events/${EVENT_ID}/recommendations/live/generate`),
      expect.anything(),
    );
  });

  it('shows a clear domain error when styling preferences have not been saved yet', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/recommendations/live/generate')) {
        return Promise.resolve(missingPreferencesErrorResponse());
      }
      if (url.includes('/interpretation')) {
        return Promise.resolve(interpretationResponse());
      }
      if (url.includes('/weather')) {
        return Promise.resolve(weatherAvailableResponse());
      }
      if (url.includes('/recommendations')) {
        return Promise.resolve(recommendationsNotGeneratedResponse());
      }
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByText('Generate Live Nordstrom Looks'));

    expect(await screen.findByText(/styling preferences have not been saved yet/)).toBeInTheDocument();
  });
});

