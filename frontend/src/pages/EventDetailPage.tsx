import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useEvent } from '../hooks/useEvents';

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'full',
    timeStyle: 'short',
  }).format(new Date(value));
}

/**
 * Event detail page. Always fetches from the backend (rather than relying
 * on client-side navigation state), so a browser refresh reloads the same
 * data from PostgreSQL.
 */
export function EventDetailPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const { data: event, isPending, isError, error } = useEvent(eventId);

  if (isPending) {
    return (
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
        <CircularProgress size={20} />
        <Typography variant="body1">Loading event…</Typography>
      </Stack>
    );
  }

  if (isError) {
    return (
      <Stack spacing={2}>
        <Alert severity="error" role="alert">
          {error instanceof Error ? error.message : 'Unable to load this event.'}
        </Alert>
        <Button component={RouterLink} to="/events" sx={{ alignSelf: 'flex-start' }}>
          Back to Events
        </Button>
      </Stack>
    );
  }

  return (
    <Stack spacing={2}>
      <Button component={RouterLink} to="/events" sx={{ alignSelf: 'flex-start' }}>
        Back to Events
      </Button>

      <Typography variant="h4" component="h1">
        {event.title}
      </Typography>

      <Typography variant="body1" color="text.secondary">
        {formatDateTime(event.startTime)} – {formatDateTime(event.endTime)}
      </Typography>

      <Typography variant="body1">{event.location}</Typography>

      <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
        <Chip label={event.setting === 'INDOOR' ? 'Indoor' : 'Outdoor'} />
        {event.dressCode && <Chip label={event.dressCode} />}
      </Stack>

      {event.description && <Typography variant="body1">{event.description}</Typography>}
    </Stack>
  );
}
