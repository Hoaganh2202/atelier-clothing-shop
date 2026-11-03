export function formatCurrencyVND(amount?: number | null): string {
  if (amount === undefined || amount === null) {
    return '0 ₫';
  }
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
  }).format(amount);
}

const TIMEZONE_VN = 'Asia/Ho_Chi_Minh';

function parseDateWithTimezone(value: string): Date | null {
  const str = value.trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(str)) {
    const date = new Date(`${str}T00:00:00+07:00`);
    return Number.isNaN(date.getTime()) ? null : date;
  }
  const hasTime = /[T\s]\d{2}:\d{2}/.test(str);
  if (hasTime) {
    const hasOffset = /[T\s].*(?:Z|[+-]\d{2}(?::?\d{2})?)$/i.test(str);
    const normalized = hasOffset ? str : `${str}+07:00`;
    const date = new Date(normalized);
    return Number.isNaN(date.getTime()) ? null : date;
  }
  const date = new Date(str);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDateTimeVN(value?: string | null, emptyFallback = '—'): string {
  if (!value || value.trim().length === 0) {
    return emptyFallback;
  }
  const date = parseDateWithTimezone(value);
  if (!date) {
    return value;
  }
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: TIMEZONE_VN,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function formatDateOnlyVN(value?: string | null, emptyFallback = '—'): string {
  if (!value || value.trim().length === 0) {
    return emptyFallback;
  }
  const date = parseDateWithTimezone(value);
  if (!date) {
    return value;
  }
  return new Intl.DateTimeFormat('vi-VN', {
    timeZone: TIMEZONE_VN,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(date);
}
