import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Input,
  List,
  Popconfirm,
  Row,
  Select,
  Skeleton,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { formatCurrencyVND } from '@/lib/format';
import {
  formatPromotionDateTimeVN,
  getAdminPromotionById,
  getApiErrorMessage,
  type PromotionDetail,
  type PromotionListItem,
} from '../api/promotion.api';
import {
  useDeletePromotion,
  usePromotions,
  useUpdatePromotionStatus,
} from '../hooks';
import { PromotionFormModal } from '../components/PromotionFormModal';
import { PROMOTION_STATUS_STYLES, PROMOTION_THEME } from '../constants';

const { Title, Text } = Typography;

type DiscountTypeFilter = 'ALL' | 'PERCENT' | 'AMOUNT';
type ActiveFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';
type StatusFilter = 'ALL' | 'SCHEDULED' | 'RUNNING' | 'EXPIRED';

function renderPromotionStatusTag(status?: string) {
  switch (status) {
    case 'RUNNING':
      return (
        <Tag
          style={{
            margin: 0,
            backgroundColor: PROMOTION_STATUS_STYLES.RUNNING.backgroundColor,
            borderColor: PROMOTION_STATUS_STYLES.RUNNING.borderColor,
            color: PROMOTION_STATUS_STYLES.RUNNING.color,
            fontWeight: 500,
            fontSize: 12,
            padding: '2px 8px',
            borderRadius: 6,
          }}
        >
          Đang diễn ra
        </Tag>
      );
    case 'SCHEDULED':
      return (
        <Tag
          style={{
            margin: 0,
            backgroundColor: PROMOTION_STATUS_STYLES.SCHEDULED.backgroundColor,
            borderColor: PROMOTION_STATUS_STYLES.SCHEDULED.borderColor,
            color: PROMOTION_STATUS_STYLES.SCHEDULED.color,
            fontWeight: 500,
            fontSize: 12,
            padding: '2px 8px',
            borderRadius: 6,
          }}
        >
          Sắp diễn ra
        </Tag>
      );
    case 'EXPIRED':
      return (
        <Tag
          style={{
            margin: 0,
            backgroundColor: PROMOTION_STATUS_STYLES.EXPIRED.backgroundColor,
            borderColor: PROMOTION_STATUS_STYLES.EXPIRED.borderColor,
            color: PROMOTION_STATUS_STYLES.EXPIRED.color,
            fontWeight: 500,
            fontSize: 12,
            padding: '2px 8px',
            borderRadius: 6,
          }}
        >
          Đã kết thúc
        </Tag>
      );
    default:
      return (
        <Tag
          style={{
            borderRadius: 6,
            color: PROMOTION_THEME.mutedText,
            borderColor: PROMOTION_THEME.border,
            backgroundColor: PROMOTION_THEME.mutedSurface,
          }}
        >
          {status ?? '—'}
        </Tag>
      );
  }
}

