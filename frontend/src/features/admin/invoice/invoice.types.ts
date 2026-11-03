export type DocumentStatus =
  | 'DRAFT'
  | 'PENDING_ISSUE'
  | 'ISSUING'
  | 'ISSUANCE_UNKNOWN'
  | 'RECONCILING'
  | 'ISSUED'
  | 'ISSUE_FAILED';

export type InvoiceType = 'ORIGINAL' | 'ADJUSTMENT' | 'REPLACEMENT';

export type AdjustmentType = 'INCREASE' | 'DECREASE' | 'INFORMATION';

export type PricingBasis = 'INCLUSIVE' | 'EXCLUSIVE';

export type InvoiceItemType =
  | 'MERCHANDISE'
  | 'SHIPPING_SURCHARGE'
  | 'DISCOUNT_VOUCHER'
  | 'TAX_ADJUSTMENT';

export interface InvoiceBuyerResponse {
  customerType?: string;
  buyerName?: string;
  companyName?: string;
  taxCode?: string;
  email?: string;
  address?: string;
  phoneNumber?: string;
}

export interface InvoiceSellerResponse {
  sellerTaxCode?: string;
  sellerLegalName?: string;
  sellerBrandName?: string;
  sellerAddress?: string;
  sellerPhone?: string;
  sellerEmail?: string;
  sellerBankAccount?: string;
  sellerBankName?: string;
}

export interface InvoiceItemResponse {
  id: number;
  itemType: InvoiceItemType;
  itemName: string;
  unit?: string;
  quantity: number;
  originalQuantity?: number;
  unitPriceGross: number;
  unitPriceNet: number;
  discountAmount: number;
  taxableAmount: number;
  taxCategory?: string;
  taxTreatment?: string;
  taxRate?: number;
  taxCode?: string;
  taxAmount: number;
  lineTotalGross: number;
}

export interface InvoiceSummaryResponse {
  id: number;
  orderId?: number;
  orderNumber?: string;
  invoiceType: InvoiceType;
  adjustmentType?: AdjustmentType;
  documentStatus: DocumentStatus;
  invoiceSeries?: string;
  invoiceNumber?: string;
  taxAuthorityCode?: string;
  taxPointDate: string;
  officiallyIssuedAt?: string;
  totalTaxableAmount: number;
  totalTaxAmount: number;
  totalAfterTax: number;
  buyerName?: string;
  isDemo: boolean;
  demoMode?: boolean;
  createdAt: string;
}

export interface InvoiceDetailResponse {
  id: number;
  orderId?: number;
  orderNumber?: string;
  invoiceType: InvoiceType;
  adjustmentType?: AdjustmentType;
  documentStatus: DocumentStatus;
  invoiceSeries?: string;
  invoiceNumber?: string;
  taxAuthorityCode?: string;
  taxPointDate: string;
  officiallyIssuedAt?: string;
  pricingBasis: PricingBasis;
  totalMerchandiseBeforeTax: number;
  totalMerchandiseDiscount: number;
  totalShippingBeforeTax: number;
  totalShippingDiscount: number;
  totalTaxableAmount: number;
  totalTaxAmount: number;
  totalAfterTax: number;
  storageProvider?: string;
  isDemo: boolean;
  demoMode?: boolean;
  demoNotice?: string;
  isReplaced?: boolean;
  originalInvoiceId?: number;
  replacedByInvoiceId?: number;
  replacingInvoiceId?: number;
  replacementStatus?: DocumentStatus;
  replacementNotice?: string;
  buyer?: InvoiceBuyerResponse;
  seller?: InvoiceSellerResponse;
  items: InvoiceItemResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface InvoiceRetryResponse {
  invoice: InvoiceDetailResponse;
  invoiceCreated: boolean;
  message: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last?: boolean;
}

export interface InvoiceFilterParams {
  keyword?: string;
  orderId?: number;
  documentStatus?: DocumentStatus;
  invoiceType?: InvoiceType;
  isDemo?: boolean;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const DOCUMENT_STATUS_CONFIG: Record<
  DocumentStatus,
  { label: string; color: string }
> = {
  ISSUED: { label: 'Đã phát hành', color: 'success' },
  DRAFT: { label: 'Bản nháp', color: 'warning' },
  ISSUE_FAILED: { label: 'Lỗi phát hành', color: 'error' },
  PENDING_ISSUE: { label: 'Chờ phát hành', color: 'processing' },
  ISSUING: { label: 'Đang phát hành', color: 'processing' },
  ISSUANCE_UNKNOWN: { label: 'Chưa rõ kết quả', color: 'default' },
  RECONCILING: { label: 'Đang đối soát', color: 'default' },
};

export const INVOICE_TYPE_CONFIG: Record<
  InvoiceType,
  { label: string; color: string }
> = {
  ORIGINAL: { label: 'Hóa đơn gốc', color: 'blue' },
  REPLACEMENT: { label: 'Hóa đơn thay thế', color: 'purple' },
  ADJUSTMENT: { label: 'Hóa đơn điều chỉnh', color: 'orange' },
};
