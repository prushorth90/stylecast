import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';

interface RecommendationStaleStateProps {
  onGenerateUpdatedLooks: () => void;
  disabled: boolean;
}

/**
 * Shown when the event's saved preferences or occasion interpretation
 * changed after the current recommendations were generated - the boards
 * below remain visible, but must not be presented as current/up to date
 * without this warning and an explicit way to refresh them.
 */
export function RecommendationStaleState({ onGenerateUpdatedLooks, disabled }: RecommendationStaleStateProps) {
  return (
    <Alert
      severity="warning"
      role="alert"
      action={
        <Button color="inherit" size="small" onClick={onGenerateUpdatedLooks} disabled={disabled}>
          Generate updated looks
        </Button>
      }
    >
      These recommendations were generated from older preferences.
    </Alert>
  );
}
