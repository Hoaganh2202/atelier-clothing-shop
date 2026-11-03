import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateAdminCategory, type CategoryUpsertPayload } from './category.api';
import { adminCategoryKeys } from './queryKeys';

interface UpdateCategoryVariables {
  id: number;
  payload: CategoryUpsertPayload;
}

export function useUpdateCategory() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: UpdateCategoryVariables) =>
      updateAdminCategory(id, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: adminCategoryKeys.all });
      queryClient.invalidateQueries({ queryKey: adminCategoryKeys.detail(variables.id) });
      queryClient.invalidateQueries({ queryKey: ['public', 'categories'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
    },
  });
}
