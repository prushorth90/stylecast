import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CreateEventDialog } from './CreateEventDialog';

function renderWithProviders(onClose: () => void) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <CreateEventDialog open onClose={onClose} />
    </QueryClientProvider>,
  );
}

async function fillRequiredFields(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/Title/), 'Birthday party');
  await user.type(screen.getByLabelText(/Location/), '123 Main St');
  await user.type(screen.getByLabelText(/Start date and time/), '2026-08-01T18:00');
  await user.type(screen.getByLabelText(/End date and time/), '2026-08-01T21:00');
}

describe('CreateEventDialog', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    cleanup();
  });

  it('shows validation errors and does not submit when required fields are empty', async () => {
    const user = userEvent.setup();
    renderWithProviders(vi.fn());

    await user.click(screen.getByText('Save Event'));

    expect(await screen.findByText('Title is required.')).toBeInTheDocument();
    expect(screen.getByText('Location is required.')).toBeInTheDocument();
    expect(screen.getByText('Start date and time are required.')).toBeInTheDocument();
    expect(screen.getByText('End date and time are required.')).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('shows an error when the end time is not after the start time', async () => {
    const user = userEvent.setup();
    renderWithProviders(vi.fn());

    await user.type(screen.getByLabelText(/Title/), 'Birthday party');
    await user.type(screen.getByLabelText(/Location/), '123 Main St');
    await user.type(screen.getByLabelText(/Start date and time/), '2026-08-01T18:00');
    await user.type(screen.getByLabelText(/End date and time/), '2026-08-01T17:00');

    await user.click(screen.getByText('Save Event'));

    expect(await screen.findByText('End time must be after start time.')).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('submits successfully and closes the dialog', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          id: '11111111-1111-1111-1111-111111111111',
          title: 'Birthday party',
          description: null,
          location: '123 Main St',
          startTime: '2026-08-01T18:00:00Z',
          endTime: '2026-08-01T21:00:00Z',
          setting: 'INDOOR',
          dressCode: null,
          createdAt: '2026-07-28T00:00:00Z',
        }),
        { status: 201 },
      ),
    );
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(onClose);

    await fillRequiredFields(user);
    await user.click(screen.getByText('Save Event'));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(fetch).toHaveBeenCalledWith(
      '/api/events',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('disables submission while saving', async () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}));
    const user = userEvent.setup();
    renderWithProviders(vi.fn());

    await fillRequiredFields(user);
    await user.click(screen.getByText('Save Event'));

    const savingButton = await screen.findByText('Saving…');
    expect(savingButton.closest('button')).toBeDisabled();
  });
});
