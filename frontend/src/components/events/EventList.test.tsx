import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EventList } from './EventList';

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/events']}>
        <Routes>
          <Route path="/events" element={ui} />
          <Route path="/events/:eventId" element={<div>Event detail page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const sampleEvents = [
  {
    id: '11111111-1111-1111-1111-111111111111',
    title: 'Soonest event',
    description: null,
    location: 'Downtown Hall',
    startTime: '2026-08-01T18:00:00Z',
    endTime: '2026-08-01T20:00:00Z',
    setting: 'INDOOR',
    dressCode: 'Cocktail attire',
    createdAt: '2026-07-28T00:00:00Z',
  },
  {
    id: '22222222-2222-2222-2222-222222222222',
    title: 'Later event',
    description: null,
    location: 'Rooftop Garden',
    startTime: '2026-08-05T18:00:00Z',
    endTime: '2026-08-05T21:00:00Z',
    setting: 'OUTDOOR',
    dressCode: null,
    createdAt: '2026-07-28T00:00:00Z',
  },
];

describe('EventList', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    cleanup();
  });

  it('shows a loading state before the request resolves', () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}));

    renderWithProviders(<EventList />);

    expect(screen.getByRole('status')).toHaveTextContent('Loading upcoming events');
  });

  it('shows an empty state when there are no upcoming events', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify([]), { status: 200 }));

    renderWithProviders(<EventList />);

    await waitFor(() =>
      expect(screen.getByText(/No upcoming events yet/)).toBeInTheDocument(),
    );
  });

  it('shows an error state when the request fails', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ message: 'boom', fieldErrors: null }), { status: 500 }),
    );

    renderWithProviders(<EventList />);

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('boom'));
  });

  it('renders events in the order returned by the backend and navigates on click', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(sampleEvents), { status: 200 }),
    );
    const user = userEvent.setup();

    renderWithProviders(<EventList />);

    const cards = await screen.findAllByRole('button');
    expect(cards[0]).toHaveAccessibleName(/Soonest event/);
    expect(cards[1]).toHaveAccessibleName(/Later event/);

    await user.click(cards[0]);

    await waitFor(() => expect(screen.getByText('Event detail page')).toBeInTheDocument());
  });
});
