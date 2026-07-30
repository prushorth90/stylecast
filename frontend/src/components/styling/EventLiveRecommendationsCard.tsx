import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { EventApiError } from '../../api/eventsApi';
import type { LiveOutfitItem, LiveOutfitRecommendation, LiveRecommendationsResponse } from '../../api/liveRecommendationsApi';
import {
  useGenerateLiveEventRecommendations,
  useRetryMissingLiveEventRecommendations,
} from '../../hooks/useLiveEventRecommendations';

interface EventLiveRecommendationsCardProps {
  eventId: string;
  recommendations: LiveRecommendationsResponse | undefined;
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

function formatPrice(item: LiveOutfitItem): string | null {
  if (!item.priceVerified || item.price === null) {
    return null;
  }
  const current = `$${item.price.toFixed(2)}${item.currency ? ` ${item.currency}` : ''}`;
  if (item.originalPrice !== null && item.originalPrice > item.price) {
    return `${current} (was $${item.originalPrice.toFixed(2)})`;
  }
  return current;
}

function formatSizes(item: LiveOutfitItem): string | null {
  if (!item.sizeVerified || item.availableSizes.length === 0) {
    return null;
  }
  return `Sizes: ${item.availableSizes.join(', ')}`;
}

function formatAvailability(item: LiveOutfitItem): string | null {
  if (!item.availabilityVerified || !item.stockText) {
    return null;
  }
  return item.stockText;
}

function isProviderUnavailable(error: unknown): boolean {
  return error instanceof EventApiError && error.status === 503;
}

function errorMessage(error: unknown): string {
  if (error instanceof EventApiError) {
    return error.message;
  }
  return error instanceof Error ? error.message : 'Unable to generate live recommendations right now.';
}

function LiveOutfitSummaryCard({ outfit, isPartial }: { outfit: LiveOutfitRecommendation; isPartial: boolean }) {
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
          <Typography variant="subtitle1" component="h3">
            {outfit.name}
          </Typography>
          <Chip size="small" color="primary" label="Live Nordstrom" />
          {isPartial && <Chip size="small" color="warning" variant="outlined" label="Partial" />}
        </Stack>

        <Stack spacing={1} sx={{ my: 1 }}>
          {outfit.items.map((item) => {
            const price = formatPrice(item);
            const sizes = formatSizes(item);
            const availability = formatAvailability(item);
            const hasKnownAudience = item.audience !== 'UNKNOWN';
            return (
              <Stack key={item.id} spacing={0.25}>
                <Typography variant="body2">
                  {item.brand ? `${item.brand} ` : ''}
                  {item.title ?? 'Nordstrom product'} ({formatEnumLabel(item.category)})
                </Typography>
                <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 0.5, alignItems: 'center' }}>
                  {price && <Chip size="small" variant="outlined" label={price} />}
                  {sizes && <Chip size="small" variant="outlined" label={sizes} />}
                  {availability && <Chip size="small" variant="outlined" label={availability} />}
                  {hasKnownAudience && <Chip size="small" variant="outlined" label={formatEnumLabel(item.audience)} />}
                  <Link href={item.productUrl} target="_blank" rel="noopener noreferrer" variant="body2">
                    View on Nordstrom
                  </Link>
                </Stack>
              </Stack>
            );
          })}
        </Stack>

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
 * Live-Nordstrom outfit recommendations - not the final Pinterest-style
 * mood board. Loads the event's current live recommendations
 * automatically on page load via `GET .../recommendations/live` (never
 * triggering a live search on its own); the user clicks "Generate Live
 * Nordstrom Looks" to call `POST .../recommendations/live/generate`, which
 * searches every required category independently and assembles up to
 * three outfits.
 *
 * <p>Every required category's search is independent: valid Nordstrom
 * candidates from categories that succeed are always shown, even if one
 * category is missing (`status: 'PARTIAL'`) - a partial result is never
 * presented as a complete outfit. "Retry Missing Items" re-searches only
 * the missing categories. `status: 'PROVIDER_UNAVAILABLE'` (every category
 * search failed) is shown distinctly from `status: 'NO_RESULTS'` (every
 * category was searched successfully but found nothing) - neither ever
 * falls back to fictional local products.
 */
