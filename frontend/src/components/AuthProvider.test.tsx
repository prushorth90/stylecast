import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthProvider';
import { useAuth } from '../hooks/useAuth';

function meResponse(status: 200 | 401) {
  if (status === 401) {
    return new Response(
      JSON.stringify({ timestamp: '2026-01-01T00:00:00Z', status: 401, error: 'Unauthorized', message: 'Authentication is required', path: '/api/auth/me', fieldErrors: null }),
      { status: 401 },
    );
  }
  return new Response(JSON.stringify({ id: 'user-1', email: 'user@example.com' }), { status: 200 });
}

function Probe() {
  const { user, isLoading, logout } = useAuth();
  if (isLoading) {
    return <div role="status">Loading…</div>;
  }
  return (
    <div>
      <div data-testid="user">{user ? user.email : 'anonymous'}</div>
      <button onClick={() => void logout()}>Log out</button>
    </div>
  );
}

function renderProbe() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Probe />
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('AuthProvider', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    cleanup();
  });

  it('restores the current user from /api/auth/me on mount (session survives refresh)', async () => {
    vi.mocked(fetch).mockResolvedValue(meResponse(200));

    renderProbe();

    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('user@example.com'));
  });

  it('reports anonymous when there is no valid session', async () => {
    vi.mocked(fetch).mockResolvedValue(meResponse(401));

    renderProbe();

    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('anonymous'));
  });

  it('clears the current user after logout', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/api/auth/logout')) {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      return Promise.resolve(meResponse(200));
    });

    const { user } = await import('@testing-library/user-event').then((m) => ({ user: m.default.setup() }));
    renderProbe();

    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('user@example.com'));

    await user.click(screen.getByText('Log out'));

    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('anonymous'));
  });
});
