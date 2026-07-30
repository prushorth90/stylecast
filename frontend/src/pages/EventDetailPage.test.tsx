import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EventDetailPage } from './EventDetailPage';

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

function notFoundPreferencesResponse() {
  return new Response(JSON.stringify({ message: 'Style preferences not found', fieldErrors: null }), {
    status: 404,
  });
}

function preferencesResponse() {
  return new Response(
    JSON.stringify({
      id: '22222222-2222-2222-2222-222222222222',
      eventId: EVENT_ID,
      outfitRequest: 'A navy suit and tie.',
      maxBudget: 500,
      clothingSize: 'M',
      shoeSize: '10',
      preferredStyle: 'CLASSIC',
      preferredColors: [],
      colorsToAvoid: [],
      shoppingDepartment: 'NO_PREFERENCE',
      createdAt: '2026-07-28T00:00:00Z',
      updatedAt: '2026-07-28T00:00:00Z',
      interpretationRefreshRecommended: false,
    }),
    { status: 200 },
  );
}

function renderWithProviders(eventId: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/events/${eventId}`]}>
        <Routes>
          <Route path="/events/:eventId" element={<EventDetailPage />} />
          <Route path="/events/:eventId/style" element={<div>Style page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('EventDetailPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    cleanup();
  });

  it('shows a loading state before the request resolves', () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}));

    renderWithProviders(EVENT_ID);

    expect(screen.getByRole('status')).toHaveTextContent('Loading event');
  });

  it('shows an error state when the event cannot be loaded', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({ message: 'Event not found: unknown-id', fieldErrors: null }),
        { status: 404 },
      ),
    );

    renderWithProviders('unknown-id');

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('Event not found'),
    );
  });

  it('shows the event details once the request succeeds', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundPreferencesResponse());
      }
      return Promise.resolve(eventResponse());
    });

    renderWithProviders(EVENT_ID);

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Birthday party' })).toBeInTheDocument(),
    );
    expect(screen.getByText('123 Main St')).toBeInTheDocument();
    expect(screen.getByText('Smart casual')).toBeInTheDocument();
    expect(screen.getByText('Casual celebration')).toBeInTheDocument();
  });

  it('opens the event setup modal on Step 2 when the event has no saved preferences', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/preferences')) {
        return Promise.resolve(notFoundPreferencesResponse());
      }
      return Promise.resolve(eventResponse());
    });

    const user = userEvent.setup();
    renderWithProviders(EVENT_ID);

    const button = await screen.findByText('Style this event');
    await user.click(button);

    expect(await screen.findByText('Styling preferences')).toBeInTheDocument();
    expect(screen.queryByText('Style page')).not.toBeInTheDocument();
  });

  it('navigates directly to the styling results page when preferences already exist', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/preferences')) {
        return Promise.resolve(preferencesResponse());
      }
      return Promise.resolve(eventResponse());
    });

    const user = userEvent.setup();
    renderWithProviders(EVENT_ID);

    const button = await screen.findByText('Style this event');
    await user.click(button);

    expect(await screen.findByText('Style page')).toBeInTheDocument();
    expect(screen.queryByText('Styling preferences')).not.toBeInTheDocument();
  });
});

