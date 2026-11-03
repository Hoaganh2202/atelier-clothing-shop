import { useMutation, useQueryClient } from '@tanstack/react-query';
import { uploadProductImage } from './product.api';
import { adminProductKeys } from './queryKeys';

export interface UploadImageVariables {
  productId: number;
  file: File;
  meta?: {
    isPrimary?: boolean;
    color?: string;
    alt?: string;
  };
}

export function useUploadImage() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ productId, file, meta }: UploadImageVariables) =>
      uploadProductImage(productId, file, meta),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: adminProductKeys.detail(variables.productId) });
      queryClient.invalidateQueries({ queryKey: adminProductKeys.lists() });
    },
  });
}
