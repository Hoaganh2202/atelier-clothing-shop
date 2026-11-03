import { useQuery } from '@tanstack/react-query';
import { getAdminCategories } from './product.api';
import { adminProductKeys } from './queryKeys';

export function useAdminCategories() {
  return useQuery({
    queryKey: adminProductKeys.categories,
    queryFn: getAdminCategories,
    staleTime: 5 * 60 * 1000,
  });
}
