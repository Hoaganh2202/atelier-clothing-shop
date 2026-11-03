import React, { useMemo } from 'react';
import {
  Card,
  Row,
  Col,
  Tag,
  Progress,
  Skeleton,
  Empty,
  Alert,
  Button,
  Typography,
  Tooltip,
} from 'antd';
import {
  PieChartOutlined,
  ReloadOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import {
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Tooltip as RechartsTooltip,
} from 'recharts';
import type { OrderStatusDistributionResponse } from '../reports.types';
import { ORDER_STATUS_CONFIG } from '../constants';
import { getReportApiErrorMessage } from '../api';

const { Text } = Typography;

interface OrderStatusDistributionWidgetProps {
  data?: OrderStatusDistributionResponse[];
  isLoading: boolean;
  isError: boolean;
  error: unknown;
  onRetry: () => void;
}

export const OrderStatusDistributionWidget: React.FC<OrderStatusDistributionWidgetProps> = ({
  data,
  isLoading,
  isError,
  error,
  onRetry,
}) => {
  const totalOrdersCreated = useMemo(() => {
    if (!data) return 0;
    return data.reduce((sum, item) => sum + (item.orderCount || 0), 0);
  }, [data]);

  const chartData = useMemo(() => {
    if (!data) return [];
    return data.map((item) => {
      const cfg = ORDER_STATUS_CONFIG[item.status] || {
        label: item.statusLabel || item.status,
        color: 'default',
        badgeColor: '#71717A',
      };
      return {
        name: item.statusLabel || cfg.label,
        status: item.status,
        value: item.orderCount || 0,
        percentage: item.percentage || 0,
        color: cfg.badgeColor,
      };
    });
  }, [data]);

  return (
    <Card
      bordered
      style={{
        borderRadius: 8,
        borderColor: '#E4E4E7',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
      }}
      bodyStyle={{ padding: '20px', flex: 1 }}
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <PieChartOutlined style={{ color: '#7C3AED', fontSize: 16 }} />
          <span style={{ fontWeight: 600, fontSize: 15, color: '#09090B' }}>
            Cơ cấu trạng thái đơn hàng
          </span>
          <Tooltip title="Thống kê toàn bộ đơn hàng được tạo (created_at) trong khoảng thời gian đã chọn, phân theo 6 trạng thái nghiệp vụ">
            <InfoCircleOutlined style={{ color: '#A1A1AA', fontSize: 13, cursor: 'pointer' }} />
          </Tooltip>
        </div>
      }
      extra={
        <Text type="secondary" style={{ fontSize: 12 }}>
          Theo thời điểm tạo (created_at)
        </Text>
      }
    >
      {isError ? (
        <Alert
          type="error"
          showIcon
          message="Lỗi tải cơ cấu trạng thái đơn hàng"
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
        <div style={{ padding: '16px 0' }}>
          <Skeleton active paragraph={{ rows: 5 }} />
        </div>
      ) : !data || data.length === 0 || totalOrdersCreated === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="Không có đơn hàng nào được tạo trong kỳ"
          style={{ padding: '32px 0' }}
        />
      ) : (
        <Row gutter={[20, 20]} align="middle">
          {/* Donut Chart (8 cols on lg) */}
          <Col xs={24} md={8}>
            <div style={{ width: '100%', height: 220, position: 'relative' }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={chartData}
                    dataKey="value"
                    nameKey="name"
                    cx="50%"
                    cy="50%"
                    innerRadius={55}
                    outerRadius={85}
                    paddingAngle={2}
                  >
                    {chartData.map((entry, index) => (
                      <Cell key={`status-cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <RechartsTooltip
                    content={({ active, payload }) => {
                      if (active && payload && payload.length) {
                        const item = payload[0].payload;
                        return (
                          <div
                            style={{
                              background: '#FFFFFF',
                              border: '1px solid #E4E4E7',
                              padding: '8px 12px',
                              borderRadius: 6,
                              boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
                            }}
                          >
                            <div style={{ fontWeight: 600, fontSize: 13, color: '#09090B' }}>
                              {item.name}
                            </div>
                            <div style={{ fontSize: 12, color: '#18181B', marginTop: 2 }}>
                              Số đơn: <strong>{item.value}</strong> ({item.percentage?.toFixed(1)}%)
                            </div>
                          </div>
                        );
                      }
                      return null;
                    }}
                  />
                </PieChart>
              </ResponsiveContainer>
              <div
                style={{
                  position: 'absolute',
                  top: '50%',
                  left: '50%',
                  transform: 'translate(-50%, -50%)',
                  textAlign: 'center',
                  pointerEvents: 'none',
                }}
              >
                <div style={{ fontSize: 11, color: '#71717A' }}>Tổng đơn tạo</div>
                <div style={{ fontSize: 16, fontWeight: 700, color: '#09090B' }}>
                  {totalOrdersCreated}
                </div>
              </div>
            </div>
          </Col>

          {/* Status Breakdown List (16 cols on lg) */}
          <Col xs={24} md={16}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {chartData.map((item) => {
                const cfg = ORDER_STATUS_CONFIG[item.status] || {
                  label: item.name,
                  color: 'default',
                  badgeColor: '#71717A',
                };
                return (
                  <div
                    key={item.status}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: 12,
                      padding: '4px 0',
                    }}
                  >
                    <div style={{ flex: '0 0 auto', minWidth: 110, display: 'flex', alignItems: 'center', gap: 6 }}>
                      <Tag color={cfg.color} style={{ margin: 0, fontWeight: 500, fontSize: 12 }}>
                        {item.name}
                      </Tag>
                    </div>

                    <div style={{ flex: 1, minWidth: 50, maxWidth: 220 }}>
                      <Progress
                        percent={Math.min(100, Math.max(0, item.percentage || 0))}
                        showInfo={false}
                        strokeColor={item.color}
                        size="small"
                      />
                    </div>

                    <div style={{ textAlign: 'right', flex: '0 0 auto', minWidth: 75 }}>
                      <span style={{ fontWeight: 600, fontSize: 13, color: '#09090B' }}>
                        {item.value} đơn
                      </span>
                      <Text type="secondary" style={{ fontSize: 11, marginLeft: 4 }}>
                        ({(item.percentage || 0).toFixed(1)}%)
                      </Text>
                    </div>
                  </div>
                );
              })}
            </div>
          </Col>
        </Row>
      )}
    </Card>
  );
};
