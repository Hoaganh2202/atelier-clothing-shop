import { isAxiosError } from 'axios';
import apiClient from '@/api/client';
import type {
  ApiErrorResponse,
  CheckoutRequest,
  CheckoutResponse,
  CustomerOrderDetailResponse,
  GetMyOrdersParams,
  PageResponseCustomerOrderListItemResponse,
} from '../order.types';

export async function checkoutOrder(payload: CheckoutRequest): Promise<CheckoutResponse> {
  const response = await apiClient.post<CheckoutResponse>('/orders/checkout', payload);
  return response.data;
}

export async function getMyOrders(
  params?: GetMyOrdersParams
): Promise<PageResponseCustomerOrderListItemResponse> {
  const response = await apiClient.get<PageResponseCustomerOrderListItemResponse>('/orders', {
    params,
  });
  return response.data;
}

export async function getMyOrder(orderId: number): Promise<CustomerOrderDetailResponse> {
  const response = await apiClient.get<CustomerOrderDetailResponse>(`/orders/${orderId}`);
  return response.data;
}

export async function cancelMyOrder(orderId: number): Promise<CustomerOrderDetailResponse> {
  const response = await apiClient.post<CustomerOrderDetailResponse>(`/orders/${orderId}/cancel`);
  return response.data;
}

export function getCheckoutErrorMessage(
  error: unknown,
  fallbackMessage = 'Đặt hàng không thành công. Vui lòng thử lại.'
): string {
  if (isAxiosError<ApiErrorResponse>(error)) {
    if (error.response?.status === 401) {
      return 'Vui lòng đăng nhập để tiến hành đặt hàng.';
    }
    if (error.response?.status === 403) {
      return 'Tài khoản của bạn không có quyền đặt hàng.';
    }
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

export function getOrderErrorMessage(
  error: unknown,
  fallbackMessage = 'Đã xảy ra lỗi khi xử lý đơn hàng. Vui lòng thử lại.'
): string {
  if (isAxiosError<ApiErrorResponse>(error)) {
    if (error.response?.status === 401) {
      return 'Vui lòng đăng nhập để xem thông tin đơn hàng.';
    }
    if (error.response?.status === 403) {
      return 'Bạn không có quyền truy cập đơn hàng này.';
    }
    if (error.response?.status === 404) {
      return 'Không tìm thấy thông tin đơn hàng.';
    }
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
