import React, { useEffect, useState } from 'react';
import { useAuthStore } from '@/stores/auth.store';
import {
  Alert,
  Avatar,
  Button,
  Card,
  Col,
  Divider,
  Form,
  Input,
  InputNumber,
  Row,
  Skeleton,
  Space,
  Tabs,
  Tag,
  Typography,
  Upload,
  message,
} from 'antd';
import type { UploadProps } from 'antd';
import {
  KeyOutlined,
  ReloadOutlined,
  ShopOutlined,
  ToolOutlined,
  UploadOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  useAdminAccount,
  useChangePassword,
  useStoreSettings,
  useUpdateAdminAccount,
  useUpdateStoreSettings,
  useUploadLogo,
  getApiErrorMessage,
  type StoreSettingsPayload,
  type UpdateAdminAccountPayload,
  type ChangePasswordPayload,
} from '../api';

const { Title, Text, Paragraph } = Typography;

const cardStyle: React.CSSProperties = {
  borderRadius: 6,
  border: '1px solid #E4E4E7',
  boxShadow: 'none',
  backgroundColor: '#FFFFFF',
};

export const SettingsPage: React.FC = () => {
  const user = useAuthStore((state) => state.user);
  const isStaff = user?.role?.toLowerCase() === 'staff';
  const [activeTab, setActiveTab] = useState(isStaff ? 'account' : 'store');

  useEffect(() => {
    if (isStaff && activeTab !== 'account') {
      setActiveTab('account');
    }
  }, [isStaff, activeTab]);

  const tabItems = [
    ...(!isStaff
      ? [
          {
            key: 'store',
            label: (
              <Space orientation="horizontal" size={6}>
                <ShopOutlined />
                <span>Thông tin cửa hàng</span>
              </Space>
            ),
            children: <StoreInfoTab />,
          },
          {
            key: 'operation',
            label: (
              <Space orientation="horizontal" size={6}>
                <ToolOutlined />
                <span>Vận hành</span>
              </Space>
            ),
            children: <OperationTab />,
          },
        ]
      : []),
    {
      key: 'account',
      label: (
        <Space orientation="horizontal" size={6}>
          <UserOutlined />
          <span>Tài khoản quản trị</span>
        </Space>
      ),
      children: <AdminAccountTab />,
    },
  ];

  return (
    <div style={{ maxWidth: 1040, margin: '0 auto' }}>
      <div style={{ marginBottom: 24 }}>
        <Title level={4} style={{ margin: 0, fontWeight: 700, color: '#09090B' }}>
          Cài đặt hệ thống
        </Title>
        <Text style={{ color: '#71717A', fontSize: 13 }}>
          {isStaff
            ? 'Quản lý thông tin tài khoản cá nhân và đổi mật khẩu'
            : 'Quản lý thông tin nhận diện cửa hàng, thông số vận hành và tài khoản quản trị'}
        </Text>
      </div>

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        type="card"
        style={{
          borderRadius: 6,
        }}
        items={tabItems}
      />
    </div>
  );
};

