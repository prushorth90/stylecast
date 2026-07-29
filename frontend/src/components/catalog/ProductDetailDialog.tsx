import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useProduct } from '../../hooks/useCatalog';

interface ProductDetailDialogProps {
  productId: string | null;
  onClose: () => void;
}

/**
 * Dialog showing full product detail: description, every variant (size,
 * color, effective price, stock), and all occasion/style/weather tags.
 */
export function ProductDetailDialog({ productId, onClose }: ProductDetailDialogProps) {
  const { data: product, isPending, isError, error } = useProduct(productId ?? undefined);

  return (
    <Dialog open={Boolean(productId)} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{product ? product.name : 'Product details'}</DialogTitle>
      <DialogContent>
        {isPending && (
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
            <CircularProgress size={20} />
            <Typography variant="body1">Loading product…</Typography>
          </Stack>
        )}

        {isError && (
          <Alert severity="error" role="alert">
            {error instanceof Error ? error.message : 'Unable to load this product.'}
          </Alert>
        )}

        {product && (
          <Stack spacing={2}>
            <Typography variant="body2" color="text.secondary">
              {product.brand} &middot; {product.category} &middot; Formality {product.formalityLevel}/10
            </Typography>

            <Typography variant="body1">{product.description}</Typography>

            <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
              {[...product.occasionTags, ...product.styleTags, ...product.weatherTags].map((tag) => (
                <Chip key={tag} label={tag} size="small" />
              ))}
            </Stack>

            <Typography variant="subtitle1">Variants</Typography>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Size</TableCell>
                  <TableCell>Color</TableCell>
                  <TableCell>Price</TableCell>
                  <TableCell>Stock</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {product.variants.map((variant) => (
                  <TableRow key={variant.id}>
                    <TableCell>{variant.clothingSize}</TableCell>
                    <TableCell>{variant.color}</TableCell>
                    <TableCell>${variant.effectivePrice.toFixed(2)}</TableCell>
                    <TableCell>
                      {variant.inStock ? `In stock (${variant.quantityAvailable})` : 'Out of stock'}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
