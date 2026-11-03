import type { InventoryVariantParams } from './inventory.api';

export const inventoryKeys = {
  all: ['admin', 'inventory'] as const,
  variants: () => [...inventoryKeys.all, 'variants'] as const,
  variantList: (params?: InventoryVariantParams) => [...inventoryKeys.variants(), params] as const,
  summary: (threshold?: number) => [...inventoryKeys.all, 'summary', threshold] as const,
};
