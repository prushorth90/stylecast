import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

export interface FoundItemEntry {
  id: string;
  label: string;
  url: string | null;
}

interface FoundRequestedItemsProps {
  items: FoundItemEntry[];
}

/**
 * Lists every requested item (or required category) a live search actually
 * found, each with a working Nordstrom link when one is known. Renders
 * nothing when there is nothing to list.
 */
export function FoundRequestedItems({ items }: FoundRequestedItemsProps) {
  if (items.length === 0) {
    return null;
  }

  return (
    <Stack spacing={0.5}>
      <Typography variant="subtitle2">Found</Typography>
      <Stack component="ul" sx={{ m: 0, pl: 2.5 }}>
        {items.map((item) => (
          <Typography key={item.id} component="li" variant="body2">
            {item.label}
            {item.url && (
              <>
                {' — '}
                <Link
                  href={item.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label={`View ${item.label} on Nordstrom, opens in new tab`}
                >
                  View on Nordstrom
                </Link>
              </>
            )}
          </Typography>
        ))}
      </Stack>
    </Stack>
  );
}
