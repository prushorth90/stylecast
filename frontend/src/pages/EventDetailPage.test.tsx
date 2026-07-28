import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EventDetailPage } from './EventDetailPage';

function renderWithProviders(eventId: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/events/${eventId}`]}>
        <Routes>
          <Route path="/events/:eventId" element={<EventDetailPage />} />
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

    renderWithProviders('11111111-1111-1111-1111-111111111111');

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
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          id: '11111111-1111-1111-1111-111111111111',
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
      ),
    );

    renderWithProviders('11111111-1111-1111-1111-111111111111');

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Birthday party' })).toBeInTheDocument(),
    );
    expect(screen.getByText('123 Main St')).toBeInTheDocument();
    expect(screen.getByText('Smart casual')).toBeInTheDocument();
    expect(screen.getByText('Casual celebration')).toBeInTheDocument();
  });
});
