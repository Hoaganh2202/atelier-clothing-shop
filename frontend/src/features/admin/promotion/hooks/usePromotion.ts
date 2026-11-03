import { useQuery } from '@tanstack/react-query';
import { getAdminPromotionById } from '../api/promotion.api';
import { promotionKeys } from '../api/queryKeys';

export function usePromotion(id?: number) {
  return useQuery({
    queryKey: id ? promotionKeys.detail(id) : [...promotionKeys.details(), 'undefined'],
    queryFn: () => getAdminPromotionById(id!),
    enabled: Boolean(id && id > 0),
  });
}
