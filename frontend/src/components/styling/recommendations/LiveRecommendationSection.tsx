import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CircularProgress from '@mui/material/CircularProgress';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { EventApiError } from '../../../api/eventsApi';
import type {
  LiveOutfitItem,
  LiveOutfitRecommendation,
  LiveRecommendationsResponse,
  RequestedItemSummary,
} from '../../../api/liveRecommendationsApi';
import {
  useGenerateLiveEventRecommendations,
  useRetryMissingLiveEventRecommendations,
} from '../../../hooks/useLiveEventRecommendations';
import { FoundRequestedItems, type FoundItemEntry } from './FoundRequestedItems';
import { formatEnumLabel } from './formatters';
import { MissingRequestedItems, type MissingItemEntry } from './MissingRequestedItems';
import { NordstromVerificationNotice } from './NordstromVerificationNotice';
import { RecommendationBoard } from './RecommendationBoard';
import { RecommendationEmptyState } from './RecommendationEmptyState';
import { RecommendationStaleState } from './RecommendationStaleState';
import { RecommendationUnavailableState } from './RecommendationUnavailableState';

interface LiveRecommendationSectionProps {
  eventId: string;
  recommendations: LiveRecommendationsResponse | undefined;
  isLoading: boolean;
  isError: boolean;
}

function isProviderUnavailableError(error: unknown): boolean {
  return error instanceof EventApiError && error.status === 503;
}

function errorMessage(error: unknown): string {
  if (error instanceof EventApiError) {
    return error.message;
  }
  return error instanceof Error ? error.message : 'Unable to generate live recommendations right now.';
}

/** Finds the Nordstrom product URL fulfilling a found requested item or category, for the Found list's link. */
function findFoundItemUrl(
  outfits: LiveOutfitRecommendation[],
  predicate: (item: LiveOutfitItem) => boolean,
): string | null {
  for (const outfit of outfits) {
    const match = outfit.items.find(predicate);
    if (match) {
      return match.productUrl;
    }
  }
  return null;
}

