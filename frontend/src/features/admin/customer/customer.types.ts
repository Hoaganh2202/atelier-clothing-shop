export type CustomerStatus = 'ACTIVE' | 'BLOCKED';

export type CustomerGender = 'MALE' | 'FEMALE' | 'OTHER';

export type CustomerDataSource = 'HM_REAL' | 'SYNTHETIC' | 'WEBSITE';

export interface CustomerListItem {
  id: number;
  name: string;
  email: string | null;
  phone: string | null;
  status: CustomerStatus;
  createdAt: string;
}

export interface CustomerDetail {
  id: number;
  name: string;
  email: string | null;
  phone: string | null;
  gender: CustomerGender | null;
  birthDate: string | null;
  status: CustomerStatus;
  lastLoginAt: string | null;
  dataSource: CustomerDataSource;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CustomerListParams {
  page?: number;
  size?: number;
  keyword?: string;
  status?: CustomerStatus;
  sort?: string;
}

export interface UpdateCustomerStatusPayload {
  status: CustomerStatus;
}

export interface ApiErrorResponse {
  status?: number;
  message?: string;
  errors?: Record<string, string>;
}

export const GENDER_LABELS: Record<CustomerGender, string> = {
  MALE: 'Nam',
  FEMALE: 'Nữ',
  OTHER: 'Khác',
};

export const DATA_SOURCE_LABELS: Record<CustomerDataSource, string> = {
  WEBSITE: 'Đăng ký trực tuyến',
  HM_REAL: 'Dữ liệu H&M',
  SYNTHETIC: 'Dữ liệu mô phỏng',
};
