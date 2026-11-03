import { useQuery } from '@tanstack/react-query';
import { getAdminCategoryById } from './category.api';
import { adminCategoryKeys } from './queryKeys';

export function useAdminCategory(id?: number) {
  return useQuery({
    queryKey: adminCategoryKeys.detail(id),
    queryFn: () => getAdminCategoryById(id!),
    enabled: typeof id === 'number' && !Number.isNaN(id) && id > 0,
  });
}
