import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Empty,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
  theme,
  Dropdown,
  Modal,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { FilterValue, SorterResult } from 'antd/es/table/interface';
import type { MenuProps } from 'antd';
import {
  CheckCircleOutlined,
  EyeOutlined,
  ReloadOutlined,
  SearchOutlined,
  StopOutlined,
  MoreOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import { useAuthStore } from '@/stores/auth.store';
import {
  formatDateTimeVN,
  getCustomerApiErrorMessage,
  isForbiddenError,
} from './customer.api';
import {
  useAdminCustomers,
  useUpdateCustomerStatus,
} from './customer.hooks';
import { CustomerDetailDrawer } from './CustomerDetailDrawer';
import type {
  CustomerListItem,
  CustomerStatus,
} from './customer.types';

const { Title, Text } = Typography;

type StatusFilterValue = 'ALL' | CustomerStatus;

const DEFAULT_SORT = 'createdAt,desc';

export const CustomerListPage: React.FC = () => {
  const { token } = theme.useToken();
  const currentUser = useAuthStore((state) => state.user);
  const currentAdminId = currentUser?.id;

  const [searchInput, setSearchInput] = useState<string>('');
  const [debouncedKeyword, setDebouncedKeyword] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<StatusFilterValue>('ALL');
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [pageSize, setPageSize] = useState<number>(10);
  const [sortParam, setSortParam] = useState<string>(DEFAULT_SORT);
  const [selectedCustomerId, setSelectedCustomerId] = useState<number | undefined>(undefined);
  const [isDrawerOpen, setIsDrawerOpen] = useState<boolean>(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedKeyword(searchInput.trim());
      setCurrentPage(1);
    }, 400);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  const statusQueryParam: CustomerStatus | undefined =
    statusFilter === 'ALL' ? undefined : statusFilter;

  const {
    data: customersPage,
    isLoading,
    isFetching,
    isError,
    error,
    refetch,
  } = useAdminCustomers({
    page: currentPage - 1,
    size: pageSize,
    keyword: debouncedKeyword || undefined,
    status: statusQueryParam,
    sort: sortParam,
  });

  const updateStatusMutation = useUpdateCustomerStatus();

  const handleToggleStatus = (
    customerId: number,
    customerName: string,
    nextStatus: CustomerStatus
  ) => {
    updateStatusMutation.mutate(
      { id: customerId, payload: { status: nextStatus } },
      {
        onSuccess: () => {
          message.success(
            nextStatus === 'BLOCKED'
              ? `Đã khóa tài khoản "${customerName}"`
              : `Đã mở khóa tài khoản "${customerName}"`
          );
        },
        onError: (err) => {
          message.error(
            getCustomerApiErrorMessage(err, 'Không thể cập nhật trạng thái khách hàng')
          );
        },
      }
    );
  };

  const confirmToggleStatus = (
    customerId: number,
    customerName: string,
    nextStatus: CustomerStatus
  ) => {
    const isBlocking = nextStatus === 'BLOCKED';
    Modal.confirm({
      title: isBlocking ? 'Khóa tài khoản khách hàng?' : 'Mở khóa tài khoản khách hàng?',
      icon: <ExclamationCircleOutlined style={{ color: isBlocking ? '#FF4D4F' : '#16a34a' }} />,
      content: isBlocking
        ? `Tài khoản "${customerName}" sẽ không thể đăng nhập sau khi bị khóa.`
        : `Cho phép tài khoản "${customerName}" đăng nhập và mua hàng trở lại.`,
      okText: isBlocking ? 'Khóa' : 'Mở khóa',
      cancelText: 'Hủy',
      okButtonProps: { danger: isBlocking },
      onOk: () => handleToggleStatus(customerId, customerName, nextStatus),
    });
  };

  const handleOpenDetailDrawer = (customerId: number) => {
    setSelectedCustomerId(customerId);
    setIsDrawerOpen(true);
  };

  const handleCloseDetailDrawer = () => {
    setIsDrawerOpen(false);
  };

  const handleResetFilters = () => {
    setSearchInput('');
    setDebouncedKeyword('');
    setStatusFilter('ALL');
    setSortParam(DEFAULT_SORT);
    setCurrentPage(1);
    refetch();
  };

  const handleTableChange = (
    pagination: TablePaginationConfig,
    _filters: Record<string, FilterValue | null>,
    sorter: SorterResult<CustomerListItem> | SorterResult<CustomerListItem>[]
  ) => {
    const nextPage = pagination.current ?? 1;
    const nextSize = pagination.pageSize ?? 10;

    const singleSorter = Array.isArray(sorter) ? sorter[0] : sorter;
    let nextSort = DEFAULT_SORT;
    if (singleSorter && singleSorter.field && singleSorter.order) {
      const field = String(singleSorter.field);
      const direction = singleSorter.order === 'ascend' ? 'asc' : 'desc';
      nextSort = `${field},${direction}`;
    }

    if (nextSort !== sortParam) {
      setSortParam(nextSort);
      setCurrentPage(1);
    } else {
      setCurrentPage(nextPage);
    }

    if (nextSize !== pageSize) {
      setPageSize(nextSize);
      setCurrentPage(1);
    }
  };

  const [activeSortField, activeSortDirection] = sortParam.split(',');

  const columns: ColumnsType<CustomerListItem> = [
    {
      title: 'Họ tên',
      dataIndex: 'name',
      key: 'name',
      sorter: true,
      sortOrder:
        activeSortField === 'name'
          ? activeSortDirection === 'asc'
            ? 'ascend'
            : 'descend'
          : null,
      render: (name: string, record) => (
        <div>
          <Text strong style={{ fontSize: 15, fontWeight: 600, color: token.colorPrimary }}>
            {name}
          </Text>
          <div style={{ marginTop: 4 }}>
            <Text style={{ fontSize: 13, color: '#6E675F' }}>ID: #{record.id}</Text>
          </div>
        </div>
      ),
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
      render: (email: string | null) =>
        email ? (
          <Text style={{ fontSize: 15, color: token.colorPrimary }}>{email}</Text>
        ) : (
          <Text style={{ fontSize: 13, color: '#6E675F' }}>—</Text>
        ),
    },
    {
      title: 'Số điện thoại',
      dataIndex: 'phone',
      key: 'phone',
      render: (phone: string | null) =>
        phone ? (
          <Text style={{ fontSize: 15, color: token.colorPrimary }}>{phone}</Text>
        ) : (
          <Text style={{ fontSize: 13, color: '#6E675F' }}>—</Text>
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      align: 'center',
      width: 160,
      render: (status: CustomerStatus) =>
        status === 'ACTIVE' ? (
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
        ),
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      sorter: true,
      sortOrder:
        activeSortField === 'createdAt'
          ? activeSortDirection === 'asc'
            ? 'ascend'
            : 'descend'
          : null,
      render: (createdAt: string) => (
        <Text style={{ fontSize: 13, color: '#6E675F' }}>
          {formatDateTimeVN(createdAt, '—')}
        </Text>
      ),
    },
    {
      title: 'Thao tác',
      key: 'action',
      align: 'center',
      fixed: 'right',
      width: 90,
      render: (_, record) => {
        const isActive = record.status === 'ACTIVE';
        const nextStatus: CustomerStatus = isActive ? 'BLOCKED' : 'ACTIVE';
        const isSelfAccount =
          currentAdminId !== undefined && currentAdminId === record.id;
        const isUpdatingThisRow =
          updateStatusMutation.isPending &&
          updateStatusMutation.variables?.id === record.id;

        const actionMenuItems: MenuProps['items'] = [
          {
            key: 'view',
            icon: <EyeOutlined />,
            label: 'Xem chi tiết',
            onClick: () => handleOpenDetailDrawer(record.id),
          },
          {
            type: 'divider',
          },
          {
            key: 'toggle-status',
            icon: isActive ? <StopOutlined /> : <CheckCircleOutlined />,
            label: isActive ? 'Khóa tài khoản' : 'Mở khóa tài khoản',
            danger: isActive,
            disabled: isSelfAccount || isUpdatingThisRow,
            onClick: () => confirmToggleStatus(record.id, record.name, nextStatus),
          },
        ];

        return (
          <Dropdown
            menu={{ items: actionMenuItems }}
            trigger={['click']}
            placement="bottomRight"
          >
            <Button
              type="text"
              size="small"
              icon={<MoreOutlined style={{ fontSize: 18 }} />}
              loading={isUpdatingThisRow}
              aria-label="Thao tác khách hàng"
            />
          </Dropdown>
        );
      },
    },
  ];

  const customers = customersPage?.content ?? [];
  const totalElements = customersPage?.totalElements ?? 0;
  const hasActiveFilter = Boolean(debouncedKeyword) || statusFilter !== 'ALL';

  const isUpdatingSelectedCustomer =
    updateStatusMutation.isPending &&
    selectedCustomerId !== undefined &&
    updateStatusMutation.variables?.id === selectedCustomerId;

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
        width: '100%',
        maxWidth: '100%',
        minWidth: 0,
        fontFamily: "'Be Vietnam Pro', sans-serif",
      }}
    >
      <div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: 12,
          }}
        >
          <div>
            <Title
              level={3}
              style={{
                margin: 0,
                fontSize: 22,
                fontWeight: 600,
                color: token.colorPrimary,
              }}
            >
              Danh sách khách hàng
            </Title>
            <Text style={{ fontSize: 13, color: '#6E675F' }}>
              Tra cứu thông tin tài khoản và quản lý trạng thái hoạt động của khách hàng
            </Text>
          </div>
        </div>
      </div>

      <Card
        bordered
        style={{
          borderColor: '#E4DCD3',
          backgroundColor: '#FFFFFF',
          boxShadow: 'none',
        }}
        styles={{ body: { padding: 16 } }}
      >
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
          }}
        >
          <Space wrap size={12}>
            <Input
              placeholder="Tìm theo họ tên, email hoặc số điện thoại..."
              prefix={<SearchOutlined style={{ color: '#6E675F' }} />}
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              allowClear
              style={{
                width: 320,
                borderColor: '#E4DCD3',
              }}
            />

            <Select<StatusFilterValue>
              value={statusFilter}
              onChange={(val) => {
                setStatusFilter(val);
                setCurrentPage(1);
              }}
              style={{ width: 192 }}
              options={[
                { value: 'ALL', label: 'Tất cả trạng thái' },
                { value: 'ACTIVE', label: 'Đang hoạt động' },
                { value: 'BLOCKED', label: 'Đã khóa' },
              ]}
            />
          </Space>

          <Button
            icon={<ReloadOutlined />}
            onClick={handleResetFilters}
            style={{
              borderColor: '#E4DCD3',
              color: token.colorPrimary,
            }}
          >
            Đặt lại bộ lọc
          </Button>
        </div>
      </Card>

      {isError && (
        <Alert
          type={isForbiddenError(error) ? 'warning' : 'error'}
          showIcon
          style={{ border: '1px solid #E4DCD3' }}
          message={
            isForbiddenError(error)
              ? 'Bạn không có quyền xem mục này'
              : 'Không thể tải danh sách khách hàng'
          }
          description={
            isForbiddenError(error)
              ? 'Chức năng quản lý khách hàng chỉ dành cho tài khoản Quản trị viên (ADMIN).'
              : getCustomerApiErrorMessage(error, 'Đã xảy ra lỗi khi kết nối máy chủ.')
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

      <Card
        bordered
        style={{
          borderColor: '#E4DCD3',
          backgroundColor: '#FFFFFF',
          boxShadow: 'none',
          overflow: 'hidden',
        }}
        styles={{ body: { padding: 0 } }}
      >
        <Table<CustomerListItem>
          columns={columns}
          dataSource={customers}
          loading={isLoading || isFetching}
          scroll={{ x: 'max-content' }}
          rowKey={(record) => String(record.id)}
          onChange={handleTableChange}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  hasActiveFilter
                    ? 'Không tìm thấy khách hàng nào khớp với bộ lọc hiện tại'
                    : 'Chưa có tài khoản khách hàng nào trong hệ thống'
                }
              />
            ),
          }}
          pagination={{
            current: currentPage,
            pageSize,
            total: totalElements,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            showTotal: (total) => `Tổng cộng ${total} khách hàng`,
          }}
        />
      </Card>

      <CustomerDetailDrawer
        customerId={selectedCustomerId}
        open={isDrawerOpen}
        onClose={handleCloseDetailDrawer}
        currentAdminId={currentAdminId}
        isUpdatingStatus={isUpdatingSelectedCustomer}
        onToggleStatus={handleToggleStatus}
      />
    </div>
  );
};

export default CustomerListPage;
