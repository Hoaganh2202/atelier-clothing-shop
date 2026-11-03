import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateAdminCategoryStatus, type CategoryStatusPayload } from './category.api';
import { adminCategoryKeys } from './queryKeys';

interface UpdateStatusVariables {
  id: number;
  payload: CategoryStatusPayload;
}

export function useUpdateCategoryStatus() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: UpdateStatusVariables) =>
      updateAdminCategoryStatus(id, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: adminCategoryKeys.all });
      queryClient.invalidateQueries({ queryKey: adminCategoryKeys.detail(variables.id) });
      queryClient.invalidateQueries({ queryKey: ['public', 'categories'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
    },
  });
}
