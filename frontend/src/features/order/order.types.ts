import type { components } from '@/api/schema';

export type CustomerOrderListItemResponse = components['schemas']['CustomerOrderListItemResponse'];
export type CustomerOrderPreviewItemResponse = components['schemas']['CustomerOrderPreviewItemResponse'];
export type CustomerOrderDetailResponse = components['schemas']['CustomerOrderDetailResponse'];
export type CustomerOrderItemResponse = components['schemas']['CustomerOrderItemResponse'];
export type PageResponseCustomerOrderListItemResponse = components['schemas']['PageResponseCustomerOrderListItemResponse'];

export type OrderStatus = NonNullable<CustomerOrderListItemResponse['status']>;
export type PaymentMethod = NonNullable<CustomerOrderListItemResponse['paymentMethod']>;
export type PaymentStatus = NonNullable<CustomerOrderListItemResponse['paymentStatus']>;

export interface GetMyOrdersParams {
  status?: OrderStatus;
  page?: number;
  size?: number;
}

export const ORDER_STATUS_CONFIG: Record<
  OrderStatus,
  { label: string; variant: 'default' | 'secondary' | 'outline' | 'destructive' }
> = {
  PENDING: { label: 'Chờ xác nhận', variant: 'outline' },
  CONFIRMED: { label: 'Đã xác nhận', variant: 'secondary' },
  SHIPPING: { label: 'Đang giao hàng', variant: 'secondary' },
  COMPLETED: { label: 'Hoàn thành', variant: 'default' },
  CANCELLED: { label: 'Đã hủy', variant: 'destructive' },
  REFUNDED: { label: 'Đã hoàn tiền', variant: 'destructive' },
};

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  COD: 'Thanh toán khi nhận hàng',
  VNPAY: 'VNPay',
  MOMO: 'MoMo',
  BANK: 'Chuyển khoản ngân hàng',
};

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: 'Chưa thanh toán',
  SUCCESS: 'Đã thanh toán',
  FAILED: 'Thanh toán thất bại',
  REFUNDED: 'Đã hoàn tiền',
};

export interface CheckoutItemRequest {
  variantId: number;
  quantity: number;
}

export interface CheckoutRequest {
  addressId: number;
  paymentMethod: 'COD';
  note?: string;
  voucherCode?: string;
  idempotencyKey?: string;
  items: CheckoutItemRequest[];
}

export interface CheckoutItemResponse {
  orderDetailId: number;
  variantId?: number | null;
  productName: string;
  variantSize: string;
  variantColor: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  totalPrice: number;
}

export interface CheckoutResponse {
  orderId: number;
  status: string;
  subtotal: number;
  discountAmount: number;
  shippingFee: number;
  totalAmount: number;
  paymentMethod: 'COD';
  paymentStatus: string;
  receiverName: string;
  receiverPhone: string;
  addressLine: string;
  ward?: string | null;
  district?: string | null;
  province: string;
  note?: string | null;
  voucherCode?: string | null;
  idempotencyKey?: string | null;
  createdAt: string;
  items: CheckoutItemResponse[];
}

export interface ApiErrorResponse {
  timestamp?: string;
  status?: number;
  message?: string;
  errors?: Record<string, string>;
}
