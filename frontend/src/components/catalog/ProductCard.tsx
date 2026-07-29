import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardActions from '@mui/material/CardActions';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { ProductSummary } from '../../api/catalogApi';

interface ProductCardProps {
  product: ProductSummary;
  onViewDetails: (productId: string) => void;
}

/**
 * Summary card for one catalog product. Uses a CSS placeholder (rather
 * than loading `imageUrl`, which points at a fictional, non-existent asset
 * path) so there's never a broken-image icon or an external network
 * request.
 */
export function ProductCard({ product, onViewDetails }: ProductCardProps) {
  return (
    <Card variant="outlined" sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box
        sx={{
          height: 140,
          bgcolor: 'action.hover',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
        aria-hidden="true"
      >
        <Typography variant="overline" color="text.secondary">
          {product.category}
        </Typography>
      </Box>
      <CardContent sx={{ flexGrow: 1 }}>
        <Typography variant="caption" color="text.secondary">
          {product.brand}
        </Typography>
        <Typography variant="h6" component="h3">
          {product.name}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          Starting at ${product.startingPrice.toFixed(2)} &middot; Formality {product.formalityLevel}/10
        </Typography>

        <Typography variant="body2">Sizes: {product.availableSizes.join(', ')}</Typography>
        <Typography variant="body2" sx={{ mb: 1 }}>
          Colors: {product.availableColors.join(', ')}
        </Typography>

        <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5, mb: 1 }}>
          {[...product.occasionTags, ...product.styleTags, ...product.weatherTags].map((tag) => (
            <Chip key={tag} label={tag} size="small" />
          ))}
        </Stack>

        <Chip
          label={product.inStock ? 'In stock' : 'Out of stock'}
          color={product.inStock ? 'success' : 'default'}
          size="small"
        />
      </CardContent>
      <CardActions>
        <Button size="small" onClick={() => onViewDetails(product.id)}>
          View details
        </Button>
      </CardActions>
    </Card>
  );
}