/** Structured loading placeholder matching the eventual card shape, shown while there is no prior result to keep displaying - text-only, never an image-shaped skeleton. */
function BoardSkeleton() {
  return (
    <Card variant="outlined">
      <CardContent>
        <Skeleton variant="text" width={160} height={32} />
        <Skeleton variant="text" width={220} />
        <Box sx={{ mt: 2, display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 1.5 }}>
          {[0, 1, 2].map((index) => (
            <Stack key={index} spacing={0.5} sx={{ p: 1.25, border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
              <Skeleton variant="text" width="40%" />
              <Skeleton variant="text" />
              <Skeleton variant="text" width="60%" />
            </Stack>
          ))}
        </Box>
      </CardContent>
    </Card>
  );
}

/**
 * Live-Nordstrom outfit recommendations, presented as compact, text-based
 * recommended product sets (link-based - no product images, since no
 * authorized product feed is available yet; see docs/ROADMAP.md and the
 * README's "Live outfit recommendations" section). Loads the event's
 * current live recommendations automatically on page load via `GET
 * .../recommendations/live` (never triggering a live search on its own);
 * the user clicks "Generate Live Nordstrom Looks" to call `POST
 * .../recommendations/live/generate`.
 *
 * <p>Every required category's/requested item's search is independent:
 * valid Nordstrom candidates from ones that succeed are always shown, even
 * when some are missing (`status: 'PARTIAL'`) - a partial result is never
 * presented as a complete outfit. "Retry Missing Items" re-searches only
 * the missing ones. `status: 'PROVIDER_UNAVAILABLE'` is shown distinctly
 * from `status: 'NO_RESULTS'`. `stale: true` (set by the event setup modal
 * after a preferences/interpretation change) is shown as a distinct
 * warning with a "Generate updated looks" action rather than silently
 * presenting old boards as current. Previously-loaded valid boards are
 * never cleared while a new generate/retry request is in flight - only
 * replaced once it succeeds.
 */
export function LiveRecommendationSection({ eventId, recommendations, isLoading, isError }: LiveRecommendationSectionProps) {
  const generate = useGenerateLiveEventRecommendations(eventId);
  const retryMissing = useRetryMissingLiveEventRecommendations(eventId);
  const current = generate.data ?? retryMissing.data ?? recommendations;

  const isMutating = generate.isPending || retryMissing.isPending;
  const generateDisabled = isLoading || isMutating;

  const outfits = current?.recommendations ?? [];
  const hasResults = outfits.length > 0;
  const status = current?.status;
  const isPartial = status === 'PARTIAL';
  const isComplete = status === 'COMPLETE';
  const isNoResults = status === 'NO_RESULTS';
  const isProviderUnavailableStatus = status === 'PROVIDER_UNAVAILABLE';
  const providerUnavailable = (generate.isError && isProviderUnavailableError(generate.error)) || isProviderUnavailableStatus;
  const isStale = Boolean(current?.stale) && hasResults && !isMutating;
  const showSkeleton = (isLoading || isMutating) && !hasResults;

  const foundRequestedItems = current?.foundRequestedItems ?? [];
  const missingRequestedItems = current?.missingRequestedItems ?? [];
  const foundCategories = current?.foundCategories ?? [];
  const missingCategories = current?.missingCategories ?? [];

  const usesRequestedItems = foundRequestedItems.length > 0 || missingRequestedItems.length > 0;
  const showFoundMissing = Boolean(current) && (usesRequestedItems || isPartial);

  const foundEntries: FoundItemEntry[] = usesRequestedItems
    ? foundRequestedItems.map((item: RequestedItemSummary) => ({
        id: item.id,
        label: item.originalPhrase,
        url: findFoundItemUrl(outfits, (i) => i.requestedItemPhrase === item.originalPhrase),
      }))
    : isPartial
      ? foundCategories.map((category) => ({
          id: category,
          label: formatEnumLabel(category),
          url: findFoundItemUrl(outfits, (i) => i.category === category),
        }))
      : [];

  const missingEntries: MissingItemEntry[] = usesRequestedItems
    ? missingRequestedItems.map((item: RequestedItemSummary) => ({ id: item.id, label: item.originalPhrase }))
    : isPartial
      ? missingCategories.map((category) => ({ id: category, label: formatEnumLabel(category) }))
      : [];

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}
        >
          <Typography variant="h6" component="h2">
            Live Nordstrom recommendations
          </Typography>
          <Stack direction="row" spacing={1}>
            {isPartial && (
              <Button variant="outlined" size="small" onClick={() => retryMissing.mutate()} disabled={generateDisabled}>
                {retryMissing.isPending ? 'Retrying…' : 'Retry Missing Items'}
              </Button>
            )}
            <Button variant="contained" size="small" onClick={() => generate.mutate()} disabled={generateDisabled}>
              {generate.isPending ? 'Generating…' : 'Generate Live Nordstrom Looks'}
            </Button>
          </Stack>
        </Stack>

        <Box sx={{ mt: 2 }}>
          <NordstromVerificationNotice />
        </Box>

        <Stack spacing={2} sx={{ mt: 2 }}>
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

          {providerUnavailable && <RecommendationUnavailableState message={current?.message} />}

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

          {isStale && (
            <RecommendationStaleState onGenerateUpdatedLooks={() => generate.mutate()} disabled={generateDisabled} />
          )}

          {isPartial && current?.message && (
            <Alert severity="info" role="alert">
              {current.message}
            </Alert>
          )}

          {showFoundMissing && (
            <Stack spacing={1.5}>
              <FoundRequestedItems items={foundEntries} />
              <MissingRequestedItems items={missingEntries} />
            </Stack>
          )}

          {showSkeleton && <BoardSkeleton />}

          {!isLoading && !isMutating && isNoResults && current && <RecommendationEmptyState message={current.message} />}

          {hasResults && (isPartial || isComplete) && (
            <Box
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: '1fr', md: 'repeat(auto-fit, minmax(360px, 1fr))' },
                gap: 2,
              }}
            >
              {outfits.map((outfit) => (
                <RecommendationBoard key={outfit.id} outfit={outfit} completeness={isPartial ? 'PARTIAL' : 'COMPLETE'} />
              ))}
            </Box>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