/* ========================================================================= */
/* TAB 1: THÔNG TIN CỬA HÀNG                                                 */
/* ========================================================================= */
const StoreInfoTab: React.FC = () => {
  const [form] = Form.useForm<StoreSettingsPayload>();
  const [isDirty, setIsDirty] = useState(false);
  const [logoPreview, setLogoPreview] = useState<string | null>(null);

  const { data: storeData, isLoading, isError, error, refetch } = useStoreSettings();
  const updateSettingsMutation = useUpdateStoreSettings();
  const uploadLogoMutation = useUploadLogo();

  useEffect(() => {
    if (storeData) {
      form.setFieldsValue({
        storeName: storeData.storeName || '',
        legalName: storeData.legalName || '',
        taxCode: storeData.taxCode || '',
        address: storeData.address || '',
        phone: storeData.phone || '',
        email: storeData.email || '',
        logoUrl: storeData.logoUrl || '',
        lowStockThreshold: storeData.lowStockThreshold ?? 10,
        defaultShippingFee: storeData.defaultShippingFee ?? 30000,
        freeShippingThreshold: storeData.freeShippingThreshold ?? 500000,
      });
      setLogoPreview(storeData.logoUrl || null);
      setIsDirty(false);
    }
  }, [storeData, form]);

  const handleValuesChange = () => {
    setIsDirty(true);
  };

  const handleBeforeUpload: UploadProps['beforeUpload'] = (file) => {
    const isLt2M = file.size <= 2 * 1024 * 1024;
    if (!isLt2M) {
      message.error('Dung lượng ảnh logo không được vượt quá 2MB');
      return Upload.LIST_IGNORE;
    }
    uploadLogoMutation.mutate(file, {
      onSuccess: (res) => {
        form.setFieldValue('logoUrl', res.logoUrl);
        setLogoPreview(res.logoUrl);
        setIsDirty(true);
        message.success('Tải logo lên thành công');
      },
      onError: (err) => {
        message.error(getApiErrorMessage(err, 'Lỗi tải ảnh logo lên máy chủ'));
      },
    });
    return false;
  };

  const onFinish = (values: StoreSettingsPayload) => {
    const payload: StoreSettingsPayload = {
      ...values,
      lowStockThreshold: storeData?.lowStockThreshold ?? 10,
      defaultShippingFee: storeData?.defaultShippingFee ?? 30000,
      freeShippingThreshold: storeData?.freeShippingThreshold ?? 500000,
    };

    updateSettingsMutation.mutate(payload, {
      onSuccess: () => {
        message.success('Cập nhật thông tin cửa hàng thành công');
        setIsDirty(false);
      },
      onError: (err) => {
        message.error(getApiErrorMessage(err, 'Lỗi cập nhật thông tin cửa hàng'));
      },
    });
  };

  if (isLoading) {
    return (
      <Card style={cardStyle}>
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }

  if (isError) {
    return (
      <Card style={cardStyle}>
        <Alert
          type="error"
          showIcon
          message="Không thể tải cấu hình cửa hàng"
          description={getApiErrorMessage(error, 'Vui lòng kiểm tra kết nối mạng và thử lại.')}
          action={
            <Button size="small" icon={<ReloadOutlined />} onClick={() => refetch()}>
              Thử lại
            </Button>
          }
        />
      </Card>
    );
  }

  return (
    <Card style={cardStyle}>
      <Alert
        type="info"
        showIcon
        style={{
          marginBottom: 20,
          borderRadius: 6,
          border: '1px solid #E4E4E7',
          backgroundColor: '#F8FAFC',
        }}
        message="Các thông tin này dùng cho cửa hàng. Thông tin xuất hóa đơn điện tử được cấu hình riêng (hồ sơ người bán hóa đơn điện tử) và không tự thay đổi theo mục này."
      />
      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        onValuesChange={handleValuesChange}
        requiredMark={false}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 20, marginBottom: 24 }}>
          <Avatar
            shape="square"
            size={72}
            src={logoPreview || undefined}
            style={{
              borderRadius: 6,
              backgroundColor: '#F4F4F5',
              color: '#71717A',
              border: '1px solid #E4E4E7',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 28,
            }}
          >
            {!logoPreview && <ShopOutlined />}
          </Avatar>
          <div>
            <Upload
              showUploadList={false}
              beforeUpload={handleBeforeUpload}
              accept="image/png,image/jpeg,image/webp,image/gif"
            >
              <Button
                icon={<UploadOutlined />}
                loading={uploadLogoMutation.isPending}
                style={{ borderRadius: 6 }}
              >
                Tải ảnh logo
              </Button>
            </Upload>
            <div style={{ fontSize: 12, color: '#71717A', marginTop: 6 }}>
              Chấp nhận định dạng JPG, PNG, WEBP, GIF (tối đa 10MB)
            </div>
          </div>
        </div>

        <Form.Item name="logoUrl" hidden>
          <Input />
        </Form.Item>

        <Row gutter={16}>
          <Col xs={24} md={12}>
            <Form.Item
              name="storeName"
              label={<span style={{ fontWeight: 600 }}>Tên cửa hàng (hiển thị)</span>}
              rules={[
                { required: true, message: 'Vui lòng nhập tên hiển thị của cửa hàng' },
                { max: 150, message: 'Tên cửa hàng tối đa 150 ký tự' },
              ]}
            >
              <Input placeholder="Ví dụ: Closet Shop" style={{ borderRadius: 6 }} />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item
              name="legalName"
              label={<span style={{ fontWeight: 600 }}>Tên pháp nhân công ty</span>}
              rules={[{ max: 255, message: 'Tên pháp nhân tối đa 255 ký tự' }]}
            >
              <Input placeholder="Ví dụ: Công ty TNHH Thời trang Closet" style={{ borderRadius: 6 }} />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col xs={24} md={12}>
            <Form.Item
              name="taxCode"
              label={<span style={{ fontWeight: 600 }}>Mã số thuế</span>}
              rules={[
                {
                  pattern: /^$|^(\d{10}|\d{10}-\d{3})$/,
                  message: 'Mã số thuế phải gồm 10 hoặc 13 chữ số (dạng 1234567890 hoặc 1234567890-001)',
                },
              ]}
            >
              <Input placeholder="10 hoặc 13 chữ số (để trống nếu chưa có)" style={{ borderRadius: 6 }} />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item
              name="phone"
              label={<span style={{ fontWeight: 600 }}>Số điện thoại liên hệ</span>}
              rules={[
                {
                  pattern: /^$|^[0-9+()\s.-]{8,20}$/,
                  message: 'Số điện thoại không đúng định dạng',
                },
              ]}
            >
              <Input placeholder="Ví dụ: 0987654321" style={{ borderRadius: 6 }} />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col xs={24} md={12}>
            <Form.Item
              name="email"
              label={<span style={{ fontWeight: 600 }}>Email liên hệ</span>}
              rules={[{ type: 'email', message: 'Địa chỉ email không hợp lệ' }]}
            >
              <Input placeholder="Ví dụ: contact@closetshop.com" style={{ borderRadius: 6 }} />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item
              name="address"
              label={<span style={{ fontWeight: 600 }}>Địa chỉ trụ sở / cửa hàng</span>}
              rules={[{ max: 300, message: 'Địa chỉ tối đa 300 ký tự' }]}
            >
              <Input placeholder="Ví dụ: 123 Đường Cầu Giấy, Hà Nội" style={{ borderRadius: 6 }} />
            </Form.Item>
          </Col>
        </Row>

        {storeData?.updatedAt && (
          <div style={{ fontSize: 12, color: '#A1A1AA', marginBottom: 16 }}>
            Cập nhật lần cuối: {new Date(storeData.updatedAt).toLocaleString('vi-VN')}
            {storeData.updatedBy ? ` bởi ${storeData.updatedBy}` : ''}
          </div>
        )}

        <Form.Item style={{ marginBottom: 0 }}>
          <Button
            type="primary"
            htmlType="submit"
            disabled={!isDirty}
            loading={updateSettingsMutation.isPending}
            style={{
              backgroundColor: '#18181B',
              borderColor: '#18181B',
              borderRadius: 6,
              fontWeight: 600,
            }}
          >
            Lưu thay đổi
          </Button>
        </Form.Item>
      </Form>
    </Card>
  );
};

