import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HealthStatus } from './HealthStatus';

function renderWithClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>,
  );
}

describe('HealthStatus', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows a loading state before the request resolves', () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}));

    renderWithClient(<HealthStatus />);

    expect(screen.getByRole('status')).toHaveTextContent(
      'Checking backend status',
    );
  });

  it('shows the backend status once the request succeeds', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ status: 'UP', service: 'stylecast-backend' }), {
        status: 200,
      }),
    );

    renderWithClient(<HealthStatus />);

    await waitFor(() =>
      expect(screen.getByText(/stylecast-backend: UP/)).toBeInTheDocument(),
    );
  });

  it('shows an error state when the request fails', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 500 }));

    renderWithClient(<HealthStatus />);

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(
        'Backend unavailable',
      ),
    );
  });
});
