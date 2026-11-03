import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateAdminProduct, type ProductUpsertPayload } from './product.api';
import { adminProductKeys } from './queryKeys';

export function useUpdateProduct() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: ProductUpsertPayload }) =>
      updateAdminProduct(id, payload),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: adminProductKeys.lists() });
      queryClient.invalidateQueries({ queryKey: adminProductKeys.detail(variables.id) });
    },
  });
}
