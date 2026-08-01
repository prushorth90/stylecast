import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useNavigate } from 'react-router-dom';
import type { Event } from '../../api/eventsApi';
import { useLiveEventRecommendations } from '../../hooks/useLiveEventRecommendations';

function formatDateRange(startTime: string, endTime: string): string {
  const start = new Date(startTime);
  const end = new Date(endTime);

  const dateFormatter = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' });
  const timeFormatter = new Intl.DateTimeFormat(undefined, { timeStyle: 'short' });

  return `${dateFormatter.format(start)} · ${timeFormatter.format(start)} – ${timeFormatter.format(end)}`;
}

function recommendationStatusLabel(status: string, generation: number): string {
  if (generation === 0) {
    return 'Not generated yet';
  }
  switch (status) {
    case 'COMPLETE':
      return 'Looks generated';
    case 'PARTIAL':
      return 'Partially generated';
    case 'NO_RESULTS':
      return 'No matches found';
    case 'PROVIDER_UNAVAILABLE':
      return 'Generation unavailable';
    default:
      return status;
  }
}

interface HistoryEventRowProps {
  event: Event;
  isPast: boolean;
}

/**
 * One row in the saved event/look history list. Fetches the event's live
 * recommendation status lazily (React Query caches/dedupes this, so
 * re-opening the event's own styling page afterwards reuses the same
 * cached result instead of fetching again) to show a "generation status"
 * chip without a separate backend aggregation endpoint.
 */
export function HistoryEventRow({ event, isPast }: HistoryEventRowProps) {
  const navigate = useNavigate();
  const { data: recommendations } = useLiveEventRecommendations(event.id);

  return (
    <Card variant="outlined">
      <CardActionArea
        onClick={() => navigate(`/events/${event.id}/style`)}
        aria-label={`View styling for ${event.title}`}
      >
        <CardContent>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
            <Typography variant="h6" component="h2">
              {event.title}
            </Typography>
            <Chip size="small" color={isPast ? 'default' : 'primary'} label={isPast ? 'Past' : 'Active'} />
          </Stack>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            {formatDateRange(event.startTime, event.endTime)}
          </Typography>
          {recommendations && (
            <Chip
              size="small"
              variant="outlined"
              label={recommendationStatusLabel(recommendations.status, recommendations.generation)}
            />
          )}
        </CardContent>
      </CardActionArea>
    </Card>
  );
}
