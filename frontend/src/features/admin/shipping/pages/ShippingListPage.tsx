import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Empty,
  Input,
  Select,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
  theme,
  Dropdown,
  Modal,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { MenuProps } from 'antd';
import {
  CarOutlined,
  CheckCircleOutlined,
  EyeOutlined,
  FilterOutlined,
  ReloadOutlined,
  SearchOutlined,
  MoreOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import { formatCurrencyVND } from '@/lib/format';
import {
  formatDateTimeVN,
  getShippingApiErrorMessage,
  isForbiddenError,
} from '../api/shipping.api';
import {
  useAdminShippingOrders,
  useAdminShippingSummary,
  useUpdateAdminShippingStatus,
} from '../api/shipping.hooks';
import { ShippingDetailDrawer } from '../components/ShippingDetailDrawer';
import { ShippingSummaryCards } from '../components/ShippingSummaryCards';
import {
  NEXT_ALLOWED_SHIPPING_ACTIONS,
  SHIPPING_STATUS_LABELS,
  SHIPPING_STATUS_TAG_CONFIG,
  type ShippingListItemResponse,
  type ShippingStatus,
} from '../shipping.types';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;

type StatusFilterValue = 'ALL' | ShippingStatus;

const DEFAULT_SORT = 'createdAt,desc';

export const ShippingListPage: React.FC = () => {
  const { token } = theme.useToken();

  const [searchInput, setSearchInput] = useState<string>('');
  const [debouncedKeyword, setDebouncedKeyword] = useState<string>('');
  const [provinceInput, setProvinceInput] = useState<string>('');
  const [debouncedProvince, setDebouncedProvince] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<StatusFilterValue>('ALL');
  const [dateRange, setDateRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [pageSize, setPageSize] = useState<number>(20);
  const [sortParam] = useState<string>(DEFAULT_SORT);
  const [selectedOrderId, setSelectedOrderId] = useState<number | undefined>(undefined);
  const [isDrawerOpen, setIsDrawerOpen] = useState<boolean>(false);

  // Debounce tìm kiếm từ khóa
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedKeyword(searchInput.trim());
      setCurrentPage(1);
    }, 400);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  // Debounce lọc tỉnh / thành phố
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedProvince(provinceInput.trim());
      setCurrentPage(1);
    }, 400);
    return () => window.clearTimeout(timer);
  }, [provinceInput]);

  const startDateParam: string | undefined = dateRange?.[0]
    ? dateRange[0].format('YYYY-MM-DD')
    : undefined;
  const endDateParam: string | undefined = dateRange?.[1]
    ? dateRange[1].format('YYYY-MM-DD')
    : undefined;
  const statusParam: ShippingStatus | undefined =
    statusFilter === 'ALL' ? undefined : statusFilter;

  // Query dữ liệu danh sách vận chuyển
  const {
    data: shippingPage,
    isLoading,
    isFetching,
    isError,
    error,
    refetch: refetchList,
  } = useAdminShippingOrders({
    page: currentPage - 1,
    size: pageSize,
    keyword: debouncedKeyword || undefined,
    province: debouncedProvince || undefined,
    status: statusParam,
    startDate: startDateParam,
    endDate: endDateParam,
    sort: sortParam,
  });

  // Query dữ liệu KPI tổng quan
  const {
    data: summaryData,
    isLoading: isSummaryLoading,
    refetch: refetchSummary,
  } = useAdminShippingSummary();

  const updateStatusMutation = useUpdateAdminShippingStatus();

  const handleRefreshAll = () => {
    refetchList();
    refetchSummary();
  };

  const handleResetFilters = () => {
    setSearchInput('');
    setDebouncedKeyword('');
    setProvinceInput('');
    setDebouncedProvince('');
    setStatusFilter('ALL');
    setDateRange(null);
    setCurrentPage(1);
  };

  const handleTableChange = (pagination: TablePaginationConfig) => {
    if (pagination.current && pagination.current !== currentPage) {
      setCurrentPage(pagination.current);
    }
    if (pagination.pageSize && pagination.pageSize !== pageSize) {
      setPageSize(pagination.pageSize);
      setCurrentPage(1);
    }
  };


  const handleShippingStatusWithConfirm = (
    orderId: number,
    orderCode: string,
    targetStatus: ShippingStatus,
    confirmTitle: string,
    danger = false
  ) => {
    Modal.confirm({
      title: confirmTitle,
      icon: <ExclamationCircleOutlined style={{ color: danger ? '#FF4D4F' : '#FAAD14' }} />,
      content: `Thao tác trên kiện hàng đơn #${orderCode}.`,
      okText: 'Xác nhận',
      cancelText: 'Hủy',
      okButtonProps: { danger },
      onOk: async () => {
        try {
          await updateStatusMutation.mutateAsync({
            orderId,
            payload: { status: targetStatus },
          });
          message.success(
            `Đã cập nhật trạng thái đơn sang "${SHIPPING_STATUS_LABELS[targetStatus]}"`
          );
        } catch (err) {
          const errMsg = getShippingApiErrorMessage(
            err,
            'Không thể cập nhật trạng thái vận chuyển'
          );
          message.error(errMsg);
        }
      },
    });
  };

  const handleOpenDetail = (orderId: number) => {
    setSelectedOrderId(orderId);
    setIsDrawerOpen(true);
  };

  const columns: ColumnsType<ShippingListItemResponse> = [
    {
      title: 'Mã đơn',
      dataIndex: 'orderCode',
      key: 'orderCode',
      width: 120,
      render: (code: string, record) => (
        <Button
          type="link"
          style={{ padding: 0, fontWeight: 600 }}
          onClick={() => handleOpenDetail(record.orderId)}
        >
          {code}
        </Button>
      ),
    },
    {
      title: 'Người nhận hàng',
      key: 'receiver',
      width: 180,
      render: (_, record) => (
        <div>
          <Text strong style={{ fontSize: 13, display: 'block' }}>
            {record.receiverName}
          </Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.receiverPhone}
          </Text>
        </div>
      ),
    },
    {
      title: 'Địa chỉ giao hàng',
      dataIndex: 'fullAddress',
      key: 'fullAddress',
      ellipsis: true,
      render: (addr: string) => (
        <Tooltip title={addr}>
          <Text style={{ fontSize: 13 }}>{addr}</Text>
        </Tooltip>
      ),
    },
    {
      title: 'Tỉnh / TP',
      dataIndex: 'province',
      key: 'province',
      width: 140,
      render: (prov: string) => <Tag>{prov}</Tag>,
    },
    {
      title: 'Kiện hàng',
      key: 'items',
      width: 220,
      render: (_, record) => (
        <div>
          <Text style={{ fontSize: 13, display: 'block' }} ellipsis>
            {record.itemsSummary}
          </Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Tổng số: <Text strong>{record.itemCount}</Text> sản phẩm
          </Text>
        </div>
      ),
    },
    {
      title: 'Thu hộ COD',
      key: 'codAmount',
      width: 150,
      align: 'right',
      render: (_, record) => {
        const isCod = record.paymentMethod === 'COD';
        return (
          <div style={{ textAlign: 'right' }}>
            <Text
              strong
              style={{
                fontSize: 13,
                color: isCod ? token.colorWarning : token.colorTextSecondary,
              }}
            >
              {formatCurrencyVND(record.codAmount)}
            </Text>
            <div>
              <Tag
                color={isCod ? 'orange' : 'blue'}
                style={{ fontSize: 11, padding: '0 4px', margin: 0 }}
              >
                {isCod ? 'COD' : 'Đã thanh toán'}
              </Tag>
            </div>
          </div>
        );
      },
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      align: 'center',
      render: (status: ShippingStatus) => {
        const config = SHIPPING_STATUS_TAG_CONFIG[status] || {
          color: 'default',
          label: status,
        };
        return <Tag color={config.color}>{config.label}</Tag>;
      },
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (val: string) => (
        <Text style={{ fontSize: 12 }}>{formatDateTimeVN(val)}</Text>
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 90,
      fixed: 'right',
      align: 'center',
      render: (_, record) => {
        const isUpdatingThisRecord =
          updateStatusMutation.isPending &&
          updateStatusMutation.variables?.orderId === record.orderId;
        const actions = NEXT_ALLOWED_SHIPPING_ACTIONS[record.status] || [];

        const actionMenuItems: MenuProps['items'] = [
          {
            key: 'detail',
            icon: <EyeOutlined />,
            label: 'Xem chi tiết',
            onClick: () => handleOpenDetail(record.orderId),
          },
          ...(actions.length > 0 ? [{ type: 'divider' as const }] : []),
          ...actions.map((act) => ({
            key: act.targetStatus,
            icon:
              act.targetStatus === 'SHIPPING' ? (
                <CarOutlined style={{ color: '#0284c7' }} />
              ) : act.targetStatus === 'COMPLETED' ? (
                <CheckCircleOutlined style={{ color: '#16a34a' }} />
              ) : (
                <CloseCircleOutlined />
              ),
            label: act.actionText,
            danger: act.danger,
            disabled: isUpdatingThisRecord,
            onClick: () =>
              handleShippingStatusWithConfirm(
                record.orderId,
                record.orderCode,
                act.targetStatus,
                act.confirmTitle,
                act.danger
              ),
          })),
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
              aria-label="Thao tác vận chuyển"
            />
          </Dropdown>
        );
      },
    },
  ];

  return (
    <div style={{ padding: '0 0 24px 0' }}>
      {/* Header trang */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <div>
          <Title level={4} style={{ margin: 0, fontWeight: 600 }}>
            Quản lý vận chuyển & giao nhận
          </Title>
          <Text type="secondary" style={{ fontSize: 13 }}>
            Điều phối kiện hàng, theo dõi lộ trình và xác nhận giao hàng
          </Text>
        </div>
        <Button
          icon={<ReloadOutlined />}
          onClick={handleRefreshAll}
          loading={isLoading || isFetching || isSummaryLoading}
        >
          Làm mới
        </Button>
      </div>

      {/* Thẻ KPI tổng quan */}
      <ShippingSummaryCards
        summary={summaryData}
        isLoading={isSummaryLoading}
        selectedStatus={statusFilter}
        onSelectStatus={(st) => {
          setStatusFilter(st);
          setCurrentPage(1);
        }}
      />

      {/* Card danh sách và bộ lọc */}
      <Card
        bordered
        style={{
          borderRadius: token.borderRadius,
          borderColor: token.colorBorderSecondary,
        }}
        bodyStyle={{ padding: 16 }}
      >
        {/* Bộ lọc tìm kiếm */}
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: 12,
            marginBottom: 16,
            alignItems: 'center',
          }}
        >
          <Input
            placeholder="Tìm theo mã đơn (ORD-xxx), người nhận, SĐT..."
            prefix={<SearchOutlined style={{ color: token.colorTextQuaternary }} />}
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            style={{ width: 280 }}
            allowClear
          />

          <Select<StatusFilterValue>
            value={statusFilter}
            onChange={(val) => {
              setStatusFilter(val);
              setCurrentPage(1);
            }}
            style={{ width: 190 }}
            options={[
              { label: 'Tất cả trạng thái', value: 'ALL' },
              { label: 'Chờ giao hàng (CONFIRMED)', value: 'CONFIRMED' },
              { label: 'Đang giao hàng (SHIPPING)', value: 'SHIPPING' },
              { label: 'Đã giao thành công (COMPLETED)', value: 'COMPLETED' },
              { label: 'Đã hủy giao (CANCELLED)', value: 'CANCELLED' },
            ]}
          />

          <Input
            placeholder="Lọc Tỉnh / Thành phố..."
            prefix={<FilterOutlined style={{ color: token.colorTextQuaternary }} />}
            value={provinceInput}
            onChange={(e) => setProvinceInput(e.target.value)}
            style={{ width: 180 }}
            allowClear
          />

          <RangePicker
            value={dateRange}
            onChange={(dates) => {
              setDateRange(dates as [Dayjs | null, Dayjs | null] | null);
              setCurrentPage(1);
            }}
            placeholder={['Từ ngày', 'Đến ngày']}
            format="DD/MM/YYYY"
            style={{ width: 250 }}
          />

          {(searchInput || provinceInput || statusFilter !== 'ALL' || dateRange) && (
            <Button onClick={handleResetFilters}>
              Đặt lại bộ lọc
            </Button>
          )}
        </div>

        {/* Thông báo lỗi nếu có */}
        {isError && (
          <Alert
            type="error"
            showIcon
            message="Không thể tải danh sách vận chuyển"
            description={
              isForbiddenError(error)
                ? 'Bạn không có quyền truy cập dữ liệu vận chuyển'
                : getShippingApiErrorMessage(error, 'Đã xảy ra lỗi khi lấy danh sách từ máy chủ')
            }
            style={{ marginBottom: 16 }}
            action={
              <Button size="small" type="primary" onClick={handleRefreshAll}>
                Thử lại
              </Button>
            }
          />
        )}

        {/* Bảng dữ liệu */}
        <Table<ShippingListItemResponse>
          rowKey="orderId"
          columns={columns}
          dataSource={shippingPage?.content || []}
          loading={isLoading || isFetching}
          pagination={{
            current: currentPage,
            pageSize: pageSize,
            total: shippingPage?.totalElements || 0,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            showTotal: (total, range) =>
              `${range[0]}-${range[1]} trong tổng số ${total} kiện hàng`,
          }}
          onChange={handleTableChange}
          scroll={{ x: 1200 }}
          size="middle"
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="Không tìm thấy đơn hàng vận chuyển nào phù hợp"
              />
            ),
          }}
        />
      </Card>

      {/* Drawer chi tiết kiện hàng */}
      <ShippingDetailDrawer
        orderId={selectedOrderId}
        open={isDrawerOpen}
        onClose={() => {
          setIsDrawerOpen(false);
          setSelectedOrderId(undefined);
        }}
      />
    </div>
  );
};
