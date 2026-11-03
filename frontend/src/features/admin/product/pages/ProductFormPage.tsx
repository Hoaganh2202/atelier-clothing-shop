import React, { useState, useEffect, useRef } from 'react';
import {
  Form,
  Input,
  Select,
  AutoComplete,
  InputNumber,
  Switch,
  Button,
  Card,
  Row,
  Col,
  Space,
  Typography,
  Upload,
  Popconfirm,
  Modal,
  Alert,
  Spin,
  Tag,
  Divider,
  message,
  theme,
} from 'antd';
import type { UploadProps } from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  ArrowLeftOutlined,
  SaveOutlined,
  StarOutlined,
  StarFilled,
  ArrowUpOutlined,
  ArrowDownOutlined,
} from '@ant-design/icons';
import { useNavigate, useParams, Link } from 'react-router-dom';
import {
  useAdminProduct,
  useAdminCategories,
  useCreateProduct,
  useUpdateProduct,
  useUploadImage,
  usePatchImage,
  useDeleteImage,
  getApiErrorMessage,
  getApiFieldErrors,
  generateSlug,
  type ProductUpsertPayload,
  type ProductImageItem,
} from '../api';

const { Title, Text } = Typography;
const { TextArea } = Input;

interface VariantFormItem {
  id?: number;
  sku: string;
  size?: string;
  color: string;
  stock?: number | null;
  price?: number | null;
}

interface ProductFormValues {
  name: string;
  slug?: string;
  categoryId: number;
  basePrice: number;
  isActive: boolean;
  description?: string;
  variants: VariantFormItem[];
}

interface PendingImageItem {
  uid: string;
  file: File;
  previewUrl: string;
  isPrimary: boolean;
  alt: string;
  color: string;
}

function parseFieldNameToPath(fieldKey: string): (string | number)[] {
  const variantMatch = fieldKey.match(/^variants\[(\d+)\]\.(.+)$/);
  if (variantMatch) {
    return ['variants', Number(variantMatch[1]), variantMatch[2]];
  }
  return [fieldKey];
}

