import { apiFetch } from './httpClient';

export interface AuthUser {
  id: string;
  email: string;
}

export interface RegisterInput {
  email: string;
  password: string;
}

export interface LoginInput {
  email: string;
  password: string;
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
 * Error thrown by the auth API client when a request fails, carrying the
 * backend's structured error body (when available) so callers can surface
 * a specific message rather than a generic failure.
 */
export class AuthApiError extends Error {
  readonly status: number;
  readonly fieldErrors: ApiFieldError[] | null;

  constructor(message: string, status: number, fieldErrors: ApiFieldError[] | null = null) {
    super(message);
    this.name = 'AuthApiError';
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

  throw new AuthApiError(
    body?.message ?? `Request failed with status ${response.status}`,
    response.status,
    body?.fieldErrors ?? null,
  );
}

export async function register(input: RegisterInput): Promise<AuthUser> {
  const response = await apiFetch('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as AuthUser;
}

export async function login(input: LoginInput): Promise<AuthUser> {
  const response = await apiFetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as AuthUser;
}

export async function logout(): Promise<void> {
  const response = await apiFetch('/api/auth/logout', { method: 'POST' });

  if (!response.ok) {
    return parseErrorResponse(response);
  }
}

/**
 * Checks whether the browser currently has a valid session. Returns `null`
 * for an unauthenticated caller (401) instead of throwing - this is the
 * normal "not logged in yet" case, not an error, and must never trigger the
 * global 401 handler (which is for a session that expires mid-use).
 */
export async function getCurrentUser(): Promise<AuthUser | null> {
  const response = await apiFetch('/api/auth/me', { skipUnauthorizedHandling: true });

  if (response.status === 401) {
    return null;
  }

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return (await response.json()) as AuthUser;
}
