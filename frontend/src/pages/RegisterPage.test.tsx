import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../components/AuthProvider';
import { RegisterPage } from './RegisterPage';

function meUnauthenticated() {
  return new Response(
    JSON.stringify({ timestamp: '2026-01-01T00:00:00Z', status: 401, error: 'Unauthorized', message: 'Authentication is required', path: '/api/auth/me', fieldErrors: null }),
    { status: 401 },
  );
}

function renderRegisterPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={['/register']}>
          <Routes>
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/" element={<div>Home page</div>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    cleanup();
  });

  it('validates email format and minimum password length', async () => {
    vi.mocked(fetch).mockResolvedValue(meUnauthenticated());
    const user = userEvent.setup();

    renderRegisterPage();
    await waitFor(() => expect(screen.getByText('Register', { selector: 'button' })).toBeInTheDocument());

    await user.type(screen.getByLabelText(/Email/), 'not-an-email');
    await user.type(screen.getByLabelText(/Password/), 'short');
    await user.click(screen.getByText('Register', { selector: 'button' }));

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument();
    expect(screen.getByText('Password must be at least 8 characters')).toBeInTheDocument();
  });

  it('registers, auto-logs-in, and navigates away from /register', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/api/auth/register')) {
        return Promise.resolve(new Response(JSON.stringify({ id: 'user-1', email: 'new@example.com' }), { status: 201 }));
      }
      if (url.includes('/api/auth/login')) {
        return Promise.resolve(new Response(JSON.stringify({ id: 'user-1', email: 'new@example.com' }), { status: 200 }));
      }
      return Promise.resolve(meUnauthenticated());
    });
    const user = userEvent.setup();

    renderRegisterPage();
    await waitFor(() => expect(screen.getByText('Register', { selector: 'button' })).toBeInTheDocument());

    await user.type(screen.getByLabelText(/Email/), 'new@example.com');
    await user.type(screen.getByLabelText(/Password/), 'Password123!');
    await user.click(screen.getByText('Register', { selector: 'button' }));

    await waitFor(() => expect(screen.getByText('Home page')).toBeInTheDocument());
  });

  it('shows an error message for a duplicate email', async () => {
    vi.mocked(fetch).mockImplementation((input) => {
      const url = input.toString();
      if (url.includes('/api/auth/register')) {
        return Promise.resolve(
          new Response(
            JSON.stringify({ timestamp: '2026-01-01T00:00:00Z', status: 409, error: 'Conflict', message: 'An account with that email already exists', path: '/api/auth/register', fieldErrors: null }),
            { status: 409 },
          ),
        );
      }
      return Promise.resolve(meUnauthenticated());
    });
    const user = userEvent.setup();

    renderRegisterPage();
    await waitFor(() => expect(screen.getByText('Register', { selector: 'button' })).toBeInTheDocument());

    await user.type(screen.getByLabelText(/Email/), 'existing@example.com');
    await user.type(screen.getByLabelText(/Password/), 'Password123!');
    await user.click(screen.getByText('Register', { selector: 'button' }));

    expect(await screen.findByText('An account with that email already exists')).toBeInTheDocument();
  });
});
