import { isAxiosError } from 'axios';
import apiClient from '@/api/client';
import type {
  ApiErrorResponse,
  CustomerDetail,
  CustomerListItem,
  CustomerListParams,
  PageResponse,
  UpdateCustomerStatusPayload,
} from './customer.types';

export function isForbiddenError(error: unknown): boolean {
  return isAxiosError(error) && error.response?.status === 403;
}

export function getCustomerApiErrorMessage(error: unknown, fallbackMessage: string): string {
  if (isAxiosError<ApiErrorResponse>(error)) {
    if (error.response?.status === 403) {
      return 'Bạn không có quyền xem mục này';
    }
    const serverMessage = error.response?.data?.message;
    if (serverMessage && serverMessage.trim().length > 0) {
      return serverMessage;
    }
  }
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }
  return fallbackMessage;
}

export function formatDateTimeVN(value?: string | null, emptyFallback = '—'): string {
  if (!value) {
    return emptyFallback;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function formatDateOnlyVN(value?: string | null, emptyFallback = '—'): string {
  if (!value) {
    return emptyFallback;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(date);
}

export async function getAdminCustomers(
  params?: CustomerListParams
): Promise<PageResponse<CustomerListItem>> {
  const response = await apiClient.get<PageResponse<CustomerListItem>>('/admin/customers', {
    params,
  });
  return response.data;
}

export async function getAdminCustomerById(id: number): Promise<CustomerDetail> {
  const response = await apiClient.get<CustomerDetail>(`/admin/customers/${id}`);
  return response.data;
}

export async function updateAdminCustomerStatus(
  id: number,
  payload: UpdateCustomerStatusPayload
): Promise<CustomerDetail> {
  const response = await apiClient.patch<CustomerDetail>(
    `/admin/customers/${id}/status`,
    payload
  );
  return response.data;
}
