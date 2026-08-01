import Alert from '@mui/material/Alert';

interface RecommendationUnavailableStateProps {
  message?: string | null;
}

/**
 * Distinct from `RecommendationEmptyState` - the provider itself failed
 * (a transient outage), not "searched successfully and found nothing".
 */
export function RecommendationUnavailableState({ message }: RecommendationUnavailableStateProps) {
  return (
    <Alert severity="warning" role="alert">
      {message ?? 'Live Nordstrom search is temporarily unavailable. Please try again.'}
    </Alert>
  );
}
