import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createAdminPromotion, type PromotionCreatePayload } from '../api/promotion.api';
import { promotionKeys } from '../api/queryKeys';

export function useCreatePromotion() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: PromotionCreatePayload) => createAdminPromotion(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: promotionKeys.lists() });
    },
  });
}
