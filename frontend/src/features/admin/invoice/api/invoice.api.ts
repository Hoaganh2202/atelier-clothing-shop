import apiClient from '@/api/client';
import axios from 'axios';
import type {
  InvoiceDetailResponse,
  InvoiceFilterParams,
  InvoiceRetryResponse,
  InvoiceSummaryResponse,
  PageResponse,
} from '../invoice.types';

export async function getAdminInvoices(
  params: InvoiceFilterParams
): Promise<PageResponse<InvoiceSummaryResponse>> {
  const response = await apiClient.get<PageResponse<InvoiceSummaryResponse>>(
    '/admin/invoices',
    { params }
  );
  return response.data;
}

export async function getAdminInvoiceDetail(
  id: number
): Promise<InvoiceDetailResponse> {
  const response = await apiClient.get<InvoiceDetailResponse>(
    `/admin/invoices/${id}`
  );
  return response.data;
}

export async function retryInvoiceForOrder(
  orderId: number
): Promise<InvoiceRetryResponse> {
  const response = await apiClient.post<InvoiceRetryResponse>(
    `/admin/invoices/orders/${orderId}/retry`
  );
  return response.data;
}

export function getInvoiceApiErrorMessage(
  error: unknown,
  fallback = 'Đã có lỗi xảy ra khi xử lý hóa đơn'
): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; error?: string } | undefined;
    if (data?.message && typeof data.message === 'string') {
      return data.message;
    }
    if (data?.error && typeof data.error === 'string') {
      return data.error;
    }
    if (error.response?.status === 403) {
      return 'Bạn không có quyền thực hiện thao tác này';
    }
    if (error.response?.status === 404) {
      return 'Không tìm thấy hóa đơn hoặc đơn hàng yêu cầu';
    }
  }
  return fallback;
}
