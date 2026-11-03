import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createAdminCategory, type CategoryUpsertPayload } from './category.api';
import { adminCategoryKeys } from './queryKeys';

export function useCreateCategory() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CategoryUpsertPayload) => createAdminCategory(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminCategoryKeys.all });
      queryClient.invalidateQueries({ queryKey: ['public', 'categories'] });
    },
  });
}
