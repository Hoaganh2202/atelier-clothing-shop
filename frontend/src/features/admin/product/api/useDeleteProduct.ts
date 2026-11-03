import { useMutation, useQueryClient } from '@tanstack/react-query';
import { deleteAdminProduct } from './product.api';
import { adminProductKeys } from './queryKeys';

export function useDeleteProduct() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => deleteAdminProduct(id),
    onSuccess: (_data, id) => {
      queryClient.invalidateQueries({ queryKey: adminProductKeys.lists() });
      queryClient.removeQueries({ queryKey: adminProductKeys.detail(id) });
    },
  });
}
