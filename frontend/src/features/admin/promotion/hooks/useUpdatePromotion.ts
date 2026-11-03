import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateAdminPromotion, type PromotionUpdatePayload } from '../api/promotion.api';
import { promotionKeys } from '../api/queryKeys';

interface UpdatePromotionVariables {
  id: number;
  payload: PromotionUpdatePayload;
}

export function useUpdatePromotion() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: UpdatePromotionVariables) =>
      updateAdminPromotion(id, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: promotionKeys.all });
      queryClient.invalidateQueries({
        queryKey: promotionKeys.detail(variables.id),
      });
    },
  });
}
