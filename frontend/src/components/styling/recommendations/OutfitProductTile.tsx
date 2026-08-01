import Chip from '@mui/material/Chip';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { LiveOutfitItem } from '../../../api/liveRecommendationsApi';
import { formatEnumLabel } from './formatters';

interface OutfitProductTileProps {
  item: LiveOutfitItem;
}

/** The readable generic category label for this item - `requestedItemGenericCategory` (Task 8.5 explicit-item pipeline) and `category` (legacy category-template pipeline) are mutually exclusive; never expose the raw enum value on its own. */
function categoryLabel(item: LiveOutfitItem): string | null {
  if (item.requestedItemGenericCategory) {
    return formatEnumLabel(item.requestedItemGenericCategory);
  }
  return item.category ? formatEnumLabel(item.category) : null;
}

/**
 * One compact, text-based row for a single live Nordstrom product - no
 * image, image placeholder, or reserved image space of any kind. Product
 * images, current prices, sizes, colors, and availability are not shown
 * here at all; they are confirmed directly on Nordstrom (see {@code
 * NordstromVerificationNotice}) rather than approximated in this UI.
 */
export function OutfitProductTile({ item }: OutfitProductTileProps) {
  const title = item.title ?? 'Nordstrom product';
  const displayTitle = item.brand ? `${item.brand} ${title}` : title;
  const category = categoryLabel(item);
  const hasKnownAudience = item.audience !== 'UNKNOWN';

  return (
    <Stack
      spacing={0.5}
      sx={{
        p: 1.25,
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 1,
        minWidth: 0,
      }}
    >
      {item.requestedItemPhrase && (
        <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: 0.3 }}>
          {item.requestedItemPhrase}
        </Typography>
      )}

      <Typography variant="body2" sx={{ fontWeight: 600 }}>
        {displayTitle}
      </Typography>

      {(category || hasKnownAudience) && (
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 0.5, alignItems: 'center' }}>
          {category && <Chip size="small" variant="outlined" label={category} />}
          {hasKnownAudience && <Chip size="small" variant="outlined" label={formatEnumLabel(item.audience)} />}
        </Stack>
      )}

      <Link
        href={item.productUrl}
        target="_blank"
        rel="noopener noreferrer"
        variant="body2"
        aria-label={`View ${displayTitle} on Nordstrom, opens in new tab`}
      >
        View on Nordstrom
      </Link>
    </Stack>
  );
}

