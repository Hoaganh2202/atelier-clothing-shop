import { isAxiosError } from 'axios';
import apiClient from '@/api/client';
import type { components } from '@/api/schema';

export type ProductAdminListItem = components['schemas']['ProductAdminListItemResponse'];
export type ProductDetail = components['schemas']['ProductDetailResponse'];
export type ProductUpsertPayload = components['schemas']['ProductUpsertRequest'];
export type VariantUpsertPayload = components['schemas']['VariantUpsertRequest'];
export type ProductImageItem = components['schemas']['ProductImageResponse'];
export type ImageMetadataUpdatePayload = components['schemas']['ImageMetadataUpdateRequest'];
export type BulkStatusPayload = components['schemas']['BulkStatusRequest'];
export type CategoryItem = components['schemas']['CategoryResponse'];
export type PageResponseProductAdmin = components['schemas']['PageResponseProductAdminListItemResponse'];
export type ApiErrorResponse = components['schemas']['ErrorResponse'];

export interface ProductListParams {
  keyword?: string;
  categoryId?: number;
  isActive?: boolean;
  page?: number;
  size?: number;
  sort?: string[];
}

export function getApiErrorMessage(error: unknown, fallbackMessage: string): string {
  if (isAxiosError<ApiErrorResponse>(error)) {
    const serverMessage = error.response?.data?.message;
    if (serverMessage && serverMessage.trim().length > 0) {
      return serverMessage;
    }
    if (error.response?.status === 413) {
      return 'Dung lượng file tải lên vượt quá giới hạn cho phép (tối đa 10 MB)';
    }
    if (error.response?.status === 415) {
      return 'Định dạng dữ liệu không được hỗ trợ';
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

export function generateSlug(input: string): string {
  if (!input || !input.trim()) {
    return '';
  }
  return input
    .trim()
    .toLowerCase()
    .replace(/đ/g, 'd')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
}

export async function getAdminProducts(params?: ProductListParams): Promise<PageResponseProductAdmin> {
  const response = await apiClient.get<PageResponseProductAdmin>('/admin/products', {
    params,
  });
  return response.data;
}

export async function getAdminProductById(id: number): Promise<ProductDetail> {
  const response = await apiClient.get<ProductDetail>(`/admin/products/${id}`);
  return response.data;
}

export async function createAdminProduct(payload: ProductUpsertPayload): Promise<ProductDetail> {
  const response = await apiClient.post<ProductDetail>('/admin/products', payload);
  return response.data;
}

export async function updateAdminProduct(id: number, payload: ProductUpsertPayload): Promise<ProductDetail> {
  const response = await apiClient.put<ProductDetail>(`/admin/products/${id}`, payload);
  return response.data;
}

export async function deleteAdminProduct(id: number): Promise<void> {
  await apiClient.delete(`/admin/products/${id}`);
}

export async function bulkUpdateProductStatus(payload: BulkStatusPayload): Promise<void> {
  await apiClient.patch('/admin/products/bulk-status', payload);
}

export async function uploadProductImage(
  id: number,
  file: File,
  meta?: { isPrimary?: boolean; color?: string; alt?: string }
): Promise<ProductImageItem> {
  const formData = new FormData();
  formData.append('file', file);
  const response = await apiClient.post<ProductImageItem>(`/admin/products/${id}/images`, formData, {
    params: meta,
  });
  return response.data;
}

export async function patchProductImage(
  id: number,
  imageId: number,
  payload: ImageMetadataUpdatePayload
): Promise<ProductImageItem> {
  const response = await apiClient.patch<ProductImageItem>(
    `/admin/products/${id}/images/${imageId}`,
    payload
  );
  return response.data;
}

export async function deleteProductImage(id: number, imageId: number): Promise<void> {
  await apiClient.delete(`/admin/products/${id}/images/${imageId}`);
}

export async function getAdminCategories(): Promise<CategoryItem[]> {
  const response = await apiClient.get<CategoryItem[]>('/admin/categories');
  return response.data;
}
