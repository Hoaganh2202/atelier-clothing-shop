import React, { useState, useEffect } from 'react';
import {
  Table,
  Button,
  Input,
  Select,
  Tag,
  Space,
  Typography,
  Card,
  Image,
  message,
  Alert,
  Spin,
  Empty,
  theme,
  Dropdown,
  Modal,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { MenuProps } from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  StopOutlined,
  CheckCircleOutlined,
  SearchOutlined,
  ReloadOutlined,
  MoreOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { formatCurrencyVND } from '@/lib/format';
import {
  useAdminProducts,
  useAdminProduct,
  useAdminCategories,
  useBulkStatus,
  useDeleteProduct,
  getApiErrorMessage,
  type ProductAdminListItem,
  type ProductDetail,
} from '../api';

const { Title, Text } = Typography;

type StatusFilterValue = 'all' | 'active' | 'inactive';
type VariantItem = NonNullable<ProductDetail['variants']>[number];

interface ExpandedVariantsRowProps {
  productId: number;
  isExpanded: boolean;
}

const ExpandedVariantsRow: React.FC<ExpandedVariantsRowProps> = ({ productId, isExpanded }) => {
  const { token } = theme.useToken();
  const { data, isLoading, isError, error, refetch } = useAdminProduct(
    isExpanded ? productId : undefined
  );

  if (isLoading) {
    return (
      <div
        style={{
          padding: 16,
          backgroundColor: '#FBF9F5',
          borderRadius: token.borderRadius,
          border: '1px solid #E4DCD3',
          display: 'flex',
          alignItems: 'center',
          gap: 12,
        }}
      >
        <Spin size="small" />
        <Text style={{ color: '#6E675F', fontSize: 13 }}>
          Đang tải danh sách biến thể...
        </Text>
      </div>
    );
  }

  if (isError) {
    return (
      <Alert
        type="error"
        showIcon
        message="Không thể tải biến thể sản phẩm"
        description={getApiErrorMessage(error, 'Vui lòng thử lại sau.')}
        action={
          <Button size="small" onClick={() => refetch()}>
            Thử lại
          </Button>
        }
      />
    );
  }

  const variants = data?.variants ?? [];

  if (variants.length === 0) {
    return (
      <div
        style={{
          padding: 16,
          backgroundColor: '#FBF9F5',
          borderRadius: token.borderRadius,
          border: '1px solid #E4DCD3',
        }}
      >
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="Sản phẩm này chưa có biến thể nào"
        />
      </div>
    );
  }

  const variantColumns: ColumnsType<VariantItem> = [
    {
      title: 'Mã SKU',
      dataIndex: 'sku',
      key: 'sku',
      render: (sku?: string) => <Text code>{sku ?? '—'}</Text>,
    },
    {
      title: 'Màu sắc',
      dataIndex: 'color',
      key: 'color',
      render: (color?: string) => <Text style={{ color: '#18181B' }}>{color ?? '—'}</Text>,
    },
    {
      title: 'Kích cỡ',
      dataIndex: 'size',
      key: 'size',
      render: (size?: string) => (
        <Tag
          style={{
            margin: 0,
            backgroundColor: '#F5F0EA',
            borderColor: '#E4DCD3',
            color: '#18181B',
          }}
        >
          {size || 'N/A'}
        </Tag>
      ),
    },
    {
      title: 'Giá biến thể',
      key: 'price',
      align: 'right',
      render: (_, record) => (
        <div>
          <Text strong style={{ color: '#18181B' }}>
            {formatCurrencyVND(record.effectivePrice ?? record.price ?? data?.basePrice)}
          </Text>
          {record.price === undefined || record.price === null ? (
            <div>
              <Text style={{ fontSize: 13, color: '#6E675F' }}>Theo giá cơ bản</Text>
            </div>
          ) : null}
        </div>
      ),
    },
    {
      title: 'Tồn kho',
      dataIndex: 'stock',
      key: 'stock',
      align: 'right',
      render: (stock?: number) => {
        const count = stock ?? 0;
        if (count === 0) {
          return <Tag color="error" style={{ margin: 0 }}>Hết hàng (0)</Tag>;
        }
        if (count <= 5) {
          return (
            <Text type="danger" strong>
              {count} chiếc
            </Text>
          );
        }
        return <Text style={{ color: '#18181B' }}>{count} chiếc</Text>;
      },
    },
  ];

  return (
    <div
      style={{
        padding: 16,
        backgroundColor: '#FBF9F5',
        borderRadius: token.borderRadius,
        border: '1px solid #E4DCD3',
      }}
    >
      <div style={{ marginBottom: 8 }}>
        <Text strong style={{ fontSize: 13, color: '#18181B' }}>
          Danh sách quy cách & Biến thể ({variants.length}):
        </Text>
      </div>
      <Table<VariantItem>
        columns={variantColumns}
        dataSource={variants}
        pagination={false}
        size="small"
        scroll={{ x: 'max-content' }}
        rowKey={(record) => String(record.id ?? record.sku)}
      />
    </div>
  );
};

export const ProductListPage: React.FC = () => {
  const navigate = useNavigate();
  const { token } = theme.useToken();

  const [searchInput, setSearchInput] = useState<string>('');
  const [debouncedKeyword, setDebouncedKeyword] = useState<string>('');
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | undefined>(undefined);
  const [statusFilter, setStatusFilter] = useState<StatusFilterValue>('all');
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [pageSize, setPageSize] = useState<number>(20);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [expandedRowKeys, setExpandedRowKeys] = useState<number[]>([]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedKeyword(searchInput.trim());
      setCurrentPage(1);
      setSelectedRowKeys([]);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  const isActiveParam =
    statusFilter === 'all' ? undefined : statusFilter === 'active';

  const {
    data: productsPage,
    isLoading,
    isFetching,
    isError,
    error,
    refetch,
  } = useAdminProducts({
    keyword: debouncedKeyword || undefined,
    categoryId: selectedCategoryId,
    isActive: isActiveParam,
    page: currentPage - 1,
    size: pageSize,
  });

  const { data: categories = [], isLoading: isCategoriesLoading } = useAdminCategories();
  const bulkStatusMutation = useBulkStatus();
  const deleteProductMutation = useDeleteProduct();

  const handleToggleSingleStatus = (record: ProductAdminListItem) => {
    if (!record.id) return;
    const nextActive = !record.isActive;
    bulkStatusMutation.mutate(
      { ids: [record.id], isActive: nextActive },
      {
        onSuccess: () => {
          message.success(
            nextActive
              ? `Đã mở bán sản phẩm "${record.name}"`
              : `Đã ngừng bán sản phẩm "${record.name}"`
          );
        },
        onError: (err) => {
          message.error(getApiErrorMessage(err, 'Không thể cập nhật trạng thái sản phẩm'));
        },
      }
    );
  };

  const handleBulkUpdateStatus = (isActive: boolean) => {
    const ids = selectedRowKeys
      .map((key) => Number(key))
      .filter((id) => !Number.isNaN(id) && id > 0);
    if (ids.length === 0) return;

    bulkStatusMutation.mutate(
      { ids, isActive },
      {
        onSuccess: () => {
          message.success(
            isActive
              ? `Đã mở bán ${ids.length} sản phẩm được chọn`
              : `Đã ngừng bán ${ids.length} sản phẩm được chọn`
          );
          setSelectedRowKeys([]);
        },
        onError: (err) => {
          message.error(getApiErrorMessage(err, 'Không thể cập nhật trạng thái hàng loạt'));
        },
      }
    );
  };

  const confirmDeleteProduct = (record: ProductAdminListItem) => {
    if (!record.id) return;
    Modal.confirm({
      title: 'Xác nhận xóa sản phẩm',
      icon: <ExclamationCircleOutlined style={{ color: '#FF4D4F' }} />,
      content: `Bạn có chắc muốn xóa "${record.name}" khỏi hệ thống?`,
      okText: 'Xóa',
      cancelText: 'Hủy',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteProductMutation.mutateAsync(record.id!);
          setSelectedRowKeys((prev) => prev.filter((key) => Number(key) !== record.id));
          setExpandedRowKeys((prev) => prev.filter((id) => id !== record.id));
          message.success(`Đã xóa sản phẩm "${record.name}" thành công`);
        } catch (err) {
          message.error(getApiErrorMessage(err, 'Không thể xóa sản phẩm'));
        }
      },
    });
  };

  const handleResetFilters = () => {
    setSearchInput('');
    setDebouncedKeyword('');
    setSelectedCategoryId(undefined);
    setStatusFilter('all');
    setCurrentPage(1);
    setSelectedRowKeys([]);
    refetch();
  };

  const productColumns: ColumnsType<ProductAdminListItem> = [
    {
      title: 'Ảnh',
      dataIndex: 'primaryImageUrl',
      key: 'primaryImageUrl',
      width: 72,
      render: (url: string | undefined, record) =>
        url ? (
          <Image
            src={url}
            alt={record.name ?? 'Ảnh sản phẩm'}
            width={48}
            height={48}
            style={{
              borderRadius: token.borderRadius,
              objectFit: 'cover',
              border: '1px solid #E4DCD3',
            }}
            preview={false}
          />
        ) : (
          <div
            style={{
              width: 48,
              height: 48,
              borderRadius: token.borderRadius,
              backgroundColor: '#F5F0EA',
              border: '1px solid #E4DCD3',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 13,
              color: '#6E675F',
            }}
          >
            N/A
          </div>
        ),
    },
    {
      title: 'Tên sản phẩm',
      key: 'name',
      render: (_, record) => (
        <div>
          <Text strong style={{ fontSize: 15, color: '#18181B' }}>
            {record.name}
          </Text>
          <div style={{ marginTop: 4 }}>
            <Text style={{ fontSize: 13, color: '#6E675F' }}>
              ID: #{record.id} • Slug: {record.slug}
            </Text>
          </div>
        </div>
      ),
    },
    {
      title: 'Danh mục',
      dataIndex: 'categoryName',
      key: 'categoryName',
      render: (categoryName?: string) => (
        <Tag
          style={{
            margin: 0,
            backgroundColor: '#F5F0EA',
            borderColor: '#E4DCD3',
            color: '#18181B',
          }}
        >
          {categoryName ?? 'Chưa phân loại'}
        </Tag>
      ),
    },
    {
      title: 'Khoảng giá',
      key: 'priceRange',
      align: 'right',
      render: (_, record) => {
        const minPrice = record.minPrice ?? record.basePrice ?? 0;
        const maxPrice = record.maxPrice ?? record.basePrice ?? 0;
        return (
          <div>
            <Text strong style={{ color: '#18181B' }}>
              {minPrice === maxPrice
                ? formatCurrencyVND(minPrice)
                : `${formatCurrencyVND(minPrice)} - ${formatCurrencyVND(maxPrice)}`}
            </Text>
            <div>
              <Text style={{ fontSize: 13, color: '#6E675F' }}>
                Giá gốc: {formatCurrencyVND(record.basePrice)}
              </Text>
            </div>
          </div>
        );
      },
    },
    {
      title: 'Tổng tồn kho',
      dataIndex: 'totalStock',
      key: 'totalStock',
      align: 'right',
      render: (stock?: number) => {
        const count = stock ?? 0;
        if (count === 0) {
          return (
            <Tag color="error" style={{ margin: 0 }}>
              Hết hàng (0)
            </Tag>
          );
        }
        if (count <= 15) {
          return (
            <Tag color="warning" style={{ margin: 0 }}>
              {count} chiếc
            </Tag>
          );
        }
        return <Text style={{ color: '#18181B' }}>{count} chiếc</Text>;
      },
    },
    {
      title: 'Trạng thái',
      dataIndex: 'isActive',
      key: 'isActive',
      align: 'center',
      render: (isActive?: boolean) =>
        isActive ? (
          <Tag color="success" style={{ margin: 0 }}>
            Đang bán
          </Tag>
        ) : (
          <Tag
            style={{
              margin: 0,
              backgroundColor: '#F5F0EA',
              borderColor: '#E4DCD3',
              color: '#6E675F',
            }}
          >
            Ngừng bán
          </Tag>
        ),
    },
    {
      title: 'Hành động',
      key: 'action',
      align: 'center',
      fixed: 'right',
      width: 100,
      render: (_, record) => {
        const isTogglingThisRecord =
          bulkStatusMutation.isPending &&
          Boolean(bulkStatusMutation.variables?.ids?.includes(record.id ?? 0));
        const isDeletingThisRecord =
          deleteProductMutation.isPending &&
          deleteProductMutation.variables === record.id;

        const actionMenuItems: MenuProps['items'] = [
          {
            key: 'edit',
            icon: <EditOutlined />,
            label: 'Sửa',
            onClick: () => navigate(`/admin/products/${record.id}/edit`),
          },
          {
            key: 'toggle-status',
            icon: record.isActive ? <StopOutlined /> : <CheckCircleOutlined />,
            label: record.isActive ? 'Ngừng bán' : 'Mở bán',
            disabled: isTogglingThisRecord,
            onClick: () => handleToggleSingleStatus(record),
          },
          {
            type: 'divider',
          },
          {
            key: 'delete',
            icon: <DeleteOutlined />,
            label: 'Xóa',
            danger: true,
            disabled: isDeletingThisRecord,
            onClick: () => confirmDeleteProduct(record),
          },
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
              loading={isTogglingThisRecord || isDeletingThisRecord}
              aria-label="Thao tác"
            />
          </Dropdown>
        );
      },
    },
  ];

  const categoryOptions = [
    { value: 'all', label: 'Tất cả danh mục' },
    ...categories
      .filter((c) => c.id !== undefined)
      .map((c) => ({
        value: String(c.id),
        label: c.name ?? `Danh mục #${c.id}`,
      })),
  ];

  const products = productsPage?.content ?? [];
  const totalElements = productsPage?.totalElements ?? 0;

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
        width: '100%',
        maxWidth: '100%',
        minWidth: 0,
      }}
    >
      <div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: 12,
          }}
        >
          <div>
            <Title level={3} style={{ margin: 0, fontWeight: 700, color: '#18181B' }}>
              Danh sách Sản phẩm
            </Title>
            <Text style={{ fontSize: 13, color: '#6E675F' }}>
              Quản lý danh mục sản phẩm, biến thể quy cách và số lượng tồn kho
            </Text>
          </div>

          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate('/admin/products/new')}
          >
            Thêm sản phẩm mới
          </Button>
        </div>
      </div>

      <Card bordered bodyStyle={{ padding: 16 }}>
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
          }}
        >
          <Space wrap size="middle">
            <Input
              placeholder="Tìm theo tên sản phẩm hoặc slug..."
              prefix={<SearchOutlined style={{ color: '#6E675F' }} />}
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              allowClear
              style={{ width: 288 }}
            />

            <Select
              value={selectedCategoryId !== undefined ? String(selectedCategoryId) : 'all'}
              loading={isCategoriesLoading}
              onChange={(val) => {
                setSelectedCategoryId(val === 'all' ? undefined : Number(val));
                setCurrentPage(1);
                setSelectedRowKeys([]);
              }}
              style={{ width: 192 }}
              options={categoryOptions}
            />

            <Select
              value={statusFilter}
              onChange={(val: StatusFilterValue) => {
                setStatusFilter(val);
                setCurrentPage(1);
                setSelectedRowKeys([]);
              }}
              style={{ width: 160 }}
              options={[
                { value: 'all', label: 'Tất cả trạng thái' },
                { value: 'active', label: 'Đang bán' },
                { value: 'inactive', label: 'Ngừng bán' },
              ]}
            />
          </Space>

          <Button icon={<ReloadOutlined />} onClick={handleResetFilters}>
            Đặt lại bộ lọc
          </Button>
        </div>
      </Card>

      {isError && (
        <Alert
          type="error"
          showIcon
          message="Không thể tải danh sách sản phẩm"
          description={getApiErrorMessage(error, 'Đã xảy ra lỗi khi kết nối máy chủ.')}
          action={
            <Button size="small" onClick={() => refetch()}>
              Thử lại
            </Button>
          }
        />
      )}

      {selectedRowKeys.length > 0 && (
        <Alert
          type="info"
          showIcon
          message={
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: 12,
              }}
            >
              <span>
                Đang chọn <strong>{selectedRowKeys.length}</strong> sản phẩm trên bảng
              </span>
              <Space size="middle">
                <Button
                  size="small"
                  loading={bulkStatusMutation.isPending && bulkStatusMutation.variables?.isActive === true}
                  onClick={() => handleBulkUpdateStatus(true)}
                >
                  Mở bán các mục đã chọn
                </Button>
                <Button
                  size="small"
                  loading={bulkStatusMutation.isPending && bulkStatusMutation.variables?.isActive === false}
                  onClick={() => handleBulkUpdateStatus(false)}
                >
                  Ngừng bán các mục đã chọn
                </Button>
                <Button
                  size="small"
                  type="link"
                  onClick={() => setSelectedRowKeys([])}
                >
                  Bỏ chọn
                </Button>
              </Space>
            </div>
          }
        />
      )}

      <Card bordered bodyStyle={{ padding: 0 }} style={{ overflow: 'hidden' }}>
        <Table<ProductAdminListItem>
          columns={productColumns}
          dataSource={products}
          loading={isLoading || isFetching}
          scroll={{ x: 'max-content' }}
          rowKey={(record) => String(record.id)}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="Không tìm thấy sản phẩm nào phù hợp"
              />
            ),
          }}
          pagination={{
            current: currentPage,
            pageSize,
            total: totalElements,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            showTotal: (total) => `Tổng cộng ${total} sản phẩm`,
            onChange: (page, size) => {
              setCurrentPage(page);
              setPageSize(size);
            },
          }}
          rowSelection={{
            selectedRowKeys,
            preserveSelectedRowKeys: true,
            onChange: (keys) => setSelectedRowKeys(keys),
          }}
          expandable={{
            expandedRowKeys: expandedRowKeys.map(String),
            onExpand: (expanded, record) => {
              if (!record.id) return;
              const productId = record.id;
              setExpandedRowKeys((prev) =>
                expanded
                  ? [...prev, productId]
                  : prev.filter((id) => id !== productId)
              );
            },
            expandedRowRender: (record) =>
              record.id ? (
                <ExpandedVariantsRow
                  productId={record.id}
                  isExpanded={expandedRowKeys.includes(record.id)}
                />
              ) : null,
            rowExpandable: (record) => Boolean(record.id),
          }}
        />
      </Card>
    </div>
  );
};

export default ProductListPage;
