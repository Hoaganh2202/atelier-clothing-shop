export const adminCategoryKeys = {
  all: ['admin', 'categories'] as const,
  lists: () => [...adminCategoryKeys.all, 'list'] as const,
  details: () => [...adminCategoryKeys.all, 'detail'] as const,
  detail: (id?: number) => [...adminCategoryKeys.details(), id] as const,
};
