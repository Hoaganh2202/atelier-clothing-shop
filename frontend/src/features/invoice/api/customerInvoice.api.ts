import { isAxiosError } from 'axios';
import apiClient from '@/api/client';
import type {
  InvoiceDetailResponse,
  InvoiceSummaryResponse,
  PageResponse,
} from '../invoice.types';

export interface CustomerInvoiceParams {
  page?: number;
  size?: number;
}

export async function getOrderInvoice(orderId: number): Promise<InvoiceDetailResponse> {
  const response = await apiClient.get<InvoiceDetailResponse>(`/orders/${orderId}/invoice`);
  return response.data;
}

export async function getCustomerInvoiceDetail(invoiceId: number): Promise<InvoiceDetailResponse> {
  const response = await apiClient.get<InvoiceDetailResponse>(`/invoices/${invoiceId}`);
  return response.data;
}

export async function getCustomerInvoices(
  params?: CustomerInvoiceParams
): Promise<PageResponse<InvoiceSummaryResponse>> {
  const response = await apiClient.get<PageResponse<InvoiceSummaryResponse>>('/invoices', {
    params,
  });
  return response.data;
}

export function getCustomerInvoiceErrorMessage(error: unknown, fallbackMessage: string): string {
  if (isAxiosError(error)) {
    if (error.response?.status === 404) {
      return 'Đơn hàng này chưa có hóa đơn điện tử hoặc hóa đơn đang trong quá trình phát hành.';
    }
    if (error.response?.status === 403) {
      return 'Bạn không có quyền truy cập vào hóa đơn điện tử của đơn hàng này.';
    }
    const serverMessage = (error.response?.data as { message?: string })?.message;
    if (serverMessage && typeof serverMessage === 'string') {
      return serverMessage;
    }
  }
  return fallbackMessage;
}
