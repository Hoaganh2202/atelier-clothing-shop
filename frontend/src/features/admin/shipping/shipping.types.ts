export type ShippingStatus = 'CONFIRMED' | 'SHIPPING' | 'COMPLETED' | 'CANCELLED';

export type PaymentMethod = 'COD' | 'VNPAY' | 'MOMO' | 'BANK';

export interface ShippingSummaryResponse {
  readyToShipCount: number;
  deliveringCount: number;
  deliveredTodayCount: number;
}

export interface ShippingListItemResponse {
  orderId: number;
  orderCode: string;
  status: ShippingStatus;
  receiverName: string;
  receiverPhone: string;
  fullAddress: string;
  province: string;
  paymentMethod: PaymentMethod;
  codAmount: number;
  shippingFee: number;
  totalAmount: number;
  itemCount: number;
  itemsSummary: string;
  customerNote?: string | null;
  createdAt: string;
  completedAt?: string | null;
}

export interface ShippingItemResponse {
  id: number;
  variantId?: number | null;
  productName: string;
  variantSize?: string | null;
  variantColor?: string | null;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
}

export interface ShippingDetailResponse {
  orderId: number;
  orderCode: string;
  status: ShippingStatus;
  receiverName: string;
  receiverPhone: string;
  addressLine?: string | null;
  ward?: string | null;
  district?: string | null;
  province: string;
  fullAddress: string;
  paymentMethod: PaymentMethod;
  codAmount: number;
  shippingFee: number;
  subtotal: number;
  discountAmount?: number | null;
  totalAmount: number;
  customerNote?: string | null;
  createdAt: string;
  completedAt?: string | null;
  items: ShippingItemResponse[];
}

export interface ShippingStatusUpdateRequest {
  status: ShippingStatus;
}

export interface ShippingListParams {
  page?: number;
  size?: number;
  status?: ShippingStatus;
  keyword?: string;
  province?: string;
  startDate?: string;
  endDate?: string;
  sort?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiErrorResponse {
  status?: number;
  message?: string;
  errors?: Record<string, string>;
}

export const SHIPPING_STATUS_LABELS: Record<ShippingStatus, string> = {
  CONFIRMED: 'Chờ giao hàng',
  SHIPPING: 'Đang giao hàng',
  COMPLETED: 'Đã giao thành công',
  CANCELLED: 'Đã hủy giao',
};

export const SHIPPING_STATUS_TAG_CONFIG: Record<
  ShippingStatus,
  { color: string; label: string }
> = {
  CONFIRMED: { color: 'processing', label: 'Chờ giao hàng' },
  SHIPPING: { color: 'cyan', label: 'Đang giao hàng' },
  COMPLETED: { color: 'success', label: 'Đã giao thành công' },
  CANCELLED: { color: 'error', label: 'Đã hủy giao' },
};

export interface StatusTransitionAction {
  targetStatus: ShippingStatus;
  label: string;
  actionText: string;
  confirmTitle: string;
  danger?: boolean;
}

export const NEXT_ALLOWED_SHIPPING_ACTIONS: Record<
  ShippingStatus,
  StatusTransitionAction[]
> = {
  CONFIRMED: [
    {
      targetStatus: 'SHIPPING',
      label: 'Đang giao hàng',
      actionText: 'Xuất kho & Giao hàng',
      confirmTitle: 'Xác nhận xuất kho và bắt đầu giao đơn hàng này?',
    },
    {
      targetStatus: 'CANCELLED',
      label: 'Hủy giao',
      actionText: 'Hủy giao hàng',
      confirmTitle: 'Bạn có chắc chắn muốn hủy giao đơn hàng này?',
      danger: true,
    },
  ],
  SHIPPING: [
    {
      targetStatus: 'COMPLETED',
      label: 'Đã giao thành công',
      actionText: 'Xác nhận giao thành công',
      confirmTitle: 'Xác nhận khách đã nhận hàng và hoàn tất đơn vận chuyển?',
    },
    {
      targetStatus: 'CANCELLED',
      label: 'Giao thất bại / Hủy',
      actionText: 'Báo giao thất bại',
      confirmTitle: 'Xác nhận giao hàng không thành công (khách từ chối / hủy khi giao)?',
      danger: true,
    },
  ],
  COMPLETED: [],
  CANCELLED: [],
};

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  COD: 'Thanh toán khi nhận hàng (COD)',
  VNPAY: 'VNPAY',
  MOMO: 'Ví MoMo',
  BANK: 'Chuyển khoản ngân hàng',
};
