import React from 'react';
import { Skeleton, Typography } from 'antd';
import {
  InboxOutlined,
  AppstoreOutlined,
  AlertOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { INVENTORY_THEME } from '../constants';
import type { InventorySummary, InventoryStockStatusType } from '../api';

const { Text } = Typography;

interface InventorySummaryCardsProps {
  summary?: InventorySummary;
  isLoading: boolean;
  selectedStatus?: string;
  onSelectStockStatus: (status: InventoryStockStatusType) => void;
}

export const InventorySummaryCards: React.FC<InventorySummaryCardsProps> = ({
  summary,
  isLoading,
  selectedStatus,
  onSelectStockStatus,
}) => {
  const cards = [
    {
      key: 'totalVariants',
      title: 'Tổng biến thể',
      value: summary?.totalVariants ?? 0,
      icon: <AppstoreOutlined style={{ fontSize: 18, color: INVENTORY_THEME.mutedText }} />,
      clickable: false,
      status: 'ALL' as const,
      active: selectedStatus === 'ALL' || !selectedStatus,
      valueColor: INVENTORY_THEME.primary,
    },
    {
      key: 'totalStock',
      title: 'Tổng tồn kho',
      value: summary?.totalStock ?? 0,
      icon: <InboxOutlined style={{ fontSize: 18, color: INVENTORY_THEME.primary }} />,
      clickable: false,
      status: 'ALL' as const,
      active: false,
      valueColor: INVENTORY_THEME.primary,
    },
    {
      key: 'lowStock',
      title: 'Sắp hết hàng',
      value: summary?.lowStockCount ?? 0,
      icon: <AlertOutlined style={{ fontSize: 18, color: INVENTORY_THEME.warning }} />,
      clickable: true,
      status: 'LOW_STOCK' as const,
      active: selectedStatus === 'LOW_STOCK',
      valueColor: INVENTORY_THEME.warning,
      accentBorder: INVENTORY_THEME.warning,
    },
    {
      key: 'outOfStock',
      title: 'Hết hàng',
      value: summary?.outOfStockCount ?? 0,
      icon: <StopOutlined style={{ fontSize: 18, color: INVENTORY_THEME.danger }} />,
      clickable: true,
      status: 'OUT_OF_STOCK' as const,
      active: selectedStatus === 'OUT_OF_STOCK',
      valueColor: INVENTORY_THEME.danger,
      accentBorder: INVENTORY_THEME.danger,
    },
  ];

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
        gap: 16,
        marginBottom: 16,
      }}
    >
      {cards.map((card) => {
        const isClickable = card.clickable;
        const isActive = card.active;

        return (
          <div
            key={card.key}
            onClick={() => {
              if (isClickable) {
                onSelectStockStatus(isActive ? 'ALL' : card.status);
              }
            }}
            style={{
              backgroundColor: INVENTORY_THEME.surface,
              border: `1px solid ${isActive && card.accentBorder ? card.accentBorder : INVENTORY_THEME.border}`,
              borderRadius: 6,
              padding: '16px 20px',
              cursor: isClickable ? 'pointer' : 'default',
              transition: 'all 0.2s ease',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              boxShadow: isActive ? '0 1px 3px rgba(0,0,0,0.06)' : 'none',
            }}
          >
            <div>
              <Text
                style={{
                  fontSize: 13,
                  color: INVENTORY_THEME.mutedText,
                  display: 'block',
                  marginBottom: 4,
                  fontWeight: 500,
                }}
              >
                {card.title}
              </Text>
              {isLoading ? (
                <Skeleton.Input active size="small" style={{ width: 80, height: 28 }} />
              ) : (
                <span
                  style={{
                    fontSize: 22,
                    fontWeight: 600,
                    color: card.valueColor,
                    lineHeight: 1.2,
                  }}
                >
                  {new Intl.NumberFormat('vi-VN').format(card.value)}
                </span>
              )}
            </div>
            <div
              style={{
                width: 40,
                height: 40,
                borderRadius: 6,
                backgroundColor: INVENTORY_THEME.mutedSurface,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              {card.icon}
            </div>
          </div>
        );
      })}
    </div>
  );
};
