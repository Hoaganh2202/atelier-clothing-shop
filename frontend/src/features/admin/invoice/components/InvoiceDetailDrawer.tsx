import React from 'react';
import {
  Alert,
  Button,
  Descriptions,
  Divider,
  Drawer,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  FileTextOutlined,
  PrinterOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { formatCurrencyVND, formatDateTimeVN } from '@/lib/format';
import { useAdminInvoiceDetail } from '../api/invoice.hooks';
import {
  DOCUMENT_STATUS_CONFIG,
  INVOICE_TYPE_CONFIG,
  type InvoiceItemResponse,
} from '../invoice.types';

const { Text, Title } = Typography;

interface InvoiceDetailDrawerProps {
  invoiceId?: number;
  open: boolean;
  onClose: () => void;
}

export const InvoiceDetailDrawer: React.FC<InvoiceDetailDrawerProps> = ({
  invoiceId,
  open,
  onClose,
}) => {
  const {
    data: invoice,
    isLoading,
    isError,
    error,
    refetch,
  } = useAdminInvoiceDetail(open ? invoiceId : undefined);

  const handlePrint = () => {
    window.print();
  };

  const itemColumns: ColumnsType<InvoiceItemResponse> = [
    {
      title: 'STT',
      key: 'index',
      width: 50,
      align: 'center',
      render: (_value, _record, index) => index + 1,
    },
    {
      title: 'Tên mặt hàng / Dịch vụ',
      dataIndex: 'itemName',
      key: 'itemName',
      render: (name: string, record) => (
        <Space direction="vertical" size={2}>
          <Text strong style={{ fontSize: 13 }}>
            {name}
          </Text>
          {record.itemType === 'SHIPPING_SURCHARGE' && (
            <Tag color="cyan" style={{ fontSize: 11 }}>
              Phí vận chuyển
            </Tag>
          )}
          {record.itemType === 'DISCOUNT_VOUCHER' && (
            <Tag color="orange" style={{ fontSize: 11 }}>
              Chiết khấu / Voucher
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: 'ĐVT',
      dataIndex: 'unit',
      key: 'unit',
      width: 70,
      align: 'center',
      render: (unit?: string) => unit || '—',
    },
    {
      title: 'Số lượng',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 80,
      align: 'center',
    },
    {
      title: 'Đơn giá chưa thuế',
      dataIndex: 'unitPriceNet',
      key: 'unitPriceNet',
      width: 130,
      align: 'right',
      render: (price: number) => formatCurrencyVND(price),
    },
    {
      title: 'Thuế suất',
      dataIndex: 'taxCode',
      key: 'taxCode',
      width: 90,
      align: 'center',
      render: (taxCode?: string, record?: InvoiceItemResponse) => {
        if (record?.taxRate) {
          return `${(record.taxRate * 100).toFixed(0)}%`;
        }
        return taxCode || '—';
      },
    },
    {
      title: 'Tiền thuế VAT',
      dataIndex: 'taxAmount',
      key: 'taxAmount',
      width: 110,
      align: 'right',
      render: (amount: number) => formatCurrencyVND(amount),
    },
    {
      title: 'Thành tiền',
      dataIndex: 'lineTotalGross',
      key: 'lineTotalGross',
      width: 130,
      align: 'right',
      render: (total: number) => (
        <Text strong style={{ color: '#18181B' }}>
          {formatCurrencyVND(total)}
        </Text>
      ),
    },
  ];

  return (
    <Drawer
      title={
        <Space align="center" size={8}>
          <FileTextOutlined style={{ fontSize: 18 }} />
          <span>Chi tiết hóa đơn điện tử</span>
          {invoice?.invoiceNumber && (
            <Text code style={{ fontSize: 13 }}>
              {invoice.invoiceNumber}
            </Text>
          )}
        </Space>
      }
      placement="right"
      width={760}
      open={open}
      onClose={onClose}
      destroyOnClose
      footer={
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Button
            icon={<PrinterOutlined />}
            onClick={handlePrint}
            disabled={!invoice || isLoading}
          >
            In hóa đơn
          </Button>
          <Button onClick={onClose}>Đóng</Button>
        </div>
      }
    >
      {isLoading && (
        <div style={{ padding: '16px 0' }}>
          <Skeleton active paragraph={{ rows: 12 }} />
        </div>
      )}

      {isError && (
        <Alert
          type="error"
          showIcon
          message="Không thể tải thông tin hóa đơn"
          description={
            error instanceof Error ? error.message : 'Đã có lỗi xảy ra từ máy chủ.'
          }
          action={
            <Button size="small" type="primary" onClick={() => refetch()}>
              Thử lại
            </Button>
          }
          style={{ marginBottom: 16 }}
        />
      )}

      {!isLoading && !isError && invoice && (
        <div>
          {/* Cảnh báo thay thế */}
          {invoice.replacementNotice && (
            <Alert
              type="info"
              showIcon
              icon={<InfoCircleOutlined />}
              message="Thông tin thay thế hóa đơn"
              description={invoice.replacementNotice}
              style={{ marginBottom: 16 }}
            />
          )}

          {/* Khối 1: Thông tin chung */}
          <Descriptions
            title="Thông tin chứng từ"
            bordered
            size="small"
            column={2}
            style={{ marginBottom: 20 }}
          >
            <Descriptions.Item label="Số hóa đơn">
              <Text strong>{invoice.invoiceNumber || 'Chưa cấp số (DRAFT)'}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="Ký hiệu hóa đơn">
              {invoice.invoiceSeries || '—'}
            </Descriptions.Item>

            <Descriptions.Item label="Trạng thái chứng từ">
              <Tag color={DOCUMENT_STATUS_CONFIG[invoice.documentStatus]?.color || 'default'}>
                {DOCUMENT_STATUS_CONFIG[invoice.documentStatus]?.label || invoice.documentStatus}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Loại hóa đơn">
              <Tag color={INVOICE_TYPE_CONFIG[invoice.invoiceType]?.color || 'default'}>
                {INVOICE_TYPE_CONFIG[invoice.invoiceType]?.label || invoice.invoiceType}
              </Tag>
            </Descriptions.Item>

            <Descriptions.Item label="Đơn hàng liên kết">
              {invoice.orderId ? (
                <Text code>ORD-{invoice.orderId}</Text>
              ) : (
                '—'
              )}
            </Descriptions.Item>
            <Descriptions.Item label="Mã cơ quan thuế">
              {invoice.taxAuthorityCode || '—'}
            </Descriptions.Item>

            <Descriptions.Item label="Ngày lập thuế">
              {formatDateTimeVN(invoice.taxPointDate)}
            </Descriptions.Item>
            <Descriptions.Item label="Thời điểm phát hành">
              {formatDateTimeVN(invoice.officiallyIssuedAt)}
            </Descriptions.Item>

            {invoice.isReplaced && (
              <Descriptions.Item label="Trạng thái thay thế" span={2}>
                <Tag color="red">ĐÃ BỊ THAY THẾ</Tag>
                {invoice.replacedByInvoiceId && (
                  <Text type="secondary" style={{ marginLeft: 8 }}>
                    (Thay thế bởi hóa đơn ID #{invoice.replacedByInvoiceId})
                  </Text>
                )}
              </Descriptions.Item>
            )}

            {invoice.replacingInvoiceId && (
              <Descriptions.Item label="Hóa đơn gốc thay thế" span={2}>
                <Text>Thay thế cho hóa đơn gốc ID #{invoice.replacingInvoiceId}</Text>
              </Descriptions.Item>
            )}
          </Descriptions>

          {/* Khối 2: Thông tin Bên Bán & Bên Mua */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 20 }}>
            <Descriptions
              title="Đơn vị bán hàng (Người bán)"
              bordered
              size="small"
              column={1}
            >
              <Descriptions.Item label="Tên đơn vị">
                <Text strong>
                  {invoice.seller?.sellerLegalName?.trim() ||
                    invoice.seller?.sellerBrandName?.trim() ||
                    '—'}
                </Text>
              </Descriptions.Item>
              <Descriptions.Item label="Mã số thuế">
                {invoice.seller?.sellerTaxCode || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Địa chỉ">
                {invoice.seller?.sellerAddress || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Email / Hotline">
                {invoice.seller?.sellerEmail || invoice.seller?.sellerPhone || '—'}
              </Descriptions.Item>
              {invoice.seller?.sellerBankAccount && (
                <Descriptions.Item label="Tài khoản NH">
                  {invoice.seller.sellerBankAccount} - {invoice.seller.sellerBankName}
                </Descriptions.Item>
              )}
            </Descriptions>

            <Descriptions
              title="Khách hàng (Người mua)"
              bordered
              size="small"
              column={1}
            >
              <Descriptions.Item label="Họ tên người mua">
                <Text strong>{invoice.buyer?.buyerName || 'Khách hàng vãng lai'}</Text>
              </Descriptions.Item>
              {invoice.buyer?.companyName && (
                <Descriptions.Item label="Tên công ty">
                  {invoice.buyer.companyName}
                </Descriptions.Item>
              )}
              <Descriptions.Item label="Mã số thuế">
                {invoice.buyer?.taxCode || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Địa chỉ">
                {invoice.buyer?.address || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Email / SĐT">
                {invoice.buyer?.email || invoice.buyer?.phoneNumber || '—'}
              </Descriptions.Item>
            </Descriptions>
          </div>

          <Divider style={{ margin: '16px 0' }} />

          {/* Khối 3: Bảng dòng hàng */}
          <Title level={5} style={{ fontSize: 14, marginBottom: 12 }}>
            Chi tiết hàng hóa & Dịch vụ
          </Title>
          <Table<InvoiceItemResponse>
            columns={itemColumns}
            dataSource={invoice.items || []}
            rowKey="id"
            pagination={false}
            size="small"
            bordered
            style={{ marginBottom: 20 }}
          />

          {/* Khối 4: Tổng hợp thanh toán */}
          <Descriptions
            bordered
            size="small"
            column={1}
            style={{ maxWidth: 420, marginLeft: 'auto', marginBottom: 20 }}
          >
            <Descriptions.Item label="Tiền hàng chưa thuế">
              <div style={{ textAlign: 'right' }}>
                {formatCurrencyVND(invoice.totalMerchandiseBeforeTax)}
              </div>
            </Descriptions.Item>
            {invoice.totalMerchandiseDiscount > 0 && (
              <Descriptions.Item label="Chiết khấu hàng hóa">
                <div style={{ textAlign: 'right', color: '#DC2626' }}>
                  -{formatCurrencyVND(invoice.totalMerchandiseDiscount)}
                </div>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="Cước vận chuyển">
              <div style={{ textAlign: 'right' }}>
                {formatCurrencyVND(invoice.totalShippingBeforeTax)}
              </div>
            </Descriptions.Item>
            {invoice.totalShippingDiscount > 0 && (
              <Descriptions.Item label="Giảm giá vận chuyển">
                <div style={{ textAlign: 'right', color: '#DC2626' }}>
                  -{formatCurrencyVND(invoice.totalShippingDiscount)}
                </div>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="Tổng tiền chịu thuế VAT">
              <div style={{ textAlign: 'right' }}>
                {formatCurrencyVND(invoice.totalTaxableAmount)}
              </div>
            </Descriptions.Item>
            <Descriptions.Item label="Tổng tiền thuế GTGT (VAT)">
              <div style={{ textAlign: 'right', fontWeight: 600 }}>
                {formatCurrencyVND(invoice.totalTaxAmount)}
              </div>
            </Descriptions.Item>
            <Descriptions.Item label="Tổng cộng thanh toán sau thuế">
              <div style={{ textAlign: 'right', fontSize: 16, fontWeight: 700, color: '#18181B' }}>
                {formatCurrencyVND(invoice.totalAfterTax)}
              </div>
            </Descriptions.Item>
          </Descriptions>
        </div>
      )}
    </Drawer>
  );
};
