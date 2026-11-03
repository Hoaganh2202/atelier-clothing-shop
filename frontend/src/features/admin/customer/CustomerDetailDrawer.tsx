import React from 'react';
import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Popconfirm,
  Skeleton,
  Space,
  Tag,
  Typography,
  theme,
} from 'antd';
import {
  CheckCircleOutlined,
  ReloadOutlined,
  StopOutlined,
} from '@ant-design/icons';
import {
  formatDateOnlyVN,
  formatDateTimeVN,
  getCustomerApiErrorMessage,
  isForbiddenError,
} from './customer.api';
import { useAdminCustomer } from './customer.hooks';
import {
  DATA_SOURCE_LABELS,
  GENDER_LABELS,
  type CustomerStatus,
} from './customer.types';

const { Text } = Typography;

interface CustomerDetailDrawerProps {
  customerId?: number;
  open: boolean;
  onClose: () => void;
  currentAdminId?: number;
  isUpdatingStatus: boolean;
  onToggleStatus: (customerId: number, customerName: string, nextStatus: CustomerStatus) => void;
}

export const CustomerDetailDrawer: React.FC<CustomerDetailDrawerProps> = ({
  customerId,
  open,
  onClose,
  currentAdminId,
  isUpdatingStatus,
  onToggleStatus,
}) => {
  const { token } = theme.useToken();
  const {
    data: customer,
    isLoading,
    isError,
    error,
    refetch,
  } = useAdminCustomer(open ? customerId : undefined);

  const isSelfAccount = Boolean(
    currentAdminId !== undefined && customer?.id !== undefined && currentAdminId === customer.id
  );
  const isActive = customer?.status === 'ACTIVE';
  const nextStatus: CustomerStatus = isActive ? 'BLOCKED' : 'ACTIVE';

  return (
    <Drawer
      title={
        <Text strong style={{ fontSize: 15, color: token.colorPrimary, fontWeight: 600 }}>
          {customer ? `Hồ sơ khách hàng #${customer.id}` : 'Chi tiết khách hàng'}
        </Text>
      }
      placement="right"
      width={480}
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
        customer ? (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 12,
            }}
          >
            <Button onClick={onClose}>
              Đóng
            </Button>

            <Popconfirm
              title={
                isActive ? 'Khóa tài khoản khách hàng?' : 'Mở khóa tài khoản khách hàng?'
              }
              description={
                isActive
                  ? `Tài khoản "${customer.name}" sẽ không thể đăng nhập sau khi bị khóa.`
                  : `Cho phép tài khoản "${customer.name}" đăng nhập và mua hàng trở lại.`
              }
              okText={isActive ? 'Khóa tài khoản' : 'Mở khóa'}
              cancelText="Hủy"
              okButtonProps={{
                danger: isActive,
                loading: isUpdatingStatus,
              }}
              disabled={isSelfAccount}
              onConfirm={() => onToggleStatus(customer.id, customer.name, nextStatus)}
            >
              <Button
                type={isActive ? 'default' : 'primary'}
                danger={isActive}
                icon={isActive ? <StopOutlined /> : <CheckCircleOutlined />}
                loading={isUpdatingStatus}
                disabled={isSelfAccount}
                style={{ fontWeight: 500 }}
              >
                {isActive ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}
              </Button>
            </Popconfirm>
          </div>
        ) : null
      }
    >
      {isLoading && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Skeleton active paragraph={{ rows: 6 }} />
        </div>
      )}

      {!isLoading && isError && (
        <Alert
          type={isForbiddenError(error) ? 'warning' : 'error'}
          showIcon
          style={{ border: '1px solid #E4DCD3' }}
          message={
            isForbiddenError(error)
              ? 'Bạn không có quyền xem mục này'
              : 'Không thể tải thông tin khách hàng'
          }
          description={
            isForbiddenError(error)
              ? 'Chức năng quản lý khách hàng chỉ dành cho tài khoản Quản trị viên (ADMIN).'
              : getCustomerApiErrorMessage(error, 'Đã xảy ra lỗi khi tải chi tiết khách hàng.')
          }
          action={
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={() => refetch()}
            >
              Thử lại
            </Button>
          }
        />
      )}

      {!isLoading && !isError && customer && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          <div
            style={{
              padding: 16,
              backgroundColor: '#FBF9F5',
              border: '1px solid #E4DCD3',
              borderRadius: token.borderRadius,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 12,
            }}
          >
            <div>
              <Text
                strong
                style={{
                  display: 'block',
                  fontSize: 15,
                  fontWeight: 600,
                  color: token.colorPrimary,
                }}
              >
                {customer.name}
              </Text>
              <Text style={{ fontSize: 13, color: '#6E675F' }}>
                Mã khách hàng: #{customer.id}
              </Text>
            </div>

            {isActive ? (
              <Tag
                style={{
                  margin: 0,
                  backgroundColor: '#F5F0EA',
                  borderColor: '#E4DCD3',
                  color: token.colorPrimary,
                  fontWeight: 500,
                  fontSize: 13,
                  padding: '4px 12px',
                }}
              >
                Đang hoạt động
              </Tag>
            ) : (
              <Tag
                style={{
                  margin: 0,
                  backgroundColor: '#C2410C',
                  borderColor: '#C2410C',
                  color: '#FFFFFF',
                  fontWeight: 500,
                  fontSize: 13,
                  padding: '4px 12px',
                }}
              >
                Đã khóa
              </Tag>
            )}
          </div>

          <Descriptions
            column={1}
            bordered
            size="middle"
            styles={{
              label: {
                width: 160,
                backgroundColor: '#FBF9F5',
                color: '#6E675F',
                fontWeight: 500,
                fontSize: 13,
              },
              content: {
                backgroundColor: '#FFFFFF',
                color: token.colorPrimary,
                fontSize: 15,
              },
            }}
          >
            <Descriptions.Item label="Họ và tên">
              <Text strong style={{ color: token.colorPrimary, fontSize: 15 }}>
                {customer.name}
              </Text>
            </Descriptions.Item>

            <Descriptions.Item label="Email">
              {customer.email ? (
                <Text style={{ color: token.colorPrimary, fontSize: 15 }}>{customer.email}</Text>
              ) : (
                <Text style={{ color: '#6E675F', fontSize: 13 }}>—</Text>
              )}
            </Descriptions.Item>

            <Descriptions.Item label="Số điện thoại">
              {customer.phone ? (
                <Text style={{ color: token.colorPrimary, fontSize: 15 }}>{customer.phone}</Text>
              ) : (
                <Text style={{ color: '#6E675F', fontSize: 13 }}>—</Text>
              )}
            </Descriptions.Item>

            <Descriptions.Item label="Giới tính">
              {customer.gender ? (
                <Text style={{ color: token.colorPrimary, fontSize: 15 }}>
                  {GENDER_LABELS[customer.gender] ?? customer.gender}
                </Text>
              ) : (
                <Text style={{ color: '#6E675F', fontSize: 13 }}>—</Text>
              )}
            </Descriptions.Item>

            <Descriptions.Item label="Ngày sinh">
              <Text
                style={{
                  color: customer.birthDate ? token.colorPrimary : '#6E675F',
                  fontSize: customer.birthDate ? 15 : 13,
                }}
              >
                {formatDateOnlyVN(customer.birthDate, '—')}
              </Text>
            </Descriptions.Item>

            <Descriptions.Item label="Nguồn dữ liệu">
              <Space size={8}>
                <Tag
                  style={{
                    margin: 0,
                    backgroundColor: '#F5F0EA',
                    borderColor: '#E4DCD3',
                    color: token.colorPrimary,
                    fontSize: 13,
                  }}
                >
                  {DATA_SOURCE_LABELS[customer.dataSource] ?? customer.dataSource}
                </Tag>
              </Space>
            </Descriptions.Item>

            <Descriptions.Item label="Đăng nhập gần nhất">
              <Text
                style={{
                  color: customer.lastLoginAt ? token.colorPrimary : '#6E675F',
                  fontSize: 13,
                }}
              >
                {formatDateTimeVN(customer.lastLoginAt, 'Chưa đăng nhập')}
              </Text>
            </Descriptions.Item>

            <Descriptions.Item label="Ngày tạo tài khoản">
              <Text style={{ color: token.colorPrimary, fontSize: 13 }}>
                {formatDateTimeVN(customer.createdAt, '—')}
              </Text>
            </Descriptions.Item>
          </Descriptions>
        </div>
      )}
    </Drawer>
  );
};

export default CustomerDetailDrawer;