export function EventLiveRecommendationsCard({ eventId, recommendations, isLoading, isError }: EventLiveRecommendationsCardProps) {
  const generate = useGenerateLiveEventRecommendations(eventId);
  const retryMissing = useRetryMissingLiveEventRecommendations(eventId);
  const current = generate.data ?? retryMissing.data ?? recommendations;
  const generateDisabled = isLoading || generate.isPending || retryMissing.isPending;
  const outfits = current?.recommendations ?? [];
  const status = current?.status;
  const isPartial = status === 'PARTIAL';
  const isProviderUnavailableStatus = status === 'PROVIDER_UNAVAILABLE';
  const hasResults = outfits.length > 0;
  const providerUnavailable = (generate.isError && isProviderUnavailable(generate.error)) || isProviderUnavailableStatus;

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
          <Stack direction="row" spacing={1}>
            {isPartial && (
              <Button
                variant="outlined"
                size="small"
                onClick={() => retryMissing.mutate()}
                disabled={generateDisabled}
              >
                {retryMissing.isPending ? 'Retrying…' : 'Retry Missing Items'}
              </Button>
            )}
            <Button variant="contained" size="small" onClick={() => generate.mutate()} disabled={generateDisabled}>
              {generate.isPending ? 'Generating…' : 'Generate Live Nordstrom Looks'}
            </Button>
          </Stack>
        </Stack>

        <Chip label="Live Nordstrom search - temporary integration" size="small" sx={{ mt: 1, alignSelf: 'flex-start' }} />

        <Alert severity="info" sx={{ mt: 2 }}>
          Confirm current product details, sizes, prices, and availability on Nordstrom.
        </Alert>

        <Divider sx={{ my: 2 }} />

        <Stack spacing={2}>
          {isLoading && (
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
              <CircularProgress size={20} />
              <Typography variant="body1">Loading live Nordstrom recommendations…</Typography>
            </Stack>
          )}

          {generate.isPending && (
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
              <CircularProgress size={20} />
              <Typography variant="body1">Searching nordstrom.com…</Typography>
            </Stack>
          )}

          {retryMissing.isPending && (
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
              <CircularProgress size={20} />
              <Typography variant="body1">Retrying missing categories…</Typography>
            </Stack>
          )}

          {!isLoading && isError && !current && (
            <Alert severity="error" role="alert">
              Unable to load recommendations right now.
            </Alert>
          )}

          {providerUnavailable && (
            <Alert severity="warning" role="alert">
              Live Nordstrom search is temporarily unavailable. Please try again shortly.
            </Alert>
          )}

          {generate.isError && !providerUnavailable && (
            <Alert severity="error" role="alert">
              {errorMessage(generate.error)}
            </Alert>
          )}

          {retryMissing.isError && (
            <Alert severity="error" role="alert">
              {errorMessage(retryMissing.error)}
            </Alert>
          )}

          {isPartial && current?.message && (
            <Alert severity="info" role="alert">
              {current.message}
            </Alert>
          )}

          {!isPartial && !isProviderUnavailableStatus && !isLoading && !generate.isPending
            && !retryMissing.isPending && current && !hasResults && (
            <Alert severity="info">
              {current.message ??
                'No live Nordstrom recommendations found yet. Click "Generate Live Nordstrom Looks" to search nordstrom.com for up to three outfits.'}
            </Alert>
          )}

          {!generate.isPending && !retryMissing.isPending && hasResults
            && outfits.map((outfit) => <LiveOutfitSummaryCard key={outfit.id} outfit={outfit} isPartial={isPartial} />)}
        </Stack>
      </CardContent>
    </Card>
  );
}
