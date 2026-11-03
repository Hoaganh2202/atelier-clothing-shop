import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateAdminVoucher, type VoucherUpdatePayload } from './voucher.api';
import { adminVoucherKeys } from './queryKeys';

interface UpdateVoucherVariables {
  id: number;
  payload: VoucherUpdatePayload;
}

export function useUpdateVoucher() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: UpdateVoucherVariables) =>
      updateAdminVoucher(id, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: adminVoucherKeys.all });
      queryClient.invalidateQueries({
        queryKey: adminVoucherKeys.detail(variables.id),
      });
    },
  });
}
