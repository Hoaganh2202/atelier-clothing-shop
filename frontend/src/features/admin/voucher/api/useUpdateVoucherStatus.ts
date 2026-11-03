import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateAdminVoucherStatus, type VoucherStatusPayload } from './voucher.api';
import { adminVoucherKeys } from './queryKeys';

interface UpdateVoucherStatusVariables {
  id: number;
  payload: VoucherStatusPayload;
}

export function useUpdateVoucherStatus() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: UpdateVoucherStatusVariables) =>
      updateAdminVoucherStatus(id, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: adminVoucherKeys.all });
      queryClient.invalidateQueries({
        queryKey: adminVoucherKeys.detail(variables.id),
      });
    },
  });
}
