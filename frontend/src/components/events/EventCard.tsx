import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import CardActionArea from '@mui/material/CardActionArea';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import { useNavigate } from 'react-router-dom';
import type { Event } from '../../api/eventsApi';

function formatDateRange(startTime: string, endTime: string): string {
  const start = new Date(startTime);
  const end = new Date(endTime);

  const dateFormatter = new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
  });
  const timeFormatter = new Intl.DateTimeFormat(undefined, {
    timeStyle: 'short',
  });

  return `${dateFormatter.format(start)} · ${timeFormatter.format(start)} – ${timeFormatter.format(end)}`;
}

interface EventCardProps {
  event: Event;
}

/**
 * Summary card for a single upcoming event. Clicking or activating it via
 * keyboard (Enter/Space, since CardActionArea renders a real button)
 * navigates to the event's detail page.
 */
export function EventCard({ event }: EventCardProps) {
  const navigate = useNavigate();

  return (
    <Card variant="outlined">
      <CardActionArea
        onClick={() => navigate(`/events/${event.id}`)}
        aria-label={`View details for ${event.title}`}
      >
        <CardContent>
          <Typography variant="h6" component="h2">
            {event.title}
          </Typography>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            {formatDateRange(event.startTime, event.endTime)}
          </Typography>
          <Typography variant="body2" gutterBottom>
            {event.location}
          </Typography>
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
            <Chip
              size="small"
              label={event.setting === 'INDOOR' ? 'Indoor' : 'Outdoor'}
            />
            {event.dressCode && <Chip size="small" label={event.dressCode} />}
          </Stack>
        </CardContent>
      </CardActionArea>
    </Card>
  );
}
