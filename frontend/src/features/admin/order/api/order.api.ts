import { isAxiosError } from 'axios';
import apiClient from '@/api/client';
import type {
  ApiErrorResponse,
  OrderAdminDetailResponse,
  OrderAdminListItemResponse,
  OrderListParams,
  OrderStatusUpdateRequest,
  PageResponse,
} from '../order.types';

export function isForbiddenError(error: unknown): boolean {
  return isAxiosError(error) && error.response?.status === 403;
}

export function getOrderApiErrorMessage(error: unknown, fallbackMessage: string): string {
  if (isAxiosError<ApiErrorResponse>(error)) {
    if (error.response?.status === 403) {
      return 'Bạn không có quyền truy cập mục này';
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

const TIMEZONE_VN = 'Asia/Ho_Chi_Minh';

function parseDateWithTimezone(value: string): Date | null {
  const str = value.trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(str)) {
    const date = new Date(`${str}T00:00:00+07:00`);
    return Number.isNaN(date.getTime()) ? null : date;
  }
  const hasTime = /[T\s]\d{2}:\d{2}/.test(str);
  if (hasTime) {
    const hasOffset = /[T\s].*(?:Z|[+-]\d{2}(?::?\d{2})?)$/i.test(str);
    const normalized = hasOffset ? str : `${str}+07:00`;
    const date = new Date(normalized);
    return Number.isNaN(date.getTime()) ? null : date;
  }
  const date = new Date(str);
  return Number.isNaN(date.getTime()) ? null : date;
}

export { formatDateTimeVN } from '@/lib/format';

export function formatDateOnlyVN(value?: string | null, emptyFallback = '—'): string {
  if (!value || value.trim().length === 0) {
    return emptyFallback;
  }
  const date = parseDateWithTimezone(value);
  if (!date) {
    return value;
  }
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: TIMEZONE_VN,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(date);
}

export async function getAdminOrders(
  params?: OrderListParams
): Promise<PageResponse<OrderAdminListItemResponse>> {
  const cleanParams: Record<string, unknown> = {};
  if (params) {
    if (params.page !== undefined) cleanParams.page = params.page;
    if (params.size !== undefined) cleanParams.size = params.size;
    if (params.status) cleanParams.status = params.status;
    if (params.keyword && params.keyword.trim().length > 0) {
      cleanParams.keyword = params.keyword.trim();
    }
    if (params.startDate) cleanParams.startDate = params.startDate;
    if (params.endDate) cleanParams.endDate = params.endDate;
    if (params.sort) cleanParams.sort = params.sort;
  }

  const response = await apiClient.get<PageResponse<OrderAdminListItemResponse>>(
    '/admin/orders',
    { params: cleanParams }
  );
  return response.data;
}

export async function getAdminOrderById(id: number): Promise<OrderAdminDetailResponse> {
  const response = await apiClient.get<OrderAdminDetailResponse>(`/admin/orders/${id}`);
  return response.data;
}

export async function updateAdminOrderStatus(
  id: number,
  payload: OrderStatusUpdateRequest
): Promise<OrderAdminDetailResponse> {
  const response = await apiClient.patch<OrderAdminDetailResponse>(
    `/admin/orders/${id}/status`,
    payload
  );
  return response.data;
}
