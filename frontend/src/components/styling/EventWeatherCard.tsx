import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { EventWeather } from '../../api/weatherApi';
import { useRefreshEventWeather } from '../../hooks/useEventWeather';

interface EventWeatherCardProps {
  eventId: string;
  weather: EventWeather | undefined;
  isLoading: boolean;
  isError: boolean;
}

function formatTemperature(value: number | null): string {
  return value === null || value === undefined ? '—' : `${value}°C`;
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

/**
 * Displays the event's weather (loading / available / forecast-unavailable)
 * plus a "Refresh Weather" action for manual updates. Weather loads
 * automatically as soon as this renders - no click is required. A refresh
 * failure (manual or automatic) shows its own error/warning without hiding
 * whatever weather state was already displayed.
 */
export function EventWeatherCard({ eventId, weather, isLoading, isError }: EventWeatherCardProps) {
  const refreshWeather = useRefreshEventWeather(eventId);
  const refreshDisabled = isLoading || refreshWeather.isPending;

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}
        >
          <Typography variant="h6" component="h2">
            Weather
          </Typography>
          <Button
            variant="outlined"
            size="small"
            onClick={() => refreshWeather.mutate()}
            disabled={refreshDisabled}
          >
            {refreshWeather.isPending ? 'Refreshing…' : 'Refresh Weather'}
          </Button>
        </Stack>

        <Stack spacing={2} sx={{ mt: 2 }}>
          {isLoading && (
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
              <CircularProgress size={20} />
              <Typography variant="body1">Loading weather…</Typography>
            </Stack>
          )}

          {!isLoading && isError && (
            <Alert severity="error" role="alert">
              Unable to load weather.
            </Alert>
          )}

          {!isLoading && !isError && weather?.status === 'FORECAST_UNAVAILABLE' && (
            <Alert severity="info">
              Forecast not yet available{weather.message ? ` — ${weather.message}` : ''}.
            </Alert>
          )}

          {!isLoading && !isError && weather?.status === 'AVAILABLE' && (
            <>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={4} sx={{ flexWrap: 'wrap', rowGap: 2 }}>
                <Stack>
                  <Typography variant="body2" color="text.secondary">
                    Temperature at start
                  </Typography>
                  <Typography variant="h6">{formatTemperature(weather.temperatureAtStart)}</Typography>
                </Stack>
                <Stack>
                  <Typography variant="body2" color="text.secondary">
                    Temperature at end
                  </Typography>
                  <Typography variant="h6">{formatTemperature(weather.temperatureAtEnd)}</Typography>
                </Stack>
                <Stack>
                  <Typography variant="body2" color="text.secondary">
                    Precipitation probability
                  </Typography>
                  <Typography variant="h6">
                    {weather.precipitationProbability === null ? '—' : `${weather.precipitationProbability}%`}
                  </Typography>
                </Stack>
                <Stack>
                  <Typography variant="body2" color="text.secondary">
                    Wind speed
                  </Typography>
                  <Typography variant="h6">
                    {weather.windSpeed === null ? '—' : `${weather.windSpeed} km/h`}
                  </Typography>
                </Stack>
                <Stack>
                  <Typography variant="body2" color="text.secondary">
                    Condition
                  </Typography>
                  <Typography variant="h6">{weather.condition ?? '—'}</Typography>
                </Stack>
              </Stack>
              <Typography variant="caption" color="text.secondary">
                Last updated {formatDateTime(weather.retrievedAt)}
              </Typography>
            </>
          )}

          {!isLoading && !isError && weather?.stale && (
            <Alert severity="warning" role="alert">
              {weather.staleWarning ?? 'Showing the last known forecast; unable to refresh right now.'}
            </Alert>
          )}

          {refreshWeather.isError && (
            <Alert severity="error" role="alert">
              {refreshWeather.error instanceof Error
                ? refreshWeather.error.message
                : 'Unable to retrieve weather right now. Please try again.'}
            </Alert>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
