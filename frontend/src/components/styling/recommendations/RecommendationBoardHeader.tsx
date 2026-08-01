import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { LiveRecommendationSource } from '../../../api/liveRecommendationsApi';
import { formatDateTime } from './formatters';
import { RecommendationStatusBadge, type BoardCompleteness } from './RecommendationStatusBadge';

const SOURCE_LABELS: Record<string, string> = {
  LIVE_NORDSTROM: 'Live Nordstrom',
  LOCAL_CATALOG: 'Demo catalog',
};

interface RecommendationBoardHeaderProps {
  name: string;
  completeness: BoardCompleteness;
  source: LiveRecommendationSource;
  generatedAt: string | null;
}

/**
 * Recommended product set header: name, completeness badge, source badge
 * (Live Nordstrom vs. Demo catalog - never shown ambiguously), and a
 * generation timestamp. Never shows a price total - current prices are
 * confirmed directly on Nordstrom, not approximated here.
 */
export function RecommendationBoardHeader({
  name,
  completeness,
  source,
  generatedAt,
}: RecommendationBoardHeaderProps) {
  return (
    <Stack spacing={0.5}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
        <Typography variant="subtitle1" component="h3">
          {name}
        </Typography>
        <RecommendationStatusBadge status={completeness} />
        <Chip size="small" color="primary" label={SOURCE_LABELS[source] ?? source} />
      </Stack>
      {generatedAt && (
        <Typography variant="caption" color="text.secondary">
          Generated {formatDateTime(generatedAt)}
        </Typography>
      )}
    </Stack>
  );
}
