import React, { useState } from 'react';
import { Table, Tag, Button, Typography, Space, Empty, Alert, Image } from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { FilterValue, SorterResult } from 'antd/es/table/interface';
import { EditOutlined, ReloadOutlined, PictureOutlined } from '@ant-design/icons';
import { formatCurrencyVND } from '@/lib/format';
import {
  useInventoryVariants,
  useInventorySummary,
} from '../hooks';
import {
  InventorySummaryCards,
  InventoryFilterBar,
  InventoryStockUpdateModal,
  type InventoryFilterValues,
} from '../components';
import {
  INVENTORY_THEME,
  INVENTORY_STOCK_STATUS_CONFIG,

  DEFAULT_PAGE_SIZE,
} from '../constants';
import type {
  InventoryVariantItem,
  InventoryVariantParams,
} from '../api';

const { Title, Text } = Typography;

export const InventoryListPage: React.FC = () => {
  const [filters, setFilters] = useState<InventoryFilterValues>({
    keyword: undefined,
    categoryId: undefined,
    stockStatus: 'ALL',
    productActive: undefined,
    threshold: undefined,
  });

  const [page, setPage] = useState<number>(1);
  const [pageSize, setPageSize] = useState<number>(DEFAULT_PAGE_SIZE);
  const [sortField, setSortField] = useState<string>('stock');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc');

  const [selectedVariant, setSelectedVariant] = useState<InventoryVariantItem | null>(null);
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [updatingVariantId, setUpdatingVariantId] = useState<number | null>(null);

  const queryParams: InventoryVariantParams = {
    keyword: filters.keyword || undefined,
    categoryId: filters.categoryId,
    stockStatus: filters.stockStatus,
    productActive: filters.productActive,
    threshold: filters.threshold,
    page: page - 1,
    pageSize,
    sort: `${sortField},${sortOrder}`,
  };

  const {
    data: variantPage,
    isLoading: isLoadingVariants,
    isError: isVariantsError,
    error: variantsError,
    refetch: refetchVariants,
  } = useInventoryVariants(queryParams);

  const {
    data: summary,
    isLoading: isLoadingSummary,
    refetch: refetchSummary,
  } = useInventorySummary(filters.threshold);

  const handleFilterChange = (updated: Partial<InventoryFilterValues>) => {
    setFilters((prev) => ({ ...prev, ...updated }));
    setPage(1);
  };

  const handleResetFilters = () => {
    setFilters({
      keyword: undefined,
      categoryId: undefined,
      stockStatus: 'ALL',
      productActive: undefined,
      threshold: undefined,
    });
    setPage(1);
    setSortField('stock');
    setSortOrder('asc');
  };

  const handleTableChange = (
    pagination: TablePaginationConfig,
    _filters: Record<string, FilterValue | null>,
    sorter: SorterResult<InventoryVariantItem> | SorterResult<InventoryVariantItem>[]
  ) => {
    if (pagination.current && pagination.current !== page) {
      setPage(pagination.current);
    }
    if (pagination.pageSize && pagination.pageSize !== pageSize) {
      setPageSize(pagination.pageSize);
      setPage(1);
    }

    if (!Array.isArray(sorter) && sorter.field) {
      const fieldStr = String(sorter.field);
      const whitelist = ['stock', 'id', 'productName', 'sku'];
      if (whitelist.includes(fieldStr)) {
        setSortField(fieldStr);
        setSortOrder(sorter.order === 'descend' ? 'desc' : 'asc');
      }
    }
  };

  const handleOpenUpdateModal = (variant: InventoryVariantItem) => {
    setSelectedVariant(variant);
    setIsModalOpen(true);
  };

  const handleCloseUpdateModal = () => {
    setIsModalOpen(false);
    setSelectedVariant(null);
  };

  const handleUpdateSuccess = () => {
    refetchVariants();
    refetchSummary();
  };

  const isFiltering =
    Boolean(filters.keyword) ||
    filters.categoryId !== undefined ||
    filters.stockStatus !== 'ALL' ||
    filters.productActive !== undefined ||
    filters.threshold !== undefined;

  const columns: ColumnsType<InventoryVariantItem> = [
    {
      title: 'Sản phẩm',
      dataIndex: 'productName',
      key: 'productName',
      sorter: true,
      sortOrder: sortField === 'productName' ? (sortOrder === 'asc' ? 'ascend' : 'descend') : null,
      render: (_, record) => {
        return (
          <Space orientation="horizontal" size={12} align="center">
            {record.primaryImageUrl ? (
              <Image
                src={record.primaryImageUrl}
                alt={record.productName ?? 'Product'}
                width={40}
                height={40}
                style={{ objectFit: 'cover', borderRadius: 4, border: `1px solid ${INVENTORY_THEME.border}` }}
                fallback="data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40' viewBox='0 0 40 40'><rect width='40' height='40' fill='%23F5F0EA'/></svg>"
              />
            ) : (
              <div
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: 4,
                  backgroundColor: INVENTORY_THEME.mutedSurface,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  border: `1px solid ${INVENTORY_THEME.border}`,
                }}
              >
                <PictureOutlined style={{ color: INVENTORY_THEME.mutedText, fontSize: 16 }} />
              </div>
            )}
            <div>
              <Text strong style={{ color: INVENTORY_THEME.primary, display: 'block' }}>
                {record.productName ?? '—'}
              </Text>
              <Space orientation="horizontal" size={6} style={{ marginTop: 2 }}>
                {record.categoryName && (
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {record.categoryName}
                  </Text>
                )}
                {record.productIsActive === false && (
                  <Tag
                    style={{
                      borderRadius: 4,
                      backgroundColor: INVENTORY_THEME.mutedSurface,
                      color: INVENTORY_THEME.mutedText,
                      borderColor: INVENTORY_THEME.border,
                      fontSize: 11,
                      lineHeight: '18px',
                      padding: '0 6px',
                    }}
                  >
                    Ngừng bán
                  </Tag>
                )}
              </Space>
            </div>
          </Space>
        );
      },
    },
    {
      title: 'SKU',
      dataIndex: 'sku',
      key: 'sku',
      width: 160,
      sorter: true,
      sortOrder: sortField === 'sku' ? (sortOrder === 'asc' ? 'ascend' : 'descend') : null,
      render: (sku: string) => (
        <Text
          code
          style={{
            color: INVENTORY_THEME.primary,
            fontSize: 12,
            backgroundColor: INVENTORY_THEME.mutedSurface,
          }}
        >
          {sku ?? '—'}
        </Text>
      ),
    },
    {
      title: 'Size / Màu',
      key: 'sizeColor',
      width: 140,
      render: (_, record) => (
        <span style={{ fontSize: 13, color: INVENTORY_THEME.primary }}>
          {record.size ?? '—'} / {record.color ?? '—'}
        </span>
      ),
    },
    {
      title: 'Giá hiệu lực',
      dataIndex: 'price',
      key: 'price',
      width: 140,
      align: 'right',
      render: (price: number) => (
        <span style={{ fontSize: 13, fontWeight: 500, color: INVENTORY_THEME.primary }}>
          {formatCurrencyVND(price)}
        </span>
      ),
    },
    {
      title: 'Tồn kho',
      dataIndex: 'stock',
      key: 'stock',
      width: 110,
      align: 'right',
      sorter: true,
      sortOrder: sortField === 'stock' ? (sortOrder === 'asc' ? 'ascend' : 'descend') : null,
      render: (stock: number) => (
        <span
          style={{
            fontSize: 15,
            fontWeight: 600,
            color: stock === 0 ? INVENTORY_THEME.danger : INVENTORY_THEME.primary,
          }}
        >
          {new Intl.NumberFormat('vi-VN').format(stock ?? 0)}
        </span>
      ),
    },
    {
      title: 'Trạng thái kho',
      dataIndex: 'stockStatus',
      key: 'stockStatus',
      width: 140,
      align: 'center',
      render: (status: keyof typeof INVENTORY_STOCK_STATUS_CONFIG) => {
        const config = INVENTORY_STOCK_STATUS_CONFIG[status] ?? {
          label: status,
          color: INVENTORY_THEME.mutedText,
          bgColor: INVENTORY_THEME.mutedSurface,
          borderColor: INVENTORY_THEME.border,
        };

        return (
          <Tag
            style={{
              color: config.color,
              backgroundColor: config.bgColor,
              borderColor: config.borderColor,
              borderRadius: 4,
              fontWeight: 500,
              padding: '2px 8px',
            }}
          >
            {config.label}
          </Tag>
        );
      },
    },
    {
      title: 'Thao tác',
      key: 'action',
      width: 140,
      align: 'center',
      render: (_, record) => {
        const isUpdatingThis = updatingVariantId === record.id;
        return (
          <Button
            type="text"
            icon={<EditOutlined style={{ color: INVENTORY_THEME.primary }} />}
            onClick={() => handleOpenUpdateModal(record)}
            loading={isUpdatingThis}
            disabled={isUpdatingThis}
            style={{
              borderRadius: 6,
              fontSize: 13,
              color: INVENTORY_THEME.primary,
            }}
          >
            Cập nhật
          </Button>
        );
      },
    },
  ];

  return (
    <div style={{ padding: '0 0 24px' }}>
      {/* Tiêu đề trang */}
      <div style={{ marginBottom: 20 }}>
        <Title level={4} style={{ margin: 0, color: INVENTORY_THEME.primary }}>
          Kho hàng & Tồn kho
        </Title>
        <Text style={{ fontSize: 13, color: INVENTORY_THEME.mutedText }}>
          Theo dõi số lượng tồn kho theo từng biến thể và điều chỉnh tồn kho an toàn bằng cơ chế CAS.
        </Text>
      </div>

      {/* Thẻ thống kê 4 chỉ số */}
      <InventorySummaryCards
        summary={summary}
        isLoading={isLoadingSummary}
        selectedStatus={filters.stockStatus}
        onSelectStockStatus={(status) => handleFilterChange({ stockStatus: status })}
      />

      {/* Thanh bộ lọc */}
      <InventoryFilterBar
        filters={filters}
        onChange={handleFilterChange}
        onReset={handleResetFilters}
      />

      {/* Thông báo lỗi nếu fetch thất bại */}
      {isVariantsError && (
        <Alert
          type="error"
          showIcon
          message="Không thể tải dữ liệu kho hàng"
          description={
            variantsError instanceof Error
              ? variantsError.message
              : 'Đã xảy ra lỗi khi kết nối tới máy chủ.'
          }
          action={
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={() => refetchVariants()}
              style={{ borderRadius: 6 }}
            >
              Thử lại
            </Button>
          }
          style={{ marginBottom: 16, borderRadius: 6 }}
        />
      )}

      {/* Bảng dữ liệu */}
      <div
        style={{
          backgroundColor: INVENTORY_THEME.surface,
          border: `1px solid ${INVENTORY_THEME.border}`,
          borderRadius: 6,
          overflow: 'hidden',
        }}
      >
        <Table<InventoryVariantItem>
          rowKey="id"
          columns={columns}
          dataSource={variantPage?.content ?? []}
          loading={isLoadingVariants}
          onChange={handleTableChange}
          pagination={{
            current: page,
            pageSize,
            total: variantPage?.totalElements ?? 0,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            showTotal: (total, range) => (
              <span style={{ fontSize: 13, color: INVENTORY_THEME.mutedText }}>
                Hiển thị {range[0]}–{range[1]} trong tổng số {new Intl.NumberFormat('vi-VN').format(total)} biến thể
              </span>
            ),
          }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  <span style={{ color: INVENTORY_THEME.mutedText }}>
                    {isFiltering
                      ? 'Không có biến thể khớp bộ lọc.'
                      : 'Chưa có biến thể nào trong kho.'}
                  </span>
                }
              >
                {isFiltering && (
                  <Button
                    size="small"
                    onClick={handleResetFilters}
                    style={{
                      borderRadius: 6,
                      color: INVENTORY_THEME.mutedText,
                      borderColor: INVENTORY_THEME.border,
                    }}
                  >
                    Đặt lại bộ lọc
                  </Button>
                )}
              </Empty>
            ),
          }}
        />
      </div>

      {/* Modal cập nhật tồn kho */}
      <InventoryStockUpdateModal
        variant={selectedVariant}
        open={isModalOpen}
        onClose={handleCloseUpdateModal}
        onSuccess={handleUpdateSuccess}
        onUpdatingChange={(isUpdating) => {
          setUpdatingVariantId(isUpdating && selectedVariant?.id ? selectedVariant.id : null);
        }}
      />
    </div>
  );
};
