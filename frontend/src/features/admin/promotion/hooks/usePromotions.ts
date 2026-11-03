import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { getAdminPromotions, type PromotionListParams } from '../api/promotion.api';
import { promotionKeys } from '../api/queryKeys';

export function usePromotions(params?: PromotionListParams) {
  return useQuery({
    queryKey: promotionKeys.list(params),
    queryFn: () => getAdminPromotions(params),
    placeholderData: keepPreviousData,
  });
}
