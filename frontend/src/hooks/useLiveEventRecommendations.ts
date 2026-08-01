import { useCallback, useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  fetchLiveEventRecommendationJobStatus,
  fetchLiveEventRecommendations,
  invalidateStaleLiveEventRecommendations,
  retryMissingLiveEventRecommendations,
  startLiveEventRecommendationGeneration,
  type LiveRecommendationsResponse,
} from '../api/liveRecommendationsApi';

/** How often the frontend polls job status while QUEUED/PROCESSING - the backend never blocks on this, so this is purely a UI refresh cadence. */
const JOB_POLL_INTERVAL_MS = 2500;

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
 * Triggers the "Generate Looks" action for the async job flow: starts a
 * background job (`POST .../generate`, HTTP 202, returns immediately),
 * polls `GET .../status` every few seconds until it reaches a terminal
 * state, then fetches the actual recommendations and replaces the cached
 * value. Never starts a second concurrent job for the same event while one
 * is still `QUEUED`/`PROCESSING` (the button is expected to be disabled via
 * `isPending` in the meantime; the backend also independently de-duplicates
 * if this is somehow bypassed).
 */
export function useGenerateLiveEventRecommendations(eventId: string | undefined) {
  const queryClient = useQueryClient();
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [data, setData] = useState<LiveRecommendationsResponse | undefined>(undefined);
  const activeJobIdRef = useRef<string | null>(null);
  const pollTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pollRef = useRef<(currentEventId: string, jobId: string) => void>(() => {});

  useEffect(() => {
    return () => {
      activeJobIdRef.current = null;
      if (pollTimeoutRef.current) {
        clearTimeout(pollTimeoutRef.current);
      }
    };
  }, []);

  useEffect(() => {
    pollRef.current = (currentEventId: string, jobId: string) => {
      fetchLiveEventRecommendationJobStatus(currentEventId)
        .then((status) => {
          // A newer job (or an unmounted component) superseded this poll - stop.
          if (activeJobIdRef.current !== jobId) {
            return;
          }
          if (status.status === 'COMPLETED' || status.status === 'PARTIAL') {
            return fetchLiveEventRecommendations(currentEventId).then((recommendations) => {
              if (activeJobIdRef.current !== jobId) {
                return;
              }
              queryClient.setQueryData(liveEventRecommendationsQueryKey(currentEventId), recommendations);
              setData(recommendations);
              setIsPending(false);
              activeJobIdRef.current = null;
            });
          }
          if (status.status === 'FAILED') {
            setError(new Error(status.message ?? 'Live recommendation generation failed unexpectedly.'));
            setIsPending(false);
            activeJobIdRef.current = null;
            return;
          }
          pollTimeoutRef.current = setTimeout(() => pollRef.current(currentEventId, jobId), JOB_POLL_INTERVAL_MS);
        })
        .catch((err: unknown) => {
          if (activeJobIdRef.current !== jobId) {
            return;
          }
          setError(err);
          setIsPending(false);
          activeJobIdRef.current = null;
        });
    };
  });

  const mutate = useCallback(() => {
    if (!eventId || isPending) {
      // Already processing - never start a second concurrent job from a duplicate click.
      return;
    }
    setError(null);
    setIsPending(true);
    startLiveEventRecommendationGeneration(eventId)
      .then((job) => {
        if (!job.jobId) {
          throw new Error('Live recommendation generation did not return a job id.');
        }
        activeJobIdRef.current = job.jobId;
        pollRef.current(eventId, job.jobId);
      })
      .catch((err: unknown) => {
        setError(err);
        setIsPending(false);
      });
  }, [eventId, isPending]);

  return { mutate, isPending, isError: error !== null, error, data };
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
