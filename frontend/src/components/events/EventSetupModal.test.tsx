import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EventSetupModal } from './EventSetupModal';
import type { Event } from '../../api/eventsApi';
import type { EventStylePreferences } from '../../api/stylePreferencesApi';

const EVENT_ID = '11111111-1111-1111-1111-111111111111';

function renderModal(props: Partial<React.ComponentProps<typeof EventSetupModal>> = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const onClose = props.onClose ?? vi.fn();
  const onCompleted = props.onCompleted ?? vi.fn();
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <div>Marker outside the modal</div>
      <EventSetupModal open onClose={onClose} onCompleted={onCompleted} {...props} />
    </QueryClientProvider>,
  );
  return { ...utils, onClose, onCompleted };
}

function createdEventResponse(overrides: Record<string, unknown> = {}) {
  return new Response(
    JSON.stringify({
      id: EVENT_ID,
      title: 'Birthday party',
      description: null,
      location: '123 Main St',
      startTime: '2026-08-01T18:00:00Z',
      endTime: '2026-08-01T21:00:00Z',
      setting: 'INDOOR',
      dressCode: null,
      createdAt: '2026-07-28T00:00:00Z',
      ...overrides,
    }),
    { status: 201 },
  );
}

function preferencesSavedResponse(overrides: Record<string, unknown> = {}) {
  return new Response(
    JSON.stringify({
      id: '22222222-2222-2222-2222-222222222222',
      eventId: EVENT_ID,
      outfitRequest: 'A navy suit and tie.',
      maxBudget: 500,
      clothingSize: 'M',
      shoeSize: '10',
      preferredStyle: 'CLASSIC',
      preferredColors: [],
      colorsToAvoid: [],
      shoppingDepartment: 'NO_PREFERENCE',
      createdAt: '2026-07-28T00:00:00Z',
      updatedAt: '2026-07-28T00:00:00Z',
      interpretationRefreshRecommended: false,
      ...overrides,
    }),
    { status: 200 },
  );
}

async function fillStep1(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/Title/), 'Birthday party');
  await user.type(screen.getByLabelText(/Location/), '123 Main St');
  await user.type(screen.getByLabelText(/Start date and time/), '2026-08-01T18:00');
  await user.type(screen.getByLabelText(/End date and time/), '2026-08-01T21:00');
}

async function fillStep2(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByLabelText(/^Outfit request/), 'A navy suit and tie.');
  await user.type(screen.getByLabelText(/^Maximum budget/), '500');
  await user.click(screen.getByLabelText(/^Clothing size/));
  await user.click(await screen.findByText('M'));
  await user.click(screen.getByLabelText(/^Shoe size/));
  await user.click(await screen.findByText('10'));
  await user.click(screen.getByLabelText(/^Preferred style/));
  await user.click(await screen.findByText('Classic'));
  // "Shop from" already defaults to "No preference" - no interaction needed.
}

