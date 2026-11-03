import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  updateInventoryStock,
  type InventoryStockUpdatePayload,
  type InventoryVariantItem,
} from '../api/inventory.api';
import { inventoryKeys } from '../api/queryKeys';

interface UpdateStockVariables {
  id: number;
  payload: InventoryStockUpdatePayload;
}

export function useUpdateInventoryStock() {
  const queryClient = useQueryClient();

  return useMutation<InventoryVariantItem, unknown, UpdateStockVariables>({
    mutationFn: ({ id, payload }) => updateInventoryStock(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: inventoryKeys.all });
    },
  });
}
