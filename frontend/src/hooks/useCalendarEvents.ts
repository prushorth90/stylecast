import { useQuery } from '@tanstack/react-query';
import { fetchCalendarEvents } from '../api/calendarApi';

/**
 * Loads every one of the current user's events that overlaps `[start,
 * end)`. Callers should memoize `start`/`end` (e.g. via `useMemo`) so the
 * query key stays stable across re-renders that don't actually change the
 * visible range - otherwise every render would look like a new range and
 * refetch.
 */
export function useCalendarEvents(start: Date, end: Date) {
  return useQuery({
    queryKey: ['events', 'calendar', start.toISOString(), end.toISOString()] as const,
    queryFn: () => fetchCalendarEvents(start, end),
  });
}
