import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import type { LiveOutfitRecommendation } from '../../../api/liveRecommendationsApi';
import { OutfitProductTile } from './OutfitProductTile';
import { RecommendationBoardHeader } from './RecommendationBoardHeader';
import { RecommendationScores } from './RecommendationScores';
import type { BoardCompleteness } from './RecommendationStatusBadge';

interface RecommendationBoardProps {
  outfit: LiveOutfitRecommendation;
  completeness: BoardCompleteness;
}

/**
 * One recommended product set: a compact, text-based group of products for
 * one outfit look - never image-based for the live Nordstrom pipeline (no
 * authorized product feed is available yet; see docs/ROADMAP.md). Used for
 * both complete and partial generations - a partial board only ever
 * contains the items that were actually found; missing items/categories
 * are surfaced once at the section level, not per board.
 */
export function RecommendationBoard({ outfit, completeness }: RecommendationBoardProps) {
  return (
    <Card variant="outlined">
      <CardContent>
        <RecommendationBoardHeader
          name={outfit.name}
          completeness={completeness}
          source={outfit.source}
          generatedAt={outfit.generatedAt}
        />

        <RecommendationScores />

        <Box
          sx={{
            mt: 2,
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
            gap: 1.5,
          }}
        >
          {outfit.items.map((item) => (
            <OutfitProductTile key={item.id} item={item} />
          ))}
        </Box>

        {outfit.explanation && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
            {outfit.explanation}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}
