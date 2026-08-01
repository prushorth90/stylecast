import Chip from '@mui/material/Chip';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';

export type BoardCompleteness = 'COMPLETE' | 'PARTIAL';

interface RecommendationStatusBadgeProps {
  status: BoardCompleteness;
}

/**
 * Communicates a board's completeness via text + icon (never color alone) -
 * "Complete" is never shown for a board that is missing a required item.
 */
export function RecommendationStatusBadge({ status }: RecommendationStatusBadgeProps) {
  if (status === 'COMPLETE') {
    return <Chip size="small" color="success" icon={<CheckCircleIcon />} label="Complete" />;
  }
  return <Chip size="small" color="warning" variant="outlined" icon={<WarningAmberIcon />} label="Partial" />;
}
