import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateAdminPromotionStatus, type PromotionStatusPayload } from '../api/promotion.api';
import { promotionKeys } from '../api/queryKeys';

interface UpdatePromotionStatusVariables {
  id: number;
  payload: PromotionStatusPayload;
}

export function useUpdatePromotionStatus() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: UpdatePromotionStatusVariables) =>
      updateAdminPromotionStatus(id, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: promotionKeys.all });
      queryClient.invalidateQueries({
        queryKey: promotionKeys.detail(variables.id),
      });
    },
  });
}
