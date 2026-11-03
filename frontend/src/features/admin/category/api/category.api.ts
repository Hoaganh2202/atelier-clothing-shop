import { isAxiosError } from 'axios';
import apiClient from '@/api/client';
import type { components } from '@/api/schema';

export type CategoryItem = components['schemas']['CategoryResponse'];
export type CategoryUpsertPayload = components['schemas']['CategoryUpsertRequest'];
export type CategoryStatusPayload = components['schemas']['CategoryStatusRequest'];
export type ApiErrorResponse = components['schemas']['ErrorResponse'];

export function getApiErrorMessage(error: unknown, fallbackMessage: string): string {
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

export function getApiFieldErrors(error: unknown): Record<string, string> | undefined {
  if (isAxiosError<ApiErrorResponse>(error)) {
    return error.response?.data?.errors;
  }
  return undefined;
}

export function generateCategorySlug(input: string): string {
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

export async function getAdminCategories(): Promise<CategoryItem[]> {
  const response = await apiClient.get<CategoryItem[]>('/admin/categories');
  return response.data;
}

export async function getAdminCategoryById(id: number): Promise<CategoryItem> {
  const response = await apiClient.get<CategoryItem>(`/admin/categories/${id}`);
  return response.data;
}

export async function createAdminCategory(payload: CategoryUpsertPayload): Promise<CategoryItem> {
  const response = await apiClient.post<CategoryItem>('/admin/categories', payload);
  return response.data;
}

export async function updateAdminCategory(
  id: number,
  payload: CategoryUpsertPayload
): Promise<CategoryItem> {
  const response = await apiClient.put<CategoryItem>(`/admin/categories/${id}`, payload);
  return response.data;
}

export async function deleteAdminCategory(id: number): Promise<void> {
  await apiClient.delete(`/admin/categories/${id}`);
}

export async function updateAdminCategoryStatus(
  id: number,
  payload: CategoryStatusPayload
): Promise<CategoryItem> {
  const response = await apiClient.patch<CategoryItem>(`/admin/categories/${id}/status`, payload);
  return response.data;
}
