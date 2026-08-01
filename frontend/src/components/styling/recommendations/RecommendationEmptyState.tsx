import Alert from '@mui/material/Alert';

interface RecommendationEmptyStateProps {
  message?: string | null;
}

/**
 * "No suitable Nordstrom products were found" state - a normal, non-error
 * outcome. Never shows a fictional fallback product in the live section.
 */
export function RecommendationEmptyState({ message }: RecommendationEmptyStateProps) {
  return <Alert severity="info">{message ?? 'No suitable Nordstrom products were found.'}</Alert>;
}
