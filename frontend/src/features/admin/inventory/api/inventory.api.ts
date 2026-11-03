import { isAxiosError } from 'axios';
import apiClient from '@/api/client';
import type { components, operations } from '@/api/schema';

export type InventoryVariantItem = components['schemas']['InventoryVariantResponse'];
export type PageResponseInventoryVariant = components['schemas']['PageResponseInventoryVariantResponse'];
export type InventorySummary = components['schemas']['InventorySummaryResponse'];
export type InventoryStockUpdatePayload = components['schemas']['InventoryStockUpdateRequest'];
export type ApiErrorResponse = components['schemas']['ErrorResponse'];

export type InventoryVariantParams = operations['getVariants']['parameters']['query'];
export type InventoryStockStatusType = 'ALL' | 'OUT_OF_STOCK' | 'LOW_STOCK' | 'IN_STOCK';

export function isConflictError(error: unknown): boolean {
  return isAxiosError(error) && error.response?.status === 409;
}

export function isNotFoundError(error: unknown): boolean {
  return isAxiosError(error) && error.response?.status === 404;
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
      return 'Biến thể không còn tồn tại trên hệ thống.';
    }
    if (error.response?.status === 409) {
      return 'Tồn kho đã bị thay đổi bởi thao tác khác. Hãy tải lại dữ liệu rồi thử lại.';
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

export async function getInventoryVariants(
  params?: InventoryVariantParams
): Promise<PageResponseInventoryVariant> {
  const response = await apiClient.get<PageResponseInventoryVariant>('/admin/inventory/variants', {
    params,
  });
  return response.data;
}

export async function getInventorySummary(threshold?: number): Promise<InventorySummary> {
  const response = await apiClient.get<InventorySummary>('/admin/inventory/summary', {
    params: threshold !== undefined ? { threshold } : undefined,
  });
  return response.data;
}

export async function updateInventoryStock(
  id: number,
  payload: InventoryStockUpdatePayload
): Promise<InventoryVariantItem> {
  const response = await apiClient.patch<InventoryVariantItem>(
    `/admin/inventory/variants/${id}/stock`,
    payload
  );
  return response.data;
}
