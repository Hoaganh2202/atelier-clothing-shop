import { useQuery } from '@tanstack/react-query';
import {
  getCustomerInvoiceDetail,
  getCustomerInvoices,
  getOrderInvoice,
  type CustomerInvoiceParams,
} from '../api/customerInvoice.api';

export const customerInvoiceKeys = {
  all: ['customer-invoices'] as const,
  lists: () => [...customerInvoiceKeys.all, 'list'] as const,
  list: (params?: CustomerInvoiceParams) => [...customerInvoiceKeys.lists(), params] as const,
  details: () => [...customerInvoiceKeys.all, 'detail'] as const,
  detail: (id: number) => [...customerInvoiceKeys.details(), id] as const,
  byOrder: (orderId: number) => [...customerInvoiceKeys.all, 'by-order', orderId] as const,
};

export function useOrderInvoice(orderId: number, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: customerInvoiceKeys.byOrder(orderId),
    queryFn: () => getOrderInvoice(orderId),
    enabled: (options?.enabled ?? true) && Boolean(orderId && orderId > 0),
    retry: 1,
    staleTime: 60 * 1000,
  });
}

export function useCustomerInvoiceDetail(invoiceId: number, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: customerInvoiceKeys.detail(invoiceId),
    queryFn: () => getCustomerInvoiceDetail(invoiceId),
    enabled: (options?.enabled ?? true) && Boolean(invoiceId && invoiceId > 0),
    retry: 1,
    staleTime: 60 * 1000,
  });
}

export function useCustomerInvoices(params?: CustomerInvoiceParams) {
  return useQuery({
    queryKey: customerInvoiceKeys.list(params),
    queryFn: () => getCustomerInvoices(params),
    staleTime: 30 * 1000,
  });
}
