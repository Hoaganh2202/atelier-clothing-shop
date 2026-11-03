import React, { useEffect, useState } from 'react';
import {
  Alert,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Spin,
  Switch,
  Typography,
  message,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { formatCurrencyVND } from '@/lib/format';
import {
  getAdminPromotionById,
  getApiErrorMessage,
  getApiFieldErrors,
  searchAdminProducts,
  type PromotionDetail,
} from '../api/promotion.api';
import { useCreatePromotion, useUpdatePromotion } from '../hooks';
import {
  promotionCreateSchema,
  promotionUpdateSchema,
  MAX_PROMOTION_AMOUNT,
  MAX_PROMOTION_PRODUCTS,
} from '../schemas/promotion.schema';
import { PROMOTION_THEME } from '../constants';

const { Text } = Typography;
const { RangePicker } = DatePicker;

const ISO_FORMAT = 'YYYY-MM-DDTHH:mm:ss';
const DISPLAY_DATETIME_FORMAT = 'DD/MM/YYYY HH:mm';

interface PromotionFormModalProps {
  open: boolean;
  promotionId?: number;
  onClose: () => void;
  onSuccess: () => void;
}

interface PromotionFormValues {
  name: string;
  discountType: 'PERCENT' | 'AMOUNT';
  discountValue?: number | null;
  dateRange?: [Dayjs, Dayjs] | null;
  isActive: boolean;
  productIds: number[];
}

interface ProductOption {
  value: number;
  label: string;
  price?: number;
}

type FormFieldName = keyof PromotionFormValues;

export const PromotionFormModal: React.FC<PromotionFormModalProps> = ({
  open,
  promotionId,
  onClose,
  onSuccess,
}) => {
  const [form] = Form.useForm<PromotionFormValues>();
  const isEditing = Boolean(promotionId);

  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [detailData, setDetailData] = useState<PromotionDetail | null>(null);

  const [searchedProducts, setSearchedProducts] = useState<ProductOption[]>([]);
  const [isSearchingProducts, setIsSearchingProducts] = useState(false);
  const [productSearchInput, setProductSearchInput] = useState('');

  const createPromotionMutation = useCreatePromotion();
  const updatePromotionMutation = useUpdatePromotion();

  const discountType = Form.useWatch('discountType', form) ?? 'PERCENT';

  useEffect(() => {
    if (!open) {
      form.resetFields();
      setDetailData(null);
      setSearchedProducts([]);
      setProductSearchInput('');
      return;
    }

    if (promotionId) {
      setIsLoadingDetail(true);
      getAdminPromotionById(promotionId)
        .then((data) => {
          setDetailData(data);
          const initialProductOptions: ProductOption[] = (data.products ?? [])
            .filter((p): p is typeof p & { id: number; name: string } => typeof p.id === 'number' && Boolean(p.name))
            .map((p) => ({
              value: p.id,
              label: p.name,
              price: p.basePrice,
            }));
          setSearchedProducts(initialProductOptions);

          form.setFieldsValue({
            name: data.name ?? '',
            discountType: data.discountType ?? 'PERCENT',
            discountValue: data.discountValue,
            dateRange: [dayjs(data.startAt), dayjs(data.endAt)],
            isActive: data.isActive ?? true,
            productIds: (data.products ?? [])
              .map((p) => p.id)
              .filter((id): id is number => typeof id === 'number'),
          });
        })
        .catch((error) => {
          message.error(getApiErrorMessage(error, 'Không thể tải thông tin khuyến mãi'));
          onClose();
        })
        .finally(() => {
          setIsLoadingDetail(false);
        });
    } else {
      const now = dayjs().second(0).millisecond(0);
      form.setFieldsValue({
        name: '',
        discountType: 'PERCENT',
        discountValue: undefined,
        dateRange: [now, now.add(7, 'day')],
        isActive: true,
        productIds: [],
      });
      loadInitialProducts();
    }
  }, [open, promotionId, form, onClose]);

  const loadInitialProducts = async () => {
    try {
      setIsSearchingProducts(true);
      const products = await searchAdminProducts('');
      const options: ProductOption[] = products
        .filter((p): p is typeof p & { id: number; name: string } => typeof p.id === 'number' && Boolean(p.name))
        .map((p) => ({
          value: p.id,
          label: p.name,
          price: p.basePrice,
        }));
      setSearchedProducts(options);
    } catch {
      // Ignore initial load failure
    } finally {
      setIsSearchingProducts(false);
    }
  };

  useEffect(() => {
    if (!open) return;
    const timer = window.setTimeout(async () => {
      try {
        setIsSearchingProducts(true);
        const products = await searchAdminProducts(productSearchInput);
        const newOptions: ProductOption[] = products
          .filter((p): p is typeof p & { id: number; name: string } => typeof p.id === 'number' && Boolean(p.name))
          .map((p) => ({
            value: p.id,
            label: p.name,
            price: p.basePrice,
          }));

        setSearchedProducts((prev) => {
          const currentIds = new Set(prev.map((opt) => opt.value));
          const merged = [...prev];
          for (const opt of newOptions) {
            if (!currentIds.has(opt.value)) {
              merged.push(opt);
              currentIds.add(opt.value);
            }
          }
          return merged;
        });
      } catch {
        // Ignore search error
      } finally {
        setIsSearchingProducts(false);
      }
    }, 400);

    return () => window.clearTimeout(timer);
  }, [productSearchInput, open]);

  const handleDiscountTypeChange = (value: 'PERCENT' | 'AMOUNT') => {
    const currentValue = form.getFieldValue('discountValue');
    if (value === 'PERCENT' && currentValue && currentValue > 100) {
      form.setFieldsValue({ discountValue: 100 });
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const startAt = values.dateRange?.[0]
        ? values.dateRange[0].format(ISO_FORMAT)
        : '';
      const endAt = values.dateRange?.[1]
        ? values.dateRange[1].format(ISO_FORMAT)
        : '';

      const rawCandidate = {
        name: values.name?.trim() ?? '',
        discountType: values.discountType,
        discountValue: values.discountValue ?? 0,
        startAt,
        endAt,
        isActive: values.isActive ?? true,
        productIds: values.productIds ?? [],
      };

      const parseResult = isEditing
        ? promotionUpdateSchema.safeParse(rawCandidate)
        : promotionCreateSchema.safeParse(rawCandidate);

      if (!parseResult.success) {
        const fieldErrors: { name: FormFieldName; errors: string[] }[] = [];
        for (const issue of parseResult.error.issues) {
          const fieldName = String(issue.path[0] ?? 'name');
          const targetField: FormFieldName =
            fieldName === 'startAt' || fieldName === 'endAt'
              ? 'dateRange'
              : (fieldName as FormFieldName);
          fieldErrors.push({
            name: targetField,
            errors: [issue.message],
          });
        }
        form.setFields(fieldErrors);
        return;
      }

      const validated = parseResult.data;

      if (isEditing && promotionId) {
        await updatePromotionMutation.mutateAsync({
          id: promotionId,
          payload: {
            name: validated.name,
            discountType: validated.discountType,
            discountValue: validated.discountValue,
            startAt: validated.startAt,
            endAt: validated.endAt,
            isActive: validated.isActive,
            productIds: validated.productIds,
          },
        });
        message.success('Cập nhật khuyến mãi thành công');
      } else {
        await createPromotionMutation.mutateAsync({
          name: validated.name,
          discountType: validated.discountType,
          discountValue: validated.discountValue,
          startAt: validated.startAt,
          endAt: validated.endAt,
          isActive: validated.isActive,
          productIds: validated.productIds,
        });
        message.success('Tạo khuyến mãi thành công');
      }

      onSuccess();
      onClose();
    } catch (error: unknown) {
      const fieldErrors = getApiFieldErrors(error);
      if (fieldErrors) {
        const formFieldList: { name: FormFieldName; errors: string[] }[] = [];
        for (const [key, msg] of Object.entries(fieldErrors)) {
          const targetField: FormFieldName =
            key === 'startAt' || key === 'endAt' ? 'dateRange' : (key as FormFieldName);
          formFieldList.push({ name: targetField, errors: [msg] });
        }
        form.setFields(formFieldList);
      }
      message.error(
        getApiErrorMessage(
          error,
          isEditing ? 'Cập nhật khuyến mãi thất bại' : 'Tạo khuyến mãi thất bại'
        )
      );
    }
  };

  const isSubmitting = createPromotionMutation.isPending || updatePromotionMutation.isPending;

  return (
    <Modal
      title={
        <span style={{ fontSize: 16, fontWeight: 600, color: PROMOTION_THEME.primary }}>
          {isEditing ? 'Chỉnh sửa chương trình khuyến mãi' : 'Tạo chương trình khuyến mãi mới'}
        </span>
      }
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      confirmLoading={isSubmitting}
      okText={isEditing ? 'Lưu thay đổi' : 'Tạo khuyến mãi'}
      cancelText="Hủy"
      width={680}
      destroyOnClose
      maskClosable={false}
      styles={{
        body: { padding: '8px 0 0 0' },
        header: { marginBottom: 16 },
      }}
    >
      {isLoadingDetail ? (
        <div style={{ textAlign: 'center', padding: '40px 0' }}>
          <Spin tip="Đang tải thông tin khuyến mãi..." />
        </div>
      ) : (
        <Form
          form={form}
          layout="vertical"
          requiredMark="optional"
          autoComplete="off"
        >
          {isEditing && detailData?.status === 'EXPIRED' && (
            <Alert
              type="warning"
              showIcon
              message="Chương trình khuyến mãi này đã kết thúc."
              description="Bạn vẫn có thể cập nhật thông tin hoặc lùi thời gian kết thúc để kích hoạt lại."
              style={{
                marginBottom: 16,
                borderRadius: 6,
                borderColor: PROMOTION_THEME.border,
                backgroundColor: PROMOTION_THEME.mutedSurface,
              }}
            />
          )}

          <Form.Item
            name="name"
            label={<Text strong>Tên khuyến mãi</Text>}
            rules={[{ required: true, message: 'Tên khuyến mãi không được để trống' }]}
          >
            <Input
              placeholder="Ví dụ: Giảm giá mùa hè 2026, Xả kho đón thu..."
              maxLength={150}
              showCount
              style={{ borderRadius: 6 }}
            />
          </Form.Item>

          <Space direction="horizontal" size={16} style={{ width: '100%' }}>
            <Form.Item
              name="discountType"
              label={<Text strong>Loại giảm giá</Text>}
              rules={[{ required: true, message: 'Loại giảm giá không được để trống' }]}
              style={{ width: 220 }}
            >
              <Select
                options={[
                  { value: 'PERCENT', label: 'Theo phần trăm (%)' },
                  { value: 'AMOUNT', label: 'Theo số tiền (VND)' },
                ]}
                onChange={handleDiscountTypeChange}
                style={{ borderRadius: 6 }}
              />
            </Form.Item>

            <Form.Item
              name="discountValue"
              label={
                <Text strong>
                  Mức giảm {discountType === 'PERCENT' ? '(%)' : '(VND)'}
                </Text>
              }
              rules={[{ required: true, message: 'Giá trị giảm không được để trống' }]}
              style={{ flex: 1 }}
            >
              <InputNumber
                style={{ width: '100%', borderRadius: 6 }}
                min={1}
                max={discountType === 'PERCENT' ? 100 : MAX_PROMOTION_AMOUNT}
                placeholder={discountType === 'PERCENT' ? '1 - 100' : 'Ví dụ: 50.000'}
                addonAfter={discountType === 'PERCENT' ? '%' : 'đ'}
                formatter={(value) =>
                  value ? `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.') : ''
                }
                parser={(value) =>
                  Number(value?.replace(/\./g, '') || 0) as 1
                }
              />
            </Form.Item>
          </Space>

          <Form.Item
            name="dateRange"
            label={<Text strong>Thời gian áp dụng</Text>}
            rules={[{ required: true, message: 'Thời gian áp dụng không được để trống' }]}
            extra="Thời gian bắt đầu và kết thúc áp dụng cho đợt khuyến mãi này."
          >
            <RangePicker
              showTime={{ format: 'HH:mm' }}
              format={DISPLAY_DATETIME_FORMAT}
              placeholder={['Thời gian bắt đầu', 'Thời gian kết thúc']}
              style={{ width: '100%', borderRadius: 6 }}
            />
          </Form.Item>

          <Form.Item
            name="productIds"
            label={
              <Space>
                <Text strong>Sản phẩm áp dụng</Text>
                <Text type="secondary" style={{ fontSize: 13, color: PROMOTION_THEME.mutedText }}>
                  (Tùy chọn, tối đa {MAX_PROMOTION_PRODUCTS} sản phẩm)
                </Text>
              </Space>
            }
            extra="Nếu không chọn sản phẩm nào, chương trình vẫn được lưu để bổ sung sản phẩm sau."
          >
            <Select
              mode="multiple"
              allowClear
              placeholder="Nhập tên sản phẩm để tìm kiếm..."
              loading={isSearchingProducts}
              onSearch={setProductSearchInput}
              filterOption={false}
              maxCount={MAX_PROMOTION_PRODUCTS}
              options={searchedProducts.map((opt) => ({
                value: opt.value,
                label: (
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span>{opt.label}</span>
                    {opt.price !== undefined && (
                      <span style={{ color: PROMOTION_THEME.mutedText, fontSize: 12 }}>
                        {formatCurrencyVND(opt.price)}
                      </span>
                    )}
                  </div>
                ),
              }))}
              style={{ width: '100%', borderRadius: 6 }}
            />
          </Form.Item>

          <Form.Item
            name="isActive"
            valuePropName="checked"
            label={<Text strong>Trạng thái kích hoạt</Text>}
            extra="Bật để cho phép khuyến mãi có hiệu lực khi đến khung giờ."
          >
            <Switch checkedChildren="Bật" unCheckedChildren="Tắt" />
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
};
