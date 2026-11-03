import React, { useState, useMemo, useCallback } from 'react';
import { Space, Typography, Row, Col } from 'antd';
import { BarChartOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';
import {
  ReportDateFilter,
  ReportSummaryCards,
  RevenueTrendChart,
  TopProductsTable,
  CategorySalesWidget,
  OrderStatusDistributionWidget,
} from '../components';
import {
  useCategorySales,
  useOrderStatusDistribution,
  useReportSummary,
  useRevenueTrend,
  useTopProducts,
} from '../api';
import type {
  DateRangeFilter,
  RevenueGranularity,
} from '../reports.types';
import { TIMEZONE_VN } from '../constants';

dayjs.extend(utc);
dayjs.extend(timezone);

const { Title, Paragraph } = Typography;

export const ReportsPage: React.FC = () => {
  const initialDateFilter = useMemo<DateRangeFilter>(() => {
    const today = dayjs().tz(TIMEZONE_VN).startOf('day');
    const start = today.subtract(29, 'day');
    return {
      startDate: start.format('YYYY-MM-DD'),
      endDate: today.format('YYYY-MM-DD'),
    };
  }, []);

  const [dateFilter, setDateFilter] = useState<DateRangeFilter>(initialDateFilter);
  const [granularity, setGranularity] = useState<RevenueGranularity>('day');
  const [topProductsLimit, setTopProductsLimit] = useState<number>(10);

  // 1. Report Summary KPI
  const {
    data: summaryData,
    isLoading: isSummaryLoading,
    isError: isSummaryError,
    error: summaryError,
    refetch: refetchSummary,
    isFetching: isSummaryFetching,
  } = useReportSummary(dateFilter);

  // 2. Revenue Trend
  const {
    data: revenueTrendData,
    isLoading: isTrendLoading,
    isError: isTrendError,
    error: trendError,
    refetch: refetchTrend,
    isFetching: isTrendFetching,
  } = useRevenueTrend({
    startDate: dateFilter.startDate,
    endDate: dateFilter.endDate,
    granularity,
  });

  // 3. Top Products
  const {
    data: topProductsData,
    isLoading: isTopProductsLoading,
    isError: isTopProductsError,
    error: topProductsError,
    refetch: refetchTopProducts,
    isFetching: isTopProductsFetching,
  } = useTopProducts({
    startDate: dateFilter.startDate,
    endDate: dateFilter.endDate,
    limit: topProductsLimit,
  });

  // 4. Category Sales
  const {
    data: categorySalesData,
    isLoading: isCategoryLoading,
    isError: isCategoryError,
    error: categoryError,
    refetch: refetchCategory,
    isFetching: isCategoryFetching,
  } = useCategorySales(dateFilter);

  // 5. Order Status Distribution
  const {
    data: orderStatusData,
    isLoading: isOrderStatusLoading,
    isError: isOrderStatusError,
    error: orderStatusError,
    refetch: refetchOrderStatus,
    isFetching: isOrderStatusFetching,
  } = useOrderStatusDistribution(dateFilter);

  const isAnyFetching =
    isSummaryFetching ||
    isTrendFetching ||
    isTopProductsFetching ||
    isCategoryFetching ||
    isOrderStatusFetching;

  const handleRefreshAll = useCallback(() => {
    refetchSummary();
    refetchTrend();
    refetchTopProducts();
    refetchCategory();
    refetchOrderStatus();
  }, [
    refetchSummary,
    refetchTrend,
    refetchTopProducts,
    refetchCategory,
    refetchOrderStatus,
  ]);

  return (
    <div style={{ maxWidth: 1440, margin: '0 auto' }}>
      <Space direction="vertical" size={20} style={{ width: '100%' }}>
        {/* Page Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <Title
              level={4}
              style={{
                margin: 0,
                fontWeight: 700,
                fontSize: 22,
                color: '#09090B',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
              }}
            >
              <BarChartOutlined style={{ fontSize: 22, color: '#18181B' }} />
              Báo cáo & Thống kê
            </Title>
            <Paragraph type="secondary" style={{ margin: '4px 0 0 0', fontSize: 13 }}>
              Phân tích doanh thu, cơ cấu đơn hàng và hiệu quả sản phẩm trong khoảng thời gian tùy chọn
            </Paragraph>
          </div>
        </div>

        {/* 1. Date Range Filter Toolbar */}
        <ReportDateFilter
          value={dateFilter}
          onChange={setDateFilter}
          onRefresh={handleRefreshAll}
          isLoading={isAnyFetching}
        />

        {/* 2. Key Metrics Summary Cards */}
        <ReportSummaryCards
          data={summaryData}
          isLoading={isSummaryLoading}
          isError={isSummaryError}
          error={summaryError}
          onRetry={refetchSummary}
        />

        {/* 3. Revenue Trend Chart */}
        <RevenueTrendChart
          data={revenueTrendData}
          granularity={granularity}
          onGranularityChange={setGranularity}
          isLoading={isTrendLoading}
          isError={isTrendError}
          error={trendError}
          onRetry={refetchTrend}
        />

        {/* 4. Top Products & Category Sales Breakdown */}
        <Row gutter={[20, 20]}>
          <Col xs={24} xl={12}>
            <TopProductsTable
              data={topProductsData}
              limit={topProductsLimit}
              onLimitChange={setTopProductsLimit}
              isLoading={isTopProductsLoading}
              isError={isTopProductsError}
              error={topProductsError}
              onRetry={refetchTopProducts}
            />
          </Col>

          <Col xs={24} xl={12}>
            <CategorySalesWidget
              data={categorySalesData}
              isLoading={isCategoryLoading}
              isError={isCategoryError}
              error={categoryError}
              onRetry={refetchCategory}
            />
          </Col>
        </Row>

        {/* 5. Order Status Distribution */}
        <OrderStatusDistributionWidget
          data={orderStatusData}
          isLoading={isOrderStatusLoading}
          isError={isOrderStatusError}
          error={orderStatusError}
          onRetry={refetchOrderStatus}
        />
      </Space>
    </div>
  );
};

export default ReportsPage;
