export const INVENTORY_THEME = {
  primary: '#18181B',
  canvas: '#FBF9F5',
  surface: '#FFFFFF',
  mutedSurface: '#F5F0EA',
  border: '#E4DCD3',
  mutedText: '#6E675F',
  accent: '#C2410C',
  success: '#16A34A',
  warning: '#D97706',
  danger: '#DC2626',
} as const;

export const INVENTORY_STOCK_STATUS_CONFIG = {
  OUT_OF_STOCK: {
    label: 'Hết hàng',
    color: INVENTORY_THEME.danger,
    bgColor: INVENTORY_THEME.mutedSurface,
    borderColor: INVENTORY_THEME.danger,
  },
  LOW_STOCK: {
    label: 'Sắp hết',
    color: INVENTORY_THEME.warning,
    bgColor: INVENTORY_THEME.mutedSurface,
    borderColor: INVENTORY_THEME.warning,
  },
  IN_STOCK: {
    label: 'Còn hàng',
    color: INVENTORY_THEME.success,
    bgColor: INVENTORY_THEME.mutedSurface,
    borderColor: INVENTORY_THEME.success,
  },
} as const;

export const DEFAULT_THRESHOLD = 10;
export const MIN_THRESHOLD = 1;
export const MAX_THRESHOLD = 1000;
export const DEFAULT_PAGE_SIZE = 20;
