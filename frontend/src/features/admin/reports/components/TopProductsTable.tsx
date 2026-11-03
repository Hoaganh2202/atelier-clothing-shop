import React from 'react';
import {
  Card,
  Table,
  Select,
  Skeleton,
  Empty,
  Alert,
  Button,
  Space,
  Typography,
  Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  TrophyOutlined,
  ReloadOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { formatCurrencyVND } from '@/lib/format';
import type { TopProductReportResponse } from '../reports.types';
import { getReportApiErrorMessage } from '../api';

const { Text } = Typography;

interface TopProductsTableProps {
  data?: TopProductReportResponse[];
  limit: number;
  onLimitChange: (limit: number) => void;
  isLoading: boolean;
  isError: boolean;
  error: unknown;
  onRetry: () => void;
}

export const TopProductsTable: React.FC<TopProductsTableProps> = ({
  data,
  limit,
  onLimitChange,
  isLoading,
  isError,
  error,
  onRetry,
}) => {
  const columns: ColumnsType<TopProductReportResponse> = [
    {
      title: 'Hạng',
      key: 'rank',
      width: 65,
      align: 'center',
      render: (_, __, index) => {
        const rank = index + 1;
        let badgeBg = '#F4F4F5';
        let badgeColor = '#71717A';
        if (rank === 1) {
          badgeBg = '#FEF3C7';
          badgeColor = '#D97706';
        } else if (rank === 2) {
          badgeBg = '#E0F2FE';
          badgeColor = '#0284C7';
        } else if (rank === 3) {
          badgeBg = '#F3E8FF';
          badgeColor = '#7C3AED';
        }

        return (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 24,
              height: 24,
              borderRadius: '50%',
              fontSize: 12,
              fontWeight: 700,
              backgroundColor: badgeBg,
              color: badgeColor,
            }}
          >
            {rank}
          </span>
        );
      },
    },
    {
      title: 'Tên sản phẩm',
      dataIndex: 'productName',
      key: 'productName',
      render: (name: string, record) => (
        <div>
          <div style={{ fontWeight: 600, color: '#09090B' }}>{name}</div>
          <Text type="secondary" style={{ fontSize: 11 }}>
            ID: #{record.productId}
          </Text>
        </div>
      ),
    },
    {
      title: 'Đã bán',
      dataIndex: 'totalQuantitySold',
      key: 'totalQuantitySold',
      align: 'right',
      sorter: (a, b) => (a.totalQuantitySold || 0) - (b.totalQuantitySold || 0),
      render: (qty: number) => (
        <span style={{ fontWeight: 600 }}>{qty.toLocaleString('vi-VN')}</span>
      ),
    },
    {
      title: 'Doanh thu snapshot',
      dataIndex: 'totalRevenue',
      key: 'totalRevenue',
      align: 'right',
      sorter: (a, b) => (a.totalRevenue || 0) - (b.totalRevenue || 0),
      render: (revenue: number) => (
        <span style={{ fontWeight: 600, color: '#16A34A' }}>
          {formatCurrencyVND(revenue)}
        </span>
      ),
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
      bodyStyle={{ padding: '16px', flex: 1 }}
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <TrophyOutlined style={{ color: '#D97706', fontSize: 16 }} />
          <span style={{ fontWeight: 600, fontSize: 15, color: '#09090B' }}>
            Top sản phẩm bán chạy
          </span>
          <Tooltip title="Thống kê dựa trên snapshot tên sản phẩm và đơn giá trong order_details của các đơn COMPLETED">
            <InfoCircleOutlined style={{ color: '#A1A1AA', fontSize: 13, cursor: 'pointer' }} />
          </Tooltip>
        </div>
      }
      extra={
        <Space size={8} align="center">
          <Text type="secondary" style={{ fontSize: 12 }}>
            Hiển thị:
          </Text>
          <Select
            size="small"
            value={limit}
            onChange={onLimitChange}
            style={{ width: 85 }}
            options={[
              { label: 'Top 10', value: 10 },
              { label: 'Top 20', value: 20 },
              { label: 'Top 50', value: 50 },
            ]}
          />
        </Space>
      }
    >
      {isError ? (
        <Alert
          type="error"
          showIcon
          message="Lỗi tải danh sách sản phẩm bán chạy"
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
          description="Chưa có dữ liệu sản phẩm bán ra trong kỳ"
          style={{ padding: '32px 0' }}
        />
      ) : (
        <Table
          rowKey="productId"
          dataSource={data}
          columns={columns}
          pagination={false}
          size="middle"
          scroll={{ x: 500 }}
        />
      )}
    </Card>
  );
};
