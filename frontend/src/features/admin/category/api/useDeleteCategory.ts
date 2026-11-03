import { useMutation, useQueryClient } from '@tanstack/react-query';
import { deleteAdminCategory } from './category.api';
import { adminCategoryKeys } from './queryKeys';

export function useDeleteCategory() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: number) => deleteAdminCategory(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminCategoryKeys.all });
      queryClient.invalidateQueries({ queryKey: ['public', 'categories'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
    },
  });
}
