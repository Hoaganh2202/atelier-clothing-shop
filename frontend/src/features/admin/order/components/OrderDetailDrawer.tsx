import React from 'react';
import {
  Alert,
  Button,
  Descriptions,
  Divider,
  Drawer,
  Popconfirm,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
  message,
  theme,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DollarOutlined,
  ReloadOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { formatCurrencyVND } from '@/lib/format';
import {
  formatDateTimeVN,
  getOrderApiErrorMessage,
  isForbiddenError,
} from '../api/order.api';
import { useAdminOrder, useUpdateAdminOrderStatus } from '../api/order.hooks';
import {
  DATA_SOURCE_LABELS,
  NEXT_ALLOWED_ORDER_STATUSES,
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TAG_CONFIG,
  PAYMENT_METHOD_LABELS,
  PAYMENT_STATUS_LABELS,
  type OrderItemAdminResponse,
  type OrderPaymentAdminResponse,
  type OrderStatus,
  type PaymentMethod,
  type PaymentStatus,
} from '../order.types';

const { Text, Title } = Typography;

interface OrderDetailDrawerProps {
  orderId?: number;
  open: boolean;
  onClose: () => void;
}

export const OrderDetailDrawer: React.FC<OrderDetailDrawerProps> = ({
  orderId,
  open,
  onClose,
}) => {
  const { token } = theme.useToken();
  const {
    data: order,
    isLoading,
    isError,
    error,
    refetch,
  } = useAdminOrder(open ? orderId : undefined);

  const updateStatusMutation = useUpdateAdminOrderStatus();

  const handleUpdateStatus = (targetStatus: OrderStatus) => {
    if (!orderId) return;

    updateStatusMutation.mutate(
      { id: orderId, payload: { status: targetStatus } },
      {
        onSuccess: () => {
          message.success(
            `Đã chuyển trạng thái đơn hàng sang "${ORDER_STATUS_LABELS[targetStatus]}"`
          );
        },
        onError: (err) => {
          message.error(
            getOrderApiErrorMessage(err, 'Không thể cập nhật trạng thái đơn hàng')
          );
        },
      }
    );
  };

  const isUpdating = updateStatusMutation.isPending;
  const allowedNextStatuses: OrderStatus[] = order
    ? NEXT_ALLOWED_ORDER_STATUSES[order.status] || []
    : [];

  const itemColumns: ColumnsType<OrderItemAdminResponse> = [
    {
      title: 'Sản phẩm',
      dataIndex: 'productName',
      key: 'productName',
      render: (productName: string, record) => (
        <div>
          <Text strong style={{ fontSize: 13, color: token.colorPrimary }}>
            {productName}
          </Text>
          <div style={{ marginTop: 2 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              Phân loại: {record.variantColor || '—'} / {record.variantSize || '—'}
            </Text>
          </div>
        </div>
      ),
    },
    {
      title: 'Đơn giá',
      dataIndex: 'unitPrice',
      key: 'unitPrice',
      align: 'right',
      width: 110,
      render: (val: number) => (
        <Text style={{ fontSize: 13 }}>{formatCurrencyVND(val)}</Text>
      ),
    },
    {
      title: 'SL',
      dataIndex: 'quantity',
      key: 'quantity',
      align: 'center',
      width: 60,
      render: (qty: number) => (
        <Text style={{ fontSize: 13, fontWeight: 500 }}>{qty}</Text>
      ),
    },
    {
      title: 'Thành tiền',
      dataIndex: 'totalPrice',
      key: 'totalPrice',
      align: 'right',
      width: 120,
      render: (total: number) => (
        <Text strong style={{ fontSize: 13, color: token.colorPrimary }}>
          {formatCurrencyVND(total)}
        </Text>
      ),
    },
  ];

  const paymentColumns: ColumnsType<OrderPaymentAdminResponse> = [
    {
      title: 'Phương thức',
      dataIndex: 'method',
      key: 'method',
      render: (method: PaymentMethod) => (
        <Text style={{ fontSize: 13 }}>
          {PAYMENT_METHOD_LABELS[method] || method}
        </Text>
      ),
    },
    {
      title: 'Số tiền',
      dataIndex: 'amount',
      key: 'amount',
      align: 'right',
      render: (amount: number) => (
        <Text strong style={{ fontSize: 13 }}>
          {formatCurrencyVND(amount)}
        </Text>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      render: (status: PaymentStatus) => {
        const config = PAYMENT_STATUS_LABELS[status] || {
          label: status,
          color: 'default',
        };
        return <Tag color={config.color}>{config.label}</Tag>;
      },
    },
    {
      title: 'Mã GD',
      dataIndex: 'transactionCode',
      key: 'transactionCode',
      render: (code?: string | null) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {code || '—'}
        </Text>
      ),
    },
    {
      title: 'Thời gian',
      dataIndex: 'paidAt',
      key: 'paidAt',
      render: (val?: string | null) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {formatDateTimeVN(val)}
        </Text>
      ),
    },
  ];

  const renderStatusActions = () => {
    if (!order) return null;

    if (allowedNextStatuses.length === 0) {
      return (
        <Text type="secondary" style={{ fontSize: 13 }}>
          Đơn hàng đã kết thúc luồng xử lý
        </Text>
      );
    }

    return (
      <Space wrap size={8}>
        {allowedNextStatuses.includes('CONFIRMED') && (
          <Popconfirm
            title="Xác nhận đơn hàng"
            description="Bạn có chắc chắn muốn xác nhận đơn hàng này?"
            okText="Xác nhận"
            cancelText="Đóng"
            onConfirm={() => handleUpdateStatus('CONFIRMED')}
            disabled={isUpdating}
          >
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              loading={isUpdating}
              style={{ borderRadius: 6 }}
            >
              Xác nhận đơn
            </Button>
          </Popconfirm>
        )}

        {allowedNextStatuses.includes('SHIPPING') && (
          <Popconfirm
            title="Bắt đầu giao hàng"
            description="Bạn có chắc chắn muốn chuyển trạng thái đơn hàng sang đang vận chuyển?"
            okText="Giao hàng"
            cancelText="Đóng"
            onConfirm={() => handleUpdateStatus('SHIPPING')}
            disabled={isUpdating}
          >
            <Button
              type="primary"
              icon={<SendOutlined />}
              loading={isUpdating}
              style={{ borderRadius: 6, backgroundColor: '#0891b2', borderColor: '#0891b2' }}
            >
              Bắt đầu giao hàng
            </Button>
          </Popconfirm>
        )}

        {allowedNextStatuses.includes('COMPLETED') && (
          <Popconfirm
            title="Hoàn tất đơn hàng"
            description="Bạn có chắc chắn muốn đánh dấu đơn hàng này là đã hoàn thành?"
            okText="Hoàn tất"
            cancelText="Đóng"
            onConfirm={() => handleUpdateStatus('COMPLETED')}
            disabled={isUpdating}
          >
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              loading={isUpdating}
              style={{ borderRadius: 6, backgroundColor: '#16a34a', borderColor: '#16a34a' }}
            >
              Hoàn tất đơn hàng
            </Button>
          </Popconfirm>
        )}

        {allowedNextStatuses.includes('REFUNDED') && (
          <Popconfirm
            title="Xác nhận hoàn tiền"
            description="Bạn có chắc chắn muốn chuyển trạng thái đơn hàng này sang hoàn tiền?"
            okText="Hoàn tiền"
            cancelText="Đóng"
            okButtonProps={{ danger: true }}
            onConfirm={() => handleUpdateStatus('REFUNDED')}
            disabled={isUpdating}
          >
            <Button
              danger
              icon={<DollarOutlined />}
              loading={isUpdating}
              style={{ borderRadius: 6 }}
            >
              Hoàn tiền
            </Button>
          </Popconfirm>
        )}

        {allowedNextStatuses.includes('CANCELLED') && (
          <Popconfirm
            title="Hủy đơn hàng"
            description="Bạn có chắc chắn muốn hủy đơn hàng này?"
            okText="Hủy đơn"
            cancelText="Đóng"
            okButtonProps={{ danger: true }}
            onConfirm={() => handleUpdateStatus('CANCELLED')}
            disabled={isUpdating}
          >
            <Button
              danger
              icon={<CloseCircleOutlined />}
              loading={isUpdating}
              style={{ borderRadius: 6 }}
            >
              Hủy đơn
            </Button>
          </Popconfirm>
        )}
      </Space>
    );
  };

  return (
    <Drawer
      key={orderId || 'none'}
      title={
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
          <Space size={8}>
            <Title level={5} style={{ margin: 0, color: token.colorPrimary }}>
              {order ? `Đơn hàng #${order.orderCode}` : 'Chi tiết đơn hàng'}
            </Title>
            {order && (
              <Tag color={ORDER_STATUS_TAG_CONFIG[order.status]?.color || 'default'}>
                {ORDER_STATUS_TAG_CONFIG[order.status]?.label || order.status}
              </Tag>
            )}
          </Space>
        </div>
      }
      placement="right"
      width={680}
      open={open}
      onClose={onClose}
      destroyOnClose
      styles={{
        header: {
          borderBottom: '1px solid #E4DCD3',
          padding: '16px 24px',
          backgroundColor: '#FFFFFF',
        },
        body: {
          padding: 24,
          backgroundColor: '#FFFFFF',
        },
        footer: {
          borderTop: '1px solid #E4DCD3',
          padding: '12px 24px',
          backgroundColor: '#FBF9F5',
        },
      }}
      footer={
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 16,
          }}
        >
          <div>{renderStatusActions()}</div>
          <Button onClick={onClose} style={{ borderRadius: 6 }}>
            Đóng
          </Button>
        </div>
      }
    >
      {isLoading && <Skeleton active paragraph={{ rows: 10 }} />}

      {isError && (
        <Alert
          type="error"
          showIcon
          message="Không thể tải thông tin đơn hàng"
          description={
            isForbiddenError(error)
              ? 'Bạn không có quyền xem thông tin đơn hàng này.'
              : getOrderApiErrorMessage(error, 'Đã xảy ra lỗi khi lấy dữ liệu chi tiết.')
          }
          action={
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={() => refetch()}
              style={{ borderRadius: 6 }}
            >
              Thử lại
            </Button>
          }
        />
      )}

      {order && !isLoading && !isError && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* Thông tin đơn hàng & Người nhận */}
          <div>
            <Text strong style={{ fontSize: 14, color: token.colorPrimary, display: 'block', marginBottom: 12 }}>
              Thông tin giao nhận & Khách hàng
            </Text>
            <Descriptions
              bordered
              size="small"
              column={{ xs: 1, sm: 2 }}
              labelStyle={{ width: 140, fontWeight: 500, backgroundColor: '#FAF8F5' }}
            >
              <Descriptions.Item label="Người nhận">
                <Text strong>{order.receiverName}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Số điện thoại">
                <Text copyable>{order.receiverPhone}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Địa chỉ giao hàng" span={2}>
                <Text>{`${order.addressLine}, ${order.ward}, ${order.district}, ${order.province}`}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Ngày đặt hàng">
                <Text>{formatDateTimeVN(order.createdAt)}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Ngày hoàn tất">
                <Text>{formatDateTimeVN(order.completedAt)}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Kênh bán hàng">
                <Text>{order.salesChannel || 'Website'}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Nguồn dữ liệu">
                <Text>{DATA_SOURCE_LABELS[order.dataSource] || order.dataSource}</Text>
              </Descriptions.Item>
              {order.note && (
                <Descriptions.Item label="Ghi chú đơn hàng" span={2}>
                  <Text type="secondary">{order.note}</Text>
                </Descriptions.Item>
              )}
            </Descriptions>
          </div>

          <Divider style={{ margin: '4px 0' }} />

          {/* Danh sách sản phẩm */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <Text strong style={{ fontSize: 14, color: token.colorPrimary }}>
                Danh sách sản phẩm ({order.items.length})
              </Text>
            </div>
            <Table
              columns={itemColumns}
              dataSource={order.items}
              rowKey="id"
              pagination={false}
              size="small"
              bordered
              style={{ borderRadius: 6 }}
            />
          </div>

          <Divider style={{ margin: '4px 0' }} />

          {/* Tổng tiền & Chi phí */}
          <div>
            <Text strong style={{ fontSize: 14, color: token.colorPrimary, display: 'block', marginBottom: 12 }}>
              Chi tiết thanh toán & Chi phí
            </Text>
            <div
              style={{
                border: '1px solid #E4DCD3',
                borderRadius: 6,
                padding: 16,
                backgroundColor: '#FAF8F5',
                display: 'flex',
                flexDirection: 'column',
                gap: 8,
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <Text type="secondary">Tạm tính</Text>
                <Text>{formatCurrencyVND(order.subtotal)}</Text>
              </div>
              {order.discountAmount ? (
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Text type="secondary">
                    Giảm giá {order.voucherId ? `(Mã #${order.voucherId})` : ''}
                  </Text>
                  <Text style={{ color: '#dc2626' }}>
                    - {formatCurrencyVND(order.discountAmount)}
                  </Text>
                </div>
              ) : null}
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <Text type="secondary">Phí vận chuyển</Text>
                <Text>{formatCurrencyVND(order.shippingFee)}</Text>
              </div>
              <Divider style={{ margin: '8px 0' }} />
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Text strong style={{ fontSize: 15, color: token.colorPrimary }}>
                  Tổng cộng
                </Text>
                <Text strong style={{ fontSize: 18, color: '#16a34a' }}>
                  {formatCurrencyVND(order.totalAmount)}
                </Text>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4 }}>
                <Text type="secondary">Phương thức thanh toán</Text>
                <Text strong>
                  {PAYMENT_METHOD_LABELS[order.paymentMethod] || order.paymentMethod}
                </Text>
              </div>
            </div>
          </div>

          {/* Lịch sử giao dịch thanh toán nếu có */}
          {order.payments && order.payments.length > 0 && (
            <>
              <Divider style={{ margin: '4px 0' }} />
              <div>
                <Text strong style={{ fontSize: 14, color: token.colorPrimary, display: 'block', marginBottom: 12 }}>
                  Giao dịch thanh toán ({order.payments.length})
                </Text>
                <Table
                  columns={paymentColumns}
                  dataSource={order.payments}
                  rowKey="id"
                  pagination={false}
                  size="small"
                  bordered
                  style={{ borderRadius: 6 }}
                />
              </div>
            </>
          )}
        </div>
      )}
    </Drawer>
  );
};
