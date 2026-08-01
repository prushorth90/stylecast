import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CalendarPage } from './CalendarPage';
import type { CalendarEvent } from '../api/calendarApi';

const READY_EVENT_ID = '11111111-1111-1111-1111-111111111111';
const ALL_DAY_EVENT_ID = '22222222-2222-2222-2222-222222222222';
const TIMEZONE_EVENT_ID = '33333333-3333-3333-3333-333333333333';

/** Fixed "now" so month/week grids and the "Today" button are deterministic. */
const NOW = new Date('2026-08-15T18:00:00Z');

function calendarEvent(overrides: Partial<CalendarEvent> = {}): CalendarEvent {
  return {
    id: READY_EVENT_ID,
    title: 'Rooftop dinner',
    start: '2026-08-20T18:00:00Z',
    end: '2026-08-20T20:00:00Z',
    timezone: 'Z',
    allDay: false,
    location: '123 Main St',
    setting: 'OUTDOOR',
    dressCode: 'Smart casual',
    stylingStatus: 'RECOMMENDATIONS_READY',
    recommendationStatus: 'COMPLETE',
    stale: false,
    canEdit: true,
    ...overrides,
  };
}

function fullEventResponse(event: CalendarEvent) {
  return new Response(
    JSON.stringify({
      id: event.id,
      title: event.title,
      description: null,
      location: event.location,
      startTime: event.start,
      endTime: event.end,
      setting: event.setting,
      dressCode: event.dressCode,
      createdAt: '2026-07-01T00:00:00Z',
    }),
    { status: 200 },
  );
}

/** A timezone-sensitive event whose UTC start crosses into a different calendar day than its browser-local day. */
const timezoneEventStartIso = '2026-08-21T02:30:00Z';
const timezoneLocalDate = new Date(timezoneEventStartIso);

