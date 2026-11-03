import apiClient from '@/api/client';
import type { components } from '@/api/schema';
import type { UserSummary } from '@/stores/auth.store';
import type { RegisterFormValues, LoginFormValues } from '../schemas/auth.schema';

export type AuthResponse = components['schemas']['AuthResponse'];
export type { UserSummary };

export type GenderType = 'MALE' | 'FEMALE' | 'OTHER';

export interface UpdateProfilePayload {
  name: string;
  phone?: string | null;
  gender?: GenderType | null;
  birthDate?: string | null;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export async function registerApi(data: RegisterFormValues): Promise<AuthResponse> {
  const normalizedEmail = data.email && data.email.trim() ? data.email.trim() : undefined;
  const normalizedPhone = data.phone && data.phone.trim() ? data.phone.trim() : undefined;

  const payload: components['schemas']['RegisterRequest'] = {
    name: data.fullName.trim(),
    email: normalizedEmail,
    phone: normalizedPhone,
    password: data.password,
  };

  const res = await apiClient.post<AuthResponse>('/auth/register', payload);
  return res.data;
}

export async function loginApi(data: LoginFormValues): Promise<AuthResponse> {
  const payload: components['schemas']['LoginRequest'] = {
    email: data.email.trim(),
    password: data.password,
  };

  const res = await apiClient.post<AuthResponse>('/auth/login', payload);
  return res.data;
}

export async function getMeApi(): Promise<UserSummary> {
  const res = await apiClient.get<UserSummary>('/auth/me');
  return res.data;
}

export async function updateProfileApi(data: UpdateProfilePayload): Promise<UserSummary> {
  const res = await apiClient.put<UserSummary>('/auth/profile', data);
  return res.data;
}

export async function changePasswordApi(data: ChangePasswordPayload): Promise<{ message: string }> {
  const res = await apiClient.post<{ message: string }>('/auth/change-password', data);
  return res.data;
}
