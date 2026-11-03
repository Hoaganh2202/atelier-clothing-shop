import { useMutation, useQueryClient } from '@tanstack/react-query';
import { deleteProductImage } from './product.api';
import { adminProductKeys } from './queryKeys';

export interface DeleteImageVariables {
  productId: number;
  imageId: number;
}

export function useDeleteImage() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ productId, imageId }: DeleteImageVariables) =>
      deleteProductImage(productId, imageId),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: adminProductKeys.detail(variables.productId) });
      queryClient.invalidateQueries({ queryKey: adminProductKeys.lists() });
    },
  });
}