function renderCalendarPage(events: CalendarEvent[]) {
  let currentEvents = events;

  vi.mocked(fetch).mockImplementation((input, init) => {
    const url = input.toString();
    const method = init?.method ?? 'GET';

    if (url.includes('/api/events/calendar')) {
      return Promise.resolve(new Response(JSON.stringify(currentEvents), { status: 200 }));
    }
    if (method === 'DELETE' && url.includes('/api/events/')) {
      const id = url.split('/api/events/')[1];
      currentEvents = currentEvents.filter((event) => event.id !== id);
      return Promise.resolve(new Response(null, { status: 204 }));
    }
    if (url.includes('/preferences')) {
      return Promise.resolve(new Response(JSON.stringify({ message: 'not found', fieldErrors: null }), { status: 404 }));
    }
    if (url.match(/\/api\/events\/[^/]+$/)) {
      const id = url.split('/api/events/')[1];
      const match = currentEvents.find((event) => event.id === id) ?? events[0];
      return Promise.resolve(fullEventResponse(match));
    }
    return Promise.resolve(new Response(JSON.stringify([]), { status: 200 }));
  });

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/calendar']}>
        <Routes>
          <Route path="/calendar" element={<CalendarPage />} />
          <Route path="/events/:eventId/style" element={<div>Style page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function dayCell(day: Date): HTMLElement {
  const label = `Create event on ${new Intl.DateTimeFormat(undefined, { dateStyle: 'full' }).format(day)}`;
  return screen.getByLabelText(label);
}

describe('CalendarPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(NOW);
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    cleanup();
  });

  it('shows a loading state before events resolve', () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}));

    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={['/calendar']}>
          <CalendarPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(screen.getByRole('status')).toHaveTextContent('Loading calendar');
  });

  it('shows an error state when the calendar cannot be loaded', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ message: 'Server error', fieldErrors: null }), { status: 500 }),
    );

    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={['/calendar']}>
          <CalendarPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('Server error');
  });

  it('month view renders an event on its correct local day, including a timezone-crossing event', async () => {
    const timedEvent = calendarEvent();
    const timezoneEvent = calendarEvent({
      id: TIMEZONE_EVENT_ID,
      title: 'Late-night timezone check',
      start: timezoneEventStartIso,
      end: '2026-08-21T04:00:00Z',
    });
    const allDayStart = new Date(2026, 7, 10);
    const allDayEnd = new Date(2026, 7, 11);
    const allDayEvent = calendarEvent({
      id: ALL_DAY_EVENT_ID,
      title: 'Company retreat',
      start: allDayStart.toISOString(),
      end: allDayEnd.toISOString(),
      allDay: true,
    });

    renderCalendarPage([timedEvent, timezoneEvent, allDayEvent]);

    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());

    expect(within(dayCell(new Date(2026, 7, 20))).getByText(/Rooftop dinner/)).toBeInTheDocument();
    expect(within(dayCell(timezoneLocalDate)).getByText(/Late-night timezone check/)).toBeInTheDocument();
    expect(within(dayCell(new Date(2026, 7, 10))).getByText(/All day.*Company retreat/)).toBeInTheDocument();
  });

  it('clicking an empty day opens event creation prefilled with that date', async () => {
    renderCalendarPage([]);
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());

    const targetDay = new Date(2026, 7, 12);
    await userEvent.click(dayCell(targetDay));

    const startField = (await screen.findByLabelText(/Start date and time/)) as HTMLInputElement;
    expect(startField.value.startsWith('2026-08-12')).toBe(true);
  });

  it('clicking an event opens its details dialog with title, time, and styling status', async () => {
    renderCalendarPage([calendarEvent()]);
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());

    await userEvent.click(within(dayCell(new Date(2026, 7, 20))).getByText(/Rooftop dinner/));

    expect(await screen.findByText('123 Main St')).toBeInTheDocument();
    expect(screen.getByText('Recommendations ready')).toBeInTheDocument();
  });

  it('deletes the event and removes it from the calendar', async () => {
    renderCalendarPage([calendarEvent()]);
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());

    await userEvent.click(within(dayCell(new Date(2026, 7, 20))).getByText(/Rooftop dinner/));
    await userEvent.click(screen.getByText('Delete'));
    await userEvent.click(screen.getByText('Confirm delete'));

    await waitFor(() => {
      expect(
        vi.mocked(fetch).mock.calls.some(
          ([input, init]) => input.toString().includes(READY_EVENT_ID) && init?.method === 'DELETE',
        ),
      ).toBe(true);
    });

    await waitFor(() => expect(screen.queryByText('Rooftop dinner')).not.toBeInTheDocument());
  });

  it('week view renders a timed event and upcoming view groups events by date', async () => {
    // Same week as NOW (2026-08-15) so it's visible without navigating.
    const thisWeekEvent = calendarEvent({ start: '2026-08-15T18:00:00Z', end: '2026-08-15T20:00:00Z' });
    renderCalendarPage([thisWeekEvent]);
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());

    await userEvent.click(screen.getByLabelText('Week view'));
    expect(await screen.findByText(/Rooftop dinner/)).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Upcoming view'));
    expect(await screen.findByText('Rooftop dinner')).toBeInTheDocument();
    expect(screen.getByText('Open event')).toBeInTheDocument();
    // A RECOMMENDATIONS_READY event has nothing left to continue styling.
    expect(screen.queryByText('Continue styling')).not.toBeInTheDocument();
  });

  it('upcoming view shows a Continue styling action for an event whose styling is not yet complete', async () => {
    renderCalendarPage([calendarEvent({ stylingStatus: 'PREFERENCES_SET', recommendationStatus: null })]);
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());

    await userEvent.click(screen.getByLabelText('Upcoming view'));

    expect(await screen.findByText('Continue styling')).toBeInTheDocument();
  });

  it('previous, next, and today controls change the visible range label', async () => {
    renderCalendarPage([]);
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument());

    expect(screen.getByText('August 2026')).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Next month'));
    expect(await screen.findByText('September 2026')).toBeInTheDocument();

    await userEvent.click(screen.getByText('Today'));
    expect(await screen.findByText('August 2026')).toBeInTheDocument();
  });
});
