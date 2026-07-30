import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { EventApiError } from '../../api/eventsApi';
import type { OutfitRecommendation, RecommendationsResponse } from '../../api/recommendationsApi';
import { useGenerateEventRecommendations } from '../../hooks/useEventRecommendations';

interface EventRecommendationsCardProps {
  eventId: string;
  recommendations: RecommendationsResponse | undefined;
  isLoading: boolean;
  isError: boolean;
}

/** Converts a SCREAMING_SNAKE_CASE enum value into a readable label, e.g. "SHOES" -> "Shoes". */
function formatEnumLabel(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

function formatPrice(value: number): string {
  return `$${value.toFixed(2)}`;
}

function errorMessage(error: unknown): string {
  if (error instanceof EventApiError) {
    return error.message;
  }
  return error instanceof Error ? error.message : 'Unable to generate recommendations right now.';
}

function OutfitSummaryCard({ outfit }: { outfit: OutfitRecommendation }) {
  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="subtitle1" component="h3">
          {outfit.name}
        </Typography>

        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1, my: 1 }}>
          <Chip size="small" color="primary" label={`Overall fit: ${outfit.overallScore}/100`} />
          <Chip size="small" variant="outlined" label={`Occasion fit: ${outfit.occasionFitScore}/100`} />
          <Chip size="small" variant="outlined" label={`Weather fit: ${outfit.weatherFitScore}/100`} />
        </Stack>

        <Stack spacing={0.5} sx={{ my: 1 }}>
          {outfit.items.map((item) => (
            <Typography key={item.id} variant="body2">
              {item.brand} {item.name} ({formatEnumLabel(item.category)}) - {item.color}, size {item.size} -{' '}
              {formatPrice(item.itemPrice)}
            </Typography>
          ))}
        </Stack>

        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          Total: {formatPrice(outfit.totalPrice)}
        </Typography>

        {outfit.explanation && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {outfit.explanation}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

/**
 * Minimal, temporary summary-card integration for deterministic outfit
 * recommendations (Task 7A) - not the final Pinterest-style mood board.
 * Loads the event's current recommendations automatically (never
 * regenerating on its own); the user clicks "Generate Looks" to produce a
 * new set from the local product catalog.
 */
export function EventRecommendationsCard({ eventId, recommendations, isLoading, isError }: EventRecommendationsCardProps) {
  const generate = useGenerateEventRecommendations(eventId);
  const current = generate.data ?? recommendations;
  const generateDisabled = isLoading || generate.isPending;
  const outfits = current?.recommendations ?? [];
  const hasResults = current?.hasResults ?? false;

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}
        >
          <Typography variant="h6" component="h2">
            Recommended looks
          </Typography>
          <Button variant="contained" size="small" onClick={() => generate.mutate()} disabled={generateDisabled}>
            {generate.isPending ? 'Generating…' : 'Generate Looks'}
          </Button>
        </Stack>

        <Chip label="Demo catalog recommendations" size="small" sx={{ mt: 1, alignSelf: 'flex-start' }} />

        <Divider sx={{ my: 2 }} />

        <Stack spacing={2}>
          {isLoading && (
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
              <CircularProgress size={20} />
              <Typography variant="body1">Loading recommendations…</Typography>
            </Stack>
          )}

          {generate.isPending && (
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
              <CircularProgress size={20} />
              <Typography variant="body1">Generating looks from the demo catalog…</Typography>
            </Stack>
          )}

          {!isLoading && isError && !current && (
            <Alert severity="error" role="alert">
              Unable to load recommendations right now.
            </Alert>
          )}

          {generate.isError && (
            <Alert severity="error" role="alert">
              {errorMessage(generate.error)}
            </Alert>
          )}

          {!isLoading && !generate.isPending && current && !hasResults && (
            <Alert severity="info">
              {current.noResultReason ??
                'No recommendations yet. Click "Generate Looks" to create up to three outfits from the demo catalog.'}
            </Alert>
          )}

          {!generate.isPending && hasResults && outfits.map((outfit) => <OutfitSummaryCard key={outfit.id} outfit={outfit} />)}
        </Stack>
      </CardContent>
    </Card>
  );
}
