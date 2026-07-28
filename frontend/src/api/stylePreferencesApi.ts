import { EventApiError, type ApiErrorBody } from './eventsApi';

export type PreferredStyle = 'CLASSIC' | 'MODERN' | 'MINIMAL' | 'BOLD' | 'CASUAL' | 'FORMAL';

/**
 * An event's saved styling preferences, as returned by the backend.
 */
export interface EventStylePreferences {
  id: string;
  eventId: string;
  outfitRequest: string;
  maxBudget: number;
  clothingSize: string;
  shoeSize: string;
  preferredStyle: PreferredStyle;
  preferredColors: string[];
  colorsToAvoid: string[];
  createdAt: string;
  updatedAt: string;
}

export interface SaveEventStylePreferencesInput {
  outfitRequest: string;
  maxBudget: number;
  clothingSize: string;
  shoeSize: string;
  preferredStyle: PreferredStyle;
  preferredColors: string[];
  colorsToAvoid: string[];
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
 * Fetches saved styling preferences for an event.
 *
 * Returns `null` when the event has no saved preferences yet (backend
 * responds 404), which callers should treat as "show a blank form" rather
 * than an error. Any other non-2xx response is thrown as an
 * {@link EventApiError}.
 */
export async function fetchEventStylePreferences(
  eventId: string,
): Promise<EventStylePreferences | null> {
  const response = await fetch(`/api/events/${encodeURIComponent(eventId)}/preferences`);

  if (response.status === 404) {
    return null;
  }

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as EventStylePreferences;
}

/**
 * Creates or updates the styling preferences for an event (upsert). Safe to
 * call repeatedly for the same event without creating duplicate records.
 */
export async function saveEventStylePreferences(
  eventId: string,
  input: SaveEventStylePreferencesInput,
): Promise<EventStylePreferences> {
  const response = await fetch(`/api/events/${encodeURIComponent(eventId)}/preferences`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as EventStylePreferences;
}
