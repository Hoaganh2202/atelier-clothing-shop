import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getAdminInvoiceDetail,
  getAdminInvoices,
  retryInvoiceForOrder,
} from './invoice.api';
import type { InvoiceFilterParams } from '../invoice.types';

export const INVOICE_QUERY_KEYS = {
  all: ['admin-invoices'] as const,
  list: (params: InvoiceFilterParams) =>
    [...INVOICE_QUERY_KEYS.all, 'list', params] as const,
  detail: (id?: number) =>
    [...INVOICE_QUERY_KEYS.all, 'detail', id] as const,
};

export function useAdminInvoices(params: InvoiceFilterParams) {
  return useQuery({
    queryKey: INVOICE_QUERY_KEYS.list(params),
    queryFn: () => getAdminInvoices(params),
    placeholderData: (previousData) => previousData,
  });
}

export function useAdminInvoiceDetail(id?: number) {
  return useQuery({
    queryKey: INVOICE_QUERY_KEYS.detail(id),
    queryFn: () => {
      if (!id) throw new Error('Invoice ID is required');
      return getAdminInvoiceDetail(id);
    },
    enabled: typeof id === 'number' && id > 0,
  });
}

export function useRetryInvoice() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (orderId: number) => retryInvoiceForOrder(orderId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: INVOICE_QUERY_KEYS.all });
    },
  });
}
