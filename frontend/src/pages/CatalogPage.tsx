import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Pagination from '@mui/material/Pagination';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import type { CatalogFilters } from '../api/catalogApi';
import { CatalogFiltersPanel } from '../components/catalog/CatalogFilters';
import { ProductCard } from '../components/catalog/ProductCard';
import { ProductDetailDialog } from '../components/catalog/ProductDetailDialog';
import { useProducts } from '../hooks/useCatalog';

const PAGE_SIZE = 20;
const defaultFilters: CatalogFilters = { page: 0, pageSize: PAGE_SIZE };

/**
 * Temporary development/demo catalog page. Lets a developer browse and
 * filter the deterministic, locally seeded fashion catalog. Not part of
 * the outfit-recommendation flow; products here are unrelated to any real
 * retailer.
 */
export function CatalogPage() {
  const [filters, setFilters] = useState<CatalogFilters>(defaultFilters);
  const [selectedProductId, setSelectedProductId] = useState<string | null>(null);
  const { data, isPending, isError, error } = useProducts(filters);

  function handleApply(newFilters: CatalogFilters) {
    setFilters({ ...newFilters, page: 0, pageSize: PAGE_SIZE });
  }

  function handleClear() {
    setFilters(defaultFilters);
  }

  function handlePageChange(_event: React.ChangeEvent<unknown>, page: number) {
    setFilters((previous) => ({ ...previous, page: page - 1 }));
  }

  return (
    <>
      <Typography variant="h4" component="h1" sx={{ mb: 1 }}>
        Catalog
      </Typography>
      <Alert severity="info" sx={{ mb: 3 }}>
        Demo catalog using fictional products.
      </Alert>

      <CatalogFiltersPanel onApply={handleApply} onClear={handleClear} />

      {isPending && (
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
          <CircularProgress size={20} />
          <Typography variant="body1">Loading products…</Typography>
        </Stack>
      )}

      {isError && (
        <Alert severity="error" role="alert">
          {error instanceof Error ? error.message : 'Unable to load products.'}
        </Alert>
      )}

      {data && data.content.length === 0 && (
        <Typography variant="body1" color="text.secondary">
          No products match the selected filters.
        </Typography>
      )}

      {data && data.content.length > 0 && (
        <>
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
              gap: 2,
            }}
          >
            {data.content.map((product) => (
              <ProductCard key={product.id} product={product} onViewDetails={setSelectedProductId} />
            ))}
          </Box>

          {data.totalPages > 1 && (
            <Stack sx={{ mt: 3, alignItems: 'center' }}>
              <Pagination
                count={data.totalPages}
                page={(filters.page ?? 0) + 1}
                onChange={handlePageChange}
              />
            </Stack>
          )}
        </>
      )}

      <ProductDetailDialog productId={selectedProductId} onClose={() => setSelectedProductId(null)} />
    </>
  );
}
