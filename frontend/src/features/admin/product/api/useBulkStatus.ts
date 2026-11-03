import { useMutation, useQueryClient } from '@tanstack/react-query';
import { bulkUpdateProductStatus, type BulkStatusPayload } from './product.api';
import { adminProductKeys } from './queryKeys';

export function useBulkStatus() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: BulkStatusPayload) => bulkUpdateProductStatus(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminProductKeys.lists() });
      queryClient.invalidateQueries({ queryKey: adminProductKeys.details() });
    },
  });
}
