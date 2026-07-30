import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchEventRecommendations, generateEventRecommendations } from '../api/recommendationsApi';

const eventRecommendationsQueryKey = (eventId: string) => ['events', eventId, 'recommendations'] as const;

/**
 * Loads the event's current outfit recommendations. Unlike weather and
 * occasion interpretation, this never generates anything on its own -
 * repeated calls (e.g. re-opening the page) never regenerate; the user
 * must click "Generate Looks" at least once first.
 */
export function useEventRecommendations(eventId: string | undefined) {
  return useQuery({
    queryKey: eventRecommendationsQueryKey(eventId ?? ''),
    queryFn: () => fetchEventRecommendations(eventId as string),
    enabled: Boolean(eventId),
  });
}

/**
 * Triggers (re)generation for the "Generate Looks" action and replaces the
 * cached recommendations with the new result.
 */
export function useGenerateEventRecommendations(eventId: string | undefined) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => {
      if (!eventId) {
        return Promise.reject(new Error('Missing eventId'));
      }
      return generateEventRecommendations(eventId);
    },
    onSuccess: (recommendations) => {
      if (eventId) {
        queryClient.setQueryData(eventRecommendationsQueryKey(eventId), recommendations);
      }
    },
  });
}
