import React from 'react';
import {
  Card,
  Row,
  Col,
  Table,
  Progress,
  Skeleton,
  Empty,
  Alert,
  Button,
  Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  AppstoreOutlined,
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
import { formatCurrencyVND } from '@/lib/format';
import type { CategorySalesReportResponse } from '../reports.types';
import { REPORT_THEME } from '../constants';
import { getReportApiErrorMessage } from '../api';


interface CategorySalesWidgetProps {
  data?: CategorySalesReportResponse[];
  isLoading: boolean;
  isError: boolean;
  error: unknown;
  onRetry: () => void;
}

export const CategorySalesWidget: React.FC<CategorySalesWidgetProps> = ({
  data,
  isLoading,
  isError,
  error,
  onRetry,
}) => {
  const chartColors = REPORT_THEME.chartColors;

  const chartData = (data || []).map((item, index) => ({
    name: item.categoryName || 'Không phân loại',
    value: item.totalRevenue,
    quantity: item.totalQuantitySold,
    percentage: item.percentage,
    color: chartColors[index % chartColors.length],
  }));

  const columns: ColumnsType<CategorySalesReportResponse> = [
    {
      title: 'Danh mục',
      dataIndex: 'categoryName',
      key: 'categoryName',
      render: (name: string, _, index) => {
        const color = chartColors[index % chartColors.length];
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span
              style={{
                width: 10,
                height: 10,
                borderRadius: '50%',
                backgroundColor: color,
                display: 'inline-block',
                flexShrink: 0,
              }}
            />
            <span style={{ fontWeight: 500, color: '#09090B' }}>{name || 'Khác'}</span>
          </div>
        );
      },
    },
    {
      title: 'Số lượng',
      dataIndex: 'totalQuantitySold',
      key: 'totalQuantitySold',
      align: 'right',
      render: (qty: number) => (
        <span style={{ fontSize: 13 }}>{qty.toLocaleString('vi-VN')}</span>
      ),
    },
    {
      title: 'Doanh thu',
      dataIndex: 'totalRevenue',
      key: 'totalRevenue',
      align: 'right',
      render: (rev: number) => (
        <span style={{ fontWeight: 600, color: '#18181B' }}>
          {formatCurrencyVND(rev)}
        </span>
      ),
    },
    {
      title: 'Tỷ trọng',
      dataIndex: 'percentage',
      key: 'percentage',
      width: 130,
      align: 'right',
      render: (pct: number, _, index) => {
        const color = chartColors[index % chartColors.length];
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, justifyContent: 'flex-end' }}>
            <div style={{ width: 60 }}>
              <Progress
                percent={Math.min(100, Math.max(0, pct || 0))}
                showInfo={false}
                strokeColor={color}
                size="small"
              />
            </div>
            <span style={{ fontSize: 12, fontWeight: 500, width: 42, textAlign: 'right' }}>
              {(pct || 0).toFixed(1)}%
            </span>
          </div>
        );
      },
    },
  ];

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
          <AppstoreOutlined style={{ color: '#0284C7', fontSize: 16 }} />
          <span style={{ fontWeight: 600, fontSize: 15, color: '#09090B' }}>
            Doanh số theo danh mục
          </span>
          <Tooltip title="Thống kê tỷ trọng doanh thu và sản lượng bán theo từng danh mục hàng hóa trong kỳ">
            <InfoCircleOutlined style={{ color: '#A1A1AA', fontSize: 13, cursor: 'pointer' }} />
          </Tooltip>
        </div>
      }
    >
      {isError ? (
        <Alert
          type="error"
          showIcon
          message="Lỗi tải báo cáo danh mục"
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
      ) : !data || data.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="Chưa có dữ liệu danh mục bán ra trong kỳ"
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
                      <Cell key={`cell-${index}`} fill={entry.color} />
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
                            <div style={{ fontSize: 12, color: '#16A34A', marginTop: 2 }}>
                              Doanh thu: <strong>{formatCurrencyVND(item.value)}</strong>
                            </div>
                            <div style={{ fontSize: 12, color: '#71717A', marginTop: 2 }}>
                              Đã bán: <strong>{item.quantity}</strong> ({item.percentage?.toFixed(1)}%)
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
                <div style={{ fontSize: 11, color: '#71717A' }}>Danh mục</div>
                <div style={{ fontSize: 16, fontWeight: 700, color: '#09090B' }}>
                  {data.length}
                </div>
              </div>
            </div>
          </Col>

          {/* Breakdown Table (16 cols on lg) */}
          <Col xs={24} md={16}>
            <Table
              rowKey="categoryId"
              dataSource={data}
              columns={columns}
              pagination={false}
              size="small"
              scroll={{ x: 380 }}
            />
          </Col>
        </Row>
      )}
    </Card>
  );
};
