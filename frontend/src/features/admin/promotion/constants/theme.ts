export const PROMOTION_THEME = {
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

export const PROMOTION_STATUS_STYLES = {
  RUNNING: {
    color: PROMOTION_THEME.success,
    backgroundColor: PROMOTION_THEME.mutedSurface,
    borderColor: PROMOTION_THEME.success,
  },
  SCHEDULED: {
    color: PROMOTION_THEME.warning,
    backgroundColor: PROMOTION_THEME.mutedSurface,
    borderColor: PROMOTION_THEME.warning,
  },
  EXPIRED: {
    color: PROMOTION_THEME.mutedText,
    backgroundColor: PROMOTION_THEME.mutedSurface,
    borderColor: PROMOTION_THEME.border,
  },
} as const;
