import { useQuery } from '@tanstack/react-query';
import { getAdminVoucherById } from './voucher.api';
import { adminVoucherKeys } from './queryKeys';

export function useVoucher(id?: number) {
  return useQuery({
    queryKey: adminVoucherKeys.detail(id),
    queryFn: () => getAdminVoucherById(id!),
    enabled: Boolean(id && id > 0),
  });
}
