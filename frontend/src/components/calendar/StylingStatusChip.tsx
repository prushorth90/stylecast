import Chip from '@mui/material/Chip';
import type { CalendarStylingStatus } from '../../api/calendarApi';
import { stylingStatusChipColor, stylingStatusLabel } from './stylingStatus';

interface StylingStatusChipProps {
  status: CalendarStylingStatus;
  size?: 'small' | 'medium';
}

/** Small readable chip showing an event's styling-workflow status - label text always present, color is a supplementary cue only. */
export function StylingStatusChip({ status, size = 'small' }: StylingStatusChipProps) {
  return <Chip size={size} variant="outlined" color={stylingStatusChipColor(status)} label={stylingStatusLabel(status)} />;
}
