import React from 'react';
import {
  Row,
  Col,
  Card,
  Statistic,
  Skeleton,
  Alert,
  Button,
  Tooltip,
  Typography,
} from 'antd';
import {
  DollarOutlined,
  ShoppingOutlined,
  TagOutlined,
  CarOutlined,
  InfoCircleOutlined,
  ReloadOutlined,
  RiseOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import { formatCurrencyVND } from '@/lib/format';
import type { ReportSummaryResponse } from '../reports.types';
import { getReportApiErrorMessage } from '../api';

const { Text } = Typography;

interface ReportSummaryCardsProps {
  data?: ReportSummaryResponse;
  isLoading: boolean;
  isError: boolean;
  error: unknown;
  onRetry: () => void;
}

export const ReportSummaryCards: React.FC<ReportSummaryCardsProps> = ({
  data,
  isLoading,
  isError,
  error,
  onRetry,
}) => {
  if (isError) {
    const errorMsg = getReportApiErrorMessage(
      error,
      'Không thể tải chỉ số tổng quan báo cáo'
    );
    return (
      <Alert
        type="error"
        showIcon
        message="Lỗi tải tổng quan tài chính"
        description={
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 8 }}>
            <span>{errorMsg}</span>
            <Button size="small" type="primary" danger icon={<ReloadOutlined />} onClick={onRetry}>
              Thử lại
            </Button>
          </div>
        }
        style={{ borderRadius: 8 }}
      />
    );
  }

  const items = [
    {
      title: 'Doanh thu thuần (Net Revenue)',
      value: data?.netRevenue ?? 0,
      isCurrency: true,
      icon: <DollarOutlined style={{ color: '#16A34A', fontSize: 20 }} />,
      tooltip: 'Tổng số tiền thực thu từ khách hàng của các đơn COMPLETED (sau khi trừ voucher và cộng phí ship).',
      highlightColor: '#16A34A',
      secondaryText: 'Thực thu sau voucher & ship',
    },
    {
      title: 'Doanh thu gộp (Gross Revenue)',
      value: data?.grossRevenue ?? 0,
      isCurrency: true,
      icon: <RiseOutlined style={{ color: '#18181B', fontSize: 20 }} />,
      tooltip: 'Tổng giá trị hàng hóa snapshot từ order_details của các đơn COMPLETED (chưa tính giảm giá & ship).',
      highlightColor: '#18181B',
      secondaryText: 'Tổng giá trị hàng hóa',
    },
    {
      title: 'Giá trị đơn TB (AOV)',
      value: data?.averageOrderValue ?? 0,
      isCurrency: true,
      icon: <ShoppingOutlined style={{ color: '#0284C7', fontSize: 20 }} />,
      tooltip: 'Giá trị trung bình mỗi đơn hoàn thành (Net Revenue chia cho Số đơn hoàn thành).',
      highlightColor: '#0284C7',
      secondaryText: 'Doanh thu thuần / Đơn hoàn thành',
    },
    {
      title: 'Đơn hàng hoàn thành',
      value: data?.completedOrderCount ?? 0,
      isCurrency: false,
      icon: <CheckCircleOutlined style={{ color: '#059669', fontSize: 20 }} />,
      tooltip: 'Số lượng đơn hàng hoàn tất giao thành công (status = COMPLETED) có completed_at trong kỳ.',
      highlightColor: '#059669',
      secondaryText: 'Chỉ tính đơn COMPLETED',
    },
    {
      title: 'Tổng tiền giảm giá',
      value: data?.totalDiscount ?? 0,
      isCurrency: true,
      icon: <TagOutlined style={{ color: '#D97706', fontSize: 20 }} />,
      tooltip: 'Tổng giá trị chiết khấu từ voucher đã áp dụng cho các đơn hoàn thành.',
      highlightColor: '#D97706',
      secondaryText: 'Chiết khấu từ voucher/khuyến mãi',
    },
    {
      title: 'Tổng phí vận chuyển',
      value: data?.totalShippingFee ?? 0,
      isCurrency: true,
      icon: <CarOutlined style={{ color: '#7C3AED', fontSize: 20 }} />,
      tooltip: 'Tổng phí vận chuyển khách hàng đã thanh toán trong các đơn hoàn thành.',
      highlightColor: '#7C3AED',
      secondaryText: 'Phí ship thu từ khách hàng',
    },
  ];

  return (
    <Row gutter={[16, 16]}>
      {items.map((item, index) => (
        <Col xs={24} sm={12} lg={8} key={index}>
          <Card
            bordered
            style={{
              borderRadius: 8,
              borderColor: '#E4E4E7',
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between',
            }}
            bodyStyle={{ padding: '16px 20px' }}
          >
            {isLoading ? (
              <Skeleton active paragraph={{ rows: 2 }} title={{ width: '60%' }} />
            ) : (
              <div>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    marginBottom: 10,
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <Text type="secondary" style={{ fontSize: 13, fontWeight: 500 }}>
                      {item.title}
                    </Text>
                    <Tooltip title={item.tooltip}>
                      <InfoCircleOutlined style={{ color: '#A1A1AA', fontSize: 12, cursor: 'pointer' }} />
                    </Tooltip>
                  </div>
                  <div
                    style={{
                      width: 36,
                      height: 36,
                      borderRadius: 6,
                      background: '#F4F4F5',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    {item.icon}
                  </div>
                </div>

                <Statistic
                  value={item.isCurrency ? formatCurrencyVND(item.value) : item.value.toLocaleString('vi-VN')}
                  valueStyle={{
                    fontWeight: 700,
                    fontSize: item.isCurrency ? 22 : 26,
                    color: '#09090B',
                    letterSpacing: '-0.02em',
                  }}
                />

                <div style={{ marginTop: 8 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {item.secondaryText}
                  </Text>
                </div>
              </div>
            )}
          </Card>
        </Col>
      ))}
    </Row>
  );
};