describe('EventSetupModal', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    cleanup();
  });

  it('begins on Step 1 (event details) when creating a new event', () => {
    renderModal();

    expect(screen.getByText('Event details')).toBeInTheDocument();
    expect(screen.getByText('Continue')).toBeInTheDocument();
  });

  it('validates Step 1 required fields and does not call the API', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.click(screen.getByText('Continue'));

    expect(await screen.findByText('Title is required.')).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('Continue creates the event and transitions to Step 2 without a full page reload', async () => {
    vi.mocked(fetch).mockResolvedValue(createdEventResponse());
    const user = userEvent.setup();
    renderModal();

    await fillStep1(user);
    await user.click(screen.getByText('Continue'));

    expect(await screen.findByText('Styling preferences')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/events', expect.objectContaining({ method: 'POST' }));
    // The rest of the page (outside the dialog) is still mounted - no full reload occurred.
    expect(screen.getByText('Marker outside the modal')).toBeInTheDocument();
  });

  it('Back returns to Step 1 and preserves previously entered values', async () => {
    vi.mocked(fetch).mockResolvedValue(createdEventResponse());
    const user = userEvent.setup();
    renderModal();

    await fillStep1(user);
    await user.click(screen.getByText('Continue'));
    await screen.findByText('Styling preferences');

    await user.click(screen.getByText('Back'));

    expect(await screen.findByText('Event details')).toBeInTheDocument();
    expect(screen.getByLabelText(/Title/)).toHaveValue('Birthday party');
    expect(screen.getByLabelText(/Location/)).toHaveValue('123 Main St');
  });

  it('Continue after Back updates the same event instead of creating a duplicate', async () => {
    let postCount = 0;
    let putCount = 0;
    vi.mocked(fetch).mockImplementation((_input, init) => {
      if (init?.method === 'POST') {
        postCount += 1;
        return Promise.resolve(createdEventResponse());
      }
      if (init?.method === 'PUT') {
        putCount += 1;
        return Promise.resolve(createdEventResponse({ title: 'Edited title' }));
      }
      return Promise.resolve(createdEventResponse());
    });
    const user = userEvent.setup();
    renderModal();

    await fillStep1(user);
    await user.click(screen.getByText('Continue'));
    await screen.findByText('Styling preferences');

    await user.click(screen.getByText('Back'));
    await user.click(screen.getByText('Continue'));

    await waitFor(() => expect(putCount).toBe(1));
    expect(postCount).toBe(1);
  });

  it('opens directly on Step 2, prefilled, when an existing event and preferences are supplied', async () => {
    const initialEvent: Event = {
      id: EVENT_ID,
      title: 'Birthday party',
      description: null,
      location: '123 Main St',
      startTime: '2026-08-01T18:00:00Z',
      endTime: '2026-08-01T21:00:00Z',
      setting: 'INDOOR',
      dressCode: null,
      createdAt: '2026-07-28T00:00:00Z',
    };
    const initialPreferences: EventStylePreferences = {
      id: '22222222-2222-2222-2222-222222222222',
      eventId: EVENT_ID,
      outfitRequest: 'A navy suit and tie.',
      maxBudget: 500,
      clothingSize: 'M',
      shoeSize: '10',
      preferredStyle: 'CLASSIC',
      preferredColors: ['navy'],
      colorsToAvoid: [],
      shoppingDepartment: 'NO_PREFERENCE',
      createdAt: '2026-07-28T00:00:00Z',
      updatedAt: '2026-07-28T00:00:00Z',
      interpretationRefreshRecommended: false,
    };

    renderModal({ eventId: EVENT_ID, initialEvent, initialPreferences });

    expect(screen.getByText('Styling preferences')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Outfit request/)).toHaveValue('A navy suit and tie.');
    expect(screen.getByLabelText(/^Maximum budget/)).toHaveValue(500);

    // "Back" shows Step 1 prefilled from the existing event too.
    await userEvent.setup().click(screen.getByText('Back'));
    expect(await screen.findByLabelText(/Title/)).toHaveValue('Birthday party');
  });

  it('saves preferences, shows the updating-interpretation progress state, then closes and completes', async () => {
    let resolveRegenerate: (value: Response) => void = () => {};
    vi.mocked(fetch).mockImplementation((input, init) => {
      const url = input.toString();
      if (url.includes('/interpretation/regenerate')) {
        return new Promise((resolve) => {
          resolveRegenerate = resolve;
        });
      }
      if (url.includes('/recommendations/live/invalidate-stale')) {
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      if (url.includes('/preferences') && init?.method === 'PUT') {
        return Promise.resolve(preferencesSavedResponse({ interpretationRefreshRecommended: true }));
      }
      return Promise.resolve(createdEventResponse());
    });

    const user = userEvent.setup();
    const onCompleted = vi.fn();
    const onClose = vi.fn();
    renderModal({ eventId: EVENT_ID, onCompleted, onClose });

    await fillStep2(user);
    await user.click(screen.getByText('Save and view recommendations'));

    await waitFor(() => {
      expect(document.querySelector('.MuiAlert-message')).toHaveTextContent('Updating interpretation…');
    });

    resolveRegenerate(
      new Response(
        JSON.stringify({
          id: '44444444-4444-4444-4444-444444444444',
          eventId: EVENT_ID,
          occasion: 'DINNER',
          dressCode: 'SMART_CASUAL',
          formalityLevel: 5,
          requiredCategories: [],
          optionalCategories: [],
          preferredColors: [],
          colorsToAvoid: [],
          specialRequirements: [],
          assumptions: [],
          requestedItems: [],
          confidence: 0.5,
          source: 'RULE_BASED_FALLBACK',
          generatedAt: '2026-07-28T12:00:00Z',
          createdAt: '2026-07-28T12:00:00Z',
          updatedAt: '2026-07-28T12:00:00Z',
        }),
        { status: 200 },
      ),
    );

    await waitFor(() => expect(onCompleted).toHaveBeenCalledWith(EVENT_ID));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(fetch).not.toHaveBeenCalledWith(
      expect.stringContaining('/recommendations/live/generate'),
      expect.anything(),
    );
  });

  it('does not regenerate the interpretation when unrelated preferences changed', async () => {
    vi.mocked(fetch).mockImplementation((input, init) => {
      const url = input.toString();
      if (url.includes('/preferences') && init?.method === 'PUT') {
        return Promise.resolve(preferencesSavedResponse({ interpretationRefreshRecommended: false }));
      }
      return Promise.resolve(createdEventResponse());
    });

    const user = userEvent.setup();
    const onCompleted = vi.fn();
    renderModal({ eventId: EVENT_ID, onCompleted });

    await fillStep2(user);
    await user.click(screen.getByText('Save and view recommendations'));

    await waitFor(() => expect(onCompleted).toHaveBeenCalledWith(EVENT_ID));
    expect(fetch).not.toHaveBeenCalledWith(
      expect.stringContaining('/interpretation/regenerate'),
      expect.anything(),
    );
    expect(fetch).not.toHaveBeenCalledWith(
      expect.stringContaining('/recommendations/live/invalidate-stale'),
      expect.anything(),
    );
  });

  it('keeps the modal open and shows an error when saving preferences fails', async () => {
    vi.mocked(fetch).mockImplementation((input, init) => {
      const url = input.toString();
      if (url.includes('/preferences') && init?.method === 'PUT') {
        return Promise.resolve(
          new Response(JSON.stringify({ message: 'Failed to save preferences.', fieldErrors: null }), {
            status: 500,
          }),
        );
      }
      return Promise.resolve(createdEventResponse());
    });

    const user = userEvent.setup();
    const onClose = vi.fn();
    const onCompleted = vi.fn();
    renderModal({ eventId: EVENT_ID, onClose, onCompleted });

    await fillStep2(user);
    await user.click(screen.getByText('Save and view recommendations'));

    expect(await screen.findByText('Failed to save preferences.')).toBeInTheDocument();
    expect(screen.getByText('Styling preferences')).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
    expect(onCompleted).not.toHaveBeenCalled();
  });

  it('disables Continue while the event is being saved', async () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}));
    const user = userEvent.setup();
    renderModal();

    await fillStep1(user);
    await user.click(screen.getByText('Continue'));

    const savingButton = await screen.findByText('Saving…');
    expect(savingButton.closest('button')).toBeDisabled();
  });
});
