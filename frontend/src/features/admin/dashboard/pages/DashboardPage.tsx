import React, { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Row,
  Col,
  Card,
  Statistic,
  Table,
  Tag,
  Button,
  Select,
  Segmented,
  Progress,
  Space,
  Typography,
  Tooltip,
  Skeleton,
  Empty,
  Alert,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowUpOutlined,
  ArrowDownOutlined,
  DownloadOutlined,
  AlertOutlined,
  ShoppingOutlined,
  UserOutlined,
  DollarOutlined,
  ArrowRightOutlined,
  InfoCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartsTooltip,
} from 'recharts';
import { formatCurrencyVND } from '@/lib/format';
import {
  useDashboardSummary,
  useRevenueTrend,
  useCustomerSegments,
  useLowStockVariants,
  getApiErrorMessage,
  type DashboardPeriod,
  type RevenueTrendPeriod,
} from '../api';
import { useAdminOrders } from '@/features/admin/order/api/order.hooks';
import { formatDateTimeVN } from '@/features/admin/order/api/order.api';
import {
  ORDER_STATUS_TAG_CONFIG,
  type OrderAdminListItemResponse,
  type OrderStatus,
} from '@/features/admin/order/order.types';
import { OrderDetailDrawer } from '@/features/admin/order/components/OrderDetailDrawer';

const { Title, Text, Paragraph } = Typography;

