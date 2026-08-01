import { apiFetch } from './httpClient';

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

export type OccasionTag =
  | 'WEDDING'
  | 'INTERVIEW'
  | 'DINNER'
  | 'NETWORKING'
  | 'CONCERT'
  | 'CASUAL'
  | 'FORMAL_EVENT';

export type StyleTag = 'CLASSIC' | 'MODERN' | 'MINIMAL' | 'BOLD' | 'CASUAL' | 'FORMAL';

export type WeatherTag = 'HOT' | 'MILD' | 'COLD' | 'RAIN' | 'WIND';

/**
 * A product as returned by the catalog list/search endpoint. Lighter than
 * {@link ProductDetail}: summarizes variants (available sizes/colors,
 * starting price, in-stock) rather than listing every one.
 */
export interface ProductSummary {
  id: string;
  brand: string;
  name: string;
  category: ProductCategory;
  startingPrice: number;
  imageUrl: string | null;
  formalityLevel: number;
  availableSizes: string[];
  availableColors: string[];
  occasionTags: OccasionTag[];
  styleTags: StyleTag[];
  weatherTags: WeatherTag[];
  inStock: boolean;
}

export interface ProductVariant {
  id: string;
  sku: string;
  clothingSize: string;
  color: string;
  effectivePrice: number;
  quantityAvailable: number;
  inStock: boolean;
}

export interface ProductDetail {
  id: string;
  brand: string;
  name: string;
  description: string | null;
  category: ProductCategory;
  basePrice: number;
  imageUrl: string | null;
  formalityLevel: number;
  occasionTags: OccasionTag[];
  styleTags: StyleTag[];
  weatherTags: WeatherTag[];
  variants: ProductVariant[];
  inStock: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProductPage {
  content: ProductSummary[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Optional filters for {@link fetchProducts}. Undefined fields are omitted
 * from the request query string entirely (rather than sent as empty
 * strings), matching the backend's "absent means unfiltered" semantics.
 */
export interface CatalogFilters {
  category?: ProductCategory;
  clothingSize?: string;
  color?: string;
  maxPrice?: number;
  preferredStyle?: StyleTag;
  occasion?: OccasionTag;
  weather?: WeatherTag;
  minimumFormality?: number;
  maximumFormality?: number;
  inStock?: boolean;
  page?: number;
  pageSize?: number;
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
 * Error thrown by the catalog API client when a request fails, carrying
 * the backend's structured error body (when available) so callers can
 * surface a specific message rather than a generic failure.
 */
export class CatalogApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'CatalogApiError';
    this.status = status;
  }
}

async function parseErrorResponse(response: Response): Promise<never> {
  let body: ApiErrorBody | null = null;
  try {
    body = (await response.json()) as ApiErrorBody;
  } catch {
    // Response body wasn't JSON (or was empty); fall back to a generic message.
  }

  throw new CatalogApiError(body?.message ?? `Request failed with status ${response.status}`, response.status);
}

function buildQueryString(filters: CatalogFilters): string {
  const params = new URLSearchParams();

  if (filters.category) params.set('category', filters.category);
  if (filters.clothingSize) params.set('clothingSize', filters.clothingSize);
  if (filters.color) params.set('color', filters.color);
  if (filters.maxPrice !== undefined) params.set('maxPrice', String(filters.maxPrice));
  if (filters.preferredStyle) params.set('preferredStyle', filters.preferredStyle);
  if (filters.occasion) params.set('occasion', filters.occasion);
  if (filters.weather) params.set('weather', filters.weather);
  if (filters.minimumFormality !== undefined) params.set('minimumFormality', String(filters.minimumFormality));
  if (filters.maximumFormality !== undefined) params.set('maximumFormality', String(filters.maximumFormality));
  if (filters.inStock) params.set('inStock', 'true');
  if (filters.page !== undefined) params.set('page', String(filters.page));
  if (filters.pageSize !== undefined) params.set('pageSize', String(filters.pageSize));

  const query = params.toString();
  return query ? `?${query}` : '';
}

/**
 * Fetches a page of catalog products matching the given filters.
 *
 * Uses a relative path so it works both in local development (proxied by
 * Vite to the backend) and in Docker (proxied by Nginx).
 */
export async function fetchProducts(filters: CatalogFilters): Promise<ProductPage> {
  const response = await apiFetch(`/api/products${buildQueryString(filters)}`);

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as ProductPage;
}

export async function fetchProductById(productId: string): Promise<ProductDetail> {
  const response = await apiFetch(`/api/products/${encodeURIComponent(productId)}`);

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as ProductDetail;
}
