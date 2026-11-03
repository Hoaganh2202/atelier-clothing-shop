import { useQuery } from '@tanstack/react-query';
import { getAdminProductById } from './product.api';
import { adminProductKeys } from './queryKeys';

export function useAdminProduct(id?: number) {
  return useQuery({
    queryKey: adminProductKeys.detail(id),
    queryFn: () => getAdminProductById(id!),
    enabled: Boolean(id && id > 0),
  });
}
