import { EventApiError, type ApiErrorBody } from './eventsApi';
import { apiFetch } from './httpClient';

export type WeatherAvailabilityStatus = 'AVAILABLE' | 'FORECAST_UNAVAILABLE';

/**
 * An event's latest weather snapshot, as returned by the backend. Weather
 * measurement fields are `null` when `status` is `'FORECAST_UNAVAILABLE'` -
 * never a fabricated value.
 *
 * `stale` is `true` when an automatic background refresh failed and this is
 * the last known-good snapshot instead (see `staleWarning` for why).
 */
export interface EventWeather {
  id: string;
  eventId: string;
  status: WeatherAvailabilityStatus;
  resolvedLocation: string | null;
  latitude: number | null;
  longitude: number | null;
  temperatureAtStart: number | null;
  temperatureAtEnd: number | null;
  precipitationProbability: number | null;
  windSpeed: number | null;
  condition: string | null;
  forecastStart: string | null;
  forecastEnd: string | null;
  retrievedAt: string;
  providerName: string | null;
  message: string | null;
  stale: boolean;
  staleWarning: string | null;
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
 * Fetches the event's weather, automatically. The backend fetches from the
 * provider and persists a snapshot on first call, transparently refreshes a
 * stale saved snapshot, and returns a saved fresh snapshot as-is otherwise -
 * no manual refresh is required before this returns real data. Throws an
 * {@link EventApiError} on failure (e.g. 422 for an unresolvable location,
 * 503 for a temporary provider failure with no previous snapshot to fall
 * back to).
 */
export async function fetchEventWeather(eventId: string): Promise<EventWeather> {
  const response = await apiFetch(`/api/events/${encodeURIComponent(eventId)}/weather`);

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as EventWeather;
}

/**
 * Triggers a fresh weather lookup for an event and returns the new snapshot.
 * Throws an {@link EventApiError} on failure (e.g. 422 for an unresolvable
 * location, 503 for a temporary provider failure).
 */
export async function refreshEventWeather(eventId: string): Promise<EventWeather> {
  const response = await apiFetch(`/api/events/${encodeURIComponent(eventId)}/weather/refresh`, {
    method: 'POST',
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as EventWeather;
}
