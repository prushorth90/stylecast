import { EventApiError, type ApiErrorBody } from './eventsApi';
import { apiFetch } from './httpClient';
import type { ProductCategory } from './occasionApi';

export type RecommendationStatus = 'ACTIVE' | 'SUPERSEDED' | 'NO_VALID_OUTFIT';

/**
 * Task 7A only ever produces LOCAL_CATALOG - the deterministic engine reads
 * exclusively from the locally seeded (fictional) product catalog and never
 * calls a live retail provider or an LLM.
 */
export type RecommendationSource = 'LOCAL_CATALOG';

/** One catalog product+variant selected for an outfit recommendation. */
export interface OutfitItem {
  id: string;
  productId: string;
  productVariantId: string;
  category: ProductCategory;
  brand: string;
  name: string;
  color: string;
  size: string;
  itemPrice: number;
  displayOrder: number;
  imageUrl: string | null;
}

/** One deterministically generated, scored outfit recommendation. */
export interface OutfitRecommendation {
  id: string;
  eventId: string;
  generation: number;
  rank: number | null;
  name: string;
  status: RecommendationStatus;
  source: RecommendationSource;
  totalPrice: number;
  occasionFitScore: number;
  weatherFitScore: number;
  styleFitScore: number;
  colorFitScore: number;
  budgetEfficiencyScore: number;
  completenessScore: number;
  overallScore: number;
  explanation: string | null;
  generatedAt: string;
  items: OutfitItem[];
}

/**
 * An event's current outfit recommendations (the latest generation only).
 * `hasResults: false` is a normal, expected outcome - not an error -
 * whenever no valid outfit could be assembled, or nothing has been
 * generated yet.
 */
export interface RecommendationsResponse {
  eventId: string;
  generation: number;
  generatedAt: string | null;
  hasResults: boolean;
  noResultReason: string | null;
  recommendations: OutfitRecommendation[];
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
 * Fetches the event's current recommendations. Never triggers generation -
 * a repeated call never regenerates. Returns `hasResults: false` (not an
 * error) when nothing has been generated for this event yet.
 */
export async function fetchEventRecommendations(eventId: string): Promise<RecommendationsResponse> {
  const response = await apiFetch(`/api/events/${encodeURIComponent(eventId)}/recommendations`);

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as RecommendationsResponse;
}

/**
 * Generates (or regenerates) up to three ranked outfit recommendations from
 * the local product catalog only. Throws an `EventApiError` with `status
 * 409` when the event has no saved styling preferences or occasion
 * interpretation yet - callers should surface `error.message` directly, it
 * is already a clear, actionable explanation.
 */
export async function generateEventRecommendations(eventId: string): Promise<RecommendationsResponse> {
  const response = await apiFetch(`/api/events/${encodeURIComponent(eventId)}/recommendations/generate`, {
    method: 'POST',
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as RecommendationsResponse;
}