/* ========================================================================= */
/* TAB 2: VẬN HÀNH                                                          */
/* ========================================================================= */
const OperationTab: React.FC = () => {
  const [form] = Form.useForm<StoreSettingsPayload>();
  const [isDirty, setIsDirty] = useState(false);

  const { data: storeData, isLoading, isError, error, refetch } = useStoreSettings();
  const updateSettingsMutation = useUpdateStoreSettings();

  useEffect(() => {
    if (storeData) {
      form.setFieldsValue({
        storeName: storeData.storeName || '',
        legalName: storeData.legalName || '',
        taxCode: storeData.taxCode || '',
        address: storeData.address || '',
        phone: storeData.phone || '',
        email: storeData.email || '',
        logoUrl: storeData.logoUrl || '',
        lowStockThreshold: storeData.lowStockThreshold ?? 10,
        defaultShippingFee: storeData.defaultShippingFee ?? 30000,
        freeShippingThreshold: storeData.freeShippingThreshold ?? 500000,
      });
      setIsDirty(false);
    }
  }, [storeData, form]);

  const onFinish = (values: StoreSettingsPayload) => {
    const payload: StoreSettingsPayload = {
      ...values,
      storeName: storeData?.storeName || 'Closet Shop',
      legalName: storeData?.legalName || null,
      taxCode: storeData?.taxCode || null,
      address: storeData?.address || null,
      phone: storeData?.phone || null,
      email: storeData?.email || null,
      logoUrl: storeData?.logoUrl || null,
    };

    updateSettingsMutation.mutate(payload, {
      onSuccess: () => {
        message.success('Cập nhật thông số vận hành thành công');
        setIsDirty(false);
      },
      onError: (err) => {
        message.error(getApiErrorMessage(err, 'Lỗi cập nhật thông số vận hành'));
      },
    });
  };

  if (isLoading) {
    return (
      <Card style={cardStyle}>
        <Skeleton active paragraph={{ rows: 6 }} />
      </Card>
    );
  }

  if (isError) {
    return (
      <Card style={cardStyle}>
        <Alert
          type="error"
          showIcon
          message="Không thể tải thông số vận hành"
          description={getApiErrorMessage(error, 'Vui lòng kiểm tra kết nối mạng và thử lại.')}
          action={
            <Button size="small" icon={<ReloadOutlined />} onClick={() => refetch()}>
              Thử lại
            </Button>
          }
        />
      </Card>
    );
  }

  return (
    <Card style={cardStyle}>
      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        onValuesChange={() => setIsDirty(true)}
        requiredMark={false}
      >
        <div style={{ marginBottom: 20 }}>
          <Title level={5} style={{ margin: 0, fontWeight: 600 }}>
            Cảnh báo tồn kho
          </Title>
          <Text style={{ color: '#71717A', fontSize: 13 }}>
            Thiết lập mức tồn kho tối thiểu để hệ thống hiển thị cảnh báo sắp hết hàng trên Bảng điều khiển và Quản lý kho.
          </Text>
        </div>

        <Row gutter={16}>
          <Col xs={24} md={12}>
            <Form.Item
              name="lowStockThreshold"
              label={<span style={{ fontWeight: 600 }}>Ngưỡng cảnh báo tồn kho thấp</span>}
              rules={[
                { required: true, message: 'Vui lòng nhập ngưỡng tồn kho thấp' },
                { type: 'number', min: 0, message: 'Ngưỡng tồn kho phải lớn hơn hoặc bằng 0' },
              ]}
              extra="Các biến thể sản phẩm có số lượng tồn kho nhỏ hơn mức này sẽ được gắn nhãn Cảnh báo tồn kho thấp."
            >
              <InputNumber
                min={0}
                precision={0}
                style={{ width: '100%', borderRadius: 6 }}
                placeholder="10"
              />
            </Form.Item>
          </Col>
        </Row>

        <Divider style={{ margin: '16px 0 24px 0' }} />

        <div style={{ marginBottom: 20 }}>
          <Title level={5} style={{ margin: 0, fontWeight: 600 }}>
            Phí vận chuyển mặc định
          </Title>
          <Text style={{ color: '#71717A', fontSize: 13 }}>
            Quy định mức cước vận chuyển tiêu chuẩn và điều kiện miễn cước giao hàng cho đơn hàng trực tuyến.
          </Text>
        </div>

        <Row gutter={16}>
          <Col xs={24} md={12}>
            <Form.Item
              name="defaultShippingFee"
              label={<span style={{ fontWeight: 600 }}>Phí vận chuyển mặc định (VND)</span>}
              rules={[
                { required: true, message: 'Vui lòng nhập phí vận chuyển mặc định' },
                { type: 'number', min: 0, message: 'Phí vận chuyển phải lớn hơn hoặc bằng 0' },
              ]}
              extra="Áp dụng cho các đơn hàng chưa đạt điều kiện miễn phí vận chuyển."
            >
              <InputNumber
                step={5000}
                formatter={(value) =>
                  value !== undefined && value !== null
                    ? `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
                    : ''
                }
                parser={(value) =>
                  value ? Number(value.replace(/\./g, '').replace(/[^\d]/g, '')) : 0
                }
                style={{ width: '100%', borderRadius: 6 }}
                placeholder="30.000"
              />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item
              name="freeShippingThreshold"
              label={<span style={{ fontWeight: 600 }}>Ngưỡng miễn phí vận chuyển (VND)</span>}
              rules={[
                { required: true, message: 'Vui lòng nhập ngưỡng miễn phí vận chuyển' },
                { type: 'number', min: 0, message: 'Ngưỡng miễn phí vận chuyển phải lớn hơn hoặc bằng 0' },
              ]}
              extra="Đơn hàng có tổng tiền hàng đạt hoặc vượt mức này sẽ được miễn toàn bộ phí giao hàng."
            >
              <InputNumber
                step={50000}
                formatter={(value) =>
                  value !== undefined && value !== null
                    ? `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
                    : ''
                }
                parser={(value) =>
                  value ? Number(value.replace(/\./g, '').replace(/[^\d]/g, '')) : 0
                }
                style={{ width: '100%', borderRadius: 6 }}
                placeholder="500.000"
              />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item style={{ marginTop: 8, marginBottom: 0 }}>
          <Button
            type="primary"
            htmlType="submit"
            disabled={!isDirty}
            loading={updateSettingsMutation.isPending}
            style={{
              backgroundColor: '#18181B',
              borderColor: '#18181B',
              borderRadius: 6,
              fontWeight: 600,
            }}
          >
            Lưu thay đổi
          </Button>
        </Form.Item>
      </Form>
    </Card>
  );
};

