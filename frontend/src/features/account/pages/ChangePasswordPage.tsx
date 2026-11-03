import React, { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import axios from 'axios';
import {
  AlertCircle,
  CheckCircle2,
  Eye,
  EyeOff,
  KeyRound,
  Loader2,
  Lock,
  X,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuthStore } from '@/stores/auth.store';
import { changePasswordApi, type ChangePasswordPayload } from '@/features/auth/api/auth.api';
import { changePasswordSchema, type ChangePasswordFormValues } from '@/features/auth/schemas/profile.schema';

interface ToastBanner {
  type: 'success' | 'error';
  message: string;
}

export const ChangePasswordPage: React.FC = () => {
  const { user, accessToken, isInitializing } = useAuthStore();
  const isAuthenticated = Boolean(accessToken && user);
  const location = useLocation();

  const [toast, setToast] = useState<ToastBanner | null>(null);
  const [formErrors, setFormErrors] = useState<Record<string, string>>({});

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  useEffect(() => {
    document.title = 'Đổi mật khẩu | ATELIER';
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 4000);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const changePasswordMutation = useMutation({
    mutationFn: (payload: ChangePasswordPayload) => changePasswordApi(payload),
    onSuccess: (data) => {
      setToast({
        type: 'success',
        message: data.message || 'Đổi mật khẩu thành công',
      });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setFormErrors({});
    },
    onError: (error: unknown) => {
      let message = 'Có lỗi xảy ra khi đổi mật khẩu';
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

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFormErrors({});
    setToast(null);

    const validationResult = changePasswordSchema.safeParse({
      currentPassword,
      newPassword,
      confirmPassword,
    });

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
        message: 'Vui lòng kiểm tra lại thông tin mật khẩu',
      });
      return;
    }

    const values: ChangePasswordFormValues = validationResult.data;
    changePasswordMutation.mutate({
      currentPassword: values.currentPassword,
      newPassword: values.newPassword,
    });
  };

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

        {/* Card Form */}
        <Card className="border-border bg-card shadow-sm">
          <CardHeader className="space-y-1 pb-6">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-foreground text-background font-semibold text-sm">
                <Lock className="h-5 w-5" />
              </div>
              <div>
                <CardTitle className="text-xl font-bold tracking-tight text-foreground">
                  Đổi mật khẩu
                </CardTitle>
                <CardDescription className="text-xs text-muted-foreground">
                  Để bảo mật tài khoản, vui lòng không chia sẻ mật khẩu cho người khác
                </CardDescription>
              </div>
            </div>
          </CardHeader>

          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-5">
              {/* Current Password */}
              <div className="space-y-1.5">
                <Label htmlFor="currentPassword" className="text-xs font-semibold text-foreground">
                  Mật khẩu hiện tại <span className="text-destructive">*</span>
                </Label>
                <div className="relative">
                  <Input
                    id="currentPassword"
                    type={showCurrentPassword ? 'text' : 'password'}
                    placeholder="Nhập mật khẩu đang sử dụng"
                    value={currentPassword}
                    onChange={(e) => {
                      setCurrentPassword(e.target.value);
                      if (formErrors.currentPassword) {
                        setFormErrors((prev) => ({ ...prev, currentPassword: '' }));
                      }
                    }}
                    className={`pr-10 text-sm ${
                      formErrors.currentPassword ? 'border-destructive focus-visible:ring-destructive' : ''
                    }`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowCurrentPassword((prev) => !prev)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                    aria-label={showCurrentPassword ? 'Ẩn mật khẩu hiện tại' : 'Hiện mật khẩu hiện tại'}
                  >
                    {showCurrentPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                {formErrors.currentPassword && (
                  <p className="text-xs font-medium text-destructive">{formErrors.currentPassword}</p>
                )}
              </div>

              {/* New Password */}
              <div className="space-y-1.5">
                <Label htmlFor="newPassword" className="text-xs font-semibold text-foreground">
                  Mật khẩu mới <span className="text-destructive">*</span>
                </Label>
                <div className="relative">
                  <Input
                    id="newPassword"
                    type={showNewPassword ? 'text' : 'password'}
                    placeholder="Nhập mật khẩu mới (tối thiểu 6 ký tự)"
                    value={newPassword}
                    onChange={(e) => {
                      setNewPassword(e.target.value);
                      if (formErrors.newPassword) {
                        setFormErrors((prev) => ({ ...prev, newPassword: '' }));
                      }
                    }}
                    className={`pr-10 text-sm ${
                      formErrors.newPassword ? 'border-destructive focus-visible:ring-destructive' : ''
                    }`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowNewPassword((prev) => !prev)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                    aria-label={showNewPassword ? 'Ẩn mật khẩu mới' : 'Hiện mật khẩu mới'}
                  >
                    {showNewPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                {formErrors.newPassword && (
                  <p className="text-xs font-medium text-destructive">{formErrors.newPassword}</p>
                )}
                <p className="text-[11px] text-muted-foreground">
                  Mật khẩu phải có từ 6 đến 72 ký tự.
                </p>
              </div>

              {/* Confirm Password */}
              <div className="space-y-1.5">
                <Label htmlFor="confirmPassword" className="text-xs font-semibold text-foreground">
                  Xác nhận mật khẩu mới <span className="text-destructive">*</span>
                </Label>
                <div className="relative">
                  <Input
                    id="confirmPassword"
                    type={showConfirmPassword ? 'text' : 'password'}
                    placeholder="Nhập lại mật khẩu mới"
                    value={confirmPassword}
                    onChange={(e) => {
                      setConfirmPassword(e.target.value);
                      if (formErrors.confirmPassword) {
                        setFormErrors((prev) => ({ ...prev, confirmPassword: '' }));
                      }
                    }}
                    className={`pr-10 text-sm ${
                      formErrors.confirmPassword ? 'border-destructive focus-visible:ring-destructive' : ''
                    }`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword((prev) => !prev)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                    aria-label={showConfirmPassword ? 'Ẩn xác nhận mật khẩu' : 'Hiện xác nhận mật khẩu'}
                  >
                    {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                {formErrors.confirmPassword && (
                  <p className="text-xs font-medium text-destructive">{formErrors.confirmPassword}</p>
                )}
              </div>

              {/* Action Buttons */}
              <div className="pt-3 border-t border-border">
                <Button
                  type="submit"
                  disabled={changePasswordMutation.isPending}
                  className="w-full flex items-center justify-center gap-2 text-xs bg-foreground text-background hover:bg-neutral-800"
                >
                  {changePasswordMutation.isPending ? (
                    <>
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      <span>Đang xử lý...</span>
                    </>
                  ) : (
                    <>
                      <KeyRound className="h-3.5 w-3.5" />
                      <span>Đổi mật khẩu</span>
                    </>
                  )}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
    </div>
  );
};
