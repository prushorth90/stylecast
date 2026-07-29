import { EventApiError, type ApiErrorBody } from './eventsApi';

export type OccasionType =
  | 'WEDDING'
  | 'INTERVIEW'
  | 'BUSINESS_MEETING'
  | 'NETWORKING'
  | 'DINNER'
  | 'DATE'
  | 'CONCERT'
  | 'PARTY'
  | 'CONFERENCE'
  | 'CASUAL_OUTING'
  | 'FORMAL_EVENT'
  | 'UNKNOWN';

export type InterpretedDressCode =
  | 'CASUAL'
  | 'SMART_CASUAL'
  | 'BUSINESS_CASUAL'
  | 'COCKTAIL'
  | 'GARDEN_COCKTAIL'
  | 'BUSINESS_FORMAL'
  | 'FORMAL'
  | 'BLACK_TIE'
  | 'UNKNOWN';

export type ProductCategory =
  | 'BLAZER'
  | 'SUIT'
  | 'SHIRT'
  | 'POLO'
  | 'TROUSERS'
  | 'DRESS'
  | 'SKIRT'
  | 'SHOES'
  | 'OUTERWEAR'
  | 'ACCESSORY';

export type SpecialRequirement =
  | 'OUTDOOR_SUITABLE'
  | 'RAIN_SUITABLE'
  | 'HOT_WEATHER_SUITABLE'
  | 'COLD_WEATHER_SUITABLE'
  | 'GRASS_FRIENDLY_FOOTWEAR'
  | 'COMFORTABLE_FOR_WALKING'
  | 'NOT_OVERLY_FORMAL'
  | 'LAYER_RECOMMENDED';

export type InterpretationSource = 'AI' | 'RULE_BASED_FALLBACK';

/**
 * An event's occasion interpretation, as returned by the backend. This task
 * only classifies the occasion - it never contains product names, URLs,
 * prices, or inventory.
 */
export interface OccasionInterpretation {
  id: string;
  eventId: string;
  occasion: OccasionType;
  dressCode: InterpretedDressCode;
  formalityLevel: number;
  requiredCategories: ProductCategory[];
  optionalCategories: ProductCategory[];
  preferredColors: string[];
  colorsToAvoid: string[];
  specialRequirements: SpecialRequirement[];
  assumptions: string[];
  confidence: number;
  source: InterpretationSource;
  generatedAt: string;
  createdAt: string;
  updatedAt: string;
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
 * Fetches the event's occasion interpretation, automatically. The backend
 * classifies and persists it on first call, and returns the saved
 * interpretation as-is on every later call - no manual action is required
 * before this returns real data.
 */
export async function fetchEventOccasionInterpretation(eventId: string): Promise<OccasionInterpretation> {
  const response = await fetch(`/api/events/${encodeURIComponent(eventId)}/interpretation`);

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as OccasionInterpretation;
}

/**
 * Triggers a fresh classification for an event and returns the updated
 * interpretation, replacing the previously saved one.
 */
export async function regenerateEventOccasionInterpretation(
  eventId: string,
): Promise<OccasionInterpretation> {
  const response = await fetch(`/api/events/${encodeURIComponent(eventId)}/interpretation/regenerate`, {
    method: 'POST',
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as OccasionInterpretation;
}
