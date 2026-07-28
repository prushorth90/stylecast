import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useUpcomingEvents } from '../../hooks/useEvents';
import { EventCard } from './EventCard';

/**
 * Displays upcoming events in chronological order, with explicit loading,
 * empty, and error states.
 */
export function EventList() {
  const { data, isPending, isError, error } = useUpcomingEvents();

  if (isPending) {
    return (
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
        <CircularProgress size={20} />
        <Typography variant="body1">Loading upcoming events…</Typography>
      </Stack>
    );
  }

  if (isError) {
    return (
      <Alert severity="error" role="alert">
        {error instanceof Error ? error.message : 'Unable to load events.'}
      </Alert>
    );
  }

  if (data.length === 0) {
    return (
      <Typography variant="body1" color="text.secondary">
        No upcoming events yet. Click "Create Event" to add one.
      </Typography>
    );
  }

  return (
    <Stack spacing={2}>
      {data.map((event) => (
        <EventCard key={event.id} event={event} />
      ))}
    </Stack>
  );
}
