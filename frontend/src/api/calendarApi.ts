import { EventApiError, type ApiErrorBody, type EventSetting } from './eventsApi';
import { apiFetch } from './httpClient';
import type { LiveRecommendationCompleteness } from './liveRecommendationsApi';

/**
 * Presentation-safe summary of how far an event has progressed through the
 * styling workflow (see the backend's `CalendarStylingStatus` javadoc for
 * the full derivation rules) - always derived from persisted data, never
 * from an in-flight generation job, so it's stable across a page refresh.
 */
export type CalendarStylingStatus =
  | 'EVENT_ONLY'
  | 'PREFERENCES_SET'
  | 'INTERPRETATION_READY'
  | 'RECOMMENDATIONS_PENDING'
  | 'RECOMMENDATIONS_READY'
  | 'RECOMMENDATIONS_STALE';

/**
 * One event as shown on the custom StyleCast calendar. Deliberately
 * narrower than `Event` (no `description`/`createdAt`) and adds
 * calendar-specific presentation fields instead.
 */
export interface CalendarEvent {
  id: string;
  title: string;
  start: string;
  end: string;
  /** The literal UTC-offset id (e.g. `"Z"`, `"+05:00"`) the event was stored with - not a location-based IANA zone (the Event model has none). */
  timezone: string;
  allDay: boolean;
  location: string;
  setting: EventSetting;
  dressCode: string | null;
  stylingStatus: CalendarStylingStatus;
  recommendationStatus: LiveRecommendationCompleteness | null;
  stale: boolean;
  canEdit: boolean;
}

async function parseErrorResponse(response: Response): Promise<never> {
  let body: ApiErrorBody | null = null;
  try {
    body = (await response.json()) as ApiErrorBody;
  } catch {
    // Response body wasn't JSON (or was empty); fall back to a generic message.
  }

  throw new EventApiError(
    body?.message ?? `Request failed with status ${response.status}`,
    response.status,
    body?.fieldErrors ?? null,
  );
}

/**
 * Fetches every one of the current user's events that overlaps
 * `[start, end)`, ordered by start time - backs the calendar's month/week/
 * upcoming views. `start`/`end` must be `Date` instances; they're sent as
 * ISO-8601 strings with an explicit offset (`toISOString()` always
 * produces UTC/"Z", which the backend compares correctly regardless of the
 * offset the events themselves were stored with).
 */
export async function fetchCalendarEvents(start: Date, end: Date): Promise<CalendarEvent[]> {
  const params = new URLSearchParams({ start: start.toISOString(), end: end.toISOString() });
  const response = await apiFetch(`/api/events/calendar?${params.toString()}`);

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as CalendarEvent[];
}
