import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  DatePicker,
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
  MoreOutlined,
  CloseCircleOutlined,
  SendOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import { formatCurrencyVND } from '@/lib/format';
import {
  formatDateTimeVN,
  getOrderApiErrorMessage,
  isForbiddenError,
} from '../api/order.api';
import {
  useAdminOrders,
  useUpdateAdminOrderStatus,
} from '../api/order.hooks';
import { OrderDetailDrawer } from '../components/OrderDetailDrawer';
import {
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TAG_CONFIG,
  type OrderAdminListItemResponse,
  type OrderStatus,
} from '../order.types';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;

type StatusFilterValue = 'ALL' | OrderStatus;

const DEFAULT_SORT = 'createdAt,desc';

export const OrderListPage: React.FC = () => {
  const { token } = theme.useToken();

  const [searchInput, setSearchInput] = useState<string>('');
  const [debouncedKeyword, setDebouncedKeyword] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<StatusFilterValue>('ALL');
  const [dateRange, setDateRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [pageSize, setPageSize] = useState<number>(20);
  const [sortParam, setSortParam] = useState<string>(DEFAULT_SORT);
  const [selectedOrderId, setSelectedOrderId] = useState<number | undefined>(undefined);
  const [isDrawerOpen, setIsDrawerOpen] = useState<boolean>(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedKeyword(searchInput.trim());
      setCurrentPage(1);
    }, 400);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  const startDateParam: string | undefined = dateRange?.[0]
    ? dateRange[0].format('YYYY-MM-DD')
    : undefined;
  const endDateParam: string | undefined = dateRange?.[1]
    ? dateRange[1].format('YYYY-MM-DD')
    : undefined;
  const statusParam: OrderStatus | undefined =
    statusFilter === 'ALL' ? undefined : statusFilter;

  const {
    data: ordersPage,
    isLoading,
    isFetching,
    isError,
    error,
    refetch,
  } = useAdminOrders({
    page: currentPage - 1,
    size: pageSize,
    keyword: debouncedKeyword || undefined,
    status: statusParam,
    startDate: startDateParam,
    endDate: endDateParam,
    sort: sortParam,
  });

  const updateStatusMutation = useUpdateAdminOrderStatus();

  const handleUpdateStatusWithConfirm = (
    orderId: number,
    orderCode: string,
    targetStatus: OrderStatus,
    actionTitle: string,
    actionDesc: string,
    isDanger = false
  ) => {
    Modal.confirm({
      title: actionTitle,
      icon: <ExclamationCircleOutlined style={{ color: isDanger ? '#FF4D4F' : '#FAAD14' }} />,
      content: actionDesc,
      okText: 'Xác nhận',
      cancelText: 'Đóng',
      okButtonProps: { danger: isDanger },
      onOk: async () => {
        try {
          await updateStatusMutation.mutateAsync({
            id: orderId,
            payload: { status: targetStatus },
          });
          message.success(
            `Đã chuyển đơn hàng #${orderCode} sang trạng thái "${ORDER_STATUS_LABELS[targetStatus]}"`
          );
        } catch (err) {
          message.error(
            getOrderApiErrorMessage(err, 'Không thể cập nhật trạng thái đơn hàng')
          );
        }
      },
    });
  };

  const handleOpenDetailDrawer = (orderId: number) => {
    setSelectedOrderId(orderId);
    setIsDrawerOpen(true);
  };

  const handleCloseDetailDrawer = () => {
    setIsDrawerOpen(false);
    setSelectedOrderId(undefined);
  };

  const handleResetFilters = () => {
    setSearchInput('');
    setDebouncedKeyword('');
    setStatusFilter('ALL');
    setDateRange(null);
    setSortParam(DEFAULT_SORT);
    setCurrentPage(1);
    refetch();
  };

  const handleTableChange = (
    pagination: TablePaginationConfig,
    _filters: Record<string, FilterValue | null>,
    sorter: SorterResult<OrderAdminListItemResponse> | SorterResult<OrderAdminListItemResponse>[]
  ) => {
    const nextPage = pagination.current ?? 1;
    const nextSize = pagination.pageSize ?? 20;

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

  const columns: ColumnsType<OrderAdminListItemResponse> = [
    {
      title: 'Mã đơn hàng',
      dataIndex: 'orderCode',
      key: 'orderCode',
      render: (orderCode: string, record) => (
        <div>
          <Button
            type="link"
            onClick={() => handleOpenDetailDrawer(record.id)}
            style={{ padding: 0, height: 'auto', fontWeight: 600, fontSize: 14 }}
          >
            #{orderCode}
          </Button>
          <div style={{ marginTop: 2 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              ID: #{record.id}
            </Text>
          </div>
        </div>
      ),
    },
    {
      title: 'Thời gian đặt',
      dataIndex: 'createdAt',
      key: 'createdAt',
      sorter: true,
      sortOrder:
        activeSortField === 'createdAt'
          ? activeSortDirection === 'asc'
            ? 'ascend'
            : 'descend'
          : null,
      width: 170,
      render: (val: string) => (
        <Text style={{ fontSize: 13, color: token.colorPrimary }}>
          {formatDateTimeVN(val)}
        </Text>
      ),
    },
    {
      title: 'Khách hàng',
      dataIndex: 'customerName',
      key: 'customerName',
      render: (name: string, record) => (
        <div>
          <Text strong style={{ fontSize: 13, color: token.colorPrimary }}>
            {name || 'Khách vãng lai'}
          </Text>
          <div style={{ marginTop: 2 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {record.customerPhone || '—'}
            </Text>
          </div>
        </div>
      ),
    },
    {
      title: 'Sản phẩm',
      dataIndex: 'itemsSummary',
      key: 'itemsSummary',
      render: (summary: string, record) => (
        <div>
          <Text style={{ fontSize: 13, color: token.colorPrimary }}>
            {summary || '—'}
          </Text>
          <div style={{ marginTop: 2 }}>
            <Tag style={{ fontSize: 11, borderRadius: 4, margin: 0 }}>
              {record.itemCount} sản phẩm
            </Tag>
          </div>
        </div>
      ),
    },
    {
      title: 'Tổng tiền',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      sorter: true,
      sortOrder:
        activeSortField === 'totalAmount'
          ? activeSortDirection === 'asc'
            ? 'ascend'
            : 'descend'
          : null,
      align: 'right',
      width: 140,
      render: (amount: number) => (
        <Text strong style={{ fontSize: 14, color: token.colorPrimary }}>
          {formatCurrencyVND(amount)}
        </Text>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (status: OrderStatus) => {
        const config = ORDER_STATUS_TAG_CONFIG[status] || {
          color: 'default',
          label: status,
        };
        return <Tag color={config.color}>{config.label}</Tag>;
      },
    },
    {
      title: 'Thao tác',
      key: 'actions',
      align: 'center',
      width: 90,
      render: (_, record) => {
        const isUpdatingThisRecord =
          updateStatusMutation.isPending &&
          updateStatusMutation.variables?.id === record.id;

        const actionMenuItems: MenuProps['items'] = [
          {
            key: 'detail',
            icon: <EyeOutlined />,
            label: 'Xem chi tiết',
            onClick: () => handleOpenDetailDrawer(record.id),
          },
          ...(record.status === 'PENDING'
            ? [
                {
                  key: 'confirm',
                  icon: <CheckCircleOutlined style={{ color: '#16a34a' }} />,
                  label: 'Xác nhận đơn hàng',
                  disabled: isUpdatingThisRecord,
                  onClick: () =>
                    handleUpdateStatusWithConfirm(
                      record.id,
                      record.orderCode,
                      'CONFIRMED',
                      'Xác nhận đơn hàng',
                      `Bạn có chắc chắn muốn xác nhận đơn hàng #${record.orderCode}?`
                    ),
                },
              ]
            : []),
          ...(record.status === 'CONFIRMED'
            ? [
                {
                  key: 'shipping',
                  icon: <SendOutlined style={{ color: '#0284c7' }} />,
                  label: 'Chuyển giao hàng',
                  disabled: isUpdatingThisRecord,
                  onClick: () =>
                    handleUpdateStatusWithConfirm(
                      record.id,
                      record.orderCode,
                      'SHIPPING',
                      'Bắt đầu giao hàng',
                      `Chuyển đơn hàng #${record.orderCode} sang trạng thái đang giao hàng?`
                    ),
                },
              ]
            : []),
          ...(record.status === 'SHIPPING'
            ? [
                {
                  key: 'complete',
                  icon: <CheckCircleOutlined style={{ color: '#16a34a' }} />,
                  label: 'Hoàn tất đơn hàng',
                  disabled: isUpdatingThisRecord,
                  onClick: () =>
                    handleUpdateStatusWithConfirm(
                      record.id,
                      record.orderCode,
                      'COMPLETED',
                      'Hoàn tất đơn hàng',
                      `Xác nhận đơn hàng #${record.orderCode} đã giao thành công và hoàn tất?`
                    ),
                },
              ]
            : []),
          ...(['PENDING', 'CONFIRMED', 'SHIPPING'].includes(record.status)
            ? [
                {
                  type: 'divider' as const,
                },
                {
                  key: 'cancel',
                  icon: <CloseCircleOutlined />,
                  label: 'Hủy đơn hàng',
                  danger: true,
                  disabled: isUpdatingThisRecord,
                  onClick: () =>
                    handleUpdateStatusWithConfirm(
                      record.id,
                      record.orderCode,
                      'CANCELLED',
                      'Xác nhận hủy đơn hàng',
                      `Bạn có chắc chắn muốn hủy đơn hàng #${record.orderCode}? Tồn kho của các sản phẩm sẽ được hoàn lại tự động.`,
                      true
                    ),
                },
              ]
            : []),
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
              loading={isUpdatingThisRecord}
              aria-label="Thao tác đơn hàng"
            />
          </Dropdown>
        );
      },
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* Header */}
      <div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: 16,
          }}
        >
          <div>
            <Title level={4} style={{ margin: 0, color: token.colorPrimary }}>
              Quản lý đơn hàng
            </Title>
            <Text type="secondary" style={{ fontSize: 13 }}>
              Theo dõi, xác nhận và xử lý đơn hàng trên toàn hệ thống
            </Text>
          </div>
          <Space>
            <Button
              icon={<ReloadOutlined spin={isFetching && !isLoading} />}
              onClick={() => refetch()}
              loading={isFetching && !isLoading}
              style={{ borderRadius: 6 }}
            >
              Làm mới
            </Button>
          </Space>
        </div>
      </div>

      {/* Bộ lọc đơn hàng */}
      <Card
        styles={{ body: { padding: 16 } }}
        style={{
          borderRadius: 6,
          border: '1px solid #E4DCD3',
        }}
      >
        <Space wrap size={12} style={{ width: '100%' }}>
          <Input
            placeholder="Tìm theo mã đơn, khách hàng, SĐT..."
            prefix={<SearchOutlined style={{ color: '#A8A29E' }} />}
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            allowClear
            style={{ width: 280, borderRadius: 6 }}
          />

          <Select<StatusFilterValue>
            value={statusFilter}
            onChange={(val) => {
              setStatusFilter(val);
              setCurrentPage(1);
            }}
            style={{ width: 180, borderRadius: 6 }}
            options={[
              { value: 'ALL', label: 'Tất cả trạng thái' },
              { value: 'PENDING', label: ORDER_STATUS_LABELS.PENDING },
              { value: 'CONFIRMED', label: ORDER_STATUS_LABELS.CONFIRMED },
              { value: 'SHIPPING', label: ORDER_STATUS_LABELS.SHIPPING },
              { value: 'COMPLETED', label: ORDER_STATUS_LABELS.COMPLETED },
              { value: 'CANCELLED', label: ORDER_STATUS_LABELS.CANCELLED },
              { value: 'REFUNDED', label: ORDER_STATUS_LABELS.REFUNDED },
            ]}
          />

          <RangePicker
            value={dateRange}
            onChange={(dates) => {
              setDateRange(dates as [Dayjs | null, Dayjs | null] | null);
              setCurrentPage(1);
            }}
            format="DD/MM/YYYY"
            placeholder={['Từ ngày', 'Đến ngày']}
            style={{ borderRadius: 6 }}
          />

          <Button onClick={handleResetFilters} style={{ borderRadius: 6 }}>
            Đặt lại
          </Button>
        </Space>
      </Card>

      {/* Thông báo lỗi nếu có */}
      {isError && (
        <Alert
          type="error"
          showIcon
          message="Không thể tải danh sách đơn hàng"
          description={
            isForbiddenError(error)
              ? 'Bạn không có quyền truy cập module đơn hàng. Vui lòng liên hệ quản trị viên.'
              : getOrderApiErrorMessage(error, 'Đã xảy ra lỗi khi kết nối tới máy chủ.')
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

      {/* Bảng dữ liệu đơn hàng */}
      <Card
        styles={{ body: { padding: 0 } }}
        style={{
          borderRadius: 6,
          border: '1px solid #E4DCD3',
          overflow: 'hidden',
        }}
      >
        <Table<OrderAdminListItemResponse>
          rowKey="id"
          columns={columns}
          dataSource={ordersPage?.content || []}
          loading={isLoading}
          onChange={handleTableChange}
          pagination={{
            current: currentPage,
            pageSize: pageSize,
            total: ordersPage?.totalElements || 0,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            showTotal: (total, range) =>
              `${range[0]}-${range[1]} của ${total} đơn hàng`,
          }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="Không tìm thấy đơn hàng nào"
              />
            ),
          }}
          scroll={{ x: 900 }}
        />
      </Card>

      {/* Drawer chi tiết đơn hàng */}
      <OrderDetailDrawer
        orderId={selectedOrderId}
        open={isDrawerOpen}
        onClose={handleCloseDetailDrawer}
      />
    </div>
  );
};
