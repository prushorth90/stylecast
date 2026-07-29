import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchEventWeather, refreshEventWeather } from '../api/weatherApi';

const eventWeatherQueryKey = (eventId: string) => ['events', eventId, 'weather'] as const;

export function useEventWeather(eventId: string | undefined) {
  return useQuery({
    queryKey: eventWeatherQueryKey(eventId ?? ''),
    queryFn: () => fetchEventWeather(eventId as string),
    enabled: Boolean(eventId),
  });
}

export function useRefreshEventWeather(eventId: string | undefined) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => {
      if (!eventId) {
        return Promise.reject(new Error('Missing eventId'));
      }
      return refreshEventWeather(eventId);
    },
    onSuccess: (weather) => {
      if (eventId) {
        queryClient.setQueryData(eventWeatherQueryKey(eventId), weather);
      }
    },
  });
}
