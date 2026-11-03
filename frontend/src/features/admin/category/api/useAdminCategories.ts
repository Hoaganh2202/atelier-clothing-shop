import { useQuery } from '@tanstack/react-query';
import { getAdminCategories } from './category.api';
import { adminCategoryKeys } from './queryKeys';

export function useAdminCategories() {
  return useQuery({
    queryKey: adminCategoryKeys.all,
    queryFn: getAdminCategories,
    staleTime: 5 * 60 * 1000,
  });
}
