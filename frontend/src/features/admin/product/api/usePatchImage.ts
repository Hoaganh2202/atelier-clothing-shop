import { useMutation, useQueryClient } from '@tanstack/react-query';
import { patchProductImage, type ImageMetadataUpdatePayload } from './product.api';
import { adminProductKeys } from './queryKeys';

export interface PatchImageVariables {
  productId: number;
  imageId: number;
  payload: ImageMetadataUpdatePayload;
}

export function usePatchImage() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ productId, imageId, payload }: PatchImageVariables) =>
      patchProductImage(productId, imageId, payload),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: adminProductKeys.detail(variables.productId) });
      queryClient.invalidateQueries({ queryKey: adminProductKeys.lists() });
    },
  });
}
