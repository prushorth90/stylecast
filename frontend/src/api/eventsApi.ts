import { apiFetch } from './httpClient';

export type EventSetting = 'INDOOR' | 'OUTDOOR';

/**
 * An event as returned by the backend. Date/time fields are ISO-8601
 * strings (with an explicit offset/UTC designator); components parse them
 * into `Date` instances when they need to render or compare them.
 */
export interface Event {
  id: string;
  title: string;
  description: string | null;
  location: string;
  startTime: string;
  endTime: string;
  setting: EventSetting;
  dressCode: string | null;
  createdAt: string;
}

export interface CreateEventInput {
  title: string;
  description?: string | null;
  location: string;
  startTime: string;
  endTime: string;
  setting: EventSetting;
  dressCode?: string | null;
}

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors: ApiFieldError[] | null;
}

/**
 * Error thrown by the events API client when a request fails, carrying the
 * backend's structured error body (when available) so callers can surface
 * a specific message rather than a generic failure.
 */
export class EventApiError extends Error {
  readonly status: number;
  readonly fieldErrors: ApiFieldError[] | null;

  constructor(message: string, status: number, fieldErrors: ApiFieldError[] | null = null) {
    super(message);
    this.name = 'EventApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
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
 * Fetches upcoming events (chronologically ascending by start time).
 *
 * Uses a relative path so it works both in local development (proxied by
 * Vite to the backend) and in Docker (proxied by Nginx).
 */
export async function fetchUpcomingEvents(): Promise<Event[]> {
  const response = await apiFetch('/api/events');

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as Event[];
}

export async function fetchEventById(eventId: string): Promise<Event> {
  const response = await apiFetch(`/api/events/${encodeURIComponent(eventId)}`);

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as Event;
}

/**
 * Fetches every one of the current user's events (past and upcoming,
 * ordered most-recent-first) - backs the saved event/look history page.
 * Unlike {@link fetchUpcomingEvents}, this never excludes events whose
 * `endTime` has already passed.
 */
export async function fetchEventHistory(): Promise<Event[]> {
  const response = await apiFetch('/api/events/history');

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as Event[];
}

export async function createEvent(input: CreateEventInput): Promise<Event> {
  const response = await apiFetch('/api/events', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as Event;
}

/**
 * Updates an existing event's details in place (never creates a new
 * record) - used by Step 1 of the event setup modal's "Continue" action
 * when the event was already saved (e.g. after clicking "Back" from
 * Step 2), so repeated edits never create duplicate events.
 */
export async function updateEvent(eventId: string, input: CreateEventInput): Promise<Event> {
  const response = await apiFetch(`/api/events/${encodeURIComponent(eventId)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as Event;
}

/** Deletes an event the current user owns. Idempotent from the caller's perspective: an already-deleted event returns a 404, surfaced as an `EventApiError`. */
export async function deleteEvent(eventId: string): Promise<void> {
  const response = await apiFetch(`/api/events/${encodeURIComponent(eventId)}`, {
    method: 'DELETE',
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }
}

