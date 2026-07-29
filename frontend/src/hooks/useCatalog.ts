import { useQuery } from '@tanstack/react-query';
import { fetchProductById, fetchProducts, type CatalogFilters } from '../api/catalogApi';

const productsQueryKey = (filters: CatalogFilters) => ['products', filters] as const;
const productQueryKey = (productId: string) => ['products', 'detail', productId] as const;

export function useProducts(filters: CatalogFilters) {
  return useQuery({
    queryKey: productsQueryKey(filters),
    queryFn: () => fetchProducts(filters),
  });
}

export function useProduct(productId: string | undefined) {
  return useQuery({
    queryKey: productQueryKey(productId ?? ''),
    queryFn: () => fetchProductById(productId as string),
    enabled: Boolean(productId),
  });
}
