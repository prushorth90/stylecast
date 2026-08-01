import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';

export type CalendarViewMode = 'month' | 'week' | 'upcoming';

interface CalendarToolbarProps {
  view: CalendarViewMode;
  onViewChange: (view: CalendarViewMode) => void;
  rangeLabel: string;
  onPrevious: () => void;
  onNext: () => void;
  onToday: () => void;
}

/**
 * Calendar navigation bar: view switcher (Month/Week/Upcoming), Today
 * button, previous/next range controls, and the currently-visible date
 * range as plain text (an `aria-live` region so screen-reader users hear
 * the range change after navigating, without needing to re-read the whole
 * page).
 */
export function CalendarToolbar({ view, onViewChange, rangeLabel, onPrevious, onNext, onToday }: CalendarToolbarProps) {
  return (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      spacing={2}
      sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between', mb: 2 }}
    >
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        <Button onClick={onToday} variant="outlined" size="small">
          Today
        </Button>
        <IconButton onClick={onPrevious} aria-label={`Previous ${view === 'upcoming' ? 'period' : view}`}>
          <ChevronLeftIcon />
        </IconButton>
        <IconButton onClick={onNext} aria-label={`Next ${view === 'upcoming' ? 'period' : view}`}>
          <ChevronRightIcon />
        </IconButton>
        <Typography variant="h6" component="h2" aria-live="polite">
          {rangeLabel}
        </Typography>
      </Stack>

      <ToggleButtonGroup
        value={view}
        exclusive
        onChange={(_event, next: CalendarViewMode | null) => {
          if (next) {
            onViewChange(next);
          }
        }}
        aria-label="Calendar view"
        size="small"
      >
        <ToggleButton value="month" aria-label="Month view">
          Month
        </ToggleButton>
        <ToggleButton value="week" aria-label="Week view">
          Week
        </ToggleButton>
        <ToggleButton value="upcoming" aria-label="Upcoming view">
          Upcoming
        </ToggleButton>
      </ToggleButtonGroup>
    </Stack>
  );
}
