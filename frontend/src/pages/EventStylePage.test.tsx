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
      createdAt: '2026-07-28T00:00:00Z',
      updatedAt: '2026-07-28T00:00:00Z',
      ...overrides,
    }),
    { status: 200 },
  );
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
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

    renderPage();

    expect(await screen.findByLabelText(labelMatcher('Outfit request'))).toHaveValue('');
    expect(screen.getByLabelText(labelMatcher('Maximum budget'))).toHaveValue(null);
  });

  it('populates the form with existing preferences', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
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
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

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
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundResponse());
      }
      return Promise.resolve(eventResponse());
    });

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
});
