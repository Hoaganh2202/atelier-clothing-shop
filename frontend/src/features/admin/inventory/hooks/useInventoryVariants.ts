import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { getInventoryVariants, type InventoryVariantParams } from '../api/inventory.api';
import { inventoryKeys } from '../api/queryKeys';

export function useInventoryVariants(params?: InventoryVariantParams) {
  return useQuery({
    queryKey: inventoryKeys.variantList(params),
    queryFn: () => getInventoryVariants(params),
    placeholderData: keepPreviousData,
  });
}
