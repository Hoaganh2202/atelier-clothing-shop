import React from 'react';
import { Input, Select, InputNumber, Button, Space } from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import { useAdminCategories } from '@/features/admin/category/api';
import {
  INVENTORY_THEME,

  MIN_THRESHOLD,
  MAX_THRESHOLD,
} from '../constants';
import type { InventoryStockStatusType } from '../api';

export interface InventoryFilterValues {
  keyword?: string;
  categoryId?: number;
  stockStatus: InventoryStockStatusType;
  productActive?: boolean;
  threshold?: number;
}

interface InventoryFilterBarProps {
  filters: InventoryFilterValues;
  onChange: (updated: Partial<InventoryFilterValues>) => void;
  onReset: () => void;
}

export const InventoryFilterBar: React.FC<InventoryFilterBarProps> = ({
  filters,
  onChange,
  onReset,
}) => {
  const { data: categories = [], isLoading: isLoadingCategories } = useAdminCategories();

  return (
    <div
      style={{
        backgroundColor: INVENTORY_THEME.surface,
        border: `1px solid ${INVENTORY_THEME.border}`,
        borderRadius: 6,
        padding: '16px',
        marginBottom: 16,
      }}
    >
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 12,
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Space wrap size={12} style={{ flex: 1, minWidth: 280 }}>
          {/* Tìm kiếm */}
          <Input
            placeholder="Tìm theo tên sản phẩm, mã SKU..."
            prefix={<SearchOutlined style={{ color: INVENTORY_THEME.mutedText }} />}
            allowClear
            value={filters.keyword ?? ''}
            onChange={(e) => onChange({ keyword: e.target.value || undefined })}
            style={{ width: 260, borderRadius: 6 }}
          />

          {/* Lọc danh mục */}
          <Select
            placeholder="Tất cả danh mục"
            allowClear
            loading={isLoadingCategories}
            value={filters.categoryId}
            onChange={(val) => onChange({ categoryId: val })}
            style={{ width: 180 }}
            options={categories.map((c) => ({
              label: c.name,
              value: c.id,
            }))}
          />

          {/* Trạng thái tồn kho */}
          <Select
            value={filters.stockStatus}
            onChange={(val) => onChange({ stockStatus: val })}
            style={{ width: 160 }}
            options={[
              { label: 'Tất cả trạng thái kho', value: 'ALL' },
              { label: 'Còn hàng', value: 'IN_STOCK' },
              { label: 'Sắp hết hàng', value: 'LOW_STOCK' },
              { label: 'Hết hàng', value: 'OUT_OF_STOCK' },
            ]}
          />

          {/* Trạng thái kinh doanh */}
          <Select
            placeholder="Trạng thái kinh doanh"
            allowClear
            value={filters.productActive}
            onChange={(val) => onChange({ productActive: val })}
            style={{ width: 180 }}
            options={[
              { label: 'Đang bán', value: true },
              { label: 'Ngừng bán', value: false },
            ]}
          />

          {/* Ngưỡng cảnh báo */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 13, color: INVENTORY_THEME.mutedText }}>
              Sắp hết khi dưới:
            </span>
            <InputNumber
              min={MIN_THRESHOLD}
              max={MAX_THRESHOLD}
              value={filters.threshold}
              placeholder="Theo cài đặt hệ thống"
              onChange={(val) => onChange({ threshold: val ?? undefined })}
              style={{ width: 175, borderRadius: 6 }}
            />
          </div>
        </Space>

        {/* Nút đặt lại */}
        <Button
          icon={<ReloadOutlined />}
          onClick={onReset}
          style={{
            borderRadius: 6,
            color: INVENTORY_THEME.mutedText,
            borderColor: INVENTORY_THEME.border,
          }}
        >
          Đặt lại
        </Button>
      </div>
    </div>
  );
};
