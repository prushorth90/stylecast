import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  fetchEventOccasionInterpretation,
  regenerateEventOccasionInterpretation,
} from '../api/occasionApi';

const eventOccasionQueryKey = (eventId: string) => ['events', eventId, 'interpretation'] as const;

/**
 * Loads the event's occasion interpretation automatically - the backend
 * generates and persists one on first call, so no user action is required
 * before this returns data.
 */
export function useEventOccasionInterpretation(eventId: string | undefined) {
  return useQuery({
    queryKey: eventOccasionQueryKey(eventId ?? ''),
    queryFn: () => fetchEventOccasionInterpretation(eventId as string),
    enabled: Boolean(eventId),
  });
}

/**
 * Forces a fresh classification for the "Regenerate Interpretation" action
 * and replaces the cached interpretation with the new result.
 */
export function useRegenerateEventOccasionInterpretation(eventId: string | undefined) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => {
      if (!eventId) {
        return Promise.reject(new Error('Missing eventId'));
      }
      return regenerateEventOccasionInterpretation(eventId);
    },
    onSuccess: (interpretation) => {
      if (eventId) {
        queryClient.setQueryData(eventOccasionQueryKey(eventId), interpretation);
      }
    },
  });
}
