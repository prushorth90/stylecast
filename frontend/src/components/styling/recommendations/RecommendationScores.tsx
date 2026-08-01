import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

interface RecommendationScoresProps {
  occasionScore?: number | null;
  weatherScore?: number | null;
  overallScore?: number | null;
}

/**
 * Displays deterministic occasion/weather/overall fit scores when the
 * recommendation source actually computed them (e.g. a future demo/local-
 * catalog board). Renders nothing when no score is available - a score is
 * never fabricated for data that wasn't computed, which is always the case
 * for the live-Nordstrom pipeline today.
 */
export function RecommendationScores({ occasionScore, weatherScore, overallScore }: RecommendationScoresProps) {
  const entries: Array<[string, number]> = [];
  if (occasionScore !== undefined && occasionScore !== null) {
    entries.push(['Occasion fit', occasionScore]);
  }
  if (weatherScore !== undefined && weatherScore !== null) {
    entries.push(['Weather fit', weatherScore]);
  }
  if (overallScore !== undefined && overallScore !== null) {
    entries.push(['Overall fit', overallScore]);
  }

  if (entries.length === 0) {
    return null;
  }

  return (
    <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
      {entries.map(([label, value]) => (
        <Typography key={label} variant="caption" color="text.secondary">
          {label}: {value}/100
        </Typography>
      ))}
    </Stack>
  );
}
