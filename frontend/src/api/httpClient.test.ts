import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { apiFetch, setUnauthorizedHandler } from './httpClient';

describe('apiFetch', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 200 })));
    document.cookie = 'XSRF-TOKEN=; Max-Age=0';
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    setUnauthorizedHandler(null);
    document.cookie = 'XSRF-TOKEN=; Max-Age=0';
  });

  it('does not attach a CSRF header to a GET request', async () => {
    await apiFetch('/api/events');

    const [, init] = vi.mocked(fetch).mock.calls[0];
    const headers = new Headers(init?.headers);
    expect(headers.has('X-XSRF-TOKEN')).toBe(false);
  });

  it('attaches the CSRF header (read from the XSRF-TOKEN cookie) to a POST request', async () => {
    document.cookie = 'XSRF-TOKEN=abc123';

    await apiFetch('/api/events', { method: 'POST' });

    const [, init] = vi.mocked(fetch).mock.calls[0];
    const headers = new Headers(init?.headers);
    expect(headers.get('X-XSRF-TOKEN')).toBe('abc123');
  });

  it('invokes the registered unauthorized handler on a 401 response', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 401 }));
    const handler = vi.fn();
    setUnauthorizedHandler(handler);

    await apiFetch('/api/events');

    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('does not invoke the unauthorized handler when skipUnauthorizedHandling is set', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 401 }));
    const handler = vi.fn();
    setUnauthorizedHandler(handler);

    await apiFetch('/api/auth/me', { skipUnauthorizedHandling: true });

    expect(handler).not.toHaveBeenCalled();
  });
});
