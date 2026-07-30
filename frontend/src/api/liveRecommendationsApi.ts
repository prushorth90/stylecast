import { EventApiError, type ApiErrorBody } from './eventsApi';
import type { GenericItemCategory, ProductCategory } from './occasionApi';

export type { GenericItemCategory };

/**
 * Lightweight summary of one explicit requested item (Task 8.5), used only
 * for the `foundRequestedItems`/`missingRequestedItems` lists below - use
 * `originalPhrase` for display, never the raw `genericCategory` enum alone.
 */
export interface RequestedItemSummary {
  id: string;
  originalPhrase: string;
  genericCategory: GenericItemCategory;
  activityContext: string | null;
}

export type LiveRecommendationStatus = 'ACTIVE' | 'SUPERSEDED' | 'NO_VALID_OUTFIT';

/** Task 8 only ever produces LIVE_NORDSTROM live recommendations. */
export type LiveRecommendationSource = 'LIVE_NORDSTROM';

/**
 * Overall completeness of one live-Nordstrom recommendation generation:
 * - `COMPLETE`: every required category found candidates.
 * - `PARTIAL`: some required categories found candidates and some did not -
 *   `recommendations` still contains valid Nordstrom candidates for the
 *   categories that succeeded, but must never be presented as a complete
 *   outfit - see `missingCategories`/`message`.
 * - `NO_RESULTS`: every required category was searched successfully but
 *   none found anything - a normal, non-error outcome.
 * - `PROVIDER_UNAVAILABLE`: every attempted search failed at the provider
 *   level (a transient outage) - retrying (including via "Retry Missing
 *   Items") may help.
 */
export type LiveRecommendationCompleteness = 'COMPLETE' | 'PARTIAL' | 'NO_RESULTS' | 'PROVIDER_UNAVAILABLE';

/**
 * Normalized department/audience classification for a candidate - `'UNKNOWN'`
 * whenever no trustworthy department signal (title, breadcrumb/structured
 * metadata) was found; never inferred from an image. The frontend should
 * simply omit any department/audience display in that case, never guess one.
 */
export type CandidateAudience = 'MEN' | 'WOMEN' | 'UNISEX' | 'UNKNOWN';

/**
 * One live Nordstrom product candidate selected for an outfit. Fields the
 * enrichment step could not independently confirm for the exact product
 * page stay `null` (or empty for `availableSizes`), and the corresponding
 * `priceVerified`/`sizeVerified`/`availabilityVerified` flag stays `false` -
 * render those as "unverified"/"not verified", never as a confirmed value.
 */
export interface LiveOutfitItem {
  id: string;
  /** `null` when this item came from an explicit requested item instead (Task 8.5) - see `requestedItemPhrase`. */
  category: ProductCategory | null;
  retailer: 'NORDSTROM';
  title: string | null;
  brand: string | null;
  productUrl: string;
  imageUrl: string | null;
  price: number | null;
  originalPrice: number | null;
  currency: string | null;
  priceVerified: boolean;
  color: string | null;
  requestedSize: string | null;
  availableSizes: string[];
  sizeVerified: boolean;
  stockText: string | null;
  availabilityVerified: boolean;
  audience: CandidateAudience;
  /** The user's own phrase (e.g. "USA soccer jersey") when this item fulfills an explicit requested item; `null` for the legacy category pipeline. Prefer this over `category` for display when present. */
  requestedItemPhrase: string | null;
  requestedItemGenericCategory: GenericItemCategory | null;
  sourceCitation: string | null;
  displayOrder: number;
}

/** One assembled outfit sourced from live nordstrom.com search candidates. */
export interface LiveOutfitRecommendation {
  id: string;
  eventId: string;
  generation: number;
  rank: number | null;
  name: string;
  status: LiveRecommendationStatus;
  source: LiveRecommendationSource;
  explanation: string | null;
  generatedAt: string;
  items: LiveOutfitItem[];
}

/**
 * An event's current live outfit recommendations (the latest generation
 * only). See `LiveRecommendationCompleteness` for what `status` means.
 * `hasResults`/`noResultReason` no longer exist - use `status`/
 * `foundCategories`/`missingCategories`/`message` instead. A live-search
 * provider failure now surfaces as `status: 'PROVIDER_UNAVAILABLE'` in a
 * normal (200) response, not as a thrown error.
 */
export interface LiveRecommendationsResponse {
  eventId: string;
  generation: number;
  generatedAt: string | null;
  status: LiveRecommendationCompleteness;
  foundCategories: ProductCategory[];
  missingCategories: ProductCategory[];
  /** Populated instead of foundCategories/missingCategories whenever explicit requested items exist (Task 8.5) - the two pairs are mutually exclusive per generation. */
  foundRequestedItems: RequestedItemSummary[];
  missingRequestedItems: RequestedItemSummary[];
  message: string | null;
  recommendations: LiveOutfitRecommendation[];
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
 * Fetches the event's current live recommendations. Never triggers a live
 * search - a repeated call never regenerates. `status: 'NO_RESULTS'` with
 * `generation: 0` (not an error) when nothing has been generated for this
 * event yet.
 */
export async function fetchLiveEventRecommendations(eventId: string): Promise<LiveRecommendationsResponse> {
  const response = await fetch(`/api/events/${encodeURIComponent(eventId)}/recommendations/live`);

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as LiveRecommendationsResponse;
}

/**
 * Searches every required category (independently) and persists the
 * result as a new generation. Throws an `EventApiError` with `status 409`
 * when the event has no saved styling preferences or occasion
 * interpretation yet - callers should surface `error.message` directly.
 * A live-search failure never throws here - it surfaces as
 * `status: 'PROVIDER_UNAVAILABLE'` in the returned response instead.
 */
export async function generateLiveEventRecommendations(eventId: string): Promise<LiveRecommendationsResponse> {
  const response = await fetch(`/api/events/${encodeURIComponent(eventId)}/recommendations/live/generate`, {
    method: 'POST',
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as LiveRecommendationsResponse;
}

/**
 * Re-searches only the categories the latest generation was missing,
 * reusing the candidates already found for every other category - bounds
 * the added API cost of a retry to just the gap. A no-op (current state
 * returned unchanged, no search calls) when nothing was missing.
 */
export async function retryMissingLiveEventRecommendations(eventId: string): Promise<LiveRecommendationsResponse> {
  const response = await fetch(`/api/events/${encodeURIComponent(eventId)}/recommendations/live/retry-missing`, {
    method: 'POST',
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as LiveRecommendationsResponse;
}

/**
 * Marks the event's current live recommendations as stale, without
 * calling the live search provider - used by the event setup modal after
 * saving preferences that changed in an interpretation-relevant way. Never
 * triggers a live Nordstrom search.
 */
export async function invalidateStaleLiveEventRecommendations(eventId: string): Promise<void> {
  const response = await fetch(
    `/api/events/${encodeURIComponent(eventId)}/recommendations/live/invalidate-stale`,
    { method: 'POST' },
  );

  if (!response.ok) {
    return parseErrorResponse(response);
  }
}
