import React from 'react';
import { Skeleton, Typography } from 'antd';
import {
  CarOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons';
import { SHIPPING_THEME } from '../constants/theme';
import type { ShippingStatus, ShippingSummaryResponse } from '../shipping.types';

const { Text } = Typography;

interface ShippingSummaryCardsProps {
  summary?: ShippingSummaryResponse;
  isLoading: boolean;
  selectedStatus?: ShippingStatus | 'ALL';
  onSelectStatus: (status: ShippingStatus | 'ALL') => void;
}

export const ShippingSummaryCards: React.FC<ShippingSummaryCardsProps> = ({
  summary,
  isLoading,
  selectedStatus,
  onSelectStatus,
}) => {
  const cards = [
    {
      key: 'readyToShip',
      title: 'Đơn chờ giao',
      value: summary?.readyToShipCount ?? 0,
      icon: <ClockCircleOutlined style={{ fontSize: 20, color: SHIPPING_THEME.warning }} />,
      status: 'CONFIRMED' as const,
      active: selectedStatus === 'CONFIRMED',
      accentColor: SHIPPING_THEME.warning,
      subText: 'Đã xác nhận, sẵn sàng xuất kho',
    },
    {
      key: 'delivering',
      title: 'Đơn đang giao',
      value: summary?.deliveringCount ?? 0,
      icon: <CarOutlined style={{ fontSize: 20, color: SHIPPING_THEME.info }} />,
      status: 'SHIPPING' as const,
      active: selectedStatus === 'SHIPPING',
      accentColor: SHIPPING_THEME.info,
      subText: 'Đang trên đường vận chuyển',
    },
    {
      key: 'deliveredToday',
      title: 'Đã giao hôm nay',
      value: summary?.deliveredTodayCount ?? 0,
      icon: <CheckCircleOutlined style={{ fontSize: 20, color: SHIPPING_THEME.success }} />,
      status: 'COMPLETED' as const,
      active: selectedStatus === 'COMPLETED',
      accentColor: SHIPPING_THEME.success,
      subText: 'Hoàn thành trong ngày (giờ VN)',
    },
  ];

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
        gap: 16,
        marginBottom: 16,
      }}
    >
      {cards.map((card) => {
        const isActive = card.active;

        return (
          <div
            key={card.key}
            onClick={() => {
              onSelectStatus(isActive ? 'ALL' : card.status);
            }}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                onSelectStatus(isActive ? 'ALL' : card.status);
              }
            }}
            style={{
              backgroundColor: SHIPPING_THEME.surface,
              border: `1px solid ${isActive ? card.accentColor : SHIPPING_THEME.border}`,
              borderRadius: 6,
              padding: '16px 20px',
              cursor: 'pointer',
              transition: 'all 0.2s ease',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              boxShadow: isActive ? '0 1px 4px rgba(0,0,0,0.08)' : 'none',
              outline: 'none',
            }}
          >
            <div>
              <Text
                style={{
                  fontSize: 13,
                  color: SHIPPING_THEME.mutedText,
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
                    fontSize: 24,
                    fontWeight: 600,
                    color: SHIPPING_THEME.primary,
                    lineHeight: 1.2,
                  }}
                >
                  {card.value.toLocaleString('vi-VN')}
                </span>
              )}
              <Text
                style={{
                  fontSize: 12,
                  color: SHIPPING_THEME.mutedText,
                  display: 'block',
                  marginTop: 4,
                }}
              >
                {card.subText}
              </Text>
            </div>
            <div
              style={{
                width: 44,
                height: 44,
                borderRadius: 6,
                backgroundColor: SHIPPING_THEME.mutedSurface,
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
