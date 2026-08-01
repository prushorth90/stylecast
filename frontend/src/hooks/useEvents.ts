import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createEvent,
  fetchEventById,
  fetchEventHistory,
  fetchUpcomingEvents,
  updateEvent,
  type CreateEventInput,
} from '../api/eventsApi';

const eventsQueryKey = ['events'] as const;
const eventHistoryQueryKey = ['events', 'history'] as const;
const eventQueryKey = (eventId: string) => ['events', eventId] as const;

export function useUpcomingEvents() {
  return useQuery({
    queryKey: eventsQueryKey,
    queryFn: fetchUpcomingEvents,
  });
}

/** Every one of the current user's events (past and upcoming) - backs the saved history page. */
export function useEventHistory() {
  return useQuery({
    queryKey: eventHistoryQueryKey,
    queryFn: fetchEventHistory,
  });
}

export function useEvent(eventId: string | undefined) {
  return useQuery({
    queryKey: eventQueryKey(eventId ?? ''),
    queryFn: () => fetchEventById(eventId as string),
    enabled: Boolean(eventId),
  });
}

export function useCreateEvent() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: CreateEventInput) => createEvent(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: eventsQueryKey });
    },
  });
}

/**
 * Updates an already-saved event's details (Step 1 "Continue" when
 * re-editing rather than creating). Replaces both the single-event cache
 * entry and invalidates the upcoming-events list so any changed
 * title/date/location is reflected immediately.
 */
export function useUpdateEvent(eventId: string | undefined) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: CreateEventInput) => {
      if (!eventId) {
        return Promise.reject(new Error('Missing eventId'));
      }
      return updateEvent(eventId, input);
    },
    onSuccess: (updated) => {
      if (eventId) {
        queryClient.setQueryData(eventQueryKey(eventId), updated);
      }
      queryClient.invalidateQueries({ queryKey: eventsQueryKey });
    },
  });
}