export const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const [period, setPeriod] = useState<DashboardPeriod>('today');
  const [chartView, setChartView] = useState<RevenueTrendPeriod>('30days');
  const [selectedOrderId, setSelectedOrderId] = useState<number | null>(null);

  // 1. Summary API (KPIs)
  const {
    data: summary,
    isLoading: isSummaryLoading,
    isError: isSummaryError,
    refetch: refetchSummary,
  } = useDashboardSummary(period);

  // 2. Revenue Trend API
  const {
    data: trendData,
    isLoading: isTrendLoading,
    isError: isTrendError,
    error: trendError,
    refetch: refetchTrend,
  } = useRevenueTrend(chartView);

  // 3. Customer Segments API
  const {
    data: customerSegments,
    isLoading: isSegmentsLoading,
    isError: isSegmentsError,
    error: segmentsError,
    refetch: refetchSegments,
  } = useCustomerSegments();

  // 4. Recent Orders API (5 latest orders)
  const {
    data: ordersPage,
    isLoading: isOrdersLoading,
    isError: isOrdersError,
    error: ordersError,
    refetch: refetchOrders,
  } = useAdminOrders({ page: 0, size: 5, sort: 'createdAt,desc' });

  // 5. Low-stock Variants API (Phase 1)
  const {
    data: lowStockData,
    isLoading: isLowStockLoading,
    isError: isLowStockError,
    error: lowStockError,
    refetch: refetchLowStock,
  } = useLowStockVariants({ page: 0, size: 20 });

  // Calculated totals for Revenue Trend Card
  const { totalChartRevenue, totalChartOrders } = useMemo(() => {
    if (!trendData || trendData.length === 0) {
      return { totalChartRevenue: 0, totalChartOrders: 0 };
    }
    return trendData.reduce(
      (acc, item) => ({
        totalChartRevenue: acc.totalChartRevenue + (item.revenue ?? 0),
        totalChartOrders: acc.totalChartOrders + (item.orders ?? 0),
      }),
      { totalChartRevenue: 0, totalChartOrders: 0 }
    );
  }, [trendData]);

  // Total customers count for Customer Segments Card
  const totalSegmentCustomers = useMemo(() => {
    if (!customerSegments || customerSegments.length === 0) {
      return summary?.totalCustomersCount ?? 0;
    }
    return customerSegments.reduce((sum, item) => sum + (item.count ?? 0), 0);
  }, [customerSegments, summary?.totalCustomersCount]);

  // Ant Design Table Columns for Recent Orders
  const orderColumns: ColumnsType<OrderAdminListItemResponse> = [
    {
      title: 'Mã đơn',
      dataIndex: 'orderCode',
      key: 'orderCode',
      render: (code: string, record) => (
        <Button
          type="link"
          style={{ padding: 0, fontWeight: 600, color: '#18181B' }}
          onClick={() => setSelectedOrderId(record.id)}
        >
          {code || `#${record.id}`}
        </Button>
      ),
    },
    {
      title: 'Khách hàng',
      key: 'customer',
      render: (_, record) => (
        <div>
          <div style={{ fontWeight: 500 }}>{record.customerName}</div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.customerPhone}
          </Text>
        </div>
      ),
    },
    {
      title: 'Thời gian',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (time: string) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {formatDateTimeVN(time)}
        </Text>
      ),
    },
    {
      title: 'Chi tiết sản phẩm',
      key: 'itemsSummary',
      render: (_, record) => (
        <div>
          <div
            style={{
              maxWidth: 220,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {record.itemsSummary || 'Chi tiết đơn hàng'}
          </div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.itemCount} sản phẩm
          </Text>
        </div>
      ),
    },
    {
      title: 'Tổng tiền',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      align: 'right',
      render: (amount: number) => (
        <Text strong>{formatCurrencyVND(amount)}</Text>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      align: 'center',
      render: (status: OrderStatus) => {
        const config = ORDER_STATUS_TAG_CONFIG[status] ?? {
          color: 'default',
          label: status,
        };
        return <Tag color={config.color}>{config.label}</Tag>;
      },
    },
    {
      title: 'Thao tác',
      key: 'action',
      align: 'right',
      render: (_, record) => (
        <Button
          size="small"
          onClick={() => setSelectedOrderId(record.id)}
        >
          Chi tiết
        </Button>
      ),
    },
  ];

  const getPeriodRevenueTitle = () => {
    switch (period) {
      case 'today':
        return 'Doanh thu hôm nay';
      case '7days':
        return 'Doanh thu 7 ngày qua';
      case '30days':
        return 'Doanh thu 30 ngày qua';
      case 'quarter':
        return 'Doanh thu quý này';
      default:
        return 'Doanh thu';
    }
  };

  const getChartViewTitle = () => {
    switch (chartView) {
      case '7days':
        return 'Doanh thu & Đơn hàng 7 ngày qua';
      case '30days':
        return 'Doanh thu & Đơn hàng 30 ngày qua';
      case 'year':
        return 'Doanh thu & Đơn hàng cả năm';
      default:
        return 'Biểu đồ doanh thu';
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* 1. Page Header */}
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 16,
        }}
      >
        <div>
          <Title level={3} style={{ margin: 0, fontWeight: 700 }}>
            Tổng quan vận hành
          </Title>
          <Paragraph type="secondary" style={{ margin: '4px 0 0 0', fontSize: 13 }}>
            Thống kê doanh thu, đơn hàng và khách hàng theo thời gian thực
          </Paragraph>
        </div>

        <Space size="middle">
          <Select
            value={period}
            onChange={(val) => setPeriod(val as DashboardPeriod)}
            style={{ width: 140 }}
            options={[
              { value: 'today', label: 'Hôm nay' },
              { value: '7days', label: '7 ngày qua' },
              { value: '30days', label: '30 ngày qua' },
              { value: 'quarter', label: 'Quý này' },
            ]}
          />
          <Tooltip title="Tính năng xuất báo cáo sẽ hỗ trợ ở phiên bản tiếp theo">
            <Button icon={<DownloadOutlined />} disabled>
              Xuất báo cáo CSV
            </Button>
          </Tooltip>
        </Space>
      </div>

      {/* 2. KPI Cards Row */}
      <Row gutter={[16, 16]}>
        {/* KPI 1: Doanh thu (Real Data) */}
        <Col xs={24} sm={12} lg={6}>
          <Card bordered style={{ height: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <Text type="secondary" style={{ fontSize: 13, fontWeight: 500 }}>
                {getPeriodRevenueTitle()}
              </Text>
              <span style={{ fontSize: 16, color: '#71717A' }}><DollarOutlined /></span>
            </div>
            {isSummaryLoading ? (
              <div style={{ padding: '8px 0' }}>
                <Skeleton active paragraph={{ rows: 2 }} title={{ width: 120 }} />
              </div>
            ) : isSummaryError ? (
              <div style={{ padding: '8px 0' }}>
                <Text type="danger" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>
                  Lỗi tải dữ liệu doanh thu
                </Text>
                <Button size="small" type="link" icon={<ReloadOutlined />} onClick={() => refetchSummary()} style={{ padding: 0 }}>
                  Thử lại
                </Button>
              </div>
            ) : (
              <>
                <div style={{ marginTop: 8, marginBottom: 4 }}>
                  <Statistic
                    value={summary?.revenueTotal !== undefined ? formatCurrencyVND(summary.revenueTotal) : '0 ₫'}
                    valueStyle={{ fontWeight: 700, fontSize: 24, color: '#09090B' }}
                  />
                </div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 12 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Chỉ tính đơn hoàn thành
                  </Text>
                  {summary?.revenueChangeRate !== undefined && (
                    <Tag
                      color={summary.revenueChangeRate >= 0 ? 'success' : 'error'}
                      style={{ margin: 0, fontSize: 11, fontWeight: 600 }}
                    >
                      {summary.revenueChangeRate >= 0 ? (
                        <ArrowUpOutlined style={{ marginRight: 2 }} />
                      ) : (
                        <ArrowDownOutlined style={{ marginRight: 2 }} />
                      )}
                      {summary.revenueChangeRate >= 0
                        ? `+${summary.revenueChangeRate}%`
                        : `${summary.revenueChangeRate}%`}
                    </Tag>
                  )}
                </div>
              </>
            )}
          </Card>
        </Col>

        {/* KPI 2: Đơn hàng mới (Real Data) */}
        <Col xs={24} sm={12} lg={6}>
          <Card bordered style={{ height: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <Text type="secondary" style={{ fontSize: 13, fontWeight: 500 }}>
                Đơn hàng mới
              </Text>
              <span style={{ fontSize: 16, color: '#71717A' }}><ShoppingOutlined /></span>
            </div>
            {isSummaryLoading ? (
              <div style={{ padding: '8px 0' }}>
                <Skeleton active paragraph={{ rows: 2 }} title={{ width: 120 }} />
              </div>
            ) : isSummaryError ? (
              <div style={{ padding: '8px 0' }}>
                <Text type="danger" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>
                  Lỗi tải dữ liệu đơn hàng
                </Text>
                <Button size="small" type="link" icon={<ReloadOutlined />} onClick={() => refetchSummary()} style={{ padding: 0 }}>
                  Thử lại
                </Button>
              </div>
            ) : (
              <>
                <div style={{ marginTop: 8, marginBottom: 4 }}>
                  <Statistic
                    value={summary?.newOrdersCount !== undefined ? `${summary.newOrdersCount.toLocaleString('vi-VN')} đơn` : '0 đơn'}
                    valueStyle={{ fontWeight: 700, fontSize: 24, color: '#09090B' }}
                  />
                </div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 12 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Tổng đơn phát sinh trong kỳ
                  </Text>
                  {summary?.newOrdersChangeRate !== undefined && (
                    <Tag
                      color={summary.newOrdersChangeRate >= 0 ? 'success' : 'error'}
                      style={{ margin: 0, fontSize: 11, fontWeight: 600 }}
                    >
                      {summary.newOrdersChangeRate >= 0 ? (
                        <ArrowUpOutlined style={{ marginRight: 2 }} />
                      ) : (
                        <ArrowDownOutlined style={{ marginRight: 2 }} />
                      )}
                      {summary.newOrdersChangeRate >= 0
                        ? `+${summary.newOrdersChangeRate}%`
                        : `${summary.newOrdersChangeRate}%`}
                    </Tag>
                  )}
                </div>
              </>
            )}
          </Card>
        </Col>

        {/* KPI 3: Khách hàng mới (DASH-1.4 Real Data) */}
        <Col xs={24} sm={12} lg={6}>
          <Card bordered style={{ height: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <Text type="secondary" style={{ fontSize: 13, fontWeight: 500 }}>
                Khách hàng mới
              </Text>
              <span style={{ fontSize: 16, color: '#71717A' }}><UserOutlined /></span>
            </div>
            {isSummaryLoading ? (
              <div style={{ padding: '8px 0' }}>
                <Skeleton active paragraph={{ rows: 2 }} title={{ width: 120 }} />
              </div>
            ) : isSummaryError ? (
              <div style={{ padding: '8px 0' }}>
                <Text type="danger" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>
                  Lỗi tải dữ liệu khách hàng
                </Text>
                <Button size="small" type="link" icon={<ReloadOutlined />} onClick={() => refetchSummary()} style={{ padding: 0 }}>
                  Thử lại
                </Button>
              </div>
            ) : (
              <>
                <div style={{ marginTop: 8, marginBottom: 4 }}>
                  <Statistic
                    value={summary?.newCustomersCount !== undefined ? `${summary.newCustomersCount.toLocaleString('vi-VN')} khách` : '0 khách'}
                    valueStyle={{ fontWeight: 700, fontSize: 24, color: '#09090B' }}
                  />
                </div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 12 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {summary?.totalCustomersCount !== undefined
                      ? `Tổng: ${summary.totalCustomersCount.toLocaleString('vi-VN')} khách`
                      : 'So với kỳ trước'}
                  </Text>
                  {summary?.newCustomersChangeRate !== undefined && (
                    <Tag
                      color={summary.newCustomersChangeRate >= 0 ? 'success' : 'error'}
                      style={{ margin: 0, fontSize: 11, fontWeight: 600 }}
                    >
                      {summary.newCustomersChangeRate >= 0 ? (
                        <ArrowUpOutlined style={{ marginRight: 2 }} />
                      ) : (
                        <ArrowDownOutlined style={{ marginRight: 2 }} />
                      )}
                      {summary.newCustomersChangeRate >= 0
                        ? `+${summary.newCustomersChangeRate}%`
                        : `${summary.newCustomersChangeRate}%`}
                    </Tag>
                  )}
                </div>
              </>
            )}
          </Card>
        </Col>

        {/* KPI 4: Cảnh báo sắp hết hàng (DASH-1.4 Real Data) */}
        <Col xs={24} sm={12} lg={6}>
          <Card bordered style={{ height: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <Text type="secondary" style={{ fontSize: 13, fontWeight: 500 }}>
                Cảnh báo sắp hết hàng
              </Text>
              <span style={{ fontSize: 16, color: '#D97706' }}><AlertOutlined /></span>
            </div>
            {isSummaryLoading ? (
              <div style={{ padding: '8px 0' }}>
                <Skeleton active paragraph={{ rows: 2 }} title={{ width: 120 }} />
              </div>
            ) : isSummaryError ? (
              <div style={{ padding: '8px 0' }}>
                <Text type="danger" style={{ fontSize: 12, display: 'block', marginBottom: 4 }}>
                  Lỗi tải dữ liệu tồn kho
                </Text>
                <Button size="small" type="link" icon={<ReloadOutlined />} onClick={() => refetchSummary()} style={{ padding: 0 }}>
                  Thử lại
                </Button>
              </div>
            ) : (
              <>
                <div style={{ marginTop: 8, marginBottom: 4 }}>
                  <Statistic
                    value={summary?.lowStockCount !== undefined ? `${summary.lowStockCount.toLocaleString('vi-VN')} mã hàng` : '0 mã hàng'}
                    valueStyle={{
                      fontWeight: 700,
                      fontSize: 24,
                      color: (summary?.lowStockCount ?? 0) > 0 ? '#D97706' : '#09090B',
                    }}
                  />
                </div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 12 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Dưới ngưỡng cảnh báo
                  </Text>
                  <Tag
                    color={(summary?.lowStockCount ?? 0) > 0 ? 'warning' : 'success'}
                    style={{ margin: 0, fontSize: 11, fontWeight: 600 }}
                  >
                    {(summary?.lowStockCount ?? 0) > 0 ? 'Ưu tiên cao' : 'Tồn kho an toàn'}
                  </Tag>
                </div>
              </>
            )}
          </Card>
        </Col>
      </Row>

      {/* 3. Analytics Section: Revenue Trends & Customer Analytics */}
      <Row gutter={[16, 16]}>
        {/* Left Column (16 cols): Revenue Trend Chart (Real Data) */}
        <Col xs={24} lg={16}>
          <Card
            bordered
            title={
              <div>
                <div style={{ fontWeight: 600, fontSize: 15 }}>
                  {getChartViewTitle()}
                </div>
                <Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                  Tổng lũy kế:{' '}
                  <Text strong style={{ color: '#09090B' }}>
                    {formatCurrencyVND(totalChartRevenue)}
                  </Text>
                  {' '}• {totalChartOrders.toLocaleString('vi-VN')} đơn hoàn thành
                </Text>
              </div>
            }
            extra={
              <Segmented
                value={chartView}
                onChange={(val) => setChartView(val as RevenueTrendPeriod)}
                options={[
                  { label: '7 ngày', value: '7days' },
                  { label: '30 ngày', value: '30days' },
                  { label: 'Cả năm', value: 'year' },
                ]}
              />
            }
          >
            {isTrendLoading ? (
              <div style={{ padding: '24px 0' }}>
                <Skeleton active paragraph={{ rows: 6 }} />
              </div>
            ) : isTrendError ? (
              <Alert
                type="error"
                message="Không thể tải biểu đồ doanh thu"
                description={getApiErrorMessage(trendError, 'Đã xảy ra lỗi khi kết nối tới máy chủ')}
                showIcon
                action={
                  <Button size="small" icon={<ReloadOutlined />} onClick={() => refetchTrend()}>
                    Thử lại
                  </Button>
                }
              />
            ) : !trendData || trendData.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="Chưa có dữ liệu doanh thu trong kỳ"
              />
            ) : (
              <div style={{ width: '100%', height: 280 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart
                    data={trendData}
                    margin={{ top: 10, right: 10, left: 10, bottom: 0 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E4E4E7" />
                    <XAxis
                      dataKey="date"
                      tick={{ fill: '#71717A', fontSize: 12 }}
                      stroke="#E4E4E7"
                    />
                    <YAxis
                      tick={{ fill: '#71717A', fontSize: 12 }}
                      stroke="#E4E4E7"
                      tickFormatter={(val) => {
                        if (val >= 1000000) return `${(val / 1000000).toFixed(0)}Tr`;
                        if (val >= 1000) return `${(val / 1000).toFixed(0)}K`;
                        return `${val}`;
                      }}
                    />
                    <RechartsTooltip
                      formatter={(value: unknown) => [formatCurrencyVND(Number(value) || 0), 'Doanh thu']}
                      labelFormatter={(label) => `Thời gian: ${label}`}
                      contentStyle={{
                        backgroundColor: '#FFFFFF',
                        borderRadius: 6,
                        border: '1px solid #E4E4E7',
                        fontSize: 13,
                      }}
                    />
                    <Area
                      type="monotone"
                      dataKey="revenue"
                      stroke="#18181B"
                      strokeWidth={2}
                      fill="#18181B"
                      fillOpacity={0.1}
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            )}
          </Card>
        </Col>

        {/* Right Column (8 cols): Customer Segmentation (Real Data) */}
        <Col xs={24} lg={8}>
          <Card
            bordered
            title={
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ fontWeight: 600, fontSize: 15 }}>Phân loại khách hàng</span>
                <Tooltip title="Thống kê phân loại dựa trên lịch sử đơn hàng hoàn thành và tổng chi tiêu của khách hàng">
                  <InfoCircleOutlined style={{ color: '#71717A', fontSize: 13 }} />
                </Tooltip>
              </div>
            }
            extra={
              <Text type="secondary" style={{ fontSize: 12 }}>
                {totalSegmentCustomers.toLocaleString('vi-VN')} khách
              </Text>
            }
            actions={[
              <Button
                type="link"
                block
                key="view-customers"
                onClick={() => navigate('/admin/customers')}
                style={{ color: '#18181B', fontWeight: 500 }}
              >
                Xem danh sách khách hàng <ArrowRightOutlined />
              </Button>,
            ]}
          >
            {isSegmentsLoading ? (
              <div style={{ padding: '8px 0' }}>
                <Skeleton active paragraph={{ rows: 4 }} />
              </div>
            ) : isSegmentsError ? (
              <Alert
                type="error"
                message="Không thể tải phân loại khách hàng"
                description={getApiErrorMessage(segmentsError, 'Đã xảy ra lỗi khi kết nối tới máy chủ')}
                showIcon
                action={
                  <Button size="small" icon={<ReloadOutlined />} onClick={() => refetchSegments()}>
                    Thử lại
                  </Button>
                }
              />
            ) : !customerSegments || customerSegments.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="Chưa có dữ liệu phân loại khách hàng"
              />
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                {customerSegments.map((seg) => (
                  <div key={seg.id}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <span
                          style={{
                            width: 8,
                            height: 8,
                            borderRadius: 2,
                            backgroundColor: seg.color,
                            display: 'inline-block',
                          }}
                        />
                        <Tooltip title={seg.description}>
                          <Text strong style={{ fontSize: 13, cursor: 'pointer' }}>
                            {seg.name}
                          </Text>
                        </Tooltip>
                      </div>
                      <Space size="small">
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {seg.count.toLocaleString('vi-VN')} khách
                        </Text>
                        <Text strong style={{ fontSize: 12, minWidth: 32, textAlign: 'right' }}>
                          {seg.percentage}%
                        </Text>
                      </Space>
                    </div>
                    <Progress
                      percent={seg.percentage}
                      showInfo={false}
                      strokeColor={seg.color}
                      size="small"
                    />
                  </div>
                ))}
              </div>
            )}
          </Card>
        </Col>
      </Row>

      {/* 4. Operations Section: Recent Orders & Stock Alert */}
      <Row gutter={[16, 16]}>
        {/* Left Column (16 cols): Recent Orders Table (Real Data) */}
        <Col xs={24} lg={16}>
          <Card
            bordered
            title={
              <div>
                <div style={{ fontWeight: 600, fontSize: 15 }}>Đơn hàng gần đây</div>
                <Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                  {ordersPage?.totalElements !== undefined
                    ? `5 đơn đặt mới nhất trên tổng số ${ordersPage.totalElements.toLocaleString('vi-VN')} đơn hệ thống`
                    : '5 đơn đặt mới nhất'}
                </Text>
              </div>
            }
            extra={
              <Button
                type="link"
                style={{ color: '#18181B', padding: 0 }}
                onClick={() => navigate('/admin/orders')}
              >
                Xem toàn bộ đơn hàng <ArrowRightOutlined />
              </Button>
            }
          >
            {isOrdersLoading ? (
              <div style={{ padding: '16px 0' }}>
                <Skeleton active paragraph={{ rows: 5 }} />
              </div>
            ) : isOrdersError ? (
              <Alert
                type="error"
                message="Không thể tải danh sách đơn hàng gần đây"
                description={getApiErrorMessage(ordersError, 'Đã xảy ra lỗi khi kết nối tới máy chủ')}
                showIcon
                action={
                  <Button size="small" icon={<ReloadOutlined />} onClick={() => refetchOrders()}>
                    Thử lại
                  </Button>
                }
              />
            ) : !ordersPage?.content || ordersPage.content.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="Chưa có đơn hàng nào phát sinh"
              />
            ) : (
              <Table
                columns={orderColumns}
                dataSource={ordersPage.content}
                pagination={false}
                size="middle"
                rowKey="id"
              />
            )}
          </Card>
        </Col>

        {/* Right Column (8 cols): Inventory Low Stock Alert (DASH-1.4 Real Data) */}
        <Col xs={24} lg={8}>
          <Card
            bordered
            title={
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ fontWeight: 600, fontSize: 15 }}>Cảnh báo tồn kho</div>
                  <Text type="secondary" style={{ fontSize: 12, fontWeight: 'normal' }}>
                    Dưới ngưỡng cảnh báo
                  </Text>
                </div>
              </div>
            }
            extra={
              isSummaryLoading ? (
                <Skeleton.Button active size="small" style={{ width: 60 }} />
              ) : (
                <Tag
                  color={(summary?.lowStockCount ?? 0) > 0 ? 'orange' : 'success'}
                  style={{ margin: 0, fontWeight: 600 }}
                >
                  {summary?.lowStockCount ?? 0} mã hàng
                </Tag>
              )
            }
            actions={[
              <Button
                type="primary"
                block
                icon={<ShoppingOutlined />}
                key="manage-products"
                onClick={() => navigate('/admin/products')}
                style={{ backgroundColor: '#18181B', borderColor: '#18181B' }}
              >
                Quản lý sản phẩm
              </Button>,
            ]}
          >
            {isLowStockLoading ? (
              <div style={{ padding: '8px 0' }}>
                <Skeleton active paragraph={{ rows: 4 }} />
              </div>
            ) : isLowStockError ? (
              <Alert
                type="error"
                message="Không thể tải danh sách tồn kho thấp"
                description={getApiErrorMessage(lowStockError, 'Đã xảy ra lỗi khi kết nối tới máy chủ')}
                showIcon
                action={
                  <Button size="small" icon={<ReloadOutlined />} onClick={() => refetchLowStock()}>
                    Thử lại
                  </Button>
                }
              />
            ) : !lowStockData?.content || lowStockData.content.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="Không có biến thể nào sắp hết hàng (tồn kho an toàn)"
              />
            ) : (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 16,
                  maxHeight: 380,
                  overflowY: 'auto',
                  paddingRight: 4,
                }}
              >
                {lowStockData.content.map((item) => {
                  const stock = item.stock ?? 0;
                  const variantDesc = [item.color, item.size].filter(Boolean).join(' - ');
                  return (
                    <div
                      key={item.id ?? item.sku}
                      style={{
                        padding: '10px 12px',
                        borderRadius: 6,
                        backgroundColor: '#FAFAFA',
                        border: '1px solid #F4F4F5',
                      }}
                    >
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                        }}
                      >
                        <div style={{ maxWidth: '65%' }}>
                          <Text strong style={{ fontSize: 13, display: 'block' }} ellipsis={{ tooltip: item.productName }}>
                            {item.productName}
                          </Text>
                          <Text type="secondary" style={{ fontSize: 11 }}>
                            {variantDesc ? `${variantDesc} • ` : ''}SKU: {item.sku}
                          </Text>
                        </div>
                        <Tag
                          color={stock === 0 ? 'error' : 'warning'}
                          style={{ margin: 0, fontWeight: 600, borderRadius: 6 }}
                        >
                          {stock === 0 ? 'Hết hàng' : `Sắp hết (còn ${stock})`}
                        </Tag>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </Card>
        </Col>
      </Row>

      {/* Order Detail Drawer for inspecting selected order snapshot */}
      <OrderDetailDrawer
        orderId={selectedOrderId ?? undefined}
        open={Boolean(selectedOrderId)}
        onClose={() => setSelectedOrderId(null)}
      />
    </div>
  );
};

export default DashboardPage;
