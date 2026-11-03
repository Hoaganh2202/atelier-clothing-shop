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

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last?: boolean;
}

export const DOCUMENT_STATUS_CONFIG: Record<
  DocumentStatus,
  { label: string; variant: 'default' | 'secondary' | 'outline' | 'destructive' }
> = {
  ISSUED: { label: 'Đã phát hành', variant: 'default' },
  DRAFT: { label: 'Bản nháp', variant: 'outline' },
  ISSUE_FAILED: { label: 'Lỗi phát hành', variant: 'destructive' },
  PENDING_ISSUE: { label: 'Chờ phát hành', variant: 'secondary' },
  ISSUING: { label: 'Đang phát hành', variant: 'secondary' },
  ISSUANCE_UNKNOWN: { label: 'Chưa rõ', variant: 'outline' },
  RECONCILING: { label: 'Đang đối soát', variant: 'outline' },
};

export const INVOICE_TYPE_CONFIG: Record<
  InvoiceType,
  { label: string; variant: 'default' | 'secondary' | 'outline' }
> = {
  ORIGINAL: { label: 'Hóa đơn gốc', variant: 'default' },
  REPLACEMENT: { label: 'Hóa đơn thay thế', variant: 'secondary' },
  ADJUSTMENT: { label: 'Hóa đơn điều chỉnh', variant: 'outline' },
};
