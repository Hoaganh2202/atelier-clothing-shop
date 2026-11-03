import { isAxiosError } from 'axios';
import apiClient from '@/api/client';
import type { components, operations } from '@/api/schema';

export type VoucherItem = components['schemas']['VoucherResponse'];
export type VoucherCreatePayload = components['schemas']['VoucherCreateRequest'];
export type VoucherUpdatePayload = components['schemas']['VoucherUpdateRequest'];
export type VoucherStatusPayload = components['schemas']['VoucherStatusRequest'];
export type VoucherPageResponse = components['schemas']['PageResponseVoucherResponse'];
export type VoucherListParams = NonNullable<
  operations['getVouchers']['parameters']['query']
>;
export type DiscountType = NonNullable<VoucherItem['discountType']>;
export type VoucherTimeStatus = NonNullable<VoucherItem['status']>;
export type ApiErrorResponse = components['schemas']['ErrorResponse'];

export function isForbiddenError(error: unknown): boolean {
  return isAxiosError(error) && error.response?.status === 403;
}

export function isConflictError(error: unknown): boolean {
  return isAxiosError(error) && error.response?.status === 409;
}

export function getVoucherApiErrorMessage(
  error: unknown,
  fallbackMessage: string
): string {
  if (isAxiosError<ApiErrorResponse>(error)) {
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

export function getVoucherApiFieldErrors(
  error: unknown
): Record<string, string> | undefined {
  if (isAxiosError<ApiErrorResponse>(error)) {
    return error.response?.data?.errors;
  }
  return undefined;
}

export function formatVoucherMoneyVND(amount?: number | null): string {
  if (amount === undefined || amount === null) {
    return '0 đ';
  }
  return `${new Intl.NumberFormat('vi-VN').format(amount)} đ`;
}

export function formatVoucherDateTimeVN(
  value?: string | null,
  emptyFallback = '—'
): string {
  if (!value) {
    return emptyFallback;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}`;
}

export async function getAdminVouchers(
  params?: VoucherListParams
): Promise<VoucherPageResponse> {
  const response = await apiClient.get<VoucherPageResponse>('/admin/vouchers', {
    params,
  });
  return response.data;
}

export async function getAdminVoucherById(id: number): Promise<VoucherItem> {
  const response = await apiClient.get<VoucherItem>(`/admin/vouchers/${id}`);
  return response.data;
}

export async function createAdminVoucher(
  payload: VoucherCreatePayload
): Promise<VoucherItem> {
  const response = await apiClient.post<VoucherItem>('/admin/vouchers', payload);
  return response.data;
}

export async function updateAdminVoucher(
  id: number,
  payload: VoucherUpdatePayload
): Promise<VoucherItem> {
  const response = await apiClient.put<VoucherItem>(
    `/admin/vouchers/${id}`,
    payload
  );
  return response.data;
}

export async function updateAdminVoucherStatus(
  id: number,
  payload: VoucherStatusPayload
): Promise<VoucherItem> {
  const response = await apiClient.patch<VoucherItem>(
    `/admin/vouchers/${id}/status`,
    payload
  );
  return response.data;
}
