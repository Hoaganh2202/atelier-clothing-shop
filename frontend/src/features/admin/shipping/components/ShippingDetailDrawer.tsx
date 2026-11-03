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
  CarOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DollarOutlined,
  EnvironmentOutlined,
  InfoCircleOutlined,
  ReloadOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { formatCurrencyVND } from '@/lib/format';
import {
  formatDateTimeVN,
  getShippingApiErrorMessage,
  isForbiddenError,
} from '../api/shipping.api';
import {
  useAdminShippingOrderDetail,
  useUpdateAdminShippingStatus,
} from '../api/shipping.hooks';
import {
  NEXT_ALLOWED_SHIPPING_ACTIONS,
  PAYMENT_METHOD_LABELS,
  SHIPPING_STATUS_LABELS,
  SHIPPING_STATUS_TAG_CONFIG,
  type ShippingItemResponse,
  type ShippingStatus,
} from '../shipping.types';

const { Text, Title } = Typography;

interface ShippingDetailDrawerProps {
  orderId?: number;
  open: boolean;
  onClose: () => void;
}

export const ShippingDetailDrawer: React.FC<ShippingDetailDrawerProps> = ({
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
  } = useAdminShippingOrderDetail(open ? orderId : undefined);

  const updateStatusMutation = useUpdateAdminShippingStatus();

  const handleUpdateStatus = (targetStatus: ShippingStatus) => {
    if (!orderId) return;

    updateStatusMutation.mutate(
      { orderId, payload: { status: targetStatus } },
      {
        onSuccess: () => {
          message.success(
            `Đã cập nhật trạng thái vận chuyển sang "${SHIPPING_STATUS_LABELS[targetStatus]}"`
          );
        },
        onError: (err) => {
          const errMsg = getShippingApiErrorMessage(
            err,
            'Không thể cập nhật trạng thái vận chuyển'
          );
          message.error(errMsg);
        },
      }
    );
  };

  const allowedActions = order ? NEXT_ALLOWED_SHIPPING_ACTIONS[order.status] ?? [] : [];

  const itemColumns: ColumnsType<ShippingItemResponse> = [
    {
      title: 'Sản phẩm',
      key: 'productName',
      render: (_, item) => (
        <div>
          <Text strong style={{ fontSize: 13, display: 'block' }}>
            {item.productName}
          </Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Phân loại:{' '}
            {[item.variantColor, item.variantSize].filter(Boolean).join(' / ') || 'Mặc định'}
          </Text>
        </div>
      ),
    },
    {
      title: 'Số lượng',
      dataIndex: 'quantity',
      key: 'quantity',
      align: 'center',
      width: 90,
      render: (qty: number) => <Text>{qty}</Text>,
    },
    {
      title: 'Đơn giá',
      dataIndex: 'unitPrice',
      key: 'unitPrice',
      align: 'right',
      width: 120,
      render: (price: number) => (
        <Text style={{ fontSize: 13 }}>{formatCurrencyVND(price)}</Text>
      ),
    },
    {
      title: 'Thành tiền',
      dataIndex: 'totalPrice',
      key: 'totalPrice',
      align: 'right',
      width: 130,
      render: (total: number) => (
        <Text strong style={{ fontSize: 13 }}>
          {formatCurrencyVND(total)}
        </Text>
      ),
    },
  ];

  const totalItemCount = order?.items?.reduce((sum, item) => sum + item.quantity, 0) ?? 0;
  const isCod = order?.paymentMethod === 'COD';

  return (
    <Drawer
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <CarOutlined style={{ fontSize: 18, color: token.colorPrimary }} />
          <span>Chi tiết kiện hàng vận chuyển {order ? `#${order.orderCode}` : ''}</span>
        </div>
      }
      placement="right"
      width={680}
      open={open}
      onClose={onClose}
      destroyOnClose
      extra={
        <Button icon={<ReloadOutlined />} onClick={() => refetch()} loading={isLoading}>
          Làm mới
        </Button>
      }
    >
      {isLoading && (
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Skeleton active paragraph={{ rows: 3 }} />
          <Divider style={{ margin: '12px 0' }} />
          <Skeleton active paragraph={{ rows: 4 }} />
          <Divider style={{ margin: '12px 0' }} />
          <Skeleton.Node active style={{ width: '100%', height: 160 }} />
        </Space>
      )}

      {isError && (
        <Alert
          type="error"
          showIcon
          message="Không thể tải thông tin vận chuyển"
          description={
            isForbiddenError(error)
              ? 'Bạn không có quyền xem thông tin vận chuyển của đơn hàng này'
              : getShippingApiErrorMessage(error, 'Đã xảy ra lỗi khi lấy dữ liệu từ máy chủ')
          }
          action={
            <Button size="small" type="primary" onClick={() => refetch()}>
              Thử lại
            </Button>
          }
        />
      )}

      {!isLoading && !isError && order && (
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          {/* Header trạng thái & Nút thao tác chuyển trạng thái */}
          <div
            style={{
              padding: '14px 16px',
              backgroundColor: token.colorFillAlter,
              borderRadius: token.borderRadius,
              border: `1px solid ${token.colorBorderSecondary}`,
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 12,
            }}
          >
            <div>
              <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 2 }}>
                Trạng thái vận chuyển
              </Text>
              <Tag
                color={SHIPPING_STATUS_TAG_CONFIG[order.status]?.color || 'default'}
                style={{ fontSize: 13, padding: '2px 8px' }}
              >
                {SHIPPING_STATUS_TAG_CONFIG[order.status]?.label || order.status}
              </Tag>
            </div>

            {allowedActions.length > 0 && (
              <Space wrap size="small">
                {allowedActions.map((action) => (
                  <Popconfirm
                    key={action.targetStatus}
                    title={action.confirmTitle}
                    okText="Xác nhận"
                    cancelText="Hủy"
                    okButtonProps={{
                      danger: action.danger,
                      loading: updateStatusMutation.isPending,
                    }}
                    onConfirm={() => handleUpdateStatus(action.targetStatus)}
                  >
                    <Button
                      type={action.danger ? 'default' : 'primary'}
                      danger={action.danger}
                      disabled={updateStatusMutation.isPending}
                      icon={
                        action.targetStatus === 'SHIPPING' ? (
                          <CarOutlined />
                        ) : action.targetStatus === 'COMPLETED' ? (
                          <CheckCircleOutlined />
                        ) : (
                          <CloseCircleOutlined />
                        )
                      }
                    >
                      {action.actionText}
                    </Button>
                  </Popconfirm>
                ))}
              </Space>
            )}
          </div>

          {/* Ghi chú của khách hàng (Customer Note - Read-only) */}
          {order.customerNote && (
            <Alert
              type="info"
              showIcon
              icon={<InfoCircleOutlined />}
              message={<Text strong>Ghi chú của khách hàng khi đặt hàng</Text>}
              description={
                <Text style={{ fontSize: 13, color: token.colorTextSecondary }}>
                  {order.customerNote}
                </Text>
              }
              style={{ borderRadius: token.borderRadius }}
            />
          )}

          {/* Thông tin giao nhận và địa chỉ */}
          <div>
            <Title level={5} style={{ margin: '0 0 10px 0', display: 'flex', alignItems: 'center', gap: 6 }}>
              <EnvironmentOutlined style={{ color: token.colorPrimary }} />
              Thông tin giao nhận
            </Title>
            <Descriptions
              bordered
              size="small"
              column={1}
              labelStyle={{ width: 160, fontWeight: 500 }}
            >
              <Descriptions.Item label="Người nhận hàng">
                <Space>
                  <UserOutlined />
                  <Text strong>{order.receiverName}</Text>
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="Số điện thoại">
                <a href={`tel:${order.receiverPhone}`} style={{ fontWeight: 500 }}>
                  {order.receiverPhone}
                </a>
              </Descriptions.Item>
              <Descriptions.Item label="Địa chỉ đầy đủ">
                <Text>{order.fullAddress}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Tỉnh / Thành phố">
                <Tag>{order.province}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Thời gian tạo">
                {formatDateTimeVN(order.createdAt)}
              </Descriptions.Item>
              {order.completedAt && (
                <Descriptions.Item label="Thời gian hoàn tất">
                  {formatDateTimeVN(order.completedAt)}
                </Descriptions.Item>
              )}
            </Descriptions>
          </div>

          <Divider style={{ margin: '8px 0' }} />

          {/* Kiện hàng & Sản phẩm */}
          <div>
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: 10,
              }}
            >
              <Title level={5} style={{ margin: 0 }}>
                Chi tiết sản phẩm ({totalItemCount} sản phẩm)
              </Title>
              <Tag color="blue">{order.items?.length || 0} dòng sản phẩm</Tag>
            </div>
            <Table
              dataSource={order.items || []}
              columns={itemColumns}
              rowKey="id"
              pagination={false}
              size="small"
              bordered
            />
          </div>

          <Divider style={{ margin: '8px 0' }} />

          {/* Thanh toán & Thu hộ COD */}
          <div>
            <Title level={5} style={{ margin: '0 0 10px 0', display: 'flex', alignItems: 'center', gap: 6 }}>
              <DollarOutlined style={{ color: token.colorSuccess }} />
              Thanh toán & Thu hộ COD
            </Title>
            <div
              style={{
                padding: '14px 16px',
                backgroundColor: token.colorFillAlter,
                borderRadius: token.borderRadius,
                border: `1px solid ${token.colorBorderSecondary}`,
              }}
            >
              {/* Highlight COD Amount */}
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginBottom: 12,
                  paddingBottom: 10,
                  borderBottom: `1px dashed ${token.colorBorderSecondary}`,
                }}
              >
                <div>
                  <Text strong style={{ fontSize: 14, display: 'block' }}>
                    Tiền thu hộ khi giao (COD)
                  </Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {isCod ? 'Thu trực tiếp từ khách khi giao hàng' : 'Đơn đã thanh toán trực tuyến qua cổng'}
                  </Text>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <Text
                    style={{
                      fontSize: 18,
                      fontWeight: 700,
                      color: isCod ? token.colorWarning : token.colorTextSecondary,
                    }}
                  >
                    {formatCurrencyVND(order.codAmount)}
                  </Text>
                  {isCod && (
                    <Tag color="orange" style={{ display: 'block', marginTop: 2, marginLeft: 'auto' }}>
                      CẦN THU COD
                    </Tag>
                  )}
                </div>
              </div>

              {/* Chi tiết tài chính */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Text type="secondary">Phương thức thanh toán:</Text>
                  <Tag color={isCod ? 'orange' : 'blue'}>
                    {PAYMENT_METHOD_LABELS[order.paymentMethod] || order.paymentMethod}
                  </Tag>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Text type="secondary">Tạm tính tiền hàng:</Text>
                  <Text>{formatCurrencyVND(order.subtotal)}</Text>
                </div>
                {order.discountAmount ? (
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <Text type="secondary">Giảm giá voucher:</Text>
                    <Text type="danger">- {formatCurrencyVND(order.discountAmount)}</Text>
                  </div>
                ) : null}
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Text type="secondary">Phí vận chuyển:</Text>
                  <Text>{formatCurrencyVND(order.shippingFee)}</Text>
                </div>
                <Divider style={{ margin: '6px 0' }} />
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Text strong style={{ fontSize: 14 }}>Tổng giá trị đơn hàng:</Text>
                  <Text strong style={{ fontSize: 16, color: token.colorPrimary }}>
                    {formatCurrencyVND(order.totalAmount)}
                  </Text>
                </div>
              </div>
            </div>
          </div>
        </Space>
      )}
    </Drawer>
  );
};
