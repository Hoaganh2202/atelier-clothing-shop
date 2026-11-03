export const MAX_ADDRESSES_PER_USER = 10;

export interface AddressItem {
  id: number;
  receiverName: string;
  phone: string;
  province: string;
  district: string | null;
  ward: string | null;
  address: string;
  isDefault: boolean;
}

export interface AddressRequestPayload {
  receiverName: string;
  phone: string;
  province: string;
  district: string | null;
  ward: string | null;
  address: string;
  isDefault: boolean;
}

export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  message?: string;
  errors?: Record<string, string>;
}
