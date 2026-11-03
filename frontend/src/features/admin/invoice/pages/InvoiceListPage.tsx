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
  theme,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { FilterValue, SorterResult } from 'antd/es/table/interface';
import {
  EyeOutlined,
  FileTextOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import { formatCurrencyVND } from '@/lib/format';
import {
  DOCUMENT_STATUS_CONFIG,
  INVOICE_TYPE_CONFIG,
  type DocumentStatus,
  type InvoiceSummaryResponse,
  type InvoiceType,
} from '../invoice.types';
import { getInvoiceApiErrorMessage } from '../api/invoice.api';
import { useAdminInvoices } from '../api/invoice.hooks';
import { InvoiceDetailDrawer } from '../components/InvoiceDetailDrawer';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;

type StatusFilterValue = 'ALL' | DocumentStatus;
type TypeFilterValue = 'ALL' | InvoiceType;
type DemoFilterValue = 'ALL' | 'DEMO' | 'OFFICIAL';

const DEFAULT_SORT = 'createdAt,desc';

export const InvoiceListPage: React.FC = () => {
  const { token } = theme.useToken();

  const [searchInput, setSearchInput] = useState<string>('');
  const [debouncedKeyword, setDebouncedKeyword] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<StatusFilterValue>('ALL');
  const [typeFilter, setTypeFilter] = useState<TypeFilterValue>('ALL');
  const [demoFilter, setDemoFilter] = useState<DemoFilterValue>('ALL');
  const [dateRange, setDateRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [pageSize, setPageSize] = useState<number>(20);
  const [sortParam, setSortParam] = useState<string>(DEFAULT_SORT);

  const [selectedInvoiceId, setSelectedInvoiceId] = useState<number | undefined>(undefined);
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

  const statusParam: DocumentStatus | undefined =
    statusFilter === 'ALL' ? undefined : statusFilter;
  const typeParam: InvoiceType | undefined =
    typeFilter === 'ALL' ? undefined : typeFilter;
  const isDemoParam: boolean | undefined =
    demoFilter === 'ALL' ? undefined : demoFilter === 'DEMO';

  const {
    data: invoicesPage,
    isLoading,
    isFetching,
    isError,
    error,
    refetch,
  } = useAdminInvoices({
    page: currentPage - 1,
    size: pageSize,
    keyword: debouncedKeyword || undefined,
    documentStatus: statusParam,
    invoiceType: typeParam,
    isDemo: isDemoParam,
    startDate: startDateParam,
    endDate: endDateParam,
    sort: sortParam,
  });

  const handleTableChange = (
    pagination: TablePaginationConfig,
    _filters: Record<string, FilterValue | null>,
    sorter: SorterResult<InvoiceSummaryResponse> | SorterResult<InvoiceSummaryResponse>[]
  ) => {
    if (pagination.current && pagination.current !== currentPage) {
      setCurrentPage(pagination.current);
    }
    if (pagination.pageSize && pagination.pageSize !== pageSize) {
      setPageSize(pagination.pageSize);
      setCurrentPage(1);
    }

    if (!Array.isArray(sorter) && sorter.field) {
      const field = String(sorter.field);
      const order = sorter.order === 'ascend' ? 'asc' : sorter.order === 'descend' ? 'desc' : undefined;
      if (order) {
        setSortParam(`${field},${order}`);
      } else {
        setSortParam(DEFAULT_SORT);
      }
    }
  };

  const handleOpenDetail = (invoiceId: number) => {
    setSelectedInvoiceId(invoiceId);
    setIsDrawerOpen(true);
  };

  const handleCloseDetail = () => {
    setIsDrawerOpen(false);
    setSelectedInvoiceId(undefined);
  };

  const handleResetFilters = () => {
    setSearchInput('');
    setDebouncedKeyword('');
    setStatusFilter('ALL');
    setTypeFilter('ALL');
    setDemoFilter('ALL');
    setDateRange(null);
    setCurrentPage(1);
    setSortParam(DEFAULT_SORT);
  };

  const columns: ColumnsType<InvoiceSummaryResponse> = [
    {
      title: 'Mã HĐ',
      dataIndex: 'id',
      key: 'id',
      width: 90,
      sorter: true,
      render: (id: number) => (
        <Text strong style={{ fontFamily: 'monospace' }}>
          #{id}
        </Text>
      ),
    },
    {
      title: 'Ký hiệu & Số hóa đơn',
      key: 'invoiceNumber',
      width: 200,
      render: (_, record) => {
        const hasNumber = Boolean(record.invoiceNumber);
        const isDemo = record.isDemo || record.demoMode;
        return (
          <Space direction="vertical" size={2}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <Text strong style={{ fontFamily: 'monospace', fontSize: 13 }}>
                {hasNumber
                  ? `${record.invoiceSeries || ''} - ${record.invoiceNumber}`
                  : 'Chưa cấp số'}
              </Text>
              {isDemo && (
                <Tag color="orange" style={{ margin: 0, fontSize: 10, lineHeight: '16px' }}>
                  Demo
                </Tag>
              )}
            </div>
            {record.taxAuthorityCode && (
              <Text type="secondary" style={{ fontSize: 11, fontFamily: 'monospace' }}>
                Mã CQT: {record.taxAuthorityCode}
              </Text>
            )}
          </Space>
        );
      },
    },
    {
      title: 'Đơn hàng',
      dataIndex: 'orderId',
      key: 'orderId',
      width: 120,
      render: (orderId?: number, record?: InvoiceSummaryResponse) => {
        if (!orderId) return <Text type="secondary">—</Text>;
        return (
          <Text style={{ fontFamily: 'monospace' }}>
            #{record?.orderNumber || orderId}
          </Text>
        );
      },
    },
    {
      title: 'Loại hóa đơn',
      dataIndex: 'invoiceType',
      key: 'invoiceType',
      width: 140,
      render: (type: InvoiceType) => {
        const meta = INVOICE_TYPE_CONFIG[type] || { label: type, color: 'default' };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: 'Người mua hàng',
      dataIndex: 'buyerName',
      key: 'buyerName',
      ellipsis: true,
      render: (buyerName?: string) => (
        <Text>{buyerName?.trim() || <span style={{ color: token.colorTextQuaternary }}>Không tên</span>}</Text>
      ),
    },
    {
      title: 'Ngày lập',
      dataIndex: 'taxPointDate',
      key: 'taxPointDate',
      width: 120,
      sorter: true,
      render: (dateStr: string) => <Text>{dateStr || '—'}</Text>,
    },
    {
      title: 'Tổng thanh toán',
      dataIndex: 'totalAfterTax',
      key: 'totalAfterTax',
      width: 140,
      align: 'right',
      sorter: true,
      render: (total: number) => (
        <Text strong style={{ color: token.colorTextHeading }}>
          {formatCurrencyVND(total || 0)}
        </Text>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'documentStatus',
      key: 'documentStatus',
      width: 140,
      render: (status: DocumentStatus) => {
        const meta = DOCUMENT_STATUS_CONFIG[status] || {
          label: status,
          color: 'default',
        };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: 'Thao tác',
      key: 'action',
      width: 90,
      fixed: 'right',
      align: 'center',
      render: (_, record) => (
        <Button
          type="text"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => handleOpenDetail(record.id)}
          title="Xem chi tiết hóa đơn"
        />
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Title level={4} style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 8 }}>
            <FileTextOutlined /> Quản lý Hóa đơn điện tử
          </Title>
        </div>
        <Button
          icon={<ReloadOutlined spin={isFetching} />}
          onClick={() => refetch()}
          loading={isFetching}
        >
          Làm mới
        </Button>
      </div>

      {/* Filter Toolbar */}
      <Card
        size="small"
        bordered
        style={{
          borderRadius: 6,
          boxShadow: 'none',
          borderColor: token.colorBorderSecondary,
        }}
      >
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: 12,
            alignItems: 'center',
          }}
        >
          <Input
            placeholder="Tìm theo số HĐ, ký hiệu, khách hàng..."
            prefix={<SearchOutlined style={{ color: token.colorTextQuaternary }} />}
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            allowClear
            style={{ width: 280 }}
          />

          <Select<StatusFilterValue>
            value={statusFilter}
            onChange={(val) => {
              setStatusFilter(val);
              setCurrentPage(1);
            }}
            style={{ width: 160 }}
            options={[
              { label: 'Tất cả trạng thái', value: 'ALL' },
              { label: 'Đã phát hành', value: 'ISSUED' },
              { label: 'Bản nháp', value: 'DRAFT' },
              { label: 'Lỗi phát hành', value: 'ISSUE_FAILED' },
              { label: 'Chờ phát hành', value: 'PENDING_ISSUE' },
              { label: 'Đang phát hành', value: 'ISSUING' },
            ]}
          />

          <Select<TypeFilterValue>
            value={typeFilter}
            onChange={(val) => {
              setTypeFilter(val);
              setCurrentPage(1);
            }}
            style={{ width: 170 }}
            options={[
              { label: 'Tất cả loại HĐ', value: 'ALL' },
              { label: 'Hóa đơn gốc', value: 'ORIGINAL' },
              { label: 'Hóa đơn thay thế', value: 'REPLACEMENT' },
              { label: 'Hóa đơn điều chỉnh', value: 'ADJUSTMENT' },
            ]}
          />

          <Select<DemoFilterValue>
            value={demoFilter}
            onChange={(val) => {
              setDemoFilter(val);
              setCurrentPage(1);
            }}
            style={{ width: 150 }}
            options={[
              { label: 'Tất cả môi trường', value: 'ALL' },
              { label: 'Chế độ Demo', value: 'DEMO' },
              { label: 'Chính thức', value: 'OFFICIAL' },
            ]}
          />

          <RangePicker
            value={dateRange}
            onChange={(dates) => {
              setDateRange(dates as [Dayjs | null, Dayjs | null] | null);
              setCurrentPage(1);
            }}
            placeholder={['Từ ngày', 'Đến ngày']}
            format="DD/MM/YYYY"
            style={{ width: 240 }}
          />

          {(searchInput ||
            statusFilter !== 'ALL' ||
            typeFilter !== 'ALL' ||
            demoFilter !== 'ALL' ||
            dateRange) && (
            <Button type="link" onClick={handleResetFilters} style={{ padding: 0 }}>
              Đặt lại bộ lọc
            </Button>
          )}
        </div>
      </Card>

      {/* Error state */}
      {isError && (
        <Alert
          type="error"
          showIcon
          message="Không thể tải danh sách hóa đơn điện tử"
          description={getInvoiceApiErrorMessage(error, 'Vui lòng kiểm tra lại kết nối mạng hoặc thử lại sau.')}
          action={
            <Button size="small" danger onClick={() => refetch()}>
              Thử lại
            </Button>
          }
          style={{ borderRadius: 6 }}
        />
      )}

      {/* Main Table */}
      <Card
        bordered
        style={{
          borderRadius: 6,
          boxShadow: 'none',
          borderColor: token.colorBorderSecondary,
        }}
        bodyStyle={{ padding: 0 }}
      >
        <Table<InvoiceSummaryResponse>
          rowKey="id"
          columns={columns}
          dataSource={invoicesPage?.content || []}
          loading={isLoading}
          onChange={handleTableChange}
          scroll={{ x: 1050 }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="Không tìm thấy hóa đơn điện tử nào"
              />
            ),
          }}
          pagination={{
            current: currentPage,
            pageSize: pageSize,
            total: invoicesPage?.totalElements || 0,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50'],
            showTotal: (total, range) => `${range[0]}-${range[1]} trên tổng số ${total} hóa đơn`,
          }}
        />
      </Card>

      {/* Invoice Detail Drawer */}
      <InvoiceDetailDrawer
        invoiceId={selectedInvoiceId}
        open={isDrawerOpen}
        onClose={handleCloseDetail}
      />
    </div>
  );
};
