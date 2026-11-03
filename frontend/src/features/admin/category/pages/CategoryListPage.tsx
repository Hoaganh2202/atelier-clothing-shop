import React, { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
  notification,
  theme,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  AppstoreOutlined,
  DeleteOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  FolderOpenOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  TagOutlined,
} from '@ant-design/icons';
import {
  useAdminCategories,
  useCreateCategory,
  useUpdateCategory,
  useDeleteCategory,
  useUpdateCategoryStatus,
  generateCategorySlug,
  getApiErrorMessage,
  getApiFieldErrors,
  type CategoryItem,
  type CategoryUpsertPayload,
} from '../api';

const { Title, Text } = Typography;

interface CategoryTreeNode extends CategoryItem {
  key: number;
  level: 1 | 2;
  children?: CategoryTreeNode[];
}

interface CategoryFormData {
  name: string;
  slug?: string;
  parentId?: number | null;
  isActive: boolean;
}

export const CategoryListPage: React.FC = () => {
  const { token } = theme.useToken();
  const [form] = Form.useForm<CategoryFormData>();

  // Filter & Search states
  const [searchKeyword, setSearchKeyword] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive'>('all');

  // Modal states
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);
  const [editingCategory, setEditingCategory] = useState<CategoryItem | null>(null);
  const [slugManuallyEdited, setSlugManuallyEdited] = useState<boolean>(false);
  const [togglingId, setTogglingId] = useState<number | null>(null);
  const [expandedRowKeys, setExpandedRowKeys] = useState<React.Key[]>([]);

  // Queries & Mutations
  const { data: rawCategories = [], isLoading, isFetching, refetch } = useAdminCategories();
  const createCategoryMutation = useCreateCategory();
  const updateCategoryMutation = useUpdateCategory();
  const deleteCategoryMutation = useDeleteCategory();
  const updateStatusMutation = useUpdateCategoryStatus();

  // Auto-expand all parent categories having children
  useEffect(() => {
    if (rawCategories.length > 0) {
      const parentIds = rawCategories
        .filter((c) => c.parentId && typeof c.parentId === 'number')
        .map((c) => c.parentId as number)
        .filter((id, index, self) => self.indexOf(id) === index);

      setExpandedRowKeys((prev) => {
        const merged = new Set([...prev, ...parentIds]);
        return Array.from(merged);
      });
    }
  }, [rawCategories]);

  // Root categories (level 1) available for parent dropdown
  const rootCategories = useMemo(() => {
    return rawCategories.filter((c) => !c.parentId);
  }, [rawCategories]);

  // Construct tree data
  const treeData = useMemo(() => {
    const parentMap = new Map<number, CategoryTreeNode>();
    const childMap = new Map<number, CategoryTreeNode[]>();

    // 1. Separate parents and children
    for (const cat of rawCategories) {
      if (!cat.id) continue;
      if (!cat.parentId) {
        parentMap.set(cat.id, {
          ...cat,
          key: cat.id,
          level: 1,
        });
      } else {
        const list = childMap.get(cat.parentId) || [];
        list.push({
          ...cat,
          key: cat.id,
          level: 2,
        });
        childMap.set(cat.parentId, list);
      }
    }

    // 2. Attach children to parents
    const roots: CategoryTreeNode[] = [];
    parentMap.forEach((parent, parentId) => {
      const children = childMap.get(parentId);
      if (children && children.length > 0) {
        parent.children = children;
      } else {
        delete parent.children;
      }
      roots.push(parent);
    });

    // Handle any orphaned children whose parent was not found (fallback to root level)
    childMap.forEach((children, parentId) => {
      if (!parentMap.has(parentId)) {
        for (const orphan of children) {
          roots.push(orphan);
        }
      }
    });

    // 3. Filter by keyword and status
    const normalizedKeyword = searchKeyword.trim().toLowerCase();
    if (!normalizedKeyword && statusFilter === 'all') {
      return roots;
    }

    const matchesFilter = (item: CategoryTreeNode): boolean => {
      const matchKeyword =
        !normalizedKeyword ||
        (item.name?.toLowerCase().includes(normalizedKeyword) ?? false) ||
        (item.slug?.toLowerCase().includes(normalizedKeyword) ?? false);

      const matchStatus =
        statusFilter === 'all' ||
        (statusFilter === 'active' && item.isActive) ||
        (statusFilter === 'inactive' && !item.isActive);

      return matchKeyword && matchStatus;
    };

    const filteredRoots: CategoryTreeNode[] = [];
    for (const root of roots) {
      const filteredChildren = (root.children || []).filter(matchesFilter);
      const rootMatches = matchesFilter(root);

      if (rootMatches || filteredChildren.length > 0) {
        filteredRoots.push({
          ...root,
          children: filteredChildren.length > 0 ? filteredChildren : undefined,
        });
      }
    }

    return filteredRoots;
  }, [rawCategories, searchKeyword, statusFilter]);

  // Open modal for creating new root category or child category
  const handleOpenCreateModal = (prefillParentId?: number) => {
    setEditingCategory(null);
    setSlugManuallyEdited(false);
    form.resetFields();
    form.setFieldsValue({
      name: '',
      slug: '',
      parentId: prefillParentId ?? null,
      isActive: true,
    });
    setIsModalOpen(true);
  };

  // Open modal for editing existing category
  const handleOpenEditModal = (category: CategoryItem) => {
    setEditingCategory(category);
    setSlugManuallyEdited(true);
    form.resetFields();
    form.setFieldsValue({
      name: category.name || '',
      slug: category.slug || '',
      parentId: category.parentId ?? null,
      isActive: category.isActive ?? true,
    });
    setIsModalOpen(true);
  };

  // Auto-generate slug when name changes (only if slug hasn't been manually edited)
  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    if (!slugManuallyEdited) {
      form.setFieldsValue({ slug: generateCategorySlug(val) });
    }
  };

  // Form submit handler
  const handleFormSubmit = async () => {
    try {
      const values = await form.validateFields();
      const payload: CategoryUpsertPayload = {
        name: values.name.trim(),
        slug: values.slug?.trim() ? values.slug.trim().toLowerCase() : undefined,
        parentId: values.parentId ? Number(values.parentId) : undefined,
        isActive: values.isActive,
      };

      if (editingCategory && editingCategory.id) {
        await updateCategoryMutation.mutateAsync({
          id: editingCategory.id,
          payload,
        });
        message.success('Cập nhật danh mục thành công');
      } else {
        await createCategoryMutation.mutateAsync(payload);
        message.success('Tạo danh mục mới thành công');
      }

      setIsModalOpen(false);
      form.resetFields();
    } catch (err: unknown) {
      const fieldErrors = getApiFieldErrors(err);
      if (fieldErrors) {
        const errorList = Object.entries(fieldErrors).map(([name, errMessage]) => ({
          name: name as keyof CategoryFormData,
          errors: [errMessage],
        }));
        form.setFields(errorList);
      } else {
        notification.error({
          message: editingCategory ? 'Lỗi cập nhật danh mục' : 'Lỗi tạo danh mục',
          description: getApiErrorMessage(
            err,
            'Không thể lưu thông tin danh mục. Vui lòng kiểm tra lại.'
          ),
          placement: 'topRight',
        });
      }
    }
  };

  // Toggle status handler with safety confirmation if category has products
  const handleToggleStatus = (record: CategoryTreeNode, nextActive: boolean) => {
    const executeToggle = async () => {
      try {
        setTogglingId(record.id!);
        await updateStatusMutation.mutateAsync({
          id: record.id!,
          payload: { isActive: nextActive },
        });
        message.success(
          `Đã ${nextActive ? 'bật' : 'tắt'} hoạt động danh mục "${record.name}"`
        );
      } catch (err) {
        notification.error({
          message: 'Lỗi cập nhật trạng thái',
          description: getApiErrorMessage(err, 'Không thể cập nhật trạng thái danh mục'),
        });
      } finally {
        setTogglingId(null);
      }
    };

    if (!nextActive && (record.productCount ?? 0) > 0) {
      Modal.confirm({
        title: `Tắt hoạt động danh mục "${record.name}"?`,
        icon: <ExclamationCircleOutlined style={{ color: '#FAAD14' }} />,
        content: (
          <div>
            <p>
              Danh mục này hiện đang có <strong>{record.productCount}</strong> sản phẩm trực thuộc.
            </p>
            <p style={{ color: '#6E675F', fontSize: 13, margin: 0 }}>
              Khi tắt hoạt động, các sản phẩm thuộc danh mục này sẽ không còn hiển thị trên trang bán lẻ người mua cho đến khi danh mục được kích hoạt lại.
            </p>
          </div>
        ),
        okText: 'Xác nhận tắt',
        cancelText: 'Hủy bỏ',
        okButtonProps: { danger: true },
        onOk: executeToggle,
      });
    } else {
      executeToggle();
    }
  };

  // Delete category handler
  const handleDeleteCategory = async (record: CategoryTreeNode) => {
    try {
      await deleteCategoryMutation.mutateAsync(record.id!);
      message.success(`Đã xóa danh mục "${record.name}"`);
    } catch (err) {
      notification.error({
        message: 'Lỗi xóa danh mục',
        description: getApiErrorMessage(
          err,
          'Không thể xóa danh mục này. Vui lòng kiểm tra lại ràng buộc sản phẩm và danh mục con.'
        ),
      });
    }
  };

  // Check if current category being edited has children (cannot become a child)
  const isEditingCategoryWithChildren = useMemo(() => {
    if (!editingCategory || !editingCategory.id) return false;
    return rawCategories.some((c) => c.parentId === editingCategory.id);
  }, [editingCategory, rawCategories]);

  // Table columns definition
  const columns: ColumnsType<CategoryTreeNode> = [
    {
      title: 'Tên danh mục',
      dataIndex: 'name',
      key: 'name',
      width: 320,
      render: (name: string, record: CategoryTreeNode) => {
        const isRoot = record.level === 1;
        return (
          <Space orientation="horizontal" size={8} style={{ alignItems: 'center' }}>
            {isRoot ? (
              <FolderOpenOutlined style={{ color: '#8C6D53', fontSize: 16 }} />
            ) : (
              <TagOutlined style={{ color: '#A89F91', fontSize: 14 }} />
            )}
            <Text
              strong={isRoot}
              style={{
                fontSize: 14,
                color: isRoot ? '#1C1917' : '#44403C',
              }}
            >
              {name}
            </Text>
            {isRoot && (
              <Tag
                style={{
                  margin: 0,
                  fontSize: 11,
                  padding: '0 6px',
                  borderRadius: 4,
                  backgroundColor: '#F5F0EA',
                  borderColor: '#E4DCD3',
                  color: '#6E675F',
                }}
              >
                Cấp 1
              </Tag>
            )}
          </Space>
        );
      },
    },
    {
      title: 'Đường dẫn (Slug)',
      dataIndex: 'slug',
      key: 'slug',
      width: 240,
      render: (slug: string) => (
        <code
          style={{
            fontFamily: 'monospace',
            fontSize: 12,
            backgroundColor: '#F7F5F0',
            padding: '2px 6px',
            borderRadius: 4,
            border: '1px solid #ECE6DE',
            color: '#57534E',
          }}
        >
          {slug}
        </code>
      ),
    },
    {
      title: 'Số sản phẩm',
      dataIndex: 'productCount',
      key: 'productCount',
      width: 140,
      align: 'center',
      render: (count: number = 0) => (
        <Tag
          style={{
            margin: 0,
            borderRadius: 12,
            padding: '1px 10px',
            fontWeight: 600,
            fontSize: 12,
            backgroundColor: count > 0 ? '#EAE3D9' : '#F5F0EA',
            borderColor: count > 0 ? '#D6C8B8' : '#E4DCD3',
            color: count > 0 ? '#543F2E' : '#8A8177',
          }}
        >
          {count} sản phẩm
        </Tag>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'isActive',
      key: 'isActive',
      width: 150,
      align: 'center',
      render: (isActive: boolean, record: CategoryTreeNode) => (
        <Switch
          checked={isActive}
          loading={togglingId === record.id}
          checkedChildren="Hoạt động"
          unCheckedChildren="Đã tắt"
          onChange={(checked) => handleToggleStatus(record, checked)}
          style={{
            backgroundColor: isActive ? '#8C6D53' : undefined,
          }}
        />
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 200,
      align: 'right',
      render: (_, record: CategoryTreeNode) => {
        const isRoot = record.level === 1;
        const hasChildren = (record.children && record.children.length > 0) || false;
        const hasProducts = (record.productCount ?? 0) > 0;
        const canDelete = !hasChildren && !hasProducts;

        let deleteTooltip = 'Xóa danh mục';
        if (hasChildren) {
          deleteTooltip = 'Không thể xóa: Vui lòng xóa hoặc di chuyển danh mục con trước.';
        } else if (hasProducts) {
          deleteTooltip = `Không thể xóa: Danh mục đang có ${record.productCount} sản phẩm trực thuộc.`;
        }

        return (
          <Space size={8}>
            {isRoot && (
              <Tooltip title="Thêm danh mục con cấp 2">
                <Button
                  size="small"
                  type="text"
                  icon={<PlusOutlined />}
                  onClick={() => handleOpenCreateModal(record.id)}
                  style={{ color: '#8C6D53' }}
                >
                  Thêm con
                </Button>
              </Tooltip>
            )}

            <Tooltip title="Chỉnh sửa thông tin">
              <Button
                size="small"
                type="text"
                icon={<EditOutlined />}
                onClick={() => handleOpenEditModal(record)}
              />
            </Tooltip>

            {canDelete ? (
              <Popconfirm
                title="Xóa danh mục"
                description={`Bạn có chắc muốn xóa danh mục "${record.name}"?`}
                okText="Xác nhận xóa"
                cancelText="Hủy"
                okButtonProps={{ danger: true }}
                onConfirm={() => handleDeleteCategory(record)}
              >
                <Tooltip title={deleteTooltip}>
                  <Button size="small" type="text" danger icon={<DeleteOutlined />} />
                </Tooltip>
              </Popconfirm>
            ) : (
              <Tooltip title={deleteTooltip}>
                <Button
                  size="small"
                  type="text"
                  disabled
                  icon={<DeleteOutlined />}
                  style={{ opacity: 0.4 }}
                />
              </Tooltip>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 20,
        width: '100%',
        maxWidth: '100%',
      }}
    >
      {/* Top Header */}
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
            <Title level={3} style={{ margin: 0, fontWeight: 700, color: '#1C1917' }}>
              Danh mục Hàng hóa
            </Title>
            <Text style={{ fontSize: 13, color: '#6E675F' }}>
              Quản lý danh mục phân loại tối đa 2 cấp (Cha - Con), điều khiển hiển thị và liên kết sản phẩm
            </Text>
          </div>

          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => handleOpenCreateModal()}
            style={{
              backgroundColor: '#8C6D53',
              borderColor: '#8C6D53',
              borderRadius: token.borderRadius,
            }}
          >
            Tạo danh mục mới
          </Button>
        </div>
      </div>

      {/* Filter and Action Bar */}
      <Card
        styles={{ body: { padding: 16 } }}
        style={{
          borderRadius: token.borderRadius,
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
              placeholder="Tìm theo tên hoặc slug danh mục..."
              prefix={<SearchOutlined style={{ color: '#A89F91' }} />}
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              allowClear
              style={{ width: 280, borderRadius: token.borderRadius }}
            />

            <Select
              value={statusFilter}
              onChange={setStatusFilter}
              style={{ width: 180, borderRadius: token.borderRadius }}
              options={[
                { value: 'all', label: 'Tất cả trạng thái' },
                { value: 'active', label: 'Đang hoạt động' },
                { value: 'inactive', label: 'Ngừng hoạt động' },
              ]}
            />
          </Space>

          <Button
            icon={<ReloadOutlined spin={isFetching} />}
            onClick={() => refetch()}
            style={{ borderRadius: token.borderRadius }}
          >
            Làm mới
          </Button>
        </div>
      </Card>

      {/* Categories Tree Table */}
      <Card
        styles={{ body: { padding: 0 } }}
        style={{
          borderRadius: token.borderRadius,
          boxShadow: 'none',
          border: '1px solid #E4DCD3',
          overflow: 'hidden',
          backgroundColor: '#FFFFFF',
        }}
      >
        <Table<CategoryTreeNode>
          columns={columns}
          dataSource={treeData}
          rowKey="id"
          loading={isLoading}
          pagination={false}
          expandable={{
            expandedRowKeys,
            onExpandedRowsChange: (keys) => setExpandedRowKeys(keys as React.Key[]),
            indentSize: 28,
          }}
          locale={{
            emptyText: (
              <div style={{ padding: '32px 0', textAlign: 'center' }}>
                <AppstoreOutlined style={{ fontSize: 36, color: '#D6C8B8', marginBottom: 12 }} />
                <div>
                  <Text style={{ color: '#6E675F' }}>
                    {searchKeyword || statusFilter !== 'all'
                      ? 'Không tìm thấy danh mục phù hợp với bộ lọc'
                      : 'Chưa có danh mục nào trong hệ thống'}
                  </Text>
                </div>
              </div>
            ),
          }}
        />
      </Card>

      {/* Add / Edit Category Modal */}
      <Modal
        title={
          <Space size={8}>
            <AppstoreOutlined style={{ color: '#8C6D53' }} />
            <span>{editingCategory ? 'Chỉnh sửa Danh mục' : 'Thêm Danh mục mới'}</span>
          </Space>
        }
        open={isModalOpen}
        onOk={handleFormSubmit}
        onCancel={() => setIsModalOpen(false)}
        confirmLoading={
          createCategoryMutation.isPending || updateCategoryMutation.isPending
        }
        okText={editingCategory ? 'Lưu thay đổi' : 'Tạo danh mục'}
        cancelText="Hủy bỏ"
        okButtonProps={{
          style: {
            backgroundColor: '#8C6D53',
            borderColor: '#8C6D53',
            borderRadius: token.borderRadius,
          },
        }}
        cancelButtonProps={{
          style: { borderRadius: token.borderRadius },
        }}
        destroyOnClose
        style={{ borderRadius: token.borderRadius }}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ isActive: true, parentId: null }}
          style={{ marginTop: 16 }}
        >
          <Form.Item
            name="name"
            label="Tên danh mục"
            rules={[
              { required: true, message: 'Vui lòng nhập tên danh mục' },
              { max: 150, message: 'Tên danh mục không được vượt quá 150 ký tự' },
            ]}
          >
            <Input
              placeholder="Ví dụ: Áo Nam, Giày Sneaker..."
              onChange={handleNameChange}
              maxLength={150}
              showCount
              style={{ borderRadius: token.borderRadius }}
            />
          </Form.Item>

          <Form.Item
            name="slug"
            label="Đường dẫn (Slug URL)"
            tooltip="Tự động sinh từ tên danh mục. Chỉ gồm chữ thường không dấu, số và gạch ngang."
            rules={[
              {
                pattern: /^[a-z0-9]+(-[a-z0-9]+)*$/,
                message:
                  'Slug chỉ chứa chữ thường không dấu, số và dấu gạch ngang (không bắt đầu/kết thúc bằng -)',
              },
              { max: 180, message: 'Slug không được vượt quá 180 ký tự' },
            ]}
          >
            <Input
              placeholder="tu-dong-sinh-neu-de-trong"
              onChange={() => setSlugManuallyEdited(true)}
              maxLength={180}
              style={{ borderRadius: token.borderRadius }}
            />
          </Form.Item>

          <Form.Item
            name="parentId"
            label="Danh mục cha (Trực thuộc)"
            extra={
              isEditingCategoryWithChildren
                ? 'Danh mục này đang có danh mục con trực thuộc, không thể chuyển thành danh mục con (tối đa 2 cấp).'
                : 'Hệ thống hỗ trợ tối đa 2 cấp danh mục. Để tạo danh mục gốc cấp 1, hãy chọn "Không có".'
            }
          >
            <Select
              allowClear
              placeholder="Chọn danh mục cha (hoặc để trống làm cấp 1)"
              disabled={isEditingCategoryWithChildren}
              style={{ borderRadius: token.borderRadius }}
              options={[
                { value: null, label: '— Không có (Danh mục gốc cấp 1) —' },
                ...rootCategories
                  .filter((c) => !editingCategory || c.id !== editingCategory.id)
                  .map((c) => ({
                    value: c.id,
                    label: c.name ?? `Danh mục #${c.id}`,
                  })),
              ]}
            />
          </Form.Item>

          <Form.Item
            name="isActive"
            label="Trạng thái hoạt động"
            valuePropName="checked"
            extra="Danh mục bị tắt sẽ ẩn toàn bộ sản phẩm trực thuộc khỏi giao diện người mua."
          >
            <Switch
              checkedChildren="Hoạt động"
              unCheckedChildren="Ngừng hoạt động"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default CategoryListPage;
