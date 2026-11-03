export interface KpiMetric {
  key: string;
  title: string;
  value: string;
  changeRate: string;
  isPositive: boolean;
  subText: string;
}

export interface RevenueTrendPoint {
  date: string;
  revenue: number;
  orders: number;
}

export interface CustomerTierItem {
  id: string;
  name: string;
  count: number;
  percentage: number;
  color: string;
  description: string;
}

export type OrderStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'CANCELLED';

export interface RecentOrder {
  key: string;
  orderCode: string;
  customerName: string;
  customerPhone: string;
  createdAt: string;
  itemsSummary: string;
  itemCount: number;
  totalAmount: number;
  status: OrderStatus;
}

export interface LowStockItem {
  key: string;
  productName: string;
  variant: string;
  sku: string;
  stock: number;
  safetyThreshold: number;
}

export const ADMIN_KPI_METRICS: KpiMetric[] = [
  {
    key: 'today-revenue',
    title: 'Doanh thu hôm nay',
    value: '48.650.000 ₫',
    changeRate: '+12.4%',
    isPositive: true,
    subText: 'Đạt 92% kế hoạch ngày',
  },
  {
    key: 'today-orders',
    title: 'Đơn hàng mới',
    value: '142 đơn',
    changeRate: '+8.1%',
    isPositive: true,
    subText: '28 đơn cần đóng gói gấp',
  },
  {
    key: 'new-customers',
    title: 'Khách hàng mới',
    value: '39 khách',
    changeRate: '+5.3%',
    isPositive: true,
    subText: '64% giao dịch lần đầu',
  },
  {
    key: 'low-stock-alert',
    title: 'Cảnh báo sắp hết hàng',
    value: '7 mã hàng',
    changeRate: 'Ưu tiên cao',
    isPositive: false,
    subText: 'Tồn dưới ngưỡng 10 sản phẩm',
  },
];

export const ADMIN_REVENUE_TREND: RevenueTrendPoint[] = [
  { date: '01/09', revenue: 32000000, orders: 94 },
  { date: '04/09', revenue: 36500000, orders: 108 },
  { date: '07/09', revenue: 41200000, orders: 122 },
  { date: '10/09', revenue: 39000000, orders: 115 },
  { date: '13/09', revenue: 45800000, orders: 134 },
  { date: '16/09', revenue: 42300000, orders: 126 },
  { date: '19/09', revenue: 47900000, orders: 140 },
  { date: '22/09', revenue: 51200000, orders: 152 },
  { date: '25/09', revenue: 44600000, orders: 130 },
  { date: '28/09', revenue: 53800000, orders: 161 },
  { date: '30/09', revenue: 48650000, orders: 142 },
];

export const ADMIN_CUSTOMER_TIERS: CustomerTierItem[] = [
  {
    id: 'vip',
    name: 'Khách hàng VIP',
    count: 320,
    percentage: 15,
    color: '#18181B',
    description: 'Chi tiêu từ 5.000.000 ₫ trở lên',
  },
  {
    id: 'loyal',
    name: 'Khách hàng thân thiết',
    count: 750,
    percentage: 35,
    color: '#059669',
    description: 'Đã hoàn tất từ 3 đơn hàng trở lên',
  },
  {
    id: 'new',
    name: 'Khách hàng mới',
    count: 600,
    percentage: 28,
    color: '#2563EB',
    description: 'Đã hoàn tất 1 - 2 đơn hàng',
  },
  {
    id: 'potential',
    name: 'Khách hàng tiềm năng',
    count: 470,
    percentage: 22,
    color: '#D97706',
    description: 'Đã đăng ký tài khoản, chưa phát sinh đơn',
  },
];

export const ADMIN_RECENT_ORDERS: RecentOrder[] = [
  {
    key: '1',
    orderCode: '#ATL-9082',
    customerName: 'Nguyễn Hoàng Long',
    customerPhone: '0912***456',
    createdAt: '14:15 hôm nay',
    itemsSummary: 'Áo thun Heavyweight, Quần jean suông',
    itemCount: 2,
    totalAmount: 940000,
    status: 'PENDING',
  },
  {
    key: '2',
    orderCode: '#ATL-9081',
    customerName: 'Lê Thị Mai Phương',
    customerPhone: '0988***112',
    createdAt: '13:40 hôm nay',
    itemsSummary: 'Đầm lụa dập ly Cổ V',
    itemCount: 1,
    totalAmount: 720000,
    status: 'PROCESSING',
  },
  {
    key: '3',
    orderCode: '#ATL-9080',
    customerName: 'Trần Quốc Bảo',
    customerPhone: '0903***789',
    createdAt: '12:20 hôm nay',
    itemsSummary: 'Áo Blazer Linen, 2 Quần âu Slimfit',
    itemCount: 3,
    totalAmount: 1830000,
    status: 'COMPLETED',
  },
  {
    key: '4',
    orderCode: '#ATL-9079',
    customerName: 'Vũ Thu Trang',
    customerPhone: '0934***556',
    createdAt: '10:05 hôm nay',
    itemsSummary: 'Chân váy xòe Midi Linen',
    itemCount: 1,
    totalAmount: 450000,
    status: 'CANCELLED',
  },
  {
    key: '5',
    orderCode: '#ATL-9078',
    customerName: 'Đặng Hữu Tài',
    customerPhone: '0971***334',
    createdAt: '09:15 hôm nay',
    itemsSummary: 'Áo sơ mi Oxford, Cà vạt lụa dệt',
    itemCount: 2,
    totalAmount: 1040000,
    status: 'PROCESSING',
  },
];

export const ADMIN_LOW_STOCK_ITEMS: LowStockItem[] = [
  {
    key: '1',
    productName: 'Áo thun Cotton 250 GSM',
    variant: 'Trắng / Size L',
    sku: 'TEE-WHT-L',
    stock: 3,
    safetyThreshold: 10,
  },
  {
    key: '2',
    productName: 'Quần jean dáng suông Classic',
    variant: 'Indigo / Size 31',
    sku: 'JEA-IND-31',
    stock: 4,
    safetyThreshold: 10,
  },
  {
    key: '3',
    productName: 'Áo sơ mi Oxford Classic',
    variant: 'Xanh nhạt / Size M',
    sku: 'SHT-BLU-M',
    stock: 5,
    safetyThreshold: 10,
  },
  {
    key: '4',
    productName: 'Đầm lụa dập ly dáng dài',
    variant: 'Đen tuyền / Size S',
    sku: 'DRS-BLK-S',
    stock: 6,
    safetyThreshold: 10,
  },
];
