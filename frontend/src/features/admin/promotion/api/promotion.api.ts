import { isAxiosError } from 'axios';
import apiClient from '@/api/client';
import type { components, operations } from '@/api/schema';

export type PromotionListItem = components['schemas']['PromotionListItemResponse'];
export type PromotionDetail = components['schemas']['PromotionResponse'];
export type PromotionProductItem = components['schemas']['PromotionProductItemResponse'];
export type PromotionCreatePayload = components['schemas']['PromotionCreateRequest'];
export type PromotionUpdatePayload = components['schemas']['PromotionUpdateRequest'];
export type PromotionStatusPayload = components['schemas']['PromotionStatusRequest'];
export type PageResponsePromotion = components['schemas']['PageResponsePromotionListItemResponse'];
export type ApiErrorResponse = components['schemas']['ErrorResponse'];
export type PromotionListParams = operations['getPromotions']['parameters']['query'];

export type ProductAdminItem = components['schemas']['ProductAdminListItemResponse'];
export type PageResponseProductAdmin = components['schemas']['PageResponseProductAdminListItemResponse'];

export function isConflictError(error: unknown): boolean {
  return isAxiosError(error) && error.response?.status === 409;
}

export function isForbiddenError(error: unknown): boolean {
  return isAxiosError(error) && error.response?.status === 403;
}

export function getApiErrorMessage(error: unknown, fallbackMessage: string): string {
  if (isAxiosError<ApiErrorResponse>(error)) {
    const serverMessage = error.response?.data?.message;
    if (serverMessage && serverMessage.trim().length > 0) {
      return serverMessage;
    }
    if (error.response?.status === 401) {
      return 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.';
    }
    if (error.response?.status === 403) {
      return 'Bạn không có quyền thực hiện thao tác này.';
    }
    if (error.response?.status === 404) {
      return 'Không tìm thấy dữ liệu yêu cầu.';
    }
  }
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }
  return fallbackMessage;
}

export function getApiFieldErrors(error: unknown): Record<string, string> | undefined {
  if (isAxiosError<ApiErrorResponse>(error)) {
    return error.response?.data?.errors;
  }
  return undefined;
}

export function formatPromotionDateTimeVN(
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

export async function getAdminPromotions(params?: PromotionListParams): Promise<PageResponsePromotion> {
  const response = await apiClient.get<PageResponsePromotion>('/admin/promotions', { params });
  return response.data;
}

export async function getAdminPromotionById(id: number): Promise<PromotionDetail> {
  const response = await apiClient.get<PromotionDetail>(`/admin/promotions/${id}`);
  return response.data;
}

export async function createAdminPromotion(payload: PromotionCreatePayload): Promise<PromotionDetail> {
  const response = await apiClient.post<PromotionDetail>('/admin/promotions', payload);
  return response.data;
}

export async function updateAdminPromotion(
  id: number,
  payload: PromotionUpdatePayload
): Promise<PromotionDetail> {
  const response = await apiClient.put<PromotionDetail>(`/admin/promotions/${id}`, payload);
  return response.data;
}

export async function updateAdminPromotionStatus(
  id: number,
  payload: PromotionStatusPayload
): Promise<PromotionDetail> {
  const response = await apiClient.patch<PromotionDetail>(`/admin/promotions/${id}/status`, payload);
  return response.data;
}

export async function deleteAdminPromotion(id: number): Promise<void> {
  await apiClient.delete(`/admin/promotions/${id}`);
}

export async function searchAdminProducts(keyword: string): Promise<ProductAdminItem[]> {
  const response = await apiClient.get<PageResponseProductAdmin>('/admin/products', {
    params: {
      keyword: keyword.trim() ? keyword.trim() : undefined,
      isActive: true,
      size: 50,
    },
  });
  return response.data.content || [];
}
