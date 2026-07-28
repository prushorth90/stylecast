import { useQuery } from '@tanstack/react-query';
import { getHealth } from '../api/healthApi';

export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
  });
}
