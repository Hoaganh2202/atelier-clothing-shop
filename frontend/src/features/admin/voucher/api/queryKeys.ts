import type { VoucherListParams } from './voucher.api';

export const adminVoucherKeys = {
  all: ['admin', 'vouchers'] as const,
  lists: () => [...adminVoucherKeys.all, 'list'] as const,
  list: (params?: VoucherListParams) => [...adminVoucherKeys.lists(), params] as const,
  details: () => [...adminVoucherKeys.all, 'detail'] as const,
  detail: (id?: number) => [...adminVoucherKeys.details(), id] as const,
};
