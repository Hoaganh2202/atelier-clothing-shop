import type { PromotionListParams } from './promotion.api';

export const promotionKeys = {
  all: ['admin', 'promotions'] as const,
  lists: () => [...promotionKeys.all, 'list'] as const,
  list: (params?: PromotionListParams) => [...promotionKeys.lists(), params] as const,
  details: () => [...promotionKeys.all, 'detail'] as const,
  detail: (id: number) => [...promotionKeys.details(), id] as const,
};
