export type OrderStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'SHIPPING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REFUNDED';

export type PaymentMethod = 'COD' | 'VNPAY' | 'MOMO' | 'BANK';

export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED';

export type DataSourceType = 'WEBSITE' | 'HM_REAL' | 'SYNTHETIC';

export interface OrderItemAdminResponse {
  id: number;
  variantId: number;
  productName: string;
  variantSize?: string | null;
  variantColor?: string | null;
  quantity: number;
  unitPrice: number;
  discountAmount?: number | null;
  totalPrice: number;
}

export interface OrderPaymentAdminResponse {
  id: number;
  method: PaymentMethod;
  amount: number;
  status: PaymentStatus;
  transactionCode?: string | null;
  paidAt?: string | null;
}

export interface OrderAdminListItemResponse {
  id: number;
  orderCode: string;
  customerName: string;
  customerPhone: string;
  createdAt: string;
  itemsSummary: string;
  itemCount: number;
  totalAmount: number;
  status: OrderStatus;
}

export interface OrderAdminDetailResponse {
  id: number;
  orderCode: string;
  status: OrderStatus;
  createdAt: string;
  completedAt?: string | null;
  subtotal: number;
  discountAmount?: number | null;
  shippingFee: number;
  totalAmount: number;
  paymentMethod: PaymentMethod;
  receiverName: string;
  receiverPhone: string;
  addressLine: string;
  ward: string;
  district: string;
  province: string;
  note?: string | null;
  salesChannel?: string | null;
  dataSource: DataSourceType;
  voucherId?: number | null;
  items: OrderItemAdminResponse[];
  payments: OrderPaymentAdminResponse[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface OrderListParams {
  page?: number;
  size?: number;
  status?: OrderStatus;
  keyword?: string;
  startDate?: string;
  endDate?: string;
  sort?: string;
}

export interface OrderStatusUpdateRequest {
  status: OrderStatus;
}

export interface ApiErrorResponse {
  status?: number;
  message?: string;
  errors?: Record<string, string>;
}

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  SHIPPING: 'Đang giao hàng',
  COMPLETED: 'Hoàn thành',
  CANCELLED: 'Đã hủy',
  REFUNDED: 'Đã hoàn tiền',
};

export const ORDER_STATUS_TAG_CONFIG: Record<
  OrderStatus,
  { color: string; label: string }
> = {
  PENDING: { color: 'warning', label: 'Chờ xác nhận' },
  CONFIRMED: { color: 'processing', label: 'Đã xác nhận' },
  SHIPPING: { color: 'cyan', label: 'Đang giao hàng' },
  COMPLETED: { color: 'success', label: 'Hoàn thành' },
  CANCELLED: { color: 'error', label: 'Đã hủy' },
  REFUNDED: { color: 'default', label: 'Đã hoàn tiền' },
};

export const NEXT_ALLOWED_ORDER_STATUSES: Record<OrderStatus, OrderStatus[]> = {
  PENDING: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['SHIPPING', 'CANCELLED'],
  SHIPPING: ['COMPLETED', 'CANCELLED'],
  COMPLETED: ['REFUNDED'],
  CANCELLED: [],
  REFUNDED: [],
};

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  COD: 'Thanh toán khi nhận hàng (COD)',
  VNPAY: 'VNPAY',
  MOMO: 'Ví MoMo',
  BANK: 'Chuyển khoản ngân hàng',
};

export const PAYMENT_STATUS_LABELS: Record<
  PaymentStatus,
  { label: string; color: string }
> = {
  PENDING: { label: 'Chờ thanh toán', color: 'warning' },
  SUCCESS: { label: 'Đã thanh toán', color: 'success' },
  FAILED: { label: 'Thất bại', color: 'error' },
  REFUNDED: { label: 'Đã hoàn tiền', color: 'default' },
};

export const DATA_SOURCE_LABELS: Record<DataSourceType, string> = {
  WEBSITE: 'Website trực tuyến',
  HM_REAL: 'Dữ liệu H&M',
  SYNTHETIC: 'Dữ liệu mô phỏng',
};
