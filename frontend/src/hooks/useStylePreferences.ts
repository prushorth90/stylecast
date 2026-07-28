import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  fetchEventStylePreferences,
  saveEventStylePreferences,
  type SaveEventStylePreferencesInput,
} from '../api/stylePreferencesApi';

const stylePreferencesQueryKey = (eventId: string) => ['events', eventId, 'preferences'] as const;

export function useEventStylePreferences(eventId: string | undefined) {
  return useQuery({
    queryKey: stylePreferencesQueryKey(eventId ?? ''),
    queryFn: () => fetchEventStylePreferences(eventId as string),
    enabled: Boolean(eventId),
  });
}

export function useSaveEventStylePreferences(eventId: string | undefined) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: SaveEventStylePreferencesInput) => {
      if (!eventId) {
        return Promise.reject(new Error('Missing eventId'));
      }
      return saveEventStylePreferences(eventId, input);
    },
    onSuccess: (saved) => {
      if (eventId) {
        queryClient.setQueryData(stylePreferencesQueryKey(eventId), saved);
      }
    },
  });
}
