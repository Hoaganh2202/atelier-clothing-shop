import { isAxiosError } from 'axios';
import apiClient from '@/api/client';
import type {
  AddressItem,
  AddressRequestPayload,
  ApiErrorBody,
} from './address.types';

const ADDRESS_BASE_URL = '/addresses';

export const addressApi = {
  getAddresses: async (): Promise<AddressItem[]> => {
    const response = await apiClient.get<AddressItem[]>(ADDRESS_BASE_URL);
    return response.data;
  },

  createAddress: async (payload: AddressRequestPayload): Promise<AddressItem> => {
    const response = await apiClient.post<AddressItem>(ADDRESS_BASE_URL, payload);
    return response.data;
  },

  updateAddress: async (
    id: number,
    payload: AddressRequestPayload
  ): Promise<AddressItem> => {
    const response = await apiClient.put<AddressItem>(
      `${ADDRESS_BASE_URL}/${id}`,
      payload
    );
    return response.data;
  },

  setDefaultAddress: async (id: number): Promise<AddressItem> => {
    const response = await apiClient.patch<AddressItem>(
      `${ADDRESS_BASE_URL}/${id}/default`
    );
    return response.data;
  },

  deleteAddress: async (id: number): Promise<void> => {
    await apiClient.delete(`${ADDRESS_BASE_URL}/${id}`);
  },
};

export function extractAddressApiError(
  error: unknown,
  fallbackMessage = 'Không thể thực hiện thao tác. Vui lòng thử lại.'
): { message: string; fieldErrors?: Record<string, string>; status?: number } {
  if (isAxiosError<ApiErrorBody>(error)) {
    const status = error.response?.status;
    const data = error.response?.data;
    const message =
      data?.message && data.message.trim().length > 0
        ? data.message
        : status === 404
        ? 'Không tìm thấy địa chỉ giao hàng'
        : fallbackMessage;

    return {
      message,
      fieldErrors: data?.errors,
      status,
    };
  }

  if (error instanceof Error && error.message) {
    return { message: error.message };
  }

  return { message: fallbackMessage };
}

export function formatFullAddress(item: Pick<AddressItem, 'address' | 'ward' | 'district' | 'province'>): string {
  return [item.address, item.ward, item.district, item.province]
    .map((part) => (part ? part.trim() : ''))
    .filter((part) => part.length > 0)
    .join(', ');
}
