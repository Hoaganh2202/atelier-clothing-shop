import React, { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import {
  AlertCircle,
  CheckCircle2,
  Loader2,
  RotateCcw,
  Save,
  User,
  X,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuthStore } from '@/stores/auth.store';
import { getMeApi, updateProfileApi, type GenderType, type UpdateProfilePayload } from '@/features/auth/api/auth.api';
import { updateProfileSchema, type UpdateProfileFormValues } from '@/features/auth/schemas/profile.schema';

interface ToastBanner {
  type: 'success' | 'error';
  message: string;
}

export const ProfilePage: React.FC = () => {
  const { user, accessToken, isInitializing, setUser } = useAuthStore();
  const isAuthenticated = Boolean(accessToken && user);
  const location = useLocation();
  const queryClient = useQueryClient();

  const [toast, setToast] = useState<ToastBanner | null>(null);
  const [formErrors, setFormErrors] = useState<Record<string, string>>({});

  const [formData, setFormData] = useState<{
    name: string;
    phone: string;
    gender: GenderType | '';
    birthDate: string;
  }>({
    name: '',
    phone: '',
    gender: '',
    birthDate: '',
  });

  useEffect(() => {
    document.title = 'Thông tin cá nhân | ATELIER';
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 4000);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const {
    data: profileData,
    isLoading: isProfileLoading,
    isError: isProfileError,
    refetch,
  } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: getMeApi,
    enabled: isAuthenticated,
    staleTime: 1000 * 60 * 5,
  });

  useEffect(() => {
    if (profileData) {
      setFormData({
        name: profileData.name || '',
        phone: profileData.phone || '',
        gender: (profileData.gender as GenderType) || '',
        birthDate: profileData.birthDate || '',
      });
      setFormErrors({});
    }
  }, [profileData]);

  const updateMutation = useMutation({
    mutationFn: (payload: UpdateProfilePayload) => updateProfileApi(payload),
    onSuccess: (updatedUser) => {
      setUser(updatedUser);
      queryClient.setQueryData(['auth', 'me'], updatedUser);
      setToast({
        type: 'success',
        message: 'Cập nhật thông tin cá nhân thành công',
      });
      setFormErrors({});
    },
    onError: (error: unknown) => {
      let message = 'Có lỗi xảy ra khi cập nhật thông tin';
      if (axios.isAxiosError(error) && error.response?.data) {
        const errorData = error.response.data as {
          message?: string;
          errors?: Record<string, string>;
        };
        if (errorData.message) {
          message = errorData.message;
        }
        if (errorData.errors) {
          setFormErrors(errorData.errors);
        }
      }
      setToast({ type: 'error', message });
    },
  });

  if (isInitializing) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-64 w-full rounded-md" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (user?.role && user.role.toLowerCase() !== 'customer') {
    return <Navigate to="/admin" replace />;
  }

  const handleReset = () => {
    if (profileData) {
      setFormData({
        name: profileData.name || '',
        phone: profileData.phone || '',
        gender: (profileData.gender as GenderType) || '',
        birthDate: profileData.birthDate || '',
      });
      setFormErrors({});
      setToast(null);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFormErrors({});
    setToast(null);

    const validationResult = updateProfileSchema.safeParse(formData);
    if (!validationResult.success) {
      const fieldErrors: Record<string, string> = {};
      validationResult.error.issues.forEach((issue) => {
        const path = issue.path[0];
        if (typeof path === 'string') {
          fieldErrors[path] = issue.message;
        }
      });
      setFormErrors(fieldErrors);
      setToast({
        type: 'error',
        message: 'Vui lòng kiểm tra lại các trường thông tin chưa hợp lệ',
      });
      return;
    }

    const values: UpdateProfileFormValues = validationResult.data;
    const payload: UpdateProfilePayload = {
      name: values.name,
      phone: values.phone ? values.phone.trim() : null,
      gender: values.gender ? (values.gender as GenderType) : null,
      birthDate: values.birthDate ? values.birthDate : null,
    };

    updateMutation.mutate(payload);
  };

  const todayDateString = new Date().toISOString().split('T')[0];

  return (
    <div className="space-y-6">
      {/* Toast / Notification Banner */}
        {toast && (
          <div
            role="status"
            className={`mb-6 flex items-center justify-between gap-3 rounded-md border p-4 text-sm transition-all animate-in fade-in-50 duration-200 ${
              toast.type === 'error'
                ? 'border-destructive/30 bg-destructive/10 text-destructive'
                : 'border-border bg-secondary/60 text-foreground'
            }`}
          >
            <div className="flex items-center gap-2.5">
              {toast.type === 'error' ? (
                <AlertCircle className="h-4 w-4 shrink-0 text-destructive" />
              ) : (
                <CheckCircle2 className="h-4 w-4 shrink-0 text-foreground" />
              )}
              <span>{toast.message}</span>
            </div>
            <button
              type="button"
              onClick={() => setToast(null)}
              className="text-muted-foreground hover:text-foreground transition-colors"
              aria-label="Đóng thông báo"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        )}

        {/* Profile Card */}
        <Card className="border-border bg-card shadow-sm">
          <CardHeader className="space-y-1 pb-6">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-foreground text-background font-semibold text-sm">
                <User className="h-5 w-5" />
              </div>
              <div>
                <CardTitle className="text-xl font-bold tracking-tight text-foreground">
                  Hồ sơ cá nhân
                </CardTitle>
                <CardDescription className="text-xs text-muted-foreground">
                  Quản lý thông tin họ tên, liên hệ và thông tin cá nhân của bạn
                </CardDescription>
              </div>
            </div>
          </CardHeader>

          <CardContent>
            {isProfileLoading ? (
              <div className="space-y-4 py-4">
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
              </div>
            ) : isProfileError ? (
              <div className="flex flex-col items-center justify-center py-8 text-center">
                <AlertCircle className="h-8 w-8 text-destructive mb-2" />
                <p className="text-sm font-medium text-foreground mb-1">
                  Không thể tải thông tin hồ sơ
                </p>
                <p className="text-xs text-muted-foreground mb-4">
                  Đã xảy ra lỗi trong quá trình kết nối đến hệ thống.
                </p>
                <Button variant="outline" size="sm" onClick={() => refetch()}>
                  Thử lại
                </Button>
              </div>
            ) : (
              <form onSubmit={handleSubmit} className="space-y-6">
                {/* Email (Read-only) */}
                <div className="space-y-1.5">
                  <Label htmlFor="email" className="text-xs font-semibold text-foreground">
                    Địa chỉ Email
                  </Label>
                  <Input
                    id="email"
                    type="email"
                    value={profileData?.email || ''}
                    disabled
                    readOnly
                    className="cursor-not-allowed bg-muted/50 text-muted-foreground text-sm"
                  />
                  <p className="text-[11px] text-muted-foreground">
                    Email là định danh tài khoản đăng nhập và không thể thay đổi.
                  </p>
                </div>

                {/* Name */}
                <div className="space-y-1.5">
                  <Label htmlFor="name" className="text-xs font-semibold text-foreground">
                    Họ và tên <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    id="name"
                    type="text"
                    placeholder="Nhập họ và tên đầy đủ"
                    value={formData.name}
                    onChange={(e) => {
                      setFormData((prev) => ({ ...prev, name: e.target.value }));
                      if (formErrors.name) {
                        setFormErrors((prev) => ({ ...prev, name: '' }));
                      }
                    }}
                    className={`text-sm ${
                      formErrors.name ? 'border-destructive focus-visible:ring-destructive' : ''
                    }`}
                  />
                  {formErrors.name && (
                    <p className="text-xs font-medium text-destructive">{formErrors.name}</p>
                  )}
                </div>

                {/* Phone */}
                <div className="space-y-1.5">
                  <Label htmlFor="phone" className="text-xs font-semibold text-foreground">
                    Số điện thoại
                  </Label>
                  <Input
                    id="phone"
                    type="tel"
                    placeholder="Ví dụ: 0912345678"
                    value={formData.phone}
                    onChange={(e) => {
                      setFormData((prev) => ({ ...prev, phone: e.target.value }));
                      if (formErrors.phone) {
                        setFormErrors((prev) => ({ ...prev, phone: '' }));
                      }
                    }}
                    className={`text-sm ${
                      formErrors.phone ? 'border-destructive focus-visible:ring-destructive' : ''
                    }`}
                  />
                  {formErrors.phone && (
                    <p className="text-xs font-medium text-destructive">{formErrors.phone}</p>
                  )}
                  <p className="text-[11px] text-muted-foreground">
                    Số điện thoại dùng để nhận thông báo vận chuyển và xác nhận đơn hàng.
                  </p>
                </div>

                {/* Gender & Birth Date Grid */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  {/* Gender */}
                  <div className="space-y-1.5">
                    <Label htmlFor="gender" className="text-xs font-semibold text-foreground">
                      Giới tính
                    </Label>
                    <select
                      id="gender"
                      value={formData.gender}
                      onChange={(e) => {
                        const val = e.target.value as GenderType | '';
                        setFormData((prev) => ({ ...prev, gender: val }));
                        if (formErrors.gender) {
                          setFormErrors((prev) => ({ ...prev, gender: '' }));
                        }
                      }}
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      <option value="">Chưa xác định</option>
                      <option value="MALE">Nam</option>
                      <option value="FEMALE">Nữ</option>
                      <option value="OTHER">Khác</option>
                    </select>
                    {formErrors.gender && (
                      <p className="text-xs font-medium text-destructive">{formErrors.gender}</p>
                    )}
                  </div>

                  {/* Birth Date */}
                  <div className="space-y-1.5">
                    <Label htmlFor="birthDate" className="text-xs font-semibold text-foreground">
                      Ngày sinh
                    </Label>
                    <Input
                      id="birthDate"
                      type="date"
                      max={todayDateString}
                      value={formData.birthDate}
                      onChange={(e) => {
                        setFormData((prev) => ({ ...prev, birthDate: e.target.value }));
                        if (formErrors.birthDate) {
                          setFormErrors((prev) => ({ ...prev, birthDate: '' }));
                        }
                      }}
                      className={`text-sm ${
                        formErrors.birthDate ? 'border-destructive focus-visible:ring-destructive' : ''
                      }`}
                    />
                    {formErrors.birthDate && (
                      <p className="text-xs font-medium text-destructive">{formErrors.birthDate}</p>
                    )}
                  </div>
                </div>

                {/* Action Buttons */}
                <div className="flex flex-col-reverse sm:flex-row sm:items-center sm:justify-end gap-3 pt-4 border-t border-border">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={handleReset}
                    disabled={updateMutation.isPending}
                    className="flex items-center gap-2 text-xs"
                  >
                    <RotateCcw className="h-3.5 w-3.5" />
                    <span>Khôi phục ban đầu</span>
                  </Button>

                  <Button
                    type="submit"
                    disabled={updateMutation.isPending}
                    className="flex items-center gap-2 text-xs bg-foreground text-background hover:bg-neutral-800"
                  >
                    {updateMutation.isPending ? (
                      <>
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        <span>Đang lưu...</span>
                      </>
                    ) : (
                      <>
                        <Save className="h-3.5 w-3.5" />
                        <span>Lưu thay đổi</span>
                      </>
                    )}
                  </Button>
                </div>
              </form>
            )}
          </CardContent>
        </Card>
    </div>
  );
};
