import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

export interface MissingItemEntry {
  id: string;
  label: string;
}

interface MissingRequestedItemsProps {
  items: MissingItemEntry[];
}

/**
 * Lists every requested item (or required category) a live search could
 * not find - a missing item is always reported here, never silently
 * dropped or substituted with an unrelated product. Renders nothing when
 * there is nothing to list.
 */
export function MissingRequestedItems({ items }: MissingRequestedItemsProps) {
  if (items.length === 0) {
    return null;
  }

  return (
    <Stack spacing={0.5}>
      <Typography variant="subtitle2">Missing</Typography>
      <Stack component="ul" sx={{ m: 0, pl: 2.5 }}>
        {items.map((item) => (
          <Typography key={item.id} component="li" variant="body2" color="text.secondary">
            {item.label} — No matching Nordstrom product found
          </Typography>
        ))}
      </Stack>
    </Stack>
  );
}
