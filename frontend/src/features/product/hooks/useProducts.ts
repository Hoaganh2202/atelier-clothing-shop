import { useQuery, keepPreviousData } from '@tanstack/react-query';
import {
  getCategoriesApi,
  getProductsApi,
  getProductBySlugApi,
  getFilterOptionsApi,
  type GetProductsParams,
} from '../api/product.api';

export function useCategories() {
  return useQuery({
    queryKey: ['categories', 'public'],
    queryFn: getCategoriesApi,
    staleTime: 5 * 60 * 1000,
  });
}

export function useProducts(params: GetProductsParams = {}) {
  return useQuery({
    queryKey: ['products', 'list', params],
    queryFn: () => getProductsApi(params),
    placeholderData: keepPreviousData,
    staleTime: 60 * 1000,
  });
}

export function useProduct(slug?: string) {
  return useQuery({
    queryKey: ['products', 'detail', slug],
    queryFn: () => getProductBySlugApi(slug!),
    enabled: Boolean(slug),
    retry: (failureCount, error: any) => {
      if (error?.response?.status === 404) {
        return false;
      }
      return failureCount < 2;
    },
  });
}

export function useFilterOptions(categoryId?: number) {
  return useQuery({
    queryKey: ['products', 'filter-options', categoryId],
    queryFn: () => getFilterOptionsApi(categoryId),
    staleTime: 5 * 60 * 1000,
  });
}
