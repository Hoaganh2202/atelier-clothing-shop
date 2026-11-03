import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { getAdminVouchers, type VoucherListParams } from './voucher.api';
import { adminVoucherKeys } from './queryKeys';

export function useVouchers(params?: VoucherListParams) {
  return useQuery({
    queryKey: adminVoucherKeys.list(params),
    queryFn: () => getAdminVouchers(params),
    placeholderData: keepPreviousData,
  });
}
