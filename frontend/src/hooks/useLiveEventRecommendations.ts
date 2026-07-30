import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  fetchLiveEventRecommendations,
  generateLiveEventRecommendations,
  invalidateStaleLiveEventRecommendations,
  retryMissingLiveEventRecommendations,
} from '../api/liveRecommendationsApi';

const liveEventRecommendationsQueryKey = (eventId: string) => ['events', eventId, 'recommendations', 'live'] as const;

/**
 * Loads the event's current live-Nordstrom outfit recommendations. Never
 * generates anything on its own - repeated calls (e.g. re-opening the
 * page) never trigger a live search; the user must click "Generate Looks"
 * at least once first.
 */
export function useLiveEventRecommendations(eventId: string | undefined) {
  return useQuery({
    queryKey: liveEventRecommendationsQueryKey(eventId ?? ''),
    queryFn: () => fetchLiveEventRecommendations(eventId as string),
    enabled: Boolean(eventId),
  });
}

/**
 * Triggers (re)generation for the "Generate Looks" action and replaces the
 * cached live recommendations with the new result.
 */
export function useGenerateLiveEventRecommendations(eventId: string | undefined) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => {
      if (!eventId) {
        return Promise.reject(new Error('Missing eventId'));
      }
      return generateLiveEventRecommendations(eventId);
    },
    onSuccess: (recommendations) => {
      if (eventId) {
        queryClient.setQueryData(liveEventRecommendationsQueryKey(eventId), recommendations);
      }
    },
  });
}

/**
 * Triggers the "Retry Missing Items" action - re-searches only the
 * categories the latest generation was missing - and replaces the cached
 * live recommendations with the new result.
 */
export function useRetryMissingLiveEventRecommendations(eventId: string | undefined) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => {
      if (!eventId) {
        return Promise.reject(new Error('Missing eventId'));
      }
      return retryMissingLiveEventRecommendations(eventId);
    },
    onSuccess: (recommendations) => {
      if (eventId) {
        queryClient.setQueryData(liveEventRecommendationsQueryKey(eventId), recommendations);
      }
    },
  });
}

/**
 * Marks the event's current live recommendations as stale after the event
 * setup modal saved preferences that changed in an interpretation-relevant
 * way. Never triggers a live search itself; invalidates the cached
 * recommendations so the next view refetches the (now stale-flagged)
 * current state.
 */
export function useInvalidateStaleLiveEventRecommendations(eventId: string | undefined) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => {
      if (!eventId) {
        return Promise.reject(new Error('Missing eventId'));
      }
      return invalidateStaleLiveEventRecommendations(eventId);
    },
    onSuccess: () => {
      if (eventId) {
        queryClient.invalidateQueries({ queryKey: liveEventRecommendationsQueryKey(eventId) });
      }
    },
  });
}
