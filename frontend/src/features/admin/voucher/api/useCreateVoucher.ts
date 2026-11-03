import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createAdminVoucher, type VoucherCreatePayload } from './voucher.api';
import { adminVoucherKeys } from './queryKeys';

export function useCreateVoucher() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: VoucherCreatePayload) => createAdminVoucher(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: adminVoucherKeys.all });
    },
  });
}
