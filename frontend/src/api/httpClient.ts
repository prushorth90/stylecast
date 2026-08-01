/**
 * Thin wrapper around the browser `fetch` used by every API module instead
 * of calling `fetch` directly. Two responsibilities:
 *
 * 1. Attaches the CSRF header (`X-XSRF-TOKEN`, read from the non-HttpOnly
 *    `XSRF-TOKEN` cookie the backend sets) to every unsafe (non-GET/HEAD/
 *    OPTIONS) request - required by the backend's session-cookie
 *    authentication (see `SecurityConfig`). The session cookie itself is
 *    HttpOnly and never touched by JS at all; only the CSRF token cookie
 *    is JS-readable, by design.
 * 2. Notifies a registered handler whenever a request comes back 401, so
 *    `AuthProvider` can clear stale authentication state and redirect to
 *    `/login` (requirement: a session expiring mid-use should never leave
 *    the app showing stale/broken authenticated UI).
 */

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

/** Registered once by `AuthProvider` on mount. */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  for (const part of document.cookie.split('; ')) {
    if (part.startsWith(prefix)) {
      return decodeURIComponent(part.slice(prefix.length));
    }
  }
  return null;
}

export interface ApiFetchInit extends RequestInit {
  /**
   * Skip the global 401 handler. Used only by the "am I logged in" check
   * itself, where a 401 is a normal, expected result (not a session that
   * just expired mid-use) and should not trigger a redirect loop.
   */
  skipUnauthorizedHandling?: boolean;
}

export async function apiFetch(input: string, init: ApiFetchInit = {}): Promise<Response> {
  const { skipUnauthorizedHandling, ...requestInit } = init;
  const method = (requestInit.method ?? 'GET').toUpperCase();

  const headers = new Headers(requestInit.headers);
  if (!SAFE_METHODS.has(method)) {
    const csrfToken = readCookie('XSRF-TOKEN');
    if (csrfToken) {
      headers.set('X-XSRF-TOKEN', csrfToken);
    }
  }

  const response = await fetch(input, { ...requestInit, headers });

  if (response.status === 401 && !skipUnauthorizedHandling) {
    unauthorizedHandler?.();
  }

  return response;
}
