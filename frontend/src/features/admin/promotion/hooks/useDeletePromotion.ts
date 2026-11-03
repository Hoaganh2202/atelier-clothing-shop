import { useMutation, useQueryClient } from '@tanstack/react-query';
import { deleteAdminPromotion } from '../api/promotion.api';
import { promotionKeys } from '../api/queryKeys';

export function useDeletePromotion() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => deleteAdminPromotion(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: promotionKeys.lists() });
    },
  });
}
