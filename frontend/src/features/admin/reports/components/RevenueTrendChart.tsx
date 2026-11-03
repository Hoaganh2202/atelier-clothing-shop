import React, { useMemo } from 'react';
import {
  Card,
  Segmented,
  Skeleton,
  Empty,
  Alert,
  Button,
  Space,
  Typography,
  Tooltip,
} from 'antd';
import {
  ReloadOutlined,
  InfoCircleOutlined,
  LineChartOutlined,
} from '@ant-design/icons';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartsTooltip,
  Legend,
} from 'recharts';
import { formatCurrencyVND } from '@/lib/format';
import type {
  RevenueGranularity,
  RevenueReportPointResponse,
} from '../reports.types';
import { getReportApiErrorMessage } from '../api';

const { Text } = Typography;

interface RevenueTrendChartProps {
  data?: RevenueReportPointResponse[];
  granularity: RevenueGranularity;
  onGranularityChange: (granularity: RevenueGranularity) => void;
  isLoading: boolean;
  isError: boolean;
  error: unknown;
  onRetry: () => void;
}

export const RevenueTrendChart: React.FC<RevenueTrendChartProps> = ({
  data,
  granularity,
  onGranularityChange,
  isLoading,
  isError,
  error,
  onRetry,
}) => {
  const { totalNetRevenue, totalGrossRevenue, totalOrders } = useMemo(() => {
    if (!data || data.length === 0) {
      return { totalNetRevenue: 0, totalGrossRevenue: 0, totalOrders: 0 };
    }
    return data.reduce(
      (acc, item) => ({
        totalNetRevenue: acc.totalNetRevenue + (item.netRevenue || 0),
        totalGrossRevenue: acc.totalGrossRevenue + (item.grossRevenue || 0),
        totalOrders: acc.totalOrders + (item.completedOrderCount || 0),
      }),
      { totalNetRevenue: 0, totalGrossRevenue: 0, totalOrders: 0 }
    );
  }, [data]);

  const formatPeriodLabel = (period: string): string => {
    if (!period) return '';
    if (granularity === 'day') {
      const parts = period.split('-');
      if (parts.length === 3) {
        return `${parts[2]}/${parts[1]}`;
      }
    }
    if (granularity === 'week') {
      // e.g. "2026-W44"
      return period.replace('-W', ' Tuần ');
    }
    if (granularity === 'month') {
      // e.g. "2026-10"
      const parts = period.split('-');
      if (parts.length === 2) {
        return `T${parts[1]}/${parts[0].slice(2)}`;
      }
    }
    return period;
  };

  const hasData = Boolean(data && data.length > 0);

  return (
    <Card
      bordered
      style={{
        borderRadius: 8,
        borderColor: '#E4E4E7',
      }}
      bodyStyle={{ padding: '20px' }}
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <LineChartOutlined style={{ color: '#18181B', fontSize: 16 }} />
          <span style={{ fontWeight: 600, fontSize: 15, color: '#09090B' }}>
            Xu hướng doanh thu theo thời gian
          </span>
          <Tooltip title="Thống kê Gross Revenue (giá trị hàng hóa) và Net Revenue (thực thu) của các đơn hàng hoàn tất theo mốc thời gian">
            <InfoCircleOutlined style={{ color: '#A1A1AA', fontSize: 13, cursor: 'pointer' }} />
          </Tooltip>
        </div>
      }
      extra={
        <Space size={12} align="center">
          <Segmented
            value={granularity}
            onChange={(val) => onGranularityChange(val as RevenueGranularity)}
            options={[
              { label: 'Theo ngày', value: 'day' },
              { label: 'Theo tuần', value: 'week' },
              { label: 'Theo tháng', value: 'month' },
            ]}
          />
        </Space>
      }
    >
      {isError ? (
        <Alert
          type="error"
          showIcon
          message="Lỗi tải biểu đồ doanh thu"
          description={
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 8 }}>
              <span>{getReportApiErrorMessage(error, 'Không thể kết nối đến máy chủ')}</span>
              <Button size="small" type="primary" danger icon={<ReloadOutlined />} onClick={onRetry}>
                Thử lại
              </Button>
            </div>
          }
        />
      ) : isLoading ? (
        <div style={{ padding: '24px 0' }}>
          <Skeleton active paragraph={{ rows: 6 }} />
        </div>
      ) : !hasData ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="Không có dữ liệu doanh thu trong khoảng thời gian này"
          style={{ padding: '40px 0' }}
        />
      ) : (
        <div>
          {/* Quick Sub-Stats */}
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: 24,
              marginBottom: 20,
              paddingBottom: 16,
              borderBottom: '1px solid #F4F4F5',
            }}
          >
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Tổng Net Revenue trong kỳ
              </Text>
              <div style={{ fontWeight: 700, fontSize: 18, color: '#16A34A' }}>
                {formatCurrencyVND(totalNetRevenue)}
              </div>
            </div>

            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Tổng Gross Revenue trong kỳ
              </Text>
              <div style={{ fontWeight: 700, fontSize: 18, color: '#18181B' }}>
                {formatCurrencyVND(totalGrossRevenue)}
              </div>
            </div>

            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Tổng đơn hoàn thành
              </Text>
              <div style={{ fontWeight: 700, fontSize: 18, color: '#09090B' }}>
                {totalOrders.toLocaleString('vi-VN')} đơn
              </div>
            </div>
          </div>

          {/* Recharts Area Chart */}
          <div style={{ width: '100%', height: 320 }}>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart
                data={data}
                margin={{ top: 10, right: 10, left: 10, bottom: 0 }}
              >
                <defs>
                  <linearGradient id="netRevenueGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#16A34A" stopOpacity={0.25} />
                    <stop offset="95%" stopColor="#16A34A" stopOpacity={0.0} />
                  </linearGradient>
                  <linearGradient id="grossRevenueGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#C2410C" stopOpacity={0.15} />
                    <stop offset="95%" stopColor="#C2410C" stopOpacity={0.0} />
                  </linearGradient>
                </defs>

                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E4E4E7" />

                <XAxis
                  dataKey="period"
                  tickFormatter={formatPeriodLabel}
                  tick={{ fill: '#71717A', fontSize: 12 }}
                  stroke="#E4E4E7"
                />

                <YAxis
                  tick={{ fill: '#71717A', fontSize: 12 }}
                  stroke="#E4E4E7"
                  tickFormatter={(val) => {
                    if (val >= 1_000_000) return `${(val / 1_000_000).toFixed(1)}Tr`;
                    if (val >= 1_000) return `${(val / 1_000).toFixed(0)}K`;
                    return `${val}`;
                  }}
                />

                <RechartsTooltip
                  content={({ active, payload, label }) => {
                    if (active && payload && payload.length) {
                      const point = payload[0]?.payload as RevenueReportPointResponse;
                      return (
                        <div
                          style={{
                            background: '#FFFFFF',
                            border: '1px solid #E4E4E7',
                            padding: '10px 14px',
                            borderRadius: 6,
                            boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
                          }}
                        >
                          <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 6, color: '#09090B' }}>
                            {formatPeriodLabel(String(label))} ({label})
                          </div>
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
                            <div style={{ color: '#16A34A' }}>
                              Net Revenue: <strong>{formatCurrencyVND(point.netRevenue)}</strong>
                            </div>
                            <div style={{ color: '#C2410C' }}>
                              Gross Revenue: <strong>{formatCurrencyVND(point.grossRevenue)}</strong>
                            </div>
                            <div style={{ color: '#71717A' }}>
                              Số đơn hoàn thành: <strong>{point.completedOrderCount}</strong>
                            </div>
                          </div>
                        </div>
                      );
                    }
                    return null;
                  }}
                />

                <Legend
                  verticalAlign="top"
                  align="right"
                  wrapperStyle={{ paddingBottom: 10, fontSize: 12 }}
                />

                <Area
                  name="Doanh thu thuần (Net Revenue)"
                  type="monotone"
                  dataKey="netRevenue"
                  stroke="#16A34A"
                  strokeWidth={2}
                  fillOpacity={1}
                  fill="url(#netRevenueGradient)"
                />

                <Area
                  name="Doanh thu gộp (Gross Revenue)"
                  type="monotone"
                  dataKey="grossRevenue"
                  stroke="#C2410C"
                  strokeWidth={1.5}
                  strokeDasharray="4 4"
                  fillOpacity={1}
                  fill="url(#grossRevenueGradient)"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}
    </Card>
  );
};
