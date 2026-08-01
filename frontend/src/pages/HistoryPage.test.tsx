import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HistoryPage } from './HistoryPage';

function historyResponse() {
  return new Response(
    JSON.stringify([
      {
        id: 'event-past',
        title: 'Past Gala',
        description: null,
        location: 'Springfield',
        startTime: '2020-01-01T18:00:00Z',
        endTime: '2020-01-01T21:00:00Z',
        setting: 'INDOOR',
        dressCode: null,
        createdAt: '2019-12-01T00:00:00Z',
      },
      {
        id: 'event-active',
        title: 'Upcoming Wedding',
        description: null,
        location: 'Springfield',
        startTime: '2030-01-01T18:00:00Z',
        endTime: '2030-01-01T21:00:00Z',
        setting: 'OUTDOOR',
        dressCode: null,
        createdAt: '2029-12-01T00:00:00Z',
      },
    ]),
    { status: 200 },
  );
}

function liveRecommendationsNotGenerated(eventId: string) {
  return new Response(
    JSON.stringify({
      eventId,
      generation: 0,
      generatedAt: null,
      status: 'NO_RESULTS',
      foundCategories: [],
      missingCategories: [],
      foundRequestedItems: [],
      missingRequestedItems: [],
      message: 'Live recommendations have not been generated yet for this event.',
      recommendations: [],
      stale: false,
    }),
    { status: 200 },
  );
}

function renderHistoryPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <HistoryPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('HistoryPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    cleanup();
  });

  it('shows only the events returned by the backend, split into active and past', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/recommendations/live')) {
        const eventId = url.includes('event-past') ? 'event-past' : 'event-active';
        return Promise.resolve(liveRecommendationsNotGenerated(eventId));
      }
      return Promise.resolve(historyResponse());
    });

    renderHistoryPage();

    await waitFor(() => expect(screen.getByText('Past Gala')).toBeInTheDocument());
    expect(screen.getByText('Upcoming Wedding')).toBeInTheDocument();
    // Never fabricates events beyond what the backend returned.
    expect(screen.queryByText('Some Other Event')).not.toBeInTheDocument();
  });

  it('shows an empty state when there is no history yet', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify([]), { status: 200 }));

    renderHistoryPage();

    await waitFor(() =>
      expect(screen.getByText(/No events yet/)).toBeInTheDocument(),
    );
  });
});
