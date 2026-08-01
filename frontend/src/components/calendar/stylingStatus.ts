import type { CalendarStylingStatus } from '../../api/calendarApi';

/**
 * Readable label for a {@link CalendarStylingStatus} - always rendered as
 * visible text (never color alone) wherever styling status is shown on the
 * calendar.
 */
export function stylingStatusLabel(status: CalendarStylingStatus): string {
  switch (status) {
    case 'EVENT_ONLY':
      return 'Styling not started';
    case 'PREFERENCES_SET':
      return 'Preferences saved';
    case 'INTERPRETATION_READY':
      return 'Interpretation ready';
    case 'RECOMMENDATIONS_PENDING':
      return 'Recommendations pending';
    case 'RECOMMENDATIONS_READY':
      return 'Recommendations ready';
    case 'RECOMMENDATIONS_STALE':
      return 'Recommendations need updating';
    default:
      return status;
  }
}

/** MUI `Chip` `color` prop for a status - a supplementary visual cue only; the label text always carries the actual meaning. */
export function stylingStatusChipColor(
  status: CalendarStylingStatus,
): 'default' | 'primary' | 'success' | 'warning' {
  switch (status) {
    case 'RECOMMENDATIONS_READY':
      return 'success';
    case 'RECOMMENDATIONS_STALE':
      return 'warning';
    case 'RECOMMENDATIONS_PENDING':
    case 'INTERPRETATION_READY':
      return 'primary';
    default:
      return 'default';
  }
}
