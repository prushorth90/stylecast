import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthProvider';
import { RequireAuth } from './RequireAuth';

function meResponse(status: number) {
  if (status === 401) {
    return new Response(
      JSON.stringify({ timestamp: '2026-01-01T00:00:00Z', status: 401, error: 'Unauthorized', message: 'Authentication is required', path: '/api/auth/me', fieldErrors: null }),
      { status: 401 },
    );
  }
  return new Response(JSON.stringify({ id: 'user-1', email: 'user@example.com' }), { status: 200 });
}

function renderApp(initialEntry: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route path="/login" element={<div>Login page</div>} />
            <Route element={<RequireAuth />}>
              <Route path="/protected" element={<div>Protected content</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('RequireAuth', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    cleanup();
  });

  it('redirects an unauthenticated user to /login', async () => {
    vi.mocked(fetch).mockResolvedValue(meResponse(401));

    renderApp('/protected');

    await waitFor(() => expect(screen.getByText('Login page')).toBeInTheDocument());
    expect(screen.queryByText('Protected content')).not.toBeInTheDocument();
  });

  it('renders the protected route for an authenticated user', async () => {
    vi.mocked(fetch).mockResolvedValue(meResponse(200));

    renderApp('/protected');

    await waitFor(() => expect(screen.getByText('Protected content')).toBeInTheDocument());
  });

  it('shows a loading indicator while the auth check is in flight', () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}));

    renderApp('/protected');

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });
});
