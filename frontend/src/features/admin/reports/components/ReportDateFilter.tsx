import React, { useMemo, useState } from 'react';
import {
  Space,
  DatePicker,
  Button,
  Radio,
  Alert,
  Tooltip,
  Typography,
} from 'antd';
import {
  CalendarOutlined,
  ReloadOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';
import isSameOrBefore from 'dayjs/plugin/isSameOrBefore';
import type { DateRangeFilter } from '../reports.types';
import { TIMEZONE_VN } from '../constants';

dayjs.extend(utc);
dayjs.extend(timezone);
dayjs.extend(isSameOrBefore);

const { Text } = Typography;
const { RangePicker } = DatePicker;

export type PresetRangeKey = 'today' | '7days' | '30days' | '90days' | 'thisMonth' | 'custom';

interface ReportDateFilterProps {
  value: DateRangeFilter;
  onChange: (filter: DateRangeFilter) => void;
  onRefresh?: () => void;
  isLoading?: boolean;
}

export function getNowInVn(): Dayjs {
  return dayjs().tz(TIMEZONE_VN);
}

export function formatDateVn(date: Dayjs): string {
  return date.tz(TIMEZONE_VN).format('YYYY-MM-DD');
}

export const ReportDateFilter: React.FC<ReportDateFilterProps> = ({
  value,
  onChange,
  onRefresh,
  isLoading = false,
}) => {
  const [selectedPreset, setSelectedPreset] = useState<PresetRangeKey>('30days');
  const [validationError, setValidationError] = useState<string | null>(null);

  const nowVn = useMemo(() => getNowInVn(), []);

  const rangeValues = useMemo<[Dayjs, Dayjs] | null>(() => {
    if (!value.startDate || !value.endDate) return null;
    const start = dayjs.tz(value.startDate, TIMEZONE_VN);
    const end = dayjs.tz(value.endDate, TIMEZONE_VN);
    if (!start.isValid() || !end.isValid()) return null;
    return [start, end];
  }, [value.startDate, value.endDate]);

  const validateAndApplyRange = (
    start: Dayjs,
    end: Dayjs,
    preset: PresetRangeKey
  ): boolean => {
    if (start.isAfter(end, 'day')) {
      setValidationError('Ngày bắt đầu không được lớn hơn ngày kết thúc.');
      return false;
    }

    const diffDays = end.diff(start, 'day') + 1;
    if (diffDays > 366) {
      setValidationError('Khoảng thời gian báo cáo không được vượt quá 366 ngày (1 năm).');
      return false;
    }

    setValidationError(null);
    setSelectedPreset(preset);
    onChange({
      startDate: formatDateVn(start),
      endDate: formatDateVn(end),
    });
    return true;
  };

  const handlePresetChange = (preset: PresetRangeKey) => {
    const today = getNowInVn().startOf('day');
    let start: Dayjs;
    let end: Dayjs = today;

    switch (preset) {
      case 'today':
        start = today;
        end = today;
        break;
      case '7days':
        start = today.subtract(6, 'day');
        break;
      case '30days':
        start = today.subtract(29, 'day');
        break;
      case '90days':
        start = today.subtract(89, 'day');
        break;
      case 'thisMonth':
        start = today.startOf('month');
        break;
      case 'custom':
      default:
        setSelectedPreset('custom');
        return;
    }

    validateAndApplyRange(start, end, preset);
  };

  const handleRangePickerChange = (dates: [Dayjs | null, Dayjs | null] | null) => {
    if (!dates || !dates[0] || !dates[1]) {
      return;
    }
    validateAndApplyRange(dates[0], dates[1], 'custom');
  };

  const pickerPresets = useMemo(
    () => [
      {
        label: 'Hôm nay',
        value: [nowVn.startOf('day'), nowVn.startOf('day')] as [Dayjs, Dayjs],
      },
      {
        label: '7 ngày qua',
        value: [nowVn.subtract(6, 'day').startOf('day'), nowVn.startOf('day')] as [Dayjs, Dayjs],
      },
      {
        label: '30 ngày qua',
        value: [nowVn.subtract(29, 'day').startOf('day'), nowVn.startOf('day')] as [Dayjs, Dayjs],
      },
      {
        label: '90 ngày qua',
        value: [nowVn.subtract(89, 'day').startOf('day'), nowVn.startOf('day')] as [Dayjs, Dayjs],
      },
      {
        label: 'Tháng này',
        value: [nowVn.startOf('month'), nowVn.startOf('day')] as [Dayjs, Dayjs],
      },
    ],
    [nowVn]
  );

  return (
    <div
      style={{
        background: '#FFFFFF',
        padding: '16px 20px',
        borderRadius: 8,
        border: '1px solid #E4E4E7',
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
      }}
    >
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
        }}
      >
        <Space wrap size={12} align="center">
          <Space size={6} align="center">
            <CalendarOutlined style={{ color: '#18181B', fontSize: 16 }} />
            <Text strong style={{ fontSize: 14 }}>
              Kỳ báo cáo:
            </Text>
          </Space>

          <Radio.Group
            value={selectedPreset}
            onChange={(e) => handlePresetChange(e.target.value as PresetRangeKey)}
            buttonStyle="solid"
            size="middle"
            style={{ maxWidth: '100%', overflowX: 'auto', display: 'inline-flex' }}
          >
            <Radio.Button value="today">Hôm nay</Radio.Button>
            <Radio.Button value="7days">7 ngày</Radio.Button>
            <Radio.Button value="30days">30 ngày</Radio.Button>
            <Radio.Button value="90days">90 ngày</Radio.Button>
            <Radio.Button value="thisMonth">Tháng này</Radio.Button>
          </Radio.Group>

          <RangePicker
            value={rangeValues}
            onChange={handleRangePickerChange}
            format="DD/MM/YYYY"
            presets={pickerPresets}
            disabledDate={(current) => current && current.isAfter(nowVn, 'day')}
            allowClear={false}
            style={{ width: '100%', maxWidth: 280, minWidth: 220 }}
          />
        </Space>

        <Space size={8} align="center">
          <Tooltip title="Dữ liệu doanh thu tính theo múi giờ Việt Nam (UTC+7, Asia/Ho_Chi_Minh)">
            <Text type="secondary" style={{ fontSize: 12, display: 'flex', alignItems: 'center', gap: 4 }}>
              <InfoCircleOutlined />
              {TIMEZONE_VN}
            </Text>
          </Tooltip>

          {onRefresh && (
            <Button
              icon={<ReloadOutlined spin={isLoading} />}
              onClick={onRefresh}
              loading={isLoading}
            >
              Làm mới
            </Button>
          )}
        </Space>
      </div>

      {validationError && (
        <Alert
          type="error"
          message={validationError}
          showIcon
          closable
          onClose={() => setValidationError(null)}
          style={{ fontSize: 13 }}
        />
      )}
    </div>
  );
};
