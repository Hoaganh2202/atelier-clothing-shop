import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createAdminProduct, type ProductUpsertPayload } from './product.api';
import { adminProductKeys } from './queryKeys';

export function useCreateProduct() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ProductUpsertPayload) => createAdminProduct(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminProductKeys.lists() });
    },
  });
}
