import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { EventSetupModal } from '../components/events/EventSetupModal';
import { EventOccasionCard } from '../components/styling/EventOccasionCard';
import { EventLiveRecommendationsCard } from '../components/styling/EventLiveRecommendationsCard';
import { EventWeatherCard } from '../components/styling/EventWeatherCard';
import { useEvent } from '../hooks/useEvents';
import { useEventOccasionInterpretation } from '../hooks/useEventOccasion';
import { useLiveEventRecommendations } from '../hooks/useLiveEventRecommendations';
import { useEventWeather } from '../hooks/useEventWeather';
import { useEventStylePreferences } from '../hooks/useStylePreferences';

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'full',
    timeStyle: 'short',
  }).format(new Date(value));
}

/**
 * Event styling page. Shows the selected event's summary, its saved
 * weather snapshot (with a manual refresh action), its occasion
 * interpretation (with a manual regenerate action), and recommended looks.
 * Styling preferences are edited via the two-step event setup modal (see
 * "Edit preferences" below), not a form on this page.
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
  const {
    data: weather,
    isPending: isWeatherPending,
    isError: isWeatherError,
  } = useEventWeather(eventId);
  const {
    data: occasionInterpretation,
    isPending: isOccasionPending,
    isError: isOccasionError,
  } = useEventOccasionInterpretation(eventId);
  const {
    data: recommendations,
    isPending: isRecommendationsPending,
    isError: isRecommendationsError,
  } = useLiveEventRecommendations(eventId);
  const [isEditPreferencesOpen, setIsEditPreferencesOpen] = useState(false);

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

      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ alignItems: { sm: 'flex-start' }, justifyContent: 'space-between' }}
      >
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

        <Button
          variant="outlined"
          onClick={() => setIsEditPreferencesOpen(true)}
          disabled={isPreferencesPending}
          sx={{ alignSelf: { xs: 'flex-start', sm: 'flex-end' } }}
        >
          Edit preferences
        </Button>
      </Stack>

      {isPreferencesError && (
        <Alert severity="error" role="alert">
          {preferencesError instanceof Error
            ? preferencesError.message
            : 'Unable to load saved preferences.'}
        </Alert>
      )}

      {event.id && (
        <EventWeatherCard
          eventId={event.id}
          weather={weather}
          isLoading={isWeatherPending}
          isError={isWeatherError}
        />
      )}

      {event.id && (
        <EventOccasionCard
          eventId={event.id}
          interpretation={occasionInterpretation}
          isLoading={isOccasionPending}
          isError={isOccasionError}
        />
      )}

      {event.id && (
        <EventLiveRecommendationsCard
          eventId={event.id}
          recommendations={recommendations}
          isLoading={isRecommendationsPending}
          isError={isRecommendationsError}
        />
      )}

      {event.id && (
        <EventSetupModal
          open={isEditPreferencesOpen}
          onClose={() => setIsEditPreferencesOpen(false)}
          eventId={event.id}
          initialEvent={event}
          initialPreferences={preferences ?? null}
          onCompleted={() => setIsEditPreferencesOpen(false)}
        />
      )}
    </Stack>
  );
}