/* ========================================================================= */
/* TAB 3: TÀI KHOẢN QUẢN TRỊ                                                */
/* ========================================================================= */
const AdminAccountTab: React.FC = () => {
  const [profileForm] = Form.useForm<UpdateAdminAccountPayload>();
  const [passwordForm] = Form.useForm<ChangePasswordPayload>();
  const [isProfileDirty, setIsProfileDirty] = useState(false);

  const { data: account, isLoading, isError, error, refetch } = useAdminAccount();
  const updateAccountMutation = useUpdateAdminAccount();
  const changePasswordMutation = useChangePassword();

  useEffect(() => {
    if (account) {
      profileForm.setFieldsValue({
        name: account.name || '',
        phone: account.phone || '',
      });
      setIsProfileDirty(false);
    }
  }, [account, profileForm]);

  const onProfileFinish = (values: UpdateAdminAccountPayload) => {
    updateAccountMutation.mutate(values, {
      onSuccess: () => {
        message.success('Cập nhật thông tin tài khoản thành công');
        setIsProfileDirty(false);
      },
      onError: (err) => {
        message.error(getApiErrorMessage(err, 'Lỗi cập nhật thông tin tài khoản'));
      },
    });
  };

  const onPasswordFinish = (values: ChangePasswordPayload) => {
    if (values.newPassword !== values.confirmPassword) {
      message.error('Mật khẩu xác nhận không khớp');
      return;
    }

    if (values.currentPassword === values.newPassword) {
      message.error('Mật khẩu mới không được trùng với mật khẩu hiện tại');
      return;
    }

    changePasswordMutation.mutate(values, {
      onSuccess: () => {
        message.success('Đổi mật khẩu thành công');
        passwordForm.resetFields();
      },
      onError: (err) => {
        message.error(getApiErrorMessage(err, 'Lỗi đổi mật khẩu'));
      },
    });
  };

  if (isLoading) {
    return (
      <Card style={cardStyle}>
        <Skeleton active avatar paragraph={{ rows: 6 }} />
      </Card>
    );
  }

  if (isError) {
    return (
      <Card style={cardStyle}>
        <Alert
          type="error"
          showIcon
          message="Không thể tải thông tin tài khoản"
          description={getApiErrorMessage(error, 'Vui lòng kiểm tra kết nối mạng và thử lại.')}
          action={
            <Button size="small" icon={<ReloadOutlined />} onClick={() => refetch()}>
              Thử lại
            </Button>
          }
        />
      </Card>
    );
  }

  const roleName = account?.role?.toLowerCase() === 'admin' ? 'Quản trị viên (ADMIN)' : account?.role;

  return (
    <Space direction="vertical" size={24} style={{ width: '100%' }}>
      {/* Thông tin hồ sơ */}
      <Card style={cardStyle} title={<span style={{ fontWeight: 600 }}>Thông tin cá nhân</span>}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 24 }}>
          <Avatar
            size={56}
            style={{ backgroundColor: '#18181B', fontSize: 22, fontWeight: 700 }}
            icon={<UserOutlined />}
          >
            {account?.name?.charAt(0)?.toUpperCase()}
          </Avatar>
          <div>
            <div style={{ fontWeight: 700, fontSize: 16, color: '#09090B' }}>
              {account?.name}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 4 }}>
              <Tag color="black" style={{ borderRadius: 6, margin: 0 }}>
                {roleName}
              </Tag>
              <Text style={{ color: '#71717A', fontSize: 13 }}>
                {account?.email}
              </Text>
            </div>
          </div>
        </div>

        <Form
          form={profileForm}
          layout="vertical"
          onFinish={onProfileFinish}
          onValuesChange={() => setIsProfileDirty(true)}
          requiredMark={false}
        >
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item
                name="name"
                label={<span style={{ fontWeight: 600 }}>Họ và tên</span>}
                rules={[
                  { required: true, message: 'Vui lòng nhập họ và tên' },
                  { max: 150, message: 'Họ tên tối đa 150 ký tự' },
                ]}
              >
                <Input placeholder="Nguyễn Văn A" style={{ borderRadius: 6 }} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                name="phone"
                label={<span style={{ fontWeight: 600 }}>Số điện thoại</span>}
                rules={[
                  {
                    pattern: /^$|^[0-9+()\s.-]{8,20}$/,
                    message: 'Số điện thoại không đúng định dạng',
                  },
                ]}
              >
                <Input placeholder="0987654321" style={{ borderRadius: 6 }} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item label={<span style={{ fontWeight: 600 }}>Email đăng nhập</span>}>
                <Input value={account?.email} disabled style={{ borderRadius: 6 }} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label={<span style={{ fontWeight: 600 }}>Lần đăng nhập gần nhất</span>}>
                <Input
                  value={
                    account?.lastLoginAt
                      ? new Date(account.lastLoginAt).toLocaleString('vi-VN')
                      : 'Lần đầu đăng nhập'
                  }
                  disabled
                  style={{ borderRadius: 6 }}
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item style={{ marginBottom: 0 }}>
            <Button
              type="primary"
              htmlType="submit"
              disabled={!isProfileDirty}
              loading={updateAccountMutation.isPending}
              style={{
                backgroundColor: '#18181B',
                borderColor: '#18181B',
                borderRadius: 6,
                fontWeight: 600,
              }}
            >
              Lưu thông tin
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {/* Đổi mật khẩu */}
      <Card style={cardStyle} title={<span style={{ fontWeight: 600 }}>Đổi mật khẩu</span>}>
        <Paragraph style={{ color: '#71717A', fontSize: 13, marginTop: -4 }}>
          Để bảo mật tài khoản, vui lòng dùng mật khẩu tối thiểu 6 ký tự kết hợp chữ hoa, chữ thường và số.
        </Paragraph>

        <Form
          form={passwordForm}
          layout="vertical"
          onFinish={onPasswordFinish}
          requiredMark={false}
          style={{ maxWidth: 480 }}
        >
          <Form.Item
            name="currentPassword"
            label={<span style={{ fontWeight: 600 }}>Mật khẩu hiện tại</span>}
            rules={[{ required: true, message: 'Vui lòng nhập mật khẩu hiện tại' }]}
          >
            <Input.Password
              prefix={<KeyOutlined style={{ color: '#A1A1AA' }} />}
              placeholder="Nhập mật khẩu đang dùng"
              style={{ borderRadius: 6 }}
            />
          </Form.Item>

          <Form.Item
            name="newPassword"
            label={<span style={{ fontWeight: 600 }}>Mật khẩu mới</span>}
            rules={[
              { required: true, message: 'Vui lòng nhập mật khẩu mới' },
              { min: 6, message: 'Mật khẩu mới phải có ít nhất 6 ký tự' },
            ]}
          >
            <Input.Password
              prefix={<KeyOutlined style={{ color: '#A1A1AA' }} />}
              placeholder="Tối thiểu 6 ký tự"
              style={{ borderRadius: 6 }}
            />
          </Form.Item>

          <Form.Item
            name="confirmPassword"
            label={<span style={{ fontWeight: 600 }}>Xác nhận mật khẩu mới</span>}
            rules={[
              { required: true, message: 'Vui lòng xác nhận lại mật khẩu mới' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('Mật khẩu xác nhận không khớp'));
                },
              }),
            ]}
          >
            <Input.Password
              prefix={<KeyOutlined style={{ color: '#A1A1AA' }} />}
              placeholder="Nhập lại mật khẩu mới"
              style={{ borderRadius: 6 }}
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0 }}>
            <Button
              type="primary"
              htmlType="submit"
              loading={changePasswordMutation.isPending}
              style={{
                backgroundColor: '#18181B',
                borderColor: '#18181B',
                borderRadius: 6,
                fontWeight: 600,
              }}
            >
              Đổi mật khẩu
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </Space>
  );
};

export default SettingsPage;