export const PromotionListPage: React.FC = () => {
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [pageSize, setPageSize] = useState<number>(10);

  const [searchInput, setSearchInput] = useState<string>('');
  const [debouncedKeyword, setDebouncedKeyword] = useState<string>('');
  const [discountTypeFilter, setDiscountTypeFilter] = useState<DiscountTypeFilter>('ALL');
  const [activeFilter, setActiveFilter] = useState<ActiveFilter>('ALL');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingPromotionId, setEditingPromotionId] = useState<number | undefined>(undefined);

  const [isDetailDrawerOpen, setIsDetailDrawerOpen] = useState(false);
  const [detailData, setDetailData] = useState<PromotionDetail | null>(null);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);

  const [updatingStatusId, setUpdatingStatusId] = useState<number | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedKeyword(searchInput.trim());
      setCurrentPage(1);
    }, 400);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  const queryParams = useMemo(() => {
    return {
      page: currentPage - 1,
      size: pageSize,
      keyword: debouncedKeyword || undefined,
      discountType:
        discountTypeFilter === 'ALL' ? undefined : discountTypeFilter,
      isActive:
        activeFilter === 'ALL' ? undefined : activeFilter === 'ACTIVE',
      status: statusFilter === 'ALL' ? undefined : statusFilter,
    };
  }, [
    currentPage,
    pageSize,
    debouncedKeyword,
    discountTypeFilter,
    activeFilter,
    statusFilter,
  ]);

  const {
    data: promotionPage,
    isLoading,
    isFetching,
    isError,
    error,
    refetch,
  } = usePromotions(queryParams);

  const updateStatusMutation = useUpdatePromotionStatus();
  const deletePromotionMutation = useDeletePromotion();

  const handleOpenCreateModal = () => {
    setEditingPromotionId(undefined);
    setIsModalOpen(true);
  };

  const handleOpenEditModal = (promotion: PromotionListItem) => {
    if (typeof promotion.id === 'number') {
      setEditingPromotionId(promotion.id);
      setIsModalOpen(true);
    }
  };

  const handleOpenDetail = (id?: number) => {
    if (!id) return;
    setIsDetailDrawerOpen(true);
    setIsLoadingDetail(true);
    getAdminPromotionById(id)
      .then((data) => setDetailData(data))
      .catch((err) => {
        message.error(getApiErrorMessage(err, 'Không thể tải chi tiết khuyến mãi'));
        setIsDetailDrawerOpen(false);
      })
      .finally(() => setIsLoadingDetail(false));
  };

  const handleStatusToggle = async (record: PromotionListItem, nextChecked: boolean) => {
    if (typeof record.id !== 'number') return;
    try {
      setUpdatingStatusId(record.id);
      await updateStatusMutation.mutateAsync({
        id: record.id,
        payload: { isActive: nextChecked },
      });
      message.success(
        nextChecked
          ? `Đã kích hoạt khuyến mãi "${record.name}"`
          : `Đã tắt khuyến mãi "${record.name}"`
      );
    } catch (err: unknown) {
      message.error(getApiErrorMessage(err, 'Không thể cập nhật trạng thái'));
    } finally {
      setUpdatingStatusId(null);
    }
  };

  const handleDeletePromotion = async (record: PromotionListItem) => {
    if (typeof record.id !== 'number') return;
    try {
      await deletePromotionMutation.mutateAsync(record.id);
      message.success(`Đã xóa khuyến mãi "${record.name}"`);
    } catch (err: unknown) {
      message.error(getApiErrorMessage(err, 'Không thể xóa khuyến mãi'));
    }
  };

  const handleResetFilters = () => {
    setSearchInput('');
    setDebouncedKeyword('');
    setDiscountTypeFilter('ALL');
    setActiveFilter('ALL');
    setStatusFilter('ALL');
    setCurrentPage(1);
  };

  const hasActiveFilters =
    Boolean(debouncedKeyword) ||
    discountTypeFilter !== 'ALL' ||
    activeFilter !== 'ALL' ||
    statusFilter !== 'ALL';

  const columns: ColumnsType<PromotionListItem> = [
    {
      title: 'Tên chương trình',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record) => (
        <Space direction="vertical" size={2}>
          <Text
            strong
            style={{ color: PROMOTION_THEME.primary, cursor: 'pointer' }}
            onClick={() => handleOpenDetail(record.id)}
          >
            {name}
          </Text>
          <Text style={{ fontSize: 12, color: PROMOTION_THEME.mutedText }}>
            Mã định danh: #{record.id}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Mức giảm',
      key: 'discount',
      width: 140,
      render: (_, record) => {
        if (record.discountType === 'PERCENT') {
          return (
            <Tag
              style={{
                borderRadius: 6,
                fontWeight: 600,
                color: PROMOTION_THEME.warning,
                backgroundColor: PROMOTION_THEME.mutedSurface,
                borderColor: PROMOTION_THEME.warning,
                padding: '2px 8px',
              }}
            >
              -{record.discountValue}%
            </Tag>
          );
        }
        return (
          <Text strong style={{ color: PROMOTION_THEME.primary }}>
            -{formatCurrencyVND(record.discountValue)}
          </Text>
        );
      },
    },
    {
      title: 'Thời gian áp dụng',
      key: 'timeRange',
      width: 220,
      render: (_, record) => (
        <Space direction="vertical" size={2} style={{ fontSize: 13 }}>
          <div>
            <Text style={{ color: PROMOTION_THEME.mutedText }}>Từ: </Text>
            <Text>{formatPromotionDateTimeVN(record.startAt)}</Text>
          </div>
          <div>
            <Text style={{ color: PROMOTION_THEME.mutedText }}>Đến: </Text>
            <Text>{formatPromotionDateTimeVN(record.endAt)}</Text>
          </div>
        </Space>
      ),
    },
    {
      title: 'Tiến độ',
      key: 'status',
      width: 130,
      render: (_, record) => renderPromotionStatusTag(record.status),
    },
    {
      title: 'Sản phẩm',
      key: 'productCount',
      width: 110,
      align: 'center',
      render: (_, record) => (
        <Tooltip title="Xem danh sách sản phẩm áp dụng">
          <Button
            type="text"
            size="small"
            onClick={() => handleOpenDetail(record.id)}
            style={{ borderRadius: 6, fontWeight: 500 }}
          >
            {record.productCount ?? 0} SP
          </Button>
        </Tooltip>
      ),
    },
    {
      title: 'Kích hoạt',
      key: 'isActive',
      width: 100,
      align: 'center',
      render: (_, record) => (
        <Switch
          checked={record.isActive}
          loading={updatingStatusId === record.id}
          onChange={(checked) => handleStatusToggle(record, checked)}
          size="small"
        />
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 110,
      align: 'center',
      render: (_, record) => (
        <Space size={4}>
          <Tooltip title="Chỉnh sửa">
            <Button
              type="text"
              size="small"
              icon={<EditOutlined style={{ color: PROMOTION_THEME.primary }} />}
              onClick={() => handleOpenEditModal(record)}
            />
          </Tooltip>
          <Tooltip title="Xóa khuyến mãi">
            <Popconfirm
              title="Xác nhận xóa khuyến mãi"
              description={`Bạn có chắc muốn xóa khuyến mãi "${record.name}"?`}
              okText="Xóa"
              cancelText="Hủy"
              okButtonProps={{ danger: true, style: { borderRadius: 6 } }}
              cancelButtonProps={{ style: { borderRadius: 6 } }}
              onConfirm={() => handleDeletePromotion(record)}
            >
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
              />
            </Popconfirm>
          </Tooltip>
        </Space>
      ),
    },
  ];

  const handleTableChange = (pagination: TablePaginationConfig) => {
    if (pagination.current && pagination.current !== currentPage) {
      setCurrentPage(pagination.current);
    }
    if (pagination.pageSize && pagination.pageSize !== pageSize) {
      setPageSize(pagination.pageSize);
      setCurrentPage(1);
    }
  };

  return (
    <div style={{ padding: '0 0 32px 0' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 20,
        }}
      >
        <div>
          <Title level={4} style={{ margin: 0, color: PROMOTION_THEME.primary }}>
            Quản lý khuyến mãi
          </Title>
          <Text style={{ fontSize: 13, color: PROMOTION_THEME.mutedText }}>
            Thiết lập các chương trình giảm giá và phân bổ theo sản phẩm
          </Text>
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={handleOpenCreateModal}
          style={{
            borderRadius: 6,
            backgroundColor: PROMOTION_THEME.primary,
            borderColor: PROMOTION_THEME.primary,
            fontWeight: 500,
          }}
        >
          Tạo khuyến mãi
        </Button>
      </div>

      <Card
        style={{
          borderRadius: 6,
          borderColor: PROMOTION_THEME.border,
          marginBottom: 16,
          boxShadow: 'none',
        }}
        styles={{ body: { padding: 16 } }}
      >
        <Row gutter={[12, 12]} align="middle">
          <Col xs={24} sm={12} md={7}>
            <Input
              placeholder="Tìm theo tên khuyến mãi..."
              prefix={<SearchOutlined style={{ color: PROMOTION_THEME.mutedText }} />}
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              allowClear
              style={{ borderRadius: 6 }}
            />
          </Col>

          <Col xs={12} sm={6} md={4}>
            <Select
              style={{ width: '100%', borderRadius: 6 }}
              value={discountTypeFilter}
              onChange={(val) => {
                setDiscountTypeFilter(val);
                setCurrentPage(1);
              }}
              options={[
                { value: 'ALL', label: 'Tất cả hình thức' },
                { value: 'PERCENT', label: 'Phần trăm (%)' },
                { value: 'AMOUNT', label: 'Số tiền (VND)' },
              ]}
            />
          </Col>

          <Col xs={12} sm={6} md={4}>
            <Select
              style={{ width: '100%', borderRadius: 6 }}
              value={statusFilter}
              onChange={(val) => {
                setStatusFilter(val);
                setCurrentPage(1);
              }}
              options={[
                { value: 'ALL', label: 'Tất cả tiến độ' },
                { value: 'RUNNING', label: 'Đang diễn ra' },
                { value: 'SCHEDULED', label: 'Sắp diễn ra' },
                { value: 'EXPIRED', label: 'Đã kết thúc' },
              ]}
            />
          </Col>

          <Col xs={12} sm={6} md={4}>
            <Select
              style={{ width: '100%', borderRadius: 6 }}
              value={activeFilter}
              onChange={(val) => {
                setActiveFilter(val);
                setCurrentPage(1);
              }}
              options={[
                { value: 'ALL', label: 'Tất cả trạng thái' },
                { value: 'ACTIVE', label: 'Đang bật' },
                { value: 'INACTIVE', label: 'Đang tắt' },
              ]}
            />
          </Col>

          <Col xs={12} sm={6} md={5} style={{ textAlign: 'right' }}>
            {hasActiveFilters && (
              <Button
                icon={<ReloadOutlined />}
                onClick={handleResetFilters}
                style={{ borderRadius: 6 }}
              >
                Đặt lại lọc
              </Button>
            )}
          </Col>
        </Row>
      </Card>

      {isError && (
        <Alert
          type="error"
          showIcon
          message="Không thể tải dữ liệu khuyến mãi"
          description={getApiErrorMessage(error, 'Đã xảy ra lỗi khi kết nối với máy chủ.')}
          action={
            <Button size="small" onClick={() => refetch()} style={{ borderRadius: 6 }}>
              Thử lại
            </Button>
          }
          style={{ marginBottom: 16, borderRadius: 6, borderColor: PROMOTION_THEME.border }}
        />
      )}

      <Card
        style={{
          borderRadius: 6,
          borderColor: PROMOTION_THEME.border,
          boxShadow: 'none',
        }}
        styles={{ body: { padding: 0 } }}
      >
        {isLoading ? (
          <div style={{ padding: 24 }}>
            <Skeleton active paragraph={{ rows: 6 }} />
          </div>
        ) : (
          <Table<PromotionListItem>
            rowKey="id"
            columns={columns}
            dataSource={promotionPage?.content ?? []}
            loading={isFetching}
            pagination={{
              current: currentPage,
              pageSize,
              total: promotionPage?.totalElements ?? 0,
              showSizeChanger: true,
              pageSizeOptions: ['10', '20', '50'],
              showTotal: (total) => `Tổng số ${total} chương trình khuyến mãi`,
            }}
            onChange={handleTableChange}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={
                    hasActiveFilters
                      ? 'Không tìm thấy khuyến mãi nào khớp với bộ lọc hiện tại'
                      : 'Chưa có chương trình khuyến mãi nào được tạo'
                  }
                >
                  {!hasActiveFilters && (
                    <Button
                      type="primary"
                      icon={<PlusOutlined />}
                      onClick={handleOpenCreateModal}
                      style={{
                        borderRadius: 6,
                        backgroundColor: PROMOTION_THEME.primary,
                        borderColor: PROMOTION_THEME.primary,
                      }}
                    >
                      Tạo khuyến mãi ngay
                    </Button>
                  )}
                </Empty>
              ),
            }}
          />
        )}
      </Card>

      <PromotionFormModal
        open={isModalOpen}
        promotionId={editingPromotionId}
        onClose={() => setIsModalOpen(false)}
        onSuccess={() => refetch()}
      />

      <Drawer
        title={
          <span style={{ fontWeight: 600, color: PROMOTION_THEME.primary }}>
            Chi tiết khuyến mãi #{detailData?.id}
          </span>
        }
        open={isDetailDrawerOpen}
        onClose={() => setIsDetailDrawerOpen(false)}
        width={500}
        styles={{
          body: { padding: 24 },
          header: { borderBottom: `1px solid ${PROMOTION_THEME.border}` },
        }}
      >
        {isLoadingDetail || !detailData ? (
          <Skeleton active paragraph={{ rows: 8 }} />
        ) : (
          <Space direction="vertical" size={20} style={{ width: '100%' }}>
            <div>
              <Title level={5} style={{ margin: 0, color: PROMOTION_THEME.primary }}>
                {detailData.name}
              </Title>
              <Space style={{ marginTop: 8 }}>
                {renderPromotionStatusTag(detailData.status)}
                <Tag
                  style={{
                    borderRadius: 6,
                    color: detailData.isActive ? PROMOTION_THEME.success : PROMOTION_THEME.mutedText,
                    backgroundColor: PROMOTION_THEME.mutedSurface,
                    borderColor: detailData.isActive ? PROMOTION_THEME.success : PROMOTION_THEME.border,
                  }}
                >
                  {detailData.isActive ? 'Đang bật' : 'Đang tắt'}
                </Tag>
              </Space>
            </div>

            <Card
              size="small"
              style={{
                borderRadius: 6,
                borderColor: PROMOTION_THEME.border,
                backgroundColor: PROMOTION_THEME.mutedSurface,
              }}
            >
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Text style={{ color: PROMOTION_THEME.mutedText }}>Hình thức giảm:</Text>
                  <Text strong>
                    {detailData.discountType === 'PERCENT'
                      ? `Giảm ${detailData.discountValue}%`
                      : `Giảm ${formatCurrencyVND(detailData.discountValue)}`}
                  </Text>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Text style={{ color: PROMOTION_THEME.mutedText }}>Bắt đầu:</Text>
                  <Text>{formatPromotionDateTimeVN(detailData.startAt)}</Text>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <Text style={{ color: PROMOTION_THEME.mutedText }}>Kết thúc:</Text>
                  <Text>{formatPromotionDateTimeVN(detailData.endAt)}</Text>
                </div>
              </Space>
            </Card>

            <div>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginBottom: 8,
                }}
              >
                <Text strong>
                  Sản phẩm áp dụng ({detailData.products?.length ?? 0})
                </Text>
              </div>

              {(detailData.products?.length ?? 0) === 0 ? (
                <Text style={{ fontSize: 13, fontStyle: 'italic', color: PROMOTION_THEME.mutedText }}>
                  Chưa gán sản phẩm cụ thể nào cho khuyến mãi này.
                </Text>
              ) : (
                <List
                  size="small"
                  bordered
                  style={{
                    borderRadius: 6,
                    maxHeight: 360,
                    overflowY: 'auto',
                    borderColor: PROMOTION_THEME.border,
                  }}
                  dataSource={detailData.products ?? []}
                  renderItem={(item) => (
                    <List.Item
                      key={item.id}
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                      }}
                    >
                      <Space direction="vertical" size={2}>
                        <Text strong style={{ fontSize: 13, color: PROMOTION_THEME.primary }}>
                          {item.name}
                        </Text>
                        <Text style={{ fontSize: 12, color: PROMOTION_THEME.mutedText }}>
                          Mã sản phẩm: #{item.id}
                        </Text>
                      </Space>
                      {item.basePrice !== undefined && (
                        <div style={{ textAlign: 'right' }}>
                          <span style={{ fontSize: 13, color: PROMOTION_THEME.mutedText }}>
                            Giá gốc: {formatCurrencyVND(item.basePrice)}
                          </span>
                        </div>
                      )}
                    </List.Item>
                  )}
                />
              )}
            </div>

            <div style={{ display: 'flex', gap: 12, marginTop: 12 }}>
              <Button
                type="primary"
                icon={<EditOutlined />}
                onClick={() => {
                  setIsDetailDrawerOpen(false);
                  if (typeof detailData.id === 'number') {
                    setEditingPromotionId(detailData.id);
                    setIsModalOpen(true);
                  }
                }}
                style={{
                  flex: 1,
                  borderRadius: 6,
                  backgroundColor: PROMOTION_THEME.primary,
                  borderColor: PROMOTION_THEME.primary,
                }}
              >
                Sửa khuyến mãi
              </Button>
            </div>
          </Space>
        )}
      </Drawer>
    </div>
  );
};
