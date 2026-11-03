import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Skeleton,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message,
  notification,
  Dropdown,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { MenuProps } from 'antd';
import {
  EditOutlined,
  EyeOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  TagOutlined,
  MoreOutlined,
  StopOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import {
  useVouchers,
  useVoucher,
  useCreateVoucher,
  useUpdateVoucher,
  useUpdateVoucherStatus,
  formatVoucherMoneyVND,
  formatVoucherDateTimeVN,
  getVoucherApiErrorMessage,
  getVoucherApiFieldErrors,
  isConflictError,
  isForbiddenError,
  type DiscountType,
  type VoucherItem,
  type VoucherTimeStatus,
} from '../api';
import {
  voucherCreateSchema,
  voucherUpdateSchema,
  normalizeVoucherCode,
  MAX_VOUCHER_MONEY,
} from '../schemas/voucher.schema';

const { Title, Text } = Typography;

type DiscountTypeFilterValue = 'ALL' | DiscountType;
type ActiveFilterValue = 'ALL' | 'ACTIVE' | 'INACTIVE';
type TimeStatusFilterValue = 'ALL' | VoucherTimeStatus;

interface VoucherFormFields {
  code?: string;
  discountType: DiscountType;
  discountValue?: number | null;
  minOrder?: number | null;
  maxDiscount?: number | null;
  usageLimit?: number | null;
  perUserLimit?: number | null;
  startAt?: Dayjs | null;
  endAt?: Dayjs | null;
  isActive: boolean;
}

const ISO_LOCAL_FORMAT = 'YYYY-MM-DDTHH:mm:ss';
const DISPLAY_DATETIME_FORMAT = 'DD/MM/YYYY HH:mm';

function renderTimeStatusTag(status?: VoucherTimeStatus) {
  if (status === 'RUNNING') {
    return (
      <Tag
        style={{
          margin: 0,
          backgroundColor: '#F5F0EA',
          borderColor: '#16A34A',
          color: '#16A34A',
          fontWeight: 500,
          fontSize: 13,
          padding: '4px 8px',
          borderRadius: 6,
        }}
      >
        Đang diễn ra
      </Tag>
    );
  }
  if (status === 'SCHEDULED') {
    return (
      <Tag
        style={{
          margin: 0,
          backgroundColor: '#F5F0EA',
          borderColor: '#D97706',
          color: '#D97706',
          fontWeight: 500,
          fontSize: 13,
          padding: '4px 8px',
          borderRadius: 6,
        }}
      >
        Sắp diễn ra
      </Tag>
    );
  }
  return (
    <Tag
      style={{
        margin: 0,
        backgroundColor: '#F5F0EA',
        borderColor: '#E4DCD3',
        color: '#6E675F',
        fontWeight: 500,
        fontSize: 13,
        padding: '4px 8px',
        borderRadius: 6,
      }}
    >
      Đã kết thúc
    </Tag>
  );
}

export const VoucherListPage: React.FC = () => {
  const [form] = Form.useForm<VoucherFormFields>();
  const watchedDiscountType = Form.useWatch('discountType', form) ?? 'PERCENT';

  const [searchInput, setSearchInput] = useState<string>('');
  const [debouncedKeyword, setDebouncedKeyword] = useState<string>('');
  const [discountTypeFilter, setDiscountTypeFilter] =
    useState<DiscountTypeFilterValue>('ALL');
  const [activeFilter, setActiveFilter] = useState<ActiveFilterValue>('ALL');
  const [timeStatusFilter, setTimeStatusFilter] =
    useState<TimeStatusFilterValue>('ALL');
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [pageSize, setPageSize] = useState<number>(20);

  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [editingVoucherId, setEditingVoucherId] = useState<number | undefined>(
    undefined
  );
  const [editingInitialItem, setEditingInitialItem] =
    useState<VoucherItem | null>(null);

  const [detailVoucherId, setDetailVoucherId] = useState<number | undefined>(
    undefined
  );
  const [isDetailDrawerOpen, setIsDetailDrawerOpen] = useState<boolean>(false);
  const [togglingId, setTogglingId] = useState<number | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedKeyword(searchInput.trim());
      setCurrentPage(1);
    }, 350);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  const queryParams = {
    page: currentPage - 1,
    size: pageSize,
    keyword: debouncedKeyword || undefined,
    discountType:
      discountTypeFilter === 'ALL' ? undefined : discountTypeFilter,
    isActive:
      activeFilter === 'ALL' ? undefined : activeFilter === 'ACTIVE',
    status: timeStatusFilter === 'ALL' ? undefined : timeStatusFilter,
  };

  const {
    data: vouchersPage,
    isLoading,
    isFetching,
    isError,
    error,
    refetch,
  } = useVouchers(queryParams);

  const {
    data: detailVoucher,
    isLoading: isDetailLoading,
    isError: isDetailError,
    error: detailError,
    refetch: refetchDetail,
  } = useVoucher(isDetailDrawerOpen ? detailVoucherId : undefined);

  const {
    data: fetchedEditVoucher,
    isLoading: isEditDetailLoading,
  } = useVoucher(isModalOpen && editingVoucherId ? editingVoucherId : undefined);

  const createVoucherMutation = useCreateVoucher();
  const updateVoucherMutation = useUpdateVoucher();
  const updateStatusMutation = useUpdateVoucherStatus();

  const populateFormFromVoucher = (voucher: VoucherItem) => {
    form.setFieldsValue({
      code: voucher.code ?? '',
      discountType: voucher.discountType ?? 'PERCENT',
      discountValue: voucher.discountValue ?? undefined,
      minOrder: voucher.minOrder ?? null,
      maxDiscount:
        voucher.discountType === 'PERCENT'
          ? (voucher.maxDiscount ?? null)
          : null,
      usageLimit: voucher.usageLimit ?? null,
      perUserLimit: voucher.perUserLimit ?? 1,
      startAt: voucher.startAt ? dayjs(voucher.startAt) : null,
      endAt: voucher.endAt ? dayjs(voucher.endAt) : null,
      isActive: voucher.isActive ?? true,
    });
  };

  useEffect(() => {
    if (isModalOpen && editingVoucherId && fetchedEditVoucher) {
      populateFormFromVoucher(fetchedEditVoucher);
    }
  }, [isModalOpen, editingVoucherId, fetchedEditVoucher]);

  const handleResetFilters = () => {
    setSearchInput('');
    setDebouncedKeyword('');
    setDiscountTypeFilter('ALL');
    setActiveFilter('ALL');
    setTimeStatusFilter('ALL');
    setCurrentPage(1);
    refetch();
  };

  const handleOpenCreateModal = () => {
    setEditingVoucherId(undefined);
    setEditingInitialItem(null);
    form.resetFields();
    const now = dayjs().second(0).millisecond(0);
    form.setFieldsValue({
      code: '',
      discountType: 'PERCENT',
      discountValue: undefined,
      minOrder: null,
      maxDiscount: null,
      usageLimit: null,
      perUserLimit: 1,
      startAt: now,
      endAt: now.add(7, 'day'),
      isActive: true,
    });
    setIsModalOpen(true);
  };

  const handleOpenEditModal = (voucher: VoucherItem) => {
    setEditingVoucherId(voucher.id);
    setEditingInitialItem(voucher);
    form.resetFields();
    populateFormFromVoucher(voucher);
    setIsModalOpen(true);
  };

  const handleOpenDetailDrawer = (voucherId?: number) => {
    if (!voucherId) return;
    setDetailVoucherId(voucherId);
    setIsDetailDrawerOpen(true);
  };

  const handleCloseDetailDrawer = () => {
    setIsDetailDrawerOpen(false);
  };

  const handleDiscountTypeChange = (nextType: DiscountType) => {
    if (nextType === 'AMOUNT') {
      form.setFieldsValue({ maxDiscount: null });
      form.setFields([{ name: 'maxDiscount', errors: [] }]);
    }
  };

  const clearAllFormFieldErrors = () => {
    const fieldNames: (keyof VoucherFormFields)[] = [
      'code',
      'discountType',
      'discountValue',
      'minOrder',
      'maxDiscount',
      'usageLimit',
      'perUserLimit',
      'startAt',
      'endAt',
      'isActive',
    ];
    form.setFields(fieldNames.map((name) => ({ name, errors: [] })));
  };

  const handleFormSubmit = async () => {
    clearAllFormFieldErrors();
    const rawValues = form.getFieldsValue(true) as VoucherFormFields;

    const candidate = {
      code: rawValues.code ?? '',
      discountType: rawValues.discountType,
      discountValue:
        typeof rawValues.discountValue === 'number'
          ? rawValues.discountValue
          : undefined,
      minOrder:
        typeof rawValues.minOrder === 'number' ? rawValues.minOrder : null,
      maxDiscount:
        rawValues.discountType === 'PERCENT' &&
        typeof rawValues.maxDiscount === 'number'
          ? rawValues.maxDiscount
          : rawValues.discountType === 'AMOUNT' &&
              typeof rawValues.maxDiscount === 'number'
            ? rawValues.maxDiscount
            : null,
      usageLimit:
        typeof rawValues.usageLimit === 'number' ? rawValues.usageLimit : null,
      perUserLimit:
        typeof rawValues.perUserLimit === 'number' ? rawValues.perUserLimit : 1,
      startAt:
        rawValues.startAt && dayjs.isDayjs(rawValues.startAt)
          ? rawValues.startAt.format(ISO_LOCAL_FORMAT)
          : '',
      endAt:
        rawValues.endAt && dayjs.isDayjs(rawValues.endAt)
          ? rawValues.endAt.format(ISO_LOCAL_FORMAT)
          : '',
      isActive: rawValues.isActive ?? true,
    };

    const isEditing = Boolean(editingVoucherId);
    const parseResult = isEditing
      ? voucherUpdateSchema.safeParse(candidate)
      : voucherCreateSchema.safeParse(candidate);

    if (!parseResult.success) {
      const groupedErrors = new Map<keyof VoucherFormFields, string[]>();
      for (const issue of parseResult.error.issues) {
        const fieldKey = (issue.path[0] ?? 'code') as keyof VoucherFormFields;
        const existing = groupedErrors.get(fieldKey) ?? [];
        if (!existing.includes(issue.message)) {
          existing.push(issue.message);
        }
        groupedErrors.set(fieldKey, existing);
      }
      form.setFields(
        Array.from(groupedErrors.entries()).map(([name, errors]) => ({
          name,
          errors,
        }))
      );
      return;
    }

    try {
      if (isEditing && editingVoucherId) {
        const validated = parseResult.data;
        await updateVoucherMutation.mutateAsync({
          id: editingVoucherId,
          payload: {
            discountType: validated.discountType,
            discountValue: validated.discountValue,
            minOrder: validated.minOrder ?? null,
            maxDiscount:
              validated.discountType === 'PERCENT'
                ? (validated.maxDiscount ?? null)
                : null,
            usageLimit: validated.usageLimit ?? null,
            perUserLimit: validated.perUserLimit ?? 1,
            startAt: validated.startAt,
            endAt: validated.endAt,
            isActive: validated.isActive,
          },
        });
        message.success('Cập nhật voucher thành công');
      } else {
        const validated = parseResult.data as typeof voucherCreateSchema._output;
        await createVoucherMutation.mutateAsync({
          code: validated.code,
          discountType: validated.discountType,
          discountValue: validated.discountValue,
          minOrder: validated.minOrder ?? null,
          maxDiscount:
            validated.discountType === 'PERCENT'
              ? (validated.maxDiscount ?? null)
              : null,
          usageLimit: validated.usageLimit ?? null,
          perUserLimit: validated.perUserLimit ?? 1,
          startAt: validated.startAt,
          endAt: validated.endAt,
          isActive: validated.isActive,
        });
        message.success(`Đã tạo voucher "${validated.code}"`);
      }

      setIsModalOpen(false);
      form.resetFields();
    } catch (err: unknown) {
      if (!isEditing && isConflictError(err)) {
        const conflictMsg = getVoucherApiErrorMessage(
          err,
          'Mã voucher đã tồn tại'
        );
        form.setFields([{ name: 'code', errors: [conflictMsg] }]);
        return;
      }

      const fieldErrors = getVoucherApiFieldErrors(err);
      if (fieldErrors && Object.keys(fieldErrors).length > 0) {
        form.setFields(
          Object.entries(fieldErrors).map(([name, errMsg]) => ({
            name: name as keyof VoucherFormFields,
            errors: [errMsg],
          }))
        );
        return;
      }

      notification.error({
        message: isEditing ? 'Không thể cập nhật voucher' : 'Không thể tạo voucher',
        description: getVoucherApiErrorMessage(
          err,
          'Dữ liệu gửi lên không hợp lệ, vui lòng kiểm tra lại.'
        ),
        placement: 'topRight',
      });
    }
  };

  const handleToggleStatus = async (
    record: VoucherItem,
    nextActive: boolean
  ) => {
    if (!record.id) return;
    try {
      setTogglingId(record.id);
      await updateStatusMutation.mutateAsync({
        id: record.id,
        payload: { isActive: nextActive },
      });
      message.success(
        `Đã ${nextActive ? 'bật' : 'tắt'} voucher "${record.code}"`
      );
    } catch (err) {
      notification.error({
        message: 'Không thể cập nhật trạng thái',
        description: getVoucherApiErrorMessage(
          err,
          'Cập nhật trạng thái bật/tắt thất bại.'
        ),
      });
    } finally {
      setTogglingId(null);
    }
  };

  const handleTableChange = (pagination: TablePaginationConfig) => {
    const nextPage = pagination.current ?? 1;
    const nextSize = pagination.pageSize ?? 20;

    if (nextSize !== pageSize) {
      setPageSize(nextSize);
      setCurrentPage(1);
    } else {
      setCurrentPage(nextPage);
    }
  };

  const columns: ColumnsType<VoucherItem> = [
    {
      title: 'Mã voucher',
      dataIndex: 'code',
      key: 'code',
      width: 180,
      render: (code: string, record) => (
        <Space size={8}>
          <TagOutlined style={{ color: '#6E675F', fontSize: 13 }} />
          <code
            style={{
              fontFamily: 'monospace',
              fontSize: 15,
              fontWeight: 600,
              backgroundColor: '#F5F0EA',
              padding: '4px 8px',
              borderRadius: 6,
              border: '1px solid #E4DCD3',
              color: '#18181B',
            }}
          >
            {code}
          </code>
          <span style={{ fontSize: 13, color: '#6E675F' }}>#{record.id}</span>
        </Space>
      ),
    },
    {
      title: 'Mức giảm',
      key: 'discount',
      width: 220,
      render: (_, record) => {
        const isPercent = record.discountType === 'PERCENT';
        return (
          <div>
            <Text
              strong
              style={{
                fontSize: 15,
                fontWeight: 600,
                color: isPercent ? '#C2410C' : '#18181B',
              }}
            >
              {isPercent
                ? `Giảm ${record.discountValue ?? 0}%`
                : `Giảm ${formatVoucherMoneyVND(record.discountValue)}`}
            </Text>
            <div style={{ marginTop: 4 }}>
              <Text style={{ fontSize: 13, color: '#6E675F' }}>
                {isPercent
                  ? record.maxDiscount
                    ? `Tối đa ${formatVoucherMoneyVND(record.maxDiscount)}`
                    : 'Không giới hạn mức giảm'
                  : 'Giảm tiền trực tiếp'}
              </Text>
            </div>
          </div>
        );
      },
    },
    {
      title: 'Đơn tối thiểu',
      dataIndex: 'minOrder',
      key: 'minOrder',
      width: 160,
      render: (minOrder?: number | null) =>
        minOrder !== null && minOrder !== undefined ? (
          <Text style={{ fontSize: 15, color: '#18181B' }}>
            {formatVoucherMoneyVND(minOrder)}
          </Text>
        ) : (
          <Text style={{ fontSize: 13, color: '#6E675F' }}>Không áp dụng</Text>
        ),
    },
    {
      title: 'Giới hạn lượt dùng',
      key: 'limits',
      width: 192,
      render: (_, record) => (
        <div>
          <Text style={{ fontSize: 15, color: '#18181B' }}>
            {record.usageLimit !== null && record.usageLimit !== undefined
              ? `Tổng: ${record.usageLimit} lượt`
              : 'Tổng: Không giới hạn'}
          </Text>
          <div style={{ marginTop: 4 }}>
            <Text style={{ fontSize: 13, color: '#6E675F' }}>
              Mỗi khách: {record.perUserLimit ?? 1} lượt
            </Text>
          </div>
        </div>
      ),
    },
    {
      title: 'Thời gian hiệu lực',
      key: 'period',
      width: 220,
      render: (_, record) => (
        <div>
          <div style={{ fontSize: 13, color: '#18181B' }}>
            Từ: {formatVoucherDateTimeVN(record.startAt)}
          </div>
          <div style={{ fontSize: 13, color: '#6E675F', marginTop: 4 }}>
            Đến: {formatVoucherDateTimeVN(record.endAt)}
          </div>
        </div>
      ),
    },
    {
      title: 'Hiệu lực thời gian',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      align: 'center',
      render: (status?: VoucherTimeStatus) => renderTimeStatusTag(status),
    },
    {
      title: 'Kích hoạt',
      dataIndex: 'isActive',
      key: 'isActive',
      width: 140,
      align: 'center',
      render: (isActive: boolean | undefined, record) => (
        <Switch
          checked={Boolean(isActive)}
          loading={togglingId === record.id}
          checkedChildren="Đang bật"
          unCheckedChildren="Đã tắt"
          onChange={(checked) => handleToggleStatus(record, checked)}
        />
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 90,
      align: 'center',
      fixed: 'right',
      render: (_, record) => {
        const isTogglingThisRecord = togglingId === record.id;

        const actionMenuItems: MenuProps['items'] = [
          {
            key: 'detail',
            icon: <EyeOutlined />,
            label: 'Xem chi tiết',
            onClick: () => handleOpenDetailDrawer(record.id),
          },
          {
            key: 'edit',
            icon: <EditOutlined />,
            label: 'Chỉnh sửa voucher',
            onClick: () => handleOpenEditModal(record),
          },
          {
            type: 'divider',
          },
          {
            key: 'toggle-status',
            icon: record.isActive ? <StopOutlined /> : <CheckCircleOutlined />,
            label: record.isActive ? 'Tắt kích hoạt' : 'Bật kích hoạt',
            danger: record.isActive,
            disabled: isTogglingThisRecord,
            onClick: () => handleToggleStatus(record, !record.isActive),
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
              loading={isTogglingThisRecord}
              aria-label="Thao tác voucher"
            />
          </Dropdown>
        );
      },
    },
  ];

  const vouchers = vouchersPage?.content ?? [];
  const totalElements = vouchersPage?.totalElements ?? 0;
  const hasActiveFilter =
    Boolean(debouncedKeyword) ||
    discountTypeFilter !== 'ALL' ||
    activeFilter !== 'ALL' ||
    timeStatusFilter !== 'ALL';

  const activeModalVoucher = fetchedEditVoucher ?? editingInitialItem;

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 24,
        width: '100%',
        maxWidth: '100%',
        minWidth: 0,
        fontFamily: "'Be Vietnam Pro', sans-serif",
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
            <Title
              level={3}
              style={{
                margin: 0,
                fontSize: 22,
                fontWeight: 600,
                color: '#18181B',
              }}
            >
              Quản lý mã giảm giá
            </Title>
            <Text style={{ fontSize: 13, color: '#6E675F' }}>
              Thiết lập mã voucher, mức giảm, điều kiện đơn tối thiểu và thời gian hiệu lực
            </Text>
          </div>

          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleOpenCreateModal}
            style={{
              backgroundColor: '#18181B',
              borderColor: '#18181B',
              borderRadius: 6,
              fontWeight: 600,
            }}
          >
            Tạo voucher mới
          </Button>
        </div>
      </div>

      <Card
        bordered
        styles={{ body: { padding: 16 } }}
        style={{
          borderRadius: 6,
          boxShadow: 'none',
          border: '1px solid #E4DCD3',
          backgroundColor: '#FFFFFF',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: 12,
          }}
        >
          <Space wrap size={12}>
            <Input
              placeholder="Tìm theo mã voucher (VD: SALE10)..."
              prefix={<SearchOutlined style={{ color: '#6E675F' }} />}
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              allowClear
              style={{
                width: 240,
                borderRadius: 6,
                borderColor: '#E4DCD3',
              }}
            />

            <Select<DiscountTypeFilterValue>
              value={discountTypeFilter}
              onChange={(value) => {
                setDiscountTypeFilter(value);
                setCurrentPage(1);
              }}
              style={{ width: 176 }}
              options={[
                { value: 'ALL', label: 'Tất cả loại giảm' },
                { value: 'PERCENT', label: 'Giảm theo phần trăm (%)' },
                { value: 'AMOUNT', label: 'Giảm số tiền (VND)' },
              ]}
            />

            <Select<TimeStatusFilterValue>
              value={timeStatusFilter}
              onChange={(value) => {
                setTimeStatusFilter(value);
                setCurrentPage(1);
              }}
              style={{ width: 176 }}
              options={[
                { value: 'ALL', label: 'Mọi thời điểm' },
                { value: 'RUNNING', label: 'Đang diễn ra' },
                { value: 'SCHEDULED', label: 'Sắp diễn ra' },
                { value: 'EXPIRED', label: 'Đã kết thúc' },
              ]}
            />

            <Select<ActiveFilterValue>
              value={activeFilter}
              onChange={(value) => {
                setActiveFilter(value);
                setCurrentPage(1);
              }}
              style={{ width: 176 }}
              options={[
                { value: 'ALL', label: 'Tất cả trạng thái bật/tắt' },
                { value: 'ACTIVE', label: 'Đang bật' },
                { value: 'INACTIVE', label: 'Đã tắt' },
              ]}
            />
          </Space>

          <Space size={8}>
            {hasActiveFilter && (
              <Button
                onClick={handleResetFilters}
                style={{ borderRadius: 6, borderColor: '#E4DCD3' }}
              >
                Xóa bộ lọc
              </Button>
            )}
            <Button
              icon={<ReloadOutlined spin={isFetching} />}
              onClick={() => refetch()}
              style={{ borderRadius: 6, borderColor: '#E4DCD3' }}
            >
              Làm mới
            </Button>
          </Space>
        </div>
      </Card>

      {isError && (
        <Alert
          type={isForbiddenError(error) ? 'warning' : 'error'}
          showIcon
          style={{ borderRadius: 6, border: '1px solid #E4DCD3' }}
          message={
            isForbiddenError(error)
              ? 'Bạn không có quyền truy cập danh sách voucher'
              : 'Không thể tải danh sách voucher'
          }
          description={getVoucherApiErrorMessage(
            error,
            'Đã xảy ra lỗi kết nối máy chủ. Vui lòng thử lại.'
          )}
          action={
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={() => refetch()}
              style={{ borderRadius: 6 }}
            >
              Thử lại
            </Button>
          }
        />
      )}

      <Card
        bordered
        styles={{ body: { padding: isLoading ? 24 : 0 } }}
        style={{
          borderRadius: 6,
          boxShadow: 'none',
          border: '1px solid #E4DCD3',
          overflow: 'hidden',
          backgroundColor: '#FFFFFF',
        }}
      >
        {isLoading ? (
          <Skeleton active paragraph={{ rows: 8 }} />
        ) : (
          <Table<VoucherItem>
            columns={columns}
            dataSource={vouchers}
            rowKey={(record) => String(record.id)}
            loading={isFetching}
            scroll={{ x: 'max-content' }}
            onChange={handleTableChange}
            locale={{
              emptyText: (
                <div style={{ padding: '32px 0' }}>
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={
                      hasActiveFilter
                        ? 'Không có voucher nào khớp với bộ lọc hiện tại'
                        : 'Chưa có mã voucher nào được tạo'
                    }
                  />
                </div>
              ),
            }}
            pagination={{
              current: currentPage,
              pageSize,
              total: totalElements,
              showSizeChanger: true,
              pageSizeOptions: ['10', '20', '50', '100'],
              showTotal: (total) => `Tổng cộng ${total} voucher`,
            }}
          />
        )}
      </Card>

      <Modal
        title={
          <span style={{ fontSize: 15, fontWeight: 600, color: '#18181B' }}>
            {editingVoucherId
              ? `Chỉnh sửa voucher ${activeModalVoucher?.code ?? ''}`
              : 'Tạo mã voucher mới'}
          </span>
        }
        open={isModalOpen}
        onOk={handleFormSubmit}
        onCancel={() => {
          setIsModalOpen(false);
          form.resetFields();
        }}
        confirmLoading={
          createVoucherMutation.isPending || updateVoucherMutation.isPending
        }
        okText={editingVoucherId ? 'Lưu thay đổi' : 'Tạo voucher'}
        cancelText="Hủy"
        width={640}
        destroyOnClose
        okButtonProps={{
          style: {
            backgroundColor: '#18181B',
            borderColor: '#18181B',
            borderRadius: 6,
            fontWeight: 600,
          },
        }}
        cancelButtonProps={{
          style: { borderRadius: 6 },
        }}
      >
        {editingVoucherId && isEditDetailLoading && !activeModalVoucher ? (
          <div style={{ padding: '16px 0' }}>
            <Skeleton active paragraph={{ rows: 6 }} />
          </div>
        ) : (
          <Form
            form={form}
            layout="vertical"
            initialValues={{
              discountType: 'PERCENT',
              perUserLimit: 1,
              isActive: true,
            }}
            style={{ marginTop: 16 }}
          >
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  name="code"
                  label="Mã voucher"
                  extra={
                    editingVoucherId
                      ? 'Mã voucher không thể thay đổi sau khi tạo.'
                      : 'Tự động chuẩn hóa in hoa. Gồm A-Z, 0-9, gạch ngang (-), gạch dưới (_), dài 3-50 ký tự.'
                  }
                >
                  <Input
                    placeholder="VD: SUMMER2026"
                    disabled={Boolean(editingVoucherId)}
                    maxLength={50}
                    onBlur={(e) => {
                      if (!editingVoucherId) {
                        form.setFieldsValue({
                          code: normalizeVoucherCode(e.target.value),
                        });
                      }
                    }}
                    style={{
                      borderRadius: 6,
                      fontFamily: 'monospace',
                      fontWeight: 600,
                    }}
                  />
                </Form.Item>
              </Col>

              <Col span={12}>
                <Form.Item name="discountType" label="Loại giảm giá">
                  <Select<DiscountType>
                    onChange={handleDiscountTypeChange}
                    style={{ borderRadius: 6 }}
                    options={[
                      {
                        value: 'PERCENT',
                        label: 'Theo phần trăm (%)',
                      },
                      {
                        value: 'AMOUNT',
                        label: 'Số tiền cố định (VND)',
                      },
                    ]}
                  />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  name="discountValue"
                  label={
                    watchedDiscountType === 'PERCENT'
                      ? 'Phần trăm giảm (1 - 100%)'
                      : 'Số tiền giảm (VND)'
                  }
                >
                  <InputNumber<number>
                    min={1}
                    max={
                      watchedDiscountType === 'PERCENT'
                        ? 100
                        : MAX_VOUCHER_MONEY
                    }
                    precision={0}
                    placeholder={
                      watchedDiscountType === 'PERCENT'
                        ? 'VD: 15'
                        : 'VD: 50.000'
                    }
                    formatter={(value) =>
                      value !== undefined && value !== null
                        ? `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
                        : ''
                    }
                    parser={(value) =>
                      Number((value ?? '').replace(/\./g, ''))
                    }
                    addonAfter={watchedDiscountType === 'PERCENT' ? '%' : 'đ'}
                    style={{ width: '100%', borderRadius: 6 }}
                  />
                </Form.Item>
              </Col>

              <Col span={12}>
                <Form.Item
                  name="maxDiscount"
                  label="Mức giảm tối đa (VND)"
                  extra={
                    watchedDiscountType === 'PERCENT'
                      ? 'Để trống nếu không giới hạn mức giảm tối đa.'
                      : 'Chỉ áp dụng khi giảm theo phần trăm.'
                  }
                >
                  <InputNumber<number>
                    min={1}
                    max={MAX_VOUCHER_MONEY}
                    precision={0}
                    disabled={watchedDiscountType !== 'PERCENT'}
                    placeholder="Không giới hạn"
                    formatter={(value) =>
                      value !== undefined && value !== null
                        ? `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
                        : ''
                    }
                    parser={(value) =>
                      Number((value ?? '').replace(/\./g, ''))
                    }
                    addonAfter="đ"
                    style={{ width: '100%', borderRadius: 6 }}
                  />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  name="minOrder"
                  label="Giá trị đơn tối thiểu (VND)"
                  extra="Để trống nếu áp dụng cho mọi giá trị đơn hàng."
                >
                  <InputNumber<number>
                    min={0}
                    max={MAX_VOUCHER_MONEY}
                    precision={0}
                    placeholder="Không áp dụng"
                    formatter={(value) =>
                      value !== undefined && value !== null
                        ? `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
                        : ''
                    }
                    parser={(value) =>
                      Number((value ?? '').replace(/\./g, ''))
                    }
                    addonAfter="đ"
                    style={{ width: '100%', borderRadius: 6 }}
                  />
                </Form.Item>
              </Col>

              <Col span={6}>
                <Form.Item
                  name="usageLimit"
                  label="Tổng số lượt"
                  extra="Trống = vô hạn"
                >
                  <InputNumber<number>
                    min={1}
                    precision={0}
                    placeholder="Vô hạn"
                    style={{ width: '100%', borderRadius: 6 }}
                  />
                </Form.Item>
              </Col>

              <Col span={6}>
                <Form.Item
                  name="perUserLimit"
                  label="Lượt / khách"
                  extra="Mặc định 1 lượt"
                >
                  <InputNumber<number>
                    min={1}
                    precision={0}
                    placeholder="1"
                    style={{ width: '100%', borderRadius: 6 }}
                  />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item name="startAt" label="Thời gian bắt đầu">
                  <DatePicker
                    showTime={{ format: 'HH:mm' }}
                    format={DISPLAY_DATETIME_FORMAT}
                    placeholder="Chọn ngày giờ bắt đầu"
                    style={{ width: '100%', borderRadius: 6 }}
                  />
                </Form.Item>
              </Col>

              <Col span={12}>
                <Form.Item name="endAt" label="Thời gian kết thúc">
                  <DatePicker
                    showTime={{ format: 'HH:mm' }}
                    format={DISPLAY_DATETIME_FORMAT}
                    placeholder="Chọn ngày giờ kết thúc"
                    style={{ width: '100%', borderRadius: 6 }}
                  />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item
              name="isActive"
              label="Trạng thái kích hoạt"
              valuePropName="checked"
            >
              <Switch
                checkedChildren="Đang bật"
                unCheckedChildren="Đã tắt"
              />
            </Form.Item>
          </Form>
        )}
      </Modal>

      <Drawer
        title={
          <span style={{ fontSize: 15, fontWeight: 600, color: '#18181B' }}>
            Chi tiết mã giảm giá
          </span>
        }
        placement="right"
        width={480}
        open={isDetailDrawerOpen}
        onClose={handleCloseDetailDrawer}
        extra={
          detailVoucher && (
            <Button
              size="small"
              icon={<EditOutlined />}
              onClick={() => {
                handleCloseDetailDrawer();
                handleOpenEditModal(detailVoucher);
              }}
              style={{ borderRadius: 6 }}
            >
              Chỉnh sửa
            </Button>
          )
        }
      >
        {isDetailLoading ? (
          <Skeleton active paragraph={{ rows: 8 }} />
        ) : isDetailError ? (
          <Alert
            type="error"
            showIcon
            style={{ borderRadius: 6, border: '1px solid #E4DCD3' }}
            message="Không thể tải chi tiết voucher"
            description={getVoucherApiErrorMessage(
              detailError,
              'Vui lòng thử lại.'
            )}
            action={
              <Button
                size="small"
                icon={<ReloadOutlined />}
                onClick={() => refetchDetail()}
              >
                Thử lại
              </Button>
            }
          />
        ) : detailVoucher ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
            <div
              style={{
                padding: 16,
                backgroundColor: '#F5F0EA',
                border: '1px solid #E4DCD3',
                borderRadius: 6,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 12,
              }}
            >
              <div>
                <div style={{ fontSize: 13, color: '#6E675F' }}>Mã voucher</div>
                <div
                  style={{
                    fontFamily: 'monospace',
                    fontSize: 22,
                    fontWeight: 700,
                    color: '#18181B',
                    marginTop: 4,
                  }}
                >
                  {detailVoucher.code}
                </div>
              </div>
              <Space direction="vertical" align="end" size={8}>
                {renderTimeStatusTag(detailVoucher.status)}
                <Switch
                  checked={Boolean(detailVoucher.isActive)}
                  loading={togglingId === detailVoucher.id}
                  checkedChildren="Đang bật"
                  unCheckedChildren="Đã tắt"
                  onChange={(checked) =>
                    handleToggleStatus(detailVoucher, checked)
                  }
                />
              </Space>
            </div>

            <Descriptions
              column={1}
              bordered
              size="small"
              labelStyle={{
                width: 176,
                backgroundColor: '#FBF9F5',
                color: '#6E675F',
                fontSize: 13,
                fontWeight: 500,
              }}
              contentStyle={{
                backgroundColor: '#FFFFFF',
                color: '#18181B',
                fontSize: 15,
              }}
            >
              <Descriptions.Item label="ID hệ thống">
                #{detailVoucher.id}
              </Descriptions.Item>
              <Descriptions.Item label="Loại giảm giá">
                {detailVoucher.discountType === 'PERCENT'
                  ? 'Theo phần trăm (%)'
                  : 'Số tiền cố định (VND)'}
              </Descriptions.Item>
              <Descriptions.Item label="Giá trị giảm">
                <Text strong style={{ color: '#C2410C', fontSize: 15 }}>
                  {detailVoucher.discountType === 'PERCENT'
                    ? `${detailVoucher.discountValue ?? 0}%`
                    : formatVoucherMoneyVND(detailVoucher.discountValue)}
                </Text>
              </Descriptions.Item>
              <Descriptions.Item label="Mức giảm tối đa">
                {detailVoucher.discountType === 'PERCENT'
                  ? detailVoucher.maxDiscount !== null &&
                    detailVoucher.maxDiscount !== undefined
                    ? formatVoucherMoneyVND(detailVoucher.maxDiscount)
                    : 'Không giới hạn'
                  : 'Không áp dụng'}
              </Descriptions.Item>
              <Descriptions.Item label="Đơn hàng tối thiểu">
                {detailVoucher.minOrder !== null &&
                detailVoucher.minOrder !== undefined
                  ? formatVoucherMoneyVND(detailVoucher.minOrder)
                  : 'Không áp dụng'}
              </Descriptions.Item>
              <Descriptions.Item label="Tổng lượt sử dụng tối đa">
                {detailVoucher.usageLimit !== null &&
                detailVoucher.usageLimit !== undefined
                  ? `${detailVoucher.usageLimit} lượt`
                  : 'Không giới hạn'}
              </Descriptions.Item>
              <Descriptions.Item label="Giới hạn mỗi khách">
                {detailVoucher.perUserLimit ?? 1} lượt / khách
              </Descriptions.Item>
              <Descriptions.Item label="Thời gian bắt đầu">
                {formatVoucherDateTimeVN(detailVoucher.startAt)}
              </Descriptions.Item>
              <Descriptions.Item label="Thời gian kết thúc">
                {formatVoucherDateTimeVN(detailVoucher.endAt)}
              </Descriptions.Item>
            </Descriptions>
          </div>
        ) : null}
      </Drawer>
    </div>
  );
};

export default VoucherListPage;
