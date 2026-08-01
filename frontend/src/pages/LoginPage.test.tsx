import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../components/AuthProvider';
import { LoginPage } from './LoginPage';

function meUnauthenticated() {
  return new Response(
    JSON.stringify({ timestamp: '2026-01-01T00:00:00Z', status: 401, error: 'Unauthorized', message: 'Authentication is required', path: '/api/auth/me', fieldErrors: null }),
    { status: 401 },
  );
}

function renderLoginPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<div>Home page</div>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    cleanup();
  });

  it('shows validation errors when submitted empty', async () => {
    vi.mocked(fetch).mockResolvedValue(meUnauthenticated());
    const user = userEvent.setup();

    renderLoginPage();
    await waitFor(() => expect(screen.getByText('Log in', { selector: 'h1' })).toBeInTheDocument());

    await user.click(screen.getByText('Log in', { selector: 'button' }));

    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
  });

  it('logs in successfully and navigates away from /login', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/api/auth/login')) {
        return Promise.resolve(new Response(JSON.stringify({ id: 'user-1', email: 'user@example.com' }), { status: 200 }));
      }
      return Promise.resolve(meUnauthenticated());
    });
    const user = userEvent.setup();

    renderLoginPage();
    await waitFor(() => expect(screen.getByText('Log in', { selector: 'button' })).toBeInTheDocument());

    await user.type(screen.getByLabelText(/Email/), 'user@example.com');
    await user.type(screen.getByLabelText(/Password/), 'Password123!');
    await user.click(screen.getByText('Log in', { selector: 'button' }));

    await waitFor(() => expect(screen.getByText('Home page')).toBeInTheDocument());
  });

  it('shows an error message for invalid credentials', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/api/auth/login')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({ timestamp: '2026-01-01T00:00:00Z', status: 401, error: 'Unauthorized', message: 'Invalid email or password', path: '/api/auth/login', fieldErrors: null }),
            { status: 401 },
          ),
        );
      }
      return Promise.resolve(meUnauthenticated());
    });
    const user = userEvent.setup();

    renderLoginPage();
    await waitFor(() => expect(screen.getByText('Log in', { selector: 'button' })).toBeInTheDocument());

    await user.type(screen.getByLabelText(/Email/), 'user@example.com');
    await user.type(screen.getByLabelText(/Password/), 'WrongPassword1!');
    await user.click(screen.getByText('Log in', { selector: 'button' }));

    expect(await screen.findByText('Invalid email or password')).toBeInTheDocument();
  });
});
