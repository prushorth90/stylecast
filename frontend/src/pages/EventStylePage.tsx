import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { StylePreferencesForm } from '../components/styling/StylePreferencesForm';
import { useEvent } from '../hooks/useEvents';
import { useEventStylePreferences } from '../hooks/useStylePreferences';

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'full',
    timeStyle: 'short',
  }).format(new Date(value));
}

/**
 * Event styling page. Shows the selected event's details, placeholders for
 * work that later tasks will implement (weather, occasion analysis,
 * recommended looks), and a form for the event's styling preferences.
 */
export function EventStylePage() {
  const { eventId } = useParams<{ eventId: string }>();
  const { data: event, isPending: isEventPending, isError: isEventError, error: eventError } =
    useEvent(eventId);
  const {
    data: preferences,
    isPending: isPreferencesPending,
    isError: isPreferencesError,
    error: preferencesError,
  } = useEventStylePreferences(eventId);

  if (isEventPending) {
    return (
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
        <CircularProgress size={20} />
        <Typography variant="body1">Loading event…</Typography>
      </Stack>
    );
  }

  if (isEventError) {
    return (
      <Stack spacing={2}>
        <Alert severity="error" role="alert">
          {eventError instanceof Error ? eventError.message : 'Unable to load this event.'}
        </Alert>
        <Button component={RouterLink} to="/events" sx={{ alignSelf: 'flex-start' }}>
          Back to Events
        </Button>
      </Stack>
    );
  }

  return (
    <Stack spacing={3}>
      <Button component={RouterLink} to={`/events/${event.id}`} sx={{ alignSelf: 'flex-start' }}>
        Back to Event
      </Button>

      <Stack spacing={1}>
        <Typography variant="h4" component="h1">
          Style: {event.title}
        </Typography>
        <Typography variant="body1" color="text.secondary">
          {formatDateTime(event.startTime)} – {formatDateTime(event.endTime)}
        </Typography>
        <Typography variant="body1">{event.location}</Typography>
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
          <Chip label={event.setting === 'INDOOR' ? 'Indoor' : 'Outdoor'} />
          {event.dressCode && <Chip label={event.dressCode} />}
        </Stack>
      </Stack>

      <Card variant="outlined">
        <CardContent>
          <Typography variant="h6" component="h2" gutterBottom>
            Status
          </Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <Alert severity="info" sx={{ flex: 1 }}>
              Weather: coming in a later task.
            </Alert>
            <Alert severity="info" sx={{ flex: 1 }}>
              Occasion analysis: coming in a later task.
            </Alert>
            <Alert severity="info" sx={{ flex: 1 }}>
              Recommendations: coming in a later task.
            </Alert>
          </Stack>
        </CardContent>
      </Card>

      <Divider />

      <Stack spacing={1}>
        <Typography variant="h6" component="h2">
          Styling preferences
        </Typography>

        {isPreferencesPending && (
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
            <CircularProgress size={20} />
            <Typography variant="body1">Loading preferences…</Typography>
          </Stack>
        )}

        {isPreferencesError && (
          <Alert severity="error" role="alert">
            {preferencesError instanceof Error
              ? preferencesError.message
              : 'Unable to load saved preferences.'}
          </Alert>
        )}

        {!isPreferencesPending && !isPreferencesError && event.id && (
          <StylePreferencesForm eventId={event.id} initialPreferences={preferences ?? null} />
        )}
      </Stack>

      <Divider />

      <Card variant="outlined">
        <CardContent>
          <Typography variant="h6" component="h2" gutterBottom>
            Recommended looks
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Outfit recommendations will appear here in a later task, once weather, occasion
            analysis, and the product catalog are available.
          </Typography>
        </CardContent>
      </Card>
    </Stack>
  );
}
