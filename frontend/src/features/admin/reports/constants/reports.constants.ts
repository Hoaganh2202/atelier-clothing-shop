import type { OrderStatus } from '../reports.types';

export const TIMEZONE_VN = 'Asia/Ho_Chi_Minh';

export const REPORT_THEME = {
  primary: '#18181B',
  primaryMuted: '#27272A',
  canvas: '#FBF9F5',
  surface: '#FFFFFF',
  border: '#E4E4E7',
  mutedText: '#71717A',
  accent: '#C2410C',
  success: '#16A34A',
  warning: '#D97706',
  danger: '#DC2626',
  info: '#0284C7',
  chartColors: [
    '#18181B',
    '#C2410C',
    '#0284C7',
    '#16A34A',
    '#D97706',
    '#7C3AED',
    '#DB2777',
    '#475569',
  ],
} as const;

export const ORDER_STATUS_CONFIG: Record<
  OrderStatus,
  { label: string; color: string; badgeColor: string }
> = {
  PENDING: {
    label: 'Chờ thanh toán',
    color: 'warning',
    badgeColor: '#D97706',
  },
  CONFIRMED: {
    label: 'Đã xác nhận',
    color: 'processing',
    badgeColor: '#0284C7',
  },
  SHIPPING: {
    label: 'Đang vận chuyển',
    color: 'purple',
    badgeColor: '#7C3AED',
  },
  COMPLETED: {
    label: 'Đã hoàn thành',
    color: 'success',
    badgeColor: '#16A34A',
  },
  CANCELLED: {
    label: 'Đã hủy',
    color: 'error',
    badgeColor: '#DC2626',
  },
  REFUNDED: {
    label: 'Đã hoàn tiền',
    color: 'default',
    badgeColor: '#71717A',
  },
};
