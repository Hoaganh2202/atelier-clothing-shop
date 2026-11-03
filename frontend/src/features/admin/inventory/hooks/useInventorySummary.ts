import { useQuery } from '@tanstack/react-query';
import { getInventorySummary } from '../api/inventory.api';
import { inventoryKeys } from '../api/queryKeys';

export function useInventorySummary(threshold?: number) {
  return useQuery({
    queryKey: inventoryKeys.summary(threshold),
    queryFn: () => getInventorySummary(threshold),
  });
}
