import apiClient from '@/api/client';

export interface StoreSettingsData {
  storeName: string;
  legalName?: string | null;
  taxCode?: string | null;
  address?: string | null;
  phone?: string | null;
  email?: string | null;
  logoUrl?: string | null;
  lowStockThreshold: number;
  defaultShippingFee: number;
  freeShippingThreshold: number;
  updatedAt?: string | null;
  updatedBy?: string | null;
}

export interface StoreSettingsPayload {
  storeName: string;
  legalName?: string | null;
  taxCode?: string | null;
  address?: string | null;
  phone?: string | null;
  email?: string | null;
  logoUrl?: string | null;
  lowStockThreshold: number;
  defaultShippingFee: number;
  freeShippingThreshold: number;
}

export interface AdminAccountData {
  id: number;
  name: string;
  email: string;
  phone?: string | null;
  role: string;
  lastLoginAt?: string | null;
  createdAt?: string | null;
}

export interface UpdateAdminAccountPayload {
  name: string;
  phone?: string | null;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export interface LogoUploadResult {
  logoUrl: string;
}

export async function getStoreSettings(): Promise<StoreSettingsData> {
  const response = await apiClient.get<StoreSettingsData>('/admin/settings/store');
  return response.data;
}

export async function updateStoreSettings(payload: StoreSettingsPayload): Promise<StoreSettingsData> {
  const response = await apiClient.put<StoreSettingsData>('/admin/settings/store', payload);
  return response.data;
}

export async function getAdminAccount(): Promise<AdminAccountData> {
  const response = await apiClient.get<AdminAccountData>('/admin/settings/account');
  return response.data;
}

export async function updateAdminAccount(payload: UpdateAdminAccountPayload): Promise<AdminAccountData> {
  const response = await apiClient.put<AdminAccountData>('/admin/settings/account', payload);
  return response.data;
}

export async function changePassword(payload: ChangePasswordPayload): Promise<{ message: string }> {
  const response = await apiClient.put<{ message: string }>('/admin/settings/account/password', payload);
  return response.data;
}

export async function uploadLogo(file: File): Promise<LogoUploadResult> {
  const formData = new FormData();
  formData.append('file', file);
  const response = await apiClient.post<LogoUploadResult>('/admin/settings/logo', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return response.data;
}

export function getApiErrorMessage(error: unknown, fallbackMessage = 'Đã xảy ra lỗi, vui lòng thử lại'): string {
  if (!error) return fallbackMessage;
  const err = error as {
    response?: {
      data?: {
        message?: string;
        errors?: Record<string, string>;
      };
    };
    message?: string;
  };

  const responseData = err.response?.data;
  if (responseData) {
    if (responseData.message) return responseData.message;
    if (responseData.errors) {
      const firstError = Object.values(responseData.errors)[0];
      if (firstError) return firstError;
    }
  }

  return err.message || fallbackMessage;
}
