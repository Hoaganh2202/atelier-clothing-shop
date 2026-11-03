import { useQuery } from '@tanstack/react-query';
import { getAdminProducts, type ProductListParams } from './product.api';
import { adminProductKeys } from './queryKeys';

export function useAdminProducts(params?: ProductListParams) {
  return useQuery({
    queryKey: adminProductKeys.list(params),
    queryFn: () => getAdminProducts(params),
    placeholderData: (previousData) => previousData,
  });
}