export const ProductFormPage: React.FC = () => {
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const { id } = useParams<{ id: string }>();

  // Check URL id parameter: must be a positive integer if route contains id param
  const hasIdParam = typeof id === 'string';
  const isValidId = hasIdParam && /^\d+$/.test(id) && Number(id) > 0;
  const numericId = isValidId ? Number(id) : undefined;
  const isEditMode = isValidId;

  const [form] = Form.useForm<ProductFormValues>();
  const watchedVariants = Form.useWatch<VariantFormItem[]>('variants', form);
  const [isSlugManuallyEdited, setIsSlugManuallyEdited] = useState<boolean>(false);
  const [pendingImages, setPendingImages] = useState<PendingImageItem[]>([]);
  const [imageDrafts, setImageDrafts] = useState<
    Record<number, { alt: string; color: string }>
  >({});

  const pendingImagesRef = useRef<PendingImageItem[]>(pendingImages);
  useEffect(() => {
    pendingImagesRef.current = pendingImages;
  }, [pendingImages]);

  const isSavedSuccessRef = useRef<boolean>(false);

  const {
    data: productDetail,
    isLoading: isProductLoading,
    isError: isProductError,
    error: productError,
    refetch: refetchProduct,
  } = useAdminProduct(isEditMode ? numericId : undefined);

  const { data: categories = [], isLoading: isCategoriesLoading } = useAdminCategories();

  const createProductMutation = useCreateProduct();
  const updateProductMutation = useUpdateProduct();
  const uploadImageMutation = useUploadImage();
  const patchImageMutation = usePatchImage();
  const deleteImageMutation = useDeleteImage();

  useEffect(() => {
    if (isEditMode && productDetail) {
      form.setFieldsValue({
        name: productDetail.name ?? '',
        slug: productDetail.slug ?? '',
        categoryId: productDetail.categoryId,
        basePrice: productDetail.basePrice ?? 0,
        isActive: productDetail.isActive ?? true,
        description: productDetail.description ?? '',
        variants:
          productDetail.variants && productDetail.variants.length > 0
            ? productDetail.variants.map((v) => ({
                id: v.id,
                sku: v.sku ?? '',
                size: v.size ?? 'N/A',
                color: v.color ?? '',
                stock: v.stock ?? 0,
                price: v.price ?? undefined,
              }))
            : [
                {
                  sku: '',
                  size: 'M',
                  color: '',
                  stock: 0,
                  price: undefined,
                },
              ],
      });
      setIsSlugManuallyEdited(true);

      const drafts: Record<number, { alt: string; color: string }> = {};
      (productDetail.images ?? []).forEach((img) => {
        if (img.id !== undefined) {
          drafts[img.id] = {
            alt: img.alt ?? '',
            color: img.color ?? '',
          };
        }
      });
      setImageDrafts(drafts);
    } else if (!isEditMode && !hasIdParam) {
      form.setFieldsValue({
        isActive: true,
        basePrice: 0,
        variants: [
          {
            sku: '',
            size: 'M',
            color: '',
            stock: 10,
            price: undefined,
          },
        ],
      });
      setIsSlugManuallyEdited(false);
    }
  }, [isEditMode, hasIdParam, productDetail, form]);

  // BUG-05: Only revoke blob preview URLs on component unmount
  useEffect(() => {
    return () => {
      pendingImagesRef.current.forEach((item) => URL.revokeObjectURL(item.previewUrl));
    };
  }, []);

  const checkIsDirty = (): boolean => {
    if (isSavedSuccessRef.current) return false;
    if (form.isFieldsTouched()) return true;
    if (pendingImages.length > 0) return true;
    if (isEditMode && productDetail?.images) {
      for (const img of productDetail.images) {
        if (img.id !== undefined && imageDrafts[img.id]) {
          const draft = imageDrafts[img.id];
          const originalAlt = img.alt ?? '';
          const originalColor = img.color ?? '';
          if (
            draft.alt.trim() !== originalAlt.trim() ||
            draft.color.trim() !== originalColor.trim()
          ) {
            return true;
          }
        }
      }
    }
    return false;
  };

  useEffect(() => {
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (checkIsDirty()) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  });

  const handleCancelOrBack = () => {
    if (checkIsDirty()) {
      Modal.confirm({
        title: 'Xác nhận rời khỏi trang',
        content: 'Bạn có thay đổi chưa được lưu. Rời khỏi trang?',
        okText: 'Rời khỏi trang',
        cancelText: 'Ở lại tiếp tục',
        okButtonProps: { danger: true },
        onOk: () => {
          navigate('/admin/products');
        },
      });
    } else {
      navigate('/admin/products');
    }
  };

  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const nextName = e.target.value;
    if (!isSlugManuallyEdited) {
      form.setFieldValue('slug', generateSlug(nextName));
    }
  };

  const applyServerValidationErrors = (error: unknown) => {
    const fieldErrors = getApiFieldErrors(error);
    if (fieldErrors && Object.keys(fieldErrors).length > 0) {
      form.setFields(
        Object.entries(fieldErrors).map(([key, errMsg]) => ({
          name: parseFieldNameToPath(key) as any,
          errors: [errMsg],
        }))
      );
    }
  };

  const handleBeforeUpload: UploadProps['beforeUpload'] = (file) => {
    const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    if (file.size > MAX_FILE_SIZE) {
      message.error('Dung lượng file tải lên vượt quá giới hạn cho phép (tối đa 10 MB)');
      return Upload.LIST_IGNORE;
    }

    if (isEditMode && numericId) {
      const existingImages = productDetail?.images ?? [];
      uploadImageMutation.mutate(
        {
          productId: numericId,
          file,
          meta: {
            isPrimary: existingImages.length === 0,
            alt: form.getFieldValue('name') || productDetail?.name,
          },
        },
        {
          onSuccess: () => {
            message.success(`Đã tải ảnh "${file.name}" lên thành công`);
          },
          onError: (err) => {
            message.error(getApiErrorMessage(err, 'Không thể tải ảnh lên máy chủ'));
          },
        }
      );
    } else {
      const previewUrl = URL.createObjectURL(file);
      setPendingImages((prev) => [
        ...prev,
        {
          uid: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
          file,
          previewUrl,
          isPrimary: prev.length === 0,
          alt: form.getFieldValue('name') || '',
          color: '',
        },
      ]);
    }
    return false;
  };

  const handleSetPrimaryExistingImage = (image: ProductImageItem) => {
    if (!numericId || !image.id) return;
    patchImageMutation.mutate(
      {
        productId: numericId,
        imageId: image.id,
        payload: { isPrimary: true },
      },
      {
        onSuccess: () => {
          message.success('Đã đặt làm ảnh đại diện chính');
        },
        onError: (err) => {
          message.error(getApiErrorMessage(err, 'Không thể đổi ảnh chính'));
        },
      }
    );
  };

  const handleMoveExistingImage = async (index: number, direction: 'up' | 'down') => {
    if (!numericId || !productDetail?.images) return;
    const images = [...productDetail.images];
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= images.length) return;

    const currentImg = images[index];
    const targetImg = images[targetIndex];
    if (!currentImg.id || !targetImg.id) return;

    try {
      await patchImageMutation.mutateAsync({
        productId: numericId,
        imageId: currentImg.id,
        payload: { sortOrder: targetIndex },
      });
      await patchImageMutation.mutateAsync({
        productId: numericId,
        imageId: targetImg.id,
        payload: { sortOrder: index },
      });
      message.success('Đã cập nhật thứ tự hiển thị ảnh');
    } catch (err) {
      message.error(getApiErrorMessage(err, 'Không thể đổi thứ tự ảnh'));
    }
  };

  const handleSaveExistingImageMeta = (imageId: number) => {
    if (!numericId) return;
    const draft = imageDrafts[imageId];
    if (!draft) return;

    patchImageMutation.mutate(
      {
        productId: numericId,
        imageId,
        payload: {
          alt: draft.alt.trim(),
          color: draft.color.trim() || undefined,
        },
      },
      {
        onSuccess: () => {
          message.success('Đã cập nhật thông tin ảnh');
        },
        onError: (err) => {
          message.error(getApiErrorMessage(err, 'Không thể cập nhật thông tin ảnh'));
        },
      }
    );
  };

  const handleDeleteExistingImage = (imageId: number) => {
    if (!numericId) return;
    deleteImageMutation.mutate(
      { productId: numericId, imageId },
      {
        onSuccess: () => {
          message.success('Đã xóa ảnh sản phẩm');
        },
        onError: (err) => {
          message.error(getApiErrorMessage(err, 'Không thể xóa ảnh sản phẩm'));
        },
      }
    );
  };

  const handleSetPrimaryPendingImage = (uid: string) => {
    setPendingImages((prev) =>
      prev.map((item) => ({
        ...item,
        isPrimary: item.uid === uid,
      }))
    );
  };

  const handleMovePendingImage = (index: number, direction: 'up' | 'down') => {
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= pendingImages.length) return;
    setPendingImages((prev) => {
      const next = [...prev];
      const [moved] = next.splice(index, 1);
      next.splice(targetIndex, 0, moved);
      return next;
    });
  };

  const handleRemovePendingImage = (uid: string) => {
    setPendingImages((prev) => {
      const target = prev.find((item) => item.uid === uid);
      if (target) {
        URL.revokeObjectURL(target.previewUrl);
      }
      const remaining = prev.filter((item) => item.uid !== uid);
      if (remaining.length > 0 && !remaining.some((item) => item.isPrimary)) {
        remaining[0] = { ...remaining[0], isPrimary: true };
      }
      return remaining;
    });
  };

  const handleFinish = async (values: ProductFormValues) => {
    const normalizedSlug = values.slug?.trim()
      ? generateSlug(values.slug.trim())
      : undefined;

    const payload: ProductUpsertPayload = {
      name: values.name.trim(),
      slug: normalizedSlug || undefined,
      categoryId: Number(values.categoryId),
      basePrice: Number(values.basePrice),
      isActive: Boolean(values.isActive),
      description: values.description?.trim() || undefined,
      variants: (values.variants ?? []).map((v) => ({
        id: v.id,
        sku: v.sku.trim(),
        size: v.size?.trim() ? v.size.trim() : 'N/A',
        color: v.color.trim(),
        price:
          v.price !== undefined && v.price !== null && String(v.price) !== ''
            ? Number(v.price)
            : undefined,
        ...(!v.id && { stock: Number(v.stock) }),
      })),
    };

    try {
      if (isEditMode && numericId) {
        // H10: Auto-save any modified image drafts before saving product info
        const changedDrafts = (productDetail?.images ?? []).filter((img) => {
          if (img.id === undefined) return false;
          const draft = imageDrafts[img.id];
          if (!draft) return false;
          const originalAlt = img.alt ?? '';
          const originalColor = img.color ?? '';
          return (
            draft.alt.trim() !== originalAlt.trim() ||
            draft.color.trim() !== originalColor.trim()
          );
        });

        for (const img of changedDrafts) {
          const draft = imageDrafts[img.id!];
          try {
            await patchImageMutation.mutateAsync({
              productId: numericId,
              imageId: img.id!,
              payload: {
                alt: draft.alt.trim(),
                color: draft.color.trim() || undefined,
              },
            });
          } catch (patchErr) {
            message.error(
              `Không thể cập nhật thông tin ảnh "${draft.alt || img.id}": ${getApiErrorMessage(
                patchErr,
                'Lỗi cập nhật ảnh'
              )}`
            );
            return;
          }
        }

        await updateProductMutation.mutateAsync({ id: numericId, payload });
        isSavedSuccessRef.current = true;
        message.success(`Đã cập nhật sản phẩm "${payload.name}" thành công`);
        navigate('/admin/products');
      } else {
        const created = await createProductMutation.mutateAsync(payload);
        if (created.id && pendingImages.length > 0) {
          let successCount = 0;
          const failedUploads: { fileName: string; errorMsg: string }[] = [];

          for (const pending of pendingImages) {
            try {
              await uploadImageMutation.mutateAsync({
                productId: created.id,
                file: pending.file,
                meta: {
                  isPrimary: pending.isPrimary,
                  alt: pending.alt.trim() || payload.name,
                  color: pending.color.trim() || undefined,
                },
              });
              successCount++;
            } catch (uploadErr) {
              failedUploads.push({
                fileName: pending.file.name,
                errorMsg: getApiErrorMessage(uploadErr, 'Lỗi tải ảnh'),
              });
            }
          }

          if (failedUploads.length > 0) {
            const failedDetails = failedUploads
              .map((f) => `"${f.fileName}" (${f.errorMsg})`)
              .join(', ');
            message.warning(
              `Đã tạo sản phẩm. ${successCount}/${pendingImages.length} ảnh tải lên thành công, các ảnh sau bị lỗi: ${failedDetails}`,
              8
            );
            isSavedSuccessRef.current = true;
            pendingImagesRef.current.forEach((item) => URL.revokeObjectURL(item.previewUrl));
            setPendingImages([]);
            navigate(`/admin/products/${created.id}/edit`);
            return;
          }
        }

        isSavedSuccessRef.current = true;
        pendingImagesRef.current.forEach((item) => URL.revokeObjectURL(item.previewUrl));
        setPendingImages([]);
        message.success(`Đã tạo mới sản phẩm "${payload.name}" thành công`);
        navigate('/admin/products');
      }
    } catch (err) {
      applyServerValidationErrors(err);
      message.error(getApiErrorMessage(err, 'Không thể lưu thông tin sản phẩm'));
    }
  };

  // BUG-09: Invalid URL id handling
  if (hasIdParam && !isValidId) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Alert
          type="error"
          showIcon
          message="Mã sản phẩm trên đường dẫn không hợp lệ"
          description="Mã sản phẩm trên đường dẫn phải là một số nguyên dương (ví dụ: 1, 2, 3...). Vui lòng kiểm tra lại đường dẫn."
          action={
            <Button type="primary" onClick={() => navigate('/admin/products')}>
              Quay lại danh sách sản phẩm
            </Button>
          }
        />
      </div>
    );
  }

  if (isEditMode && isProductLoading) {
    return (
      <div
        style={{
          padding: 48,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 12,
        }}
      >
        <Spin size="large" />
        <Text style={{ color: '#6E675F' }}>Đang tải dữ liệu sản phẩm...</Text>
      </div>
    );
  }

  if (isEditMode && isProductError) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Alert
          type="error"
          showIcon
          message="Không tìm thấy hoặc không thể tải thông tin sản phẩm"
          description={getApiErrorMessage(productError, 'Vui lòng kiểm tra lại mã sản phẩm.')}
          action={
            <Space>
              <Button size="small" onClick={() => refetchProduct()}>
                Thử lại
              </Button>
              <Button size="small" type="primary" onClick={() => navigate('/admin/products')}>
                Quay lại danh sách
              </Button>
            </Space>
          }
        />
      </div>
    );
  }

  const isSubmitting =
    createProductMutation.isPending ||
    updateProductMutation.isPending ||
    uploadImageMutation.isPending;

  const existingImages = productDetail?.images ?? [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div>
        <div>
          <Title level={3} style={{ margin: 0, fontWeight: 700, color: '#18181B' }}>
            {isEditMode ? 'Chỉnh sửa Sản phẩm' : 'Thêm Sản phẩm Mới'}
          </Title>
          <Text style={{ fontSize: 13, color: '#6E675F' }}>
            {isEditMode
              ? `Đang chỉnh sửa sản phẩm ID #${numericId}`
              : 'Khởi tạo thông tin sản phẩm, thiết lập biến thể quy cách và tải ảnh trưng bày'}
          </Text>
        </div>
      </div>

      <Form<ProductFormValues>
        form={form}
        layout="vertical"
        onFinish={handleFinish}
        onFinishFailed={({ errorFields }) => {
          if (errorFields.length > 0) {
            form.scrollToField(errorFields[0].name, { focus: true });
          }
        }}
        requiredMark="optional"
        scrollToFirstError={{ focus: true }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          <Card
            bordered
            title={
              <div>
                <span style={{ fontWeight: 600, fontSize: 15, color: '#18181B' }}>
                  1. Thông tin cơ bản
                </span>
                <div style={{ fontSize: 13, fontWeight: 400, color: '#6E675F' }}>
                  Tên sản phẩm, đường dẫn chuẩn SEO (slug), danh mục, giá cơ bản và trạng thái bán
                </div>
              </div>
            }
          >
            <Row gutter={[16, 0]}>
              <Col xs={24} md={12}>
                <Form.Item
                  label="Tên sản phẩm"
                  name="name"
                  rules={[
                    { required: true, message: 'Tên sản phẩm không được để trống' },
                    { max: 255, message: 'Tên sản phẩm tối đa 255 ký tự' },
                  ]}
                >
                  <Input
                    placeholder="Ví dụ: Áo thun Heavyweight Cotton 250 GSM"
                    onChange={handleNameChange}
                  />
                </Form.Item>
              </Col>

              <Col xs={24} md={12}>
                <Form.Item
                  label="Đường dẫn tĩnh (Slug)"
                  name="slug"
                  extra="Chỉ gồm chữ thường a-z, số 0-9 và dấu gạch ngang (-). Để trống hệ thống tự sinh theo tên."
                  rules={[
                    { max: 255, message: 'Slug tối đa 255 ký tự' },
                    {
                      pattern: /^[a-z0-9]+(-[a-z0-9]+)*$/,
                      message:
                        'Slug chỉ được chứa chữ thường, số và dấu gạch ngang, không bắt đầu hay kết thúc bằng gạch ngang',
                    },
                  ]}
                >
                  <Input
                    placeholder="Ví dụ: ao-thun-heavyweight-cotton-250-gsm"
                    onChange={() => setIsSlugManuallyEdited(true)}
                  />
                </Form.Item>
              </Col>

              <Col xs={24} md={8}>
                <Form.Item
                  label="Danh mục hàng hóa"
                  name="categoryId"
                  rules={[{ required: true, message: 'Danh mục sản phẩm không được để trống' }]}
                >
                  <Select
                    loading={isCategoriesLoading}
                    placeholder="Chọn danh mục sản phẩm"
                    options={categories
                      .filter((c) => c.id !== undefined)
                      .map((c) => ({
                        value: c.id!,
                        label: c.name ?? `Danh mục #${c.id}`,
                      }))}
                  />
                </Form.Item>
              </Col>

              <Col xs={24} md={8}>
                <Form.Item
                  label="Giá cơ bản (VND)"
                  name="basePrice"
                  rules={[
                    { required: true, message: 'Giá cơ bản không được để trống' },
                    { type: 'number', min: 0, message: 'Giá cơ bản phải lớn hơn hoặc bằng 0' },
                    { type: 'number', max: 9999999999, message: 'Giá không được vượt quá 9.999.999.999 ₫' },
                  ]}
                >
                  <InputNumber<number>
                    min={0}
                    step={10000}
                    precision={0}
                    style={{ width: '100%' }}
                    placeholder="Nhập giá cơ bản (VND)"
                    formatter={(value) =>
                      value !== undefined && value !== null
                        ? `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
                        : ''
                    }
                    parser={(value) =>
                      value ? Number(value.replace(/\./g, '').replace(/[^\d]/g, '')) : 0
                    }
                  />
                </Form.Item>
              </Col>

              <Col xs={24} md={8}>
                <Form.Item
                  label="Trạng thái kinh doanh"
                  name="isActive"
                  valuePropName="checked"
                >
                  <Switch
                    checkedChildren="Đang bán"
                    unCheckedChildren="Ngừng bán"
                  />
                </Form.Item>
              </Col>

              <Col xs={24}>
                <Form.Item
                  label="Mô tả chi tiết sản phẩm"
                  name="description"
                >
                  <TextArea
                    rows={4}
                    placeholder="Mô tả chất liệu vải, phom dáng, thông số may đo và hướng dẫn bảo quản..."
                  />
                </Form.Item>
              </Col>
            </Row>
          </Card>

          <Card
            bordered
            title={
              <div>
                <span style={{ fontWeight: 600, fontSize: 15, color: '#18181B' }}>
                  2. Quy cách & Biến thể sản phẩm
                </span>
                <div style={{ fontSize: 13, fontWeight: 400, color: '#6E675F' }}>
                  Mỗi sản phẩm phải có ít nhất 1 biến thể. Để trống ô giá biến thể nếu dùng chung giá cơ bản.
                </div>
              </div>
            }
          >
            <Form.List
              name="variants"
              rules={[
                {
                  validator: async (_, variants) => {
                    if (!variants || variants.length < 1) {
                      return Promise.reject(new Error('Sản phẩm phải có ít nhất 1 biến thể'));
                    }
                  },
                },
              ]}
            >
              {(fields, { add, remove }, { errors }) => (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  {fields.map(({ key, name, ...restField }, index) => (
                    <div
                      key={key}
                      style={{
                        padding: 16,
                        backgroundColor: '#FBF9F5',
                        border: '1px solid #E4DCD3',
                        borderRadius: token.borderRadius,
                      }}
                    >
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          marginBottom: 12,
                        }}
                      >
                        <Space size="small">
                          <Tag
                            style={{
                              margin: 0,
                              backgroundColor: '#F5F0EA',
                              borderColor: '#E4DCD3',
                              color: '#18181B',
                              fontWeight: 600,
                            }}
                          >
                            Biến thể #{index + 1}
                          </Tag>
                        </Space>

                        {fields.length > 1 && (() => {
                          const item = watchedVariants?.[name] ?? form.getFieldValue(['variants', name]);
                          const isExisting = Boolean(item?.id);
                          const currentStock = Number(item?.stock ?? 0);

                          if (isExisting && currentStock > 0) {
                            return (
                              <Popconfirm
                                title="Xóa biến thể"
                                description={`Biến thể SKU '${item?.sku || ''}' còn ${currentStock} sản phẩm tồn kho. Hãy điều chỉnh tồn kho về 0 tại trang Kho hàng trước khi xóa.`}
                                onConfirm={() => remove(name)}
                                okText="Vẫn xóa khỏi form"
                                cancelText="Hủy"
                              >
                                <Button
                                  type="text"
                                  danger
                                  size="small"
                                  icon={<DeleteOutlined />}
                                >
                                  Xóa biến thể
                                </Button>
                              </Popconfirm>
                            );
                          }

                          return (
                            <Button
                              type="text"
                              danger
                              size="small"
                              icon={<DeleteOutlined />}
                              onClick={() => remove(name)}
                            >
                              Xóa biến thể
                            </Button>
                          );
                        })()}
                      </div>

                      <Form.Item {...restField} name={[name, 'id']} hidden>
                        <Input type="hidden" />
                      </Form.Item>

                      <Row gutter={[12, 12]}>
                        <Col xs={24} sm={12} md={5}>
                          <Form.Item
                            {...restField}
                            name={[name, 'sku']}
                            label="Mã SKU"
                            rules={[
                              { required: true, message: 'Mã SKU không được để trống' },
                              { max: 64, message: 'Mã SKU tối đa 64 ký tự' },
                            ]}
                            style={{ margin: 0 }}
                          >
                            <Input placeholder="Ví dụ: TEE-BLK-M" />
                          </Form.Item>
                        </Col>

                        <Col xs={24} sm={12} md={5}>
                          <Form.Item
                            {...restField}
                            name={[name, 'color']}
                            label="Màu sắc"
                            rules={[
                              { required: true, message: 'Màu sắc không được để trống' },
                              { max: 50, message: 'Màu sắc tối đa 50 ký tự' },
                            ]}
                            style={{ margin: 0 }}
                          >
                            <Input placeholder="Ví dụ: Đen than, Trắng ngà" />
                          </Form.Item>
                        </Col>

                        <Col xs={24} sm={12} md={4}>
                          <Form.Item
                            {...restField}
                            name={[name, 'size']}
                            label="Kích cỡ"
                            rules={[{ max: 20, message: 'Kích cỡ tối đa 20 ký tự' }]}
                            style={{ margin: 0 }}
                          >
                            <AutoComplete
                              options={[
                                { value: 'S' },
                                { value: 'M' },
                                { value: 'L' },
                                { value: 'XL' },
                                { value: '2XL' },
                                { value: '3XL' },
                                { value: 'FreeSize' },
                                { value: '29' },
                                { value: '30' },
                                { value: '31' },
                                { value: '32' },
                              ]}
                              placeholder="Chọn hoặc nhập kích cỡ (để trống: N/A)"
                              maxLength={20}
                              allowClear
                            />
                          </Form.Item>
                        </Col>

                        <Col xs={24} sm={12} md={5}>
                          <Form.Item
                            {...restField}
                            name={[name, 'price']}
                            label="Giá riêng"
                            rules={[
                              {
                                type: 'number',
                                min: 0,
                                message: 'Giá biến thể phải lớn hơn hoặc bằng 0',
                              },
                              {
                                type: 'number',
                                max: 9999999999,
                                message: 'Giá không được vượt quá 9.999.999.999 ₫',
                              },
                            ]}
                            style={{ margin: 0 }}
                          >
                            <InputNumber<number>
                              min={0}
                              step={10000}
                              precision={0}
                              style={{ width: '100%' }}
                              placeholder="Theo giá cơ bản"
                              formatter={(value) =>
                                value !== undefined && value !== null
                                  ? `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
                                  : ''
                              }
                              parser={(value) =>
                                value
                                  ? Number(value.replace(/\./g, '').replace(/[^\d]/g, ''))
                                  : (undefined as unknown as number)
                              }
                            />
                          </Form.Item>
                        </Col>

                        <Col xs={24} sm={12} md={5}>
                          {(() => {
                            const item = watchedVariants?.[name] ?? form.getFieldValue(['variants', name]);
                            const isExisting = Boolean(item?.id);
                            const currentSku = item?.sku;

                            if (isExisting) {
                              return (
                                <div>
                                  <Form.Item
                                    {...restField}
                                    name={[name, 'stock']}
                                    label="Tồn kho"
                                    style={{ margin: 0 }}
                                  >
                                    <InputNumber
                                      disabled
                                      precision={0}
                                      style={{ width: '100%', color: '#18181B' }}
                                      formatter={(val) =>
                                        val !== undefined && val !== null
                                          ? `${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
                                          : '0'
                                      }
                                    />
                                  </Form.Item>
                                  <div style={{ marginTop: 4, lineHeight: 1.3 }}>
                                    <Link
                                      to="/admin/inventory"
                                      target="_blank"
                                      rel="noopener noreferrer"
                                      style={{
                                        color: '#C2410C',
                                        fontSize: 12,
                                        fontWeight: 500,
                                        textDecoration: 'none',
                                      }}
                                    >
                                      Điều chỉnh tại Kho hàng{currentSku ? ` (SKU: ${currentSku})` : ''}
                                    </Link>
                                    <div style={{ fontSize: 11, color: '#6E675F', marginTop: 2 }}>
                                      Số liệu tải lúc mở form
                                    </div>
                                  </div>
                                </div>
                              );
                            }

                            return (
                              <Form.Item
                                {...restField}
                                name={[name, 'stock']}
                                label="Tồn kho ban đầu"
                                rules={[
                                  {
                                    required: true,
                                    message: 'Tồn kho ban đầu không được để trống khi tạo biến thể mới',
                                  },
                                  {
                                    type: 'number',
                                    min: 0,
                                    message: 'Tồn kho phải lớn hơn hoặc bằng 0',
                                  },
                                  {
                                    type: 'number',
                                    max: 1000000,
                                    message: 'Tồn kho không được vượt quá 1.000.000',
                                  },
                                ]}
                                style={{ margin: 0 }}
                              >
                                <InputNumber
                                                                    precision={0}
                                  placeholder="Ví dụ: 10"
                                  style={{ width: '100%' }}
                                />
                              </Form.Item>
                            );
                          })()}
                        </Col>
                      </Row>
                    </div>
                  ))}

                  <Form.ErrorList errors={errors} />

                  <Button
                    type="dashed"
                    onClick={() =>
                      add({
                        sku: '',
                        size: 'M',
                        color: '',
                        stock: 10,
                        price: undefined,
                      })
                    }
                    block
                    icon={<PlusOutlined />}
                  >
                    Thêm biến thể mới
                  </Button>
                </div>
              )}
            </Form.List>
          </Card>

          <Card
            bordered
            title={
              <div>
                <span style={{ fontWeight: 600, fontSize: 15, color: '#18181B' }}>
                  3. Hình ảnh sản phẩm
                </span>
                <div style={{ fontSize: 13, fontWeight: 400, color: '#6E675F' }}>
                  {isEditMode
                    ? 'Tải ảnh mới trực tiếp lên máy chủ, chọn ảnh đại diện chính, cập nhật mô tả và sắp xếp thứ tự hiển thị'
                    : 'Chọn các ảnh cần tải lên; ảnh sẽ tự động được đẩy lên máy chủ ngay sau khi tạo sản phẩm'}
                </div>
              </div>
            }
          >
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div>
                <Upload
                  accept="image/jpeg,image/png,image/webp,image/gif"
                  showUploadList={false}
                  beforeUpload={handleBeforeUpload}
                  multiple
                >
                  <Button
                    icon={<PlusOutlined />}
                    loading={uploadImageMutation.isPending}
                  >
                    Chọn ảnh tải lên (JPG, PNG, WEBP, GIF - Tối đa 10MB)
                  </Button>
                </Upload>
              </div>

              {isEditMode ? (
                existingImages.length > 0 ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                    <Text strong style={{ fontSize: 13, color: '#18181B' }}>
                      Danh sách hình ảnh đã lưu ({existingImages.length} ảnh):
                    </Text>

                    {existingImages.map((img, idx) => {
                      const imgId = img.id!;
                      const isPrimary = Boolean(img.isPrimary);
                      const draft = imageDrafts[imgId] ?? {
                        alt: img.alt ?? '',
                        color: img.color ?? '',
                      };

                      return (
                        <div
                          key={imgId}
                          style={{
                            display: 'flex',
                            flexDirection: 'column',
                            gap: 12,
                            padding: 16,
                            borderRadius: token.borderRadius,
                            border: isPrimary ? '1px solid #18181B' : '1px solid #E4DCD3',
                            backgroundColor: isPrimary ? '#FBF9F5' : '#FFFFFF',
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
                            <Space size="middle">
                              <img
                                src={img.url}
                                alt={img.alt ?? 'Ảnh sản phẩm'}
                                style={{
                                  width: 64,
                                  height: 64,
                                  objectFit: 'cover',
                                  borderRadius: token.borderRadius,
                                  border: '1px solid #E4DCD3',
                                }}
                              />
                              <div>
                                <Space size="small">
                                  {isPrimary ? (
                                    <Tag
                                      style={{
                                        margin: 0,
                                        backgroundColor: '#18181B',
                                        borderColor: '#18181B',
                                        color: '#FFFFFF',
                                      }}
                                    >
                                      <StarFilled style={{ marginRight: 4 }} /> Ảnh đại diện chính
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
                                      Ảnh phụ #{idx + 1}
                                    </Tag>
                                  )}
                                </Space>
                                <div style={{ marginTop: 4 }}>
                                  <Text style={{ fontSize: 13, color: '#6E675F' }}>
                                    URL: {img.url}
                                  </Text>
                                </div>
                              </div>
                            </Space>

                            <Space size="small" wrap>
                              {!isPrimary && (
                                <Button
                                  size="small"
                                  icon={<StarOutlined />}
                                  loading={patchImageMutation.isPending}
                                  onClick={() => handleSetPrimaryExistingImage(img)}
                                >
                                  Đặt làm ảnh chính
                                </Button>
                              )}

                              <Button
                                size="small"
                                icon={<ArrowUpOutlined />}
                                disabled={idx === 0 || patchImageMutation.isPending}
                                onClick={() => handleMoveExistingImage(idx, 'up')}
                                title="Di chuyển lên trước"
                              />

                              <Button
                                size="small"
                                icon={<ArrowDownOutlined />}
                                disabled={
                                  idx === existingImages.length - 1 ||
                                  patchImageMutation.isPending
                                }
                                onClick={() => handleMoveExistingImage(idx, 'down')}
                                title="Di chuyển xuống sau"
                              />

                              <Popconfirm
                                title="Xóa hình ảnh này?"
                                description="Ảnh sẽ bị xóa khỏi sản phẩm và máy chủ."
                                onConfirm={() => handleDeleteExistingImage(imgId)}
                                okText="Xóa"
                                cancelText="Hủy"
                                okButtonProps={{ danger: true }}
                              >
                                <Button
                                  size="small"
                                  danger
                                  icon={<DeleteOutlined />}
                                  loading={deleteImageMutation.isPending}
                                />
                              </Popconfirm>
                            </Space>
                          </div>

                          <Divider style={{ margin: 0, borderColor: '#E4DCD3' }} />

                          <Row gutter={[12, 12]} align="middle">
                            <Col xs={24} sm={10}>
                              <Input
                                addonBefore="Mô tả (Alt)"
                                value={draft.alt}
                                placeholder="Mô tả ngắn cho ảnh"
                                onChange={(e) =>
                                  setImageDrafts((prev) => ({
                                    ...prev,
                                    [imgId]: { ...draft, alt: e.target.value },
                                  }))
                                }
                              />
                            </Col>
                            <Col xs={24} sm={8}>
                              <Input
                                addonBefore="Màu sắc"
                                value={draft.color}
                                placeholder="Ví dụ: Đen than"
                                onChange={(e) =>
                                  setImageDrafts((prev) => ({
                                    ...prev,
                                    [imgId]: { ...draft, color: e.target.value },
                                  }))
                                }
                              />
                            </Col>
                            <Col xs={24} sm={6}>
                              <Button
                                size="small"
                                loading={patchImageMutation.isPending}
                                onClick={() => handleSaveExistingImageMeta(imgId)}
                              >
                                Lưu thông tin ảnh
                              </Button>
                            </Col>
                          </Row>
                        </div>
                      );
                    })}
                  </div>
                ) : (
                  <Text style={{ fontSize: 13, color: '#6E675F' }}>
                    Sản phẩm này chưa có hình ảnh nào. Hãy nhấn nút chọn ảnh phía trên để tải lên.
                  </Text>
                )
              ) : pendingImages.length > 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <Text strong style={{ fontSize: 13, color: '#18181B' }}>
                    Danh sách ảnh chờ tải lên ({pendingImages.length} ảnh):
                  </Text>

                  {pendingImages.map((item, idx) => (
                    <div
                      key={item.uid}
                      style={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 12,
                        padding: 16,
                        borderRadius: token.borderRadius,
                        border: item.isPrimary ? '1px solid #18181B' : '1px solid #E4DCD3',
                        backgroundColor: item.isPrimary ? '#FBF9F5' : '#FFFFFF',
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
                        <Space size="middle">
                          <img
                            src={item.previewUrl}
                            alt={item.file.name}
                            style={{
                              width: 64,
                              height: 64,
                              objectFit: 'cover',
                              borderRadius: token.borderRadius,
                              border: '1px solid #E4DCD3',
                            }}
                          />
                          <div>
                            <Text strong style={{ fontSize: 13, color: '#18181B' }}>
                              {item.file.name}
                            </Text>
                            <div style={{ marginTop: 4 }}>
                              {item.isPrimary ? (
                                <Tag
                                  style={{
                                    margin: 0,
                                    backgroundColor: '#18181B',
                                    borderColor: '#18181B',
                                    color: '#FFFFFF',
                                  }}
                                >
                                  <StarFilled style={{ marginRight: 4 }} /> Ảnh đại diện chính
                                </Tag>
                              ) : (
                                <Text style={{ fontSize: 13, color: '#6E675F' }}>
                                  Ảnh phụ #{idx + 1}
                                </Text>
                              )}
                            </div>
                          </div>
                        </Space>

                        <Space size="small">
                          {!item.isPrimary && (
                            <Button
                              size="small"
                              icon={<StarOutlined />}
                              onClick={() => handleSetPrimaryPendingImage(item.uid)}
                            >
                              Đặt làm ảnh chính
                            </Button>
                          )}

                          <Button
                            size="small"
                            icon={<ArrowUpOutlined />}
                            disabled={idx === 0}
                            onClick={() => handleMovePendingImage(idx, 'up')}
                          />

                          <Button
                            size="small"
                            icon={<ArrowDownOutlined />}
                            disabled={idx === pendingImages.length - 1}
                            onClick={() => handleMovePendingImage(idx, 'down')}
                          />

                          <Button
                            size="small"
                            danger
                            icon={<DeleteOutlined />}
                            onClick={() => handleRemovePendingImage(item.uid)}
                          />
                        </Space>
                      </div>

                      <Row gutter={[12, 12]}>
                        <Col xs={24} sm={12}>
                          <Input
                            addonBefore="Mô tả (Alt)"
                            value={item.alt}
                            placeholder="Mô tả ngắn cho ảnh"
                            onChange={(e) =>
                              setPendingImages((prev) =>
                                prev.map((p) =>
                                  p.uid === item.uid ? { ...p, alt: e.target.value } : p
                                )
                              )
                            }
                          />
                        </Col>
                        <Col xs={24} sm={12}>
                          <Input
                            addonBefore="Màu sắc"
                            value={item.color}
                            placeholder="Ví dụ: Đen than"
                            onChange={(e) =>
                              setPendingImages((prev) =>
                                prev.map((p) =>
                                  p.uid === item.uid ? { ...p, color: e.target.value } : p
                                )
                              )
                            }
                          />
                        </Col>
                      </Row>
                    </div>
                  ))}
                </div>
              ) : (
                <Text style={{ fontSize: 13, color: '#6E675F' }}>
                  Chưa chọn ảnh nào. Bạn có thể chọn ảnh ngay bây giờ hoặc tải lên sau khi tạo sản phẩm.
                </Text>
              )}
            </div>
          </Card>

          <Card bordered bodyStyle={{ padding: 16 }}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: 12,
              }}
            >
              <Button
                icon={<ArrowLeftOutlined />}
                onClick={handleCancelOrBack}
              >
                Quay lại danh sách
              </Button>
              <Space size="middle">
                <Button onClick={handleCancelOrBack}>Hủy bỏ</Button>
                <Button
                  type="primary"
                  icon={<SaveOutlined />}
                  htmlType="submit"
                  size="large"
                  loading={isSubmitting}
                >
                  {isEditMode ? 'Lưu thông tin sản phẩm' : 'Hoàn tất & Tạo sản phẩm'}
                </Button>
              </Space>
            </div>
          </Card>
        </div>
      </Form>
    </div>
  );
};

export default ProductFormPage;
