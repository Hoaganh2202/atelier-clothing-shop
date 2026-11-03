import React, { useEffect, useState } from 'react';
import { Modal, Form, InputNumber, Typography, Alert, message, Space, Divider } from 'antd';
import {
  getInventoryVariants,
  isConflictError,
  isNotFoundError,
  getApiErrorMessage,
  type InventoryVariantItem,
} from '../api';
import { useUpdateInventoryStock } from '../hooks';
import { INVENTORY_THEME } from '../constants';

const { Text } = Typography;

interface InventoryStockUpdateModalProps {
  variant: InventoryVariantItem | null;
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  onUpdatingChange?: (isUpdating: boolean) => void;
}

export const InventoryStockUpdateModal: React.FC<InventoryStockUpdateModalProps> = ({
  variant,
  open,
  onClose,
  onSuccess,
  onUpdatingChange,
}) => {
  const [form] = Form.useForm<{ newStock: number }>();
  const [currentExpectedStock, setCurrentExpectedStock] = useState<number>(0);
  const [conflictMessage, setConflictMessage] = useState<string | null>(null);

  const updateMutation = useUpdateInventoryStock();

  useEffect(() => {
    onUpdatingChange?.(updateMutation.isPending);
  }, [updateMutation.isPending, onUpdatingChange]);

  useEffect(() => {
    if (variant && open) {
      const stock = variant.stock ?? 0;
      setCurrentExpectedStock(stock);
      form.setFieldsValue({ newStock: stock });
      setConflictMessage(null);
    }
  }, [variant, open, form]);

  const currentNewStock = Form.useWatch('newStock', form);
  const diff = typeof currentNewStock === 'number' ? currentNewStock - currentExpectedStock : 0;

  if (!variant) return null;

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (!variant.id) return;

      updateMutation.mutate(
        {
          id: variant.id,
          payload: {
            newStock: values.newStock,
            expectedStock: currentExpectedStock,
          },
        },
        {
          onSuccess: (updated) => {
            message.success(`Đã cập nhật tồn kho biến thể ${variant.sku} thành ${updated.stock}`);
            onClose();
            onSuccess();
          },
          onError: async (error) => {
            if (isConflictError(error)) {
              let latestStock: number | null = null;
              if (variant.sku) {
                try {
                  const freshData = await getInventoryVariants({
                    keyword: variant.sku,
                    pageSize: 1,
                  });
                  const freshItem = freshData.content?.find((it) => it.id === variant.id);
                  if (freshItem && typeof freshItem.stock === 'number') {
                    latestStock = freshItem.stock;
                    setCurrentExpectedStock(freshItem.stock);
                  }
                } catch {
                  // Fallback: keep previous expected stock if network fails
                }
              }
              const baseMsg = getApiErrorMessage(
                error,
                'Tồn kho đã bị thay đổi bởi thao tác khác.'
              );
              const detailMsg =
                latestStock !== null
                  ? `${baseMsg} Tồn kho hiện tại trên hệ thống là ${new Intl.NumberFormat('vi-VN').format(latestStock)}. Dữ liệu đã được làm mới, vui lòng điều chỉnh và bấm Lưu lại nếu bạn vẫn muốn cập nhật.`
                  : `${baseMsg} Vui lòng kiểm tra lại số lượng và thử lại.`;
              setConflictMessage(detailMsg);
            } else if (isNotFoundError(error)) {
              message.error('Biến thể không còn tồn tại trên hệ thống.');
              onClose();
              onSuccess();
            } else {
              message.error(getApiErrorMessage(error, 'Không thể cập nhật tồn kho.'));
            }
          },
        }
      );
    } catch {
      // Form validation error
    }
  };

  const isFormValid =
    typeof currentNewStock === 'number' &&
    currentNewStock >= 0 &&
    currentNewStock <= 1000000 &&
    Number.isInteger(currentNewStock);

  return (
    <Modal
      title="Cập nhật tồn kho biến thể"
      open={open}
      onOk={handleSubmit}
      onCancel={() => {
        if (!updateMutation.isPending) {
          onClose();
        }
      }}
      okText="Lưu thay đổi"
      cancelText="Hủy"
      confirmLoading={updateMutation.isPending}
      okButtonProps={{
        disabled: !isFormValid,
        style: {
          backgroundColor: INVENTORY_THEME.primary,
          borderColor: INVENTORY_THEME.primary,
          borderRadius: 6,
        },
      }}
      cancelButtonProps={{
        disabled: updateMutation.isPending,
        style: { borderRadius: 6 },
      }}
      destroyOnClose
      width={480}
    >
      <div style={{ marginTop: 16 }}>
        {conflictMessage && (
          <Alert
            message="Xung đột dữ liệu (409)"
            description={conflictMessage}
            type="warning"
            showIcon
            style={{ marginBottom: 16, borderRadius: 6 }}
          />
        )}

        <div
          style={{
            backgroundColor: INVENTORY_THEME.mutedSurface,
            borderRadius: 6,
            padding: '12px 16px',
            marginBottom: 20,
          }}
        >
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            <div>
              <Text type="secondary" style={{ fontSize: 13 }}>
                Sản phẩm:
              </Text>{' '}
              <Text strong>{variant.productName ?? '—'}</Text>
            </div>
            <div>
              <Text type="secondary" style={{ fontSize: 13 }}>
                Mã SKU:
              </Text>{' '}
              <Text code>{variant.sku ?? '—'}</Text>
            </div>
            <div>
              <Text type="secondary" style={{ fontSize: 13 }}>
                Kích cỡ / Màu sắc:
              </Text>{' '}
              <Text>
                {variant.size ?? '—'} / {variant.color ?? '—'}
              </Text>
            </div>
          </Space>
        </div>

        <Form form={form} layout="vertical">
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: 16,
            }}
          >
            <Text type="secondary" style={{ fontSize: 13 }}>
              Tồn kho hiện tại:
            </Text>
            <span style={{ fontSize: 18, fontWeight: 600, color: INVENTORY_THEME.primary }}>
              {new Intl.NumberFormat('vi-VN').format(currentExpectedStock)}
            </span>
          </div>

          <Divider style={{ margin: '8px 0 16px' }} />

          <Form.Item
            name="newStock"
            label="Số lượng tồn kho mới"
            rules={[
              { required: true, message: 'Vui lòng nhập số lượng tồn kho mới' },
              {
                validator: async (_, value) => {
                  if (value === undefined || value === null) return;
                  if (!Number.isInteger(value)) {
                    throw new Error('Số lượng tồn kho phải là số nguyên');
                  }
                  if (value < 0) {
                    throw new Error('Số lượng tồn kho không được âm');
                  }
                  if (value > 1000000) {
                    throw new Error('Số lượng tồn kho không được vượt quá 1.000.000');
                  }
                },
              },
            ]}
          >
            <InputNumber
              style={{ width: '100%', borderRadius: 6 }}
              size="large"
              min={0}
              max={1000000}
              precision={0}
              placeholder="Nhập số lượng tồn kho mới"
            />
          </Form.Item>

          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              fontSize: 13,
              marginTop: -8,
              minHeight: 22,
            }}
          >
            <Text type="secondary">Chênh lệch so với hiện tại:</Text>
            <span
              style={{
                fontWeight: 600,
                color:
                  diff > 0
                    ? INVENTORY_THEME.success
                    : diff < 0
                    ? INVENTORY_THEME.danger
                    : INVENTORY_THEME.mutedText,
              }}
            >
              {diff > 0 ? `+${new Intl.NumberFormat('vi-VN').format(diff)}` : diff < 0 ? new Intl.NumberFormat('vi-VN').format(diff) : 'Không thay đổi'}
            </span>
          </div>
        </Form>
      </div>
    </Modal>
  );
};
