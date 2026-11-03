import React from 'react';
import {
  formatCurrencyVND,
  formatDateOnlyVN,
  formatDateTimeVN,
} from '@/lib/format';
import {
  DOCUMENT_STATUS_CONFIG,
  type InvoiceDetailResponse,
} from '../invoice.types';

interface InvoicePrintViewProps {
  invoice: InvoiceDetailResponse;
  className?: string;
}

export const InvoicePrintView: React.FC<InvoicePrintViewProps> = ({ invoice, className = '' }) => {
  const isDemo = Boolean(invoice.isDemo || invoice.demoMode);
  const seller = invoice.seller || {};
  const buyer = invoice.buyer || {};

  const sellerDisplayName =
    seller.sellerLegalName?.trim() ||
    seller.sellerBrandName?.trim() ||
    'Cửa hàng thời trang Closet';

  const buyerDisplayName = buyer.buyerName?.trim() || 'Khách hàng cá nhân';

  const formattedTaxPointDate = formatDateOnlyVN(invoice.taxPointDate);

  const formattedOfficiallyIssuedAt = invoice.officiallyIssuedAt
    ? formatDateTimeVN(invoice.officiallyIssuedAt)
    : 'Chưa phát hành';

  return (
    <div
      className={`bg-white text-zinc-900 font-sans print:p-0 print:m-0 print:border-none print:shadow-none border border-zinc-200 rounded-[6px] p-6 sm:p-10 shadow-sm ${className}`}
      id="invoice-printable-area"
    >
      {/* Demo Warning Banner */}
      {isDemo && (
        <div className="mb-6 rounded-[6px] border border-amber-400 bg-amber-50 p-4 text-amber-900 text-xs sm:text-sm print:border-amber-400 print:bg-amber-50">
          <div className="flex items-center gap-2 font-bold tracking-wide uppercase">
            <span>⚠️</span>
            <span>HÓA ĐƠN MÔ PHỎNG (DEMO MODE) — KHÔNG CÓ GIÁ TRỊ PHÁP LÝ VỀ THUẾ</span>
          </div>
          <p className="mt-1 text-xs text-amber-800 leading-relaxed">
            {invoice.demoNotice ||
              'Chứng từ này được khởi tạo tự động trong môi trường thử nghiệm / đồ án tốt nghiệp. Hóa đơn không có giá trị pháp lý, không dùng để kê khai, quyết toán thuế hay khấu trừ chi phí doanh nghiệp.'}
          </p>
        </div>
      )}

      {/* Replacement Warning Banner */}
      {invoice.isReplaced && (
        <div className="mb-6 rounded-[6px] border border-red-300 bg-red-50 p-4 text-red-900 text-xs sm:text-sm print:border-red-400 print:bg-red-50">
          <div className="flex items-center gap-2 font-bold uppercase tracking-wide">
            <span>⚠️</span>
            <span>CHỨNG TỪ NÀY ĐÃ ĐƯỢC THAY THẾ</span>
          </div>
          <p className="mt-1 text-xs text-red-800 leading-relaxed">
            {invoice.replacementNotice ||
              `Hóa đơn này đã được thay thế bởi chứng từ mới (Mã HĐ #${invoice.replacedByInvoiceId || invoice.replacingInvoiceId || '...'}). Bản thể hiện này không còn hiệu lực hiện hành.`}
          </p>
        </div>
      )}

      {/* Header: Seller info & Invoice title / series */}
      <div className="flex flex-col sm:flex-row justify-between gap-6 pb-6 border-b border-zinc-200">
        {/* Seller Info */}
        <div className="sm:max-w-[55%] space-y-1.5 text-xs text-zinc-600">
          <div className="font-bold text-base text-zinc-900 tracking-tight uppercase">
            {sellerDisplayName}
          </div>
          {seller.sellerBrandName && seller.sellerBrandName !== seller.sellerLegalName && (
            <div className="text-zinc-700 font-medium">Thương hiệu: {seller.sellerBrandName}</div>
          )}
          {seller.sellerTaxCode && (
            <div>
              <span className="font-semibold text-zinc-800">Mã số thuế:</span>{' '}
              <span className="font-mono text-zinc-900">{seller.sellerTaxCode}</span>
            </div>
          )}
          {seller.sellerAddress && (
            <div>
              <span className="font-semibold text-zinc-800">Địa chỉ:</span> {seller.sellerAddress}
            </div>
          )}
          <div className="flex flex-wrap gap-x-4 gap-y-1">
            {seller.sellerPhone && (
              <div>
                <span className="font-semibold text-zinc-800">Điện thoại:</span> {seller.sellerPhone}
              </div>
            )}
            {seller.sellerEmail && (
              <div>
                <span className="font-semibold text-zinc-800">Email:</span> {seller.sellerEmail}
              </div>
            )}
          </div>
          {seller.sellerBankAccount && (
            <div>
              <span className="font-semibold text-zinc-800">Số tài khoản:</span>{' '}
              <span className="font-mono">{seller.sellerBankAccount}</span>
              {seller.sellerBankName && ` tại ${seller.sellerBankName}`}
            </div>
          )}
        </div>

        {/* Invoice Metadata */}
        <div className="sm:text-right space-y-1.5 text-xs text-zinc-600 sm:min-w-[220px]">
          <div className="text-lg sm:text-xl font-extrabold text-zinc-900 uppercase tracking-tight">
            {isDemo ? 'HÓA ĐƠN ĐIỆN TỬ (DEMO)' : 'HÓA ĐƠN ĐIỆN TỬ'}
          </div>
          <div className="text-[11px] text-zinc-500 italic">
            (Bản thể hiện thông tin hóa đơn điện tử)
          </div>
          <div className="pt-1">
            <span className="font-semibold text-zinc-800">Ký hiệu (Series):</span>{' '}
            <span className="font-mono font-bold text-zinc-900">
              {invoice.invoiceSeries || '—'}
            </span>
          </div>
          <div>
            <span className="font-semibold text-zinc-800">Số hóa đơn (No.):</span>{' '}
            <span className="font-mono font-bold text-base text-zinc-900">
              {invoice.invoiceNumber || 'Chưa cấp số'}
            </span>
          </div>
          <div>
            <span className="font-semibold text-zinc-800">Ngày lập:</span>{' '}
            <span>{formattedTaxPointDate}</span>
          </div>
          <div>
            <span className="font-semibold text-zinc-800">Thời điểm phát hành:</span>{' '}
            <span>{formattedOfficiallyIssuedAt}</span>
          </div>
          {invoice.taxAuthorityCode && (
            <div>
              <span className="font-semibold text-zinc-800">Mã CQT:</span>{' '}
              <span className="font-mono text-xs">{invoice.taxAuthorityCode}</span>
            </div>
          )}
          {invoice.orderNumber && (
            <div>
              <span className="font-semibold text-zinc-800">Đơn hàng liên kết:</span>{' '}
              <span className="font-mono">#{invoice.orderNumber}</span>
            </div>
          )}
          <div>
            <span className="font-semibold text-zinc-800">Trạng thái:</span>{' '}
            <span className="font-medium text-zinc-900">
              {DOCUMENT_STATUS_CONFIG[invoice.documentStatus]?.label || invoice.documentStatus}
            </span>
          </div>
        </div>
      </div>

      {/* Buyer Information Section */}
      <div className="py-5 border-b border-zinc-200 text-xs text-zinc-700 space-y-2">
        <div className="font-semibold text-sm text-zinc-900 uppercase tracking-wide">
          Thông tin người mua hàng
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-1.5">
          <div>
            <span className="font-medium text-zinc-500">Họ và tên người mua:</span>{' '}
            <span className="font-semibold text-zinc-900">{buyerDisplayName}</span>
          </div>
          {buyer.companyName && (
            <div>
              <span className="font-medium text-zinc-500">Tên đơn vị:</span>{' '}
              <span className="text-zinc-900">{buyer.companyName}</span>
            </div>
          )}
          {buyer.taxCode && (
            <div>
              <span className="font-medium text-zinc-500">Mã số thuế:</span>{' '}
              <span className="font-mono font-semibold text-zinc-900">{buyer.taxCode}</span>
            </div>
          )}
          {buyer.phoneNumber && (
            <div>
              <span className="font-medium text-zinc-500">Số điện thoại:</span>{' '}
              <span className="text-zinc-900">{buyer.phoneNumber}</span>
            </div>
          )}
          {buyer.email && (
            <div>
              <span className="font-medium text-zinc-500">Email:</span>{' '}
              <span className="text-zinc-900">{buyer.email}</span>
            </div>
          )}
          {buyer.address && (
            <div className="sm:col-span-2">
              <span className="font-medium text-zinc-500">Địa chỉ:</span>{' '}
              <span className="text-zinc-900">{buyer.address}</span>
            </div>
          )}
          <div>
            <span className="font-medium text-zinc-500">Hình thức thanh toán:</span>{' '}
            <span className="text-zinc-900 font-medium">TM/CK (Chuyển khoản / Tiền mặt)</span>
          </div>
        </div>
      </div>

      {/* Items Table */}
      <div className="py-5 overflow-x-auto">
        <table className="w-full text-left text-xs border-collapse">
          <thead>
            <tr className="border-b-2 border-zinc-900 text-zinc-900">
              <th className="py-2.5 px-2 text-center w-10 font-bold">STT</th>
              <th className="py-2.5 px-2 font-bold">Tên hàng hóa, dịch vụ</th>
              <th className="py-2.5 px-2 text-center w-16 font-bold">ĐVT</th>
              <th className="py-2.5 px-2 text-right w-16 font-bold">Số lượng</th>
              <th className="py-2.5 px-2 text-right w-24 font-bold">Đơn giá</th>
              <th className="py-2.5 px-2 text-right w-20 font-bold">Chiết khấu</th>
              <th className="py-2.5 px-2 text-right w-16 font-bold">Thuế suất</th>
              <th className="py-2.5 px-2 text-right w-24 font-bold">Tiền thuế</th>
              <th className="py-2.5 px-2 text-right w-28 font-bold">Thành tiền</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-200">
            {invoice.items && invoice.items.length > 0 ? (
              invoice.items.map((item, idx) => (
                <tr key={item.id || idx} className="hover:bg-zinc-50/50">
                  <td className="py-2.5 px-2 text-center text-zinc-500">{idx + 1}</td>
                  <td className="py-2.5 px-2 font-medium text-zinc-900">
                    {item.itemName}
                    {item.itemType === 'SHIPPING_SURCHARGE' && (
                      <span className="ml-1 text-[10px] text-zinc-500 italic">(Vận chuyển)</span>
                    )}
                    {item.itemType === 'DISCOUNT_VOUCHER' && (
                      <span className="ml-1 text-[10px] text-zinc-500 italic">(Giảm trừ)</span>
                    )}
                  </td>
                  <td className="py-2.5 px-2 text-center text-zinc-600">{item.unit || 'Cái'}</td>
                  <td className="py-2.5 px-2 text-right font-mono text-zinc-800">{item.quantity}</td>
                  <td className="py-2.5 px-2 text-right font-mono text-zinc-800">
                    {formatCurrencyVND(item.unitPriceGross || 0)}
                  </td>
                  <td className="py-2.5 px-2 text-right font-mono text-zinc-600">
                    {item.discountAmount > 0 ? formatCurrencyVND(item.discountAmount) : '—'}
                  </td>
                  <td className="py-2.5 px-2 text-right font-mono text-zinc-600">
                    {item.taxRate != null ? `${item.taxRate}%` : '—'}
                  </td>
                  <td className="py-2.5 px-2 text-right font-mono text-zinc-600">
                    {formatCurrencyVND(item.taxAmount || 0)}
                  </td>
                  <td className="py-2.5 px-2 text-right font-mono font-semibold text-zinc-900">
                    {formatCurrencyVND(item.lineTotalGross || 0)}
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={9} className="py-6 text-center text-zinc-400 italic">
                  Không có dòng chi tiết mặt hàng
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Financial Summary Calculation */}
      <div className="py-4 border-t-2 border-zinc-900 flex justify-end">
        <div className="w-full sm:w-80 space-y-1.5 text-xs">
          <div className="flex justify-between text-zinc-600">
            <span>Cộng tiền hàng (trước thuế):</span>
            <span className="font-mono text-zinc-900">
              {formatCurrencyVND(invoice.totalMerchandiseBeforeTax || 0)}
            </span>
          </div>

          {invoice.totalMerchandiseDiscount > 0 && (
            <div className="flex justify-between text-zinc-600">
              <span>Giảm giá hàng hóa:</span>
              <span className="font-mono text-zinc-900">
                -{formatCurrencyVND(invoice.totalMerchandiseDiscount)}
              </span>
            </div>
          )}

          {invoice.totalShippingBeforeTax > 0 && (
            <div className="flex justify-between text-zinc-600">
              <span>Cước phí vận chuyển:</span>
              <span className="font-mono text-zinc-900">
                {formatCurrencyVND(invoice.totalShippingBeforeTax)}
              </span>
            </div>
          )}

          {invoice.totalShippingDiscount > 0 && (
            <div className="flex justify-between text-zinc-600">
              <span>Giảm giá vận chuyển:</span>
              <span className="font-mono text-zinc-900">
                -{formatCurrencyVND(invoice.totalShippingDiscount)}
              </span>
            </div>
          )}

          <div className="flex justify-between text-zinc-600 pt-1 border-t border-zinc-200">
            <span>Tổng tiền chịu thuế:</span>
            <span className="font-mono text-zinc-900">
              {formatCurrencyVND(invoice.totalTaxableAmount || 0)}
            </span>
          </div>

          <div className="flex justify-between text-zinc-600">
            <span>Tổng tiền thuế GTGT (VAT):</span>
            <span className="font-mono text-zinc-900">
              {formatCurrencyVND(invoice.totalTaxAmount || 0)}
            </span>
          </div>

          <div className="flex justify-between items-baseline pt-2 border-t-2 border-zinc-900 font-bold text-sm text-zinc-900">
            <span>Tổng cộng thanh toán:</span>
            <span className="font-mono text-base text-zinc-900">
              {formatCurrencyVND(invoice.totalAfterTax || 0)}
            </span>
          </div>
        </div>
      </div>

      {/* Document Confirmation & Issuance Status (Replaces fake digital signature) */}
      <div className="pt-8 pb-4 grid grid-cols-2 gap-6 text-center text-xs">
        <div>
          <div className="font-bold text-zinc-900 uppercase">Người mua hàng</div>
          <div className="text-[11px] text-zinc-500 italic">(Xác nhận đơn hàng)</div>
          <div className="h-16 flex items-center justify-center text-zinc-600 text-[11px]">
            {buyerDisplayName}
          </div>
        </div>

        <div>
          <div className="font-bold text-zinc-900 uppercase">Đơn vị phát hành</div>
          <div className="text-[11px] text-zinc-500 italic">(Ghi nhận hệ thống)</div>
          <div className="h-16 flex flex-col items-center justify-center text-[11px] text-zinc-700">
            <div className="font-semibold text-zinc-900">{sellerDisplayName}</div>
            <div className="text-[10px] text-zinc-500 mt-0.5">
              {invoice.officiallyIssuedAt
                ? `Thời điểm phát hành: ${formattedOfficiallyIssuedAt}`
                : 'Thời điểm phát hành: Chưa phát hành'}
            </div>
          </div>
        </div>
      </div>

      {/* Footer System Note */}
      <div className="pt-4 border-t border-zinc-200 text-center text-[11px] text-zinc-400 italic">
        {isDemo
          ? 'Chứng từ điện tử mô phỏng phục vụ mục đích học tập và kiểm thử kỹ thuật.'
          : 'Chứng từ điện tử ghi nhận giao dịch mua sắm trên hệ thống Closet Shop.'}
      </div>
    </div>
  );
};
