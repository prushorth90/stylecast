import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useEventHistory } from '../hooks/useEvents';
import { HistoryEventRow } from '../components/history/HistoryEventRow';

/**
 * Authenticated event/look history page: every one of the current user's
 * events, split into currently-active/upcoming and past, most recent
 * first within each group.
 */
export function HistoryPage() {
  const { data, isPending, isError, error } = useEventHistory();

  if (isPending) {
    return (
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
        <CircularProgress size={20} />
        <Typography variant="body1">Loading history…</Typography>
      </Stack>
    );
  }

  if (isError) {
    return (
      <Alert severity="error" role="alert">
        {error instanceof Error ? error.message : 'Unable to load history.'}
      </Alert>
    );
  }

  if (data.length === 0) {
    return (
      <Typography variant="body1" color="text.secondary">
        No events yet. Once you create an event, it will show up here.
      </Typography>
    );
  }

  const now = new Date();
  const active = data.filter((event) => new Date(event.endTime) >= now);
  const past = data.filter((event) => new Date(event.endTime) < now);

  return (
    <Stack spacing={4}>
      <Typography variant="h4" component="h1">
        History
      </Typography>

      <Stack spacing={2}>
        <Typography variant="h6" component="h2">
          Active and upcoming
        </Typography>
        {active.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            No active or upcoming events.
          </Typography>
        ) : (
          active.map((event) => <HistoryEventRow key={event.id} event={event} isPast={false} />)
        )}
      </Stack>

      <Stack spacing={2}>
        <Typography variant="h6" component="h2">
          Past
        </Typography>
        {past.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            No past events yet.
          </Typography>
        ) : (
          past.map((event) => <HistoryEventRow key={event.id} event={event} isPast={true} />)
        )}
      </Stack>
    </Stack>
  );
}
