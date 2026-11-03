export const adminProductKeys = {
  all: ['admin', 'products'] as const,
  lists: () => [...adminProductKeys.all, 'list'] as const,
  list: (params?: unknown) => [...adminProductKeys.lists(), params] as const,
  details: () => [...adminProductKeys.all, 'detail'] as const,
  detail: (id?: number) => [...adminProductKeys.details(), id] as const,
  categories: ['admin', 'categories'] as const,
};
