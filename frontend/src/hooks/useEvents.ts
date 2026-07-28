import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  createEvent,
  fetchEventById,
  fetchUpcomingEvents,
  type CreateEventInput,
} from '../api/eventsApi';

const eventsQueryKey = ['events'] as const;
const eventQueryKey = (eventId: string) => ['events', eventId] as const;

export function useUpcomingEvents() {
  return useQuery({
    queryKey: eventsQueryKey,
    queryFn: fetchUpcomingEvents,
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
