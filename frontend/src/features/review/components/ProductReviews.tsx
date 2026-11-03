import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Star, MessageSquare, AlertCircle, CheckCircle2, Loader2, ChevronLeft, ChevronRight } from 'lucide-react';
import { useAuthStore } from '@/stores/auth.store';
import {
  getProductReviewsApi,
  getProductReviewSummaryApi,
  getReviewEligibilityApi,
  createProductReviewApi,
} from '../api/review.api';

interface ProductReviewsProps {
  productId: number;
}

export const ProductReviews: React.FC<ProductReviewsProps> = ({ productId }) => {
  const queryClient = useQueryClient();
  const accessToken = useAuthStore((state) => state.accessToken);
  const isLoggedIn = Boolean(accessToken);

  const [page, setPage] = useState(0);
  const [rating, setRating] = useState(5);
  const [content, setContent] = useState('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState(false);

  const { data: summary } = useQuery({
    queryKey: ['reviewSummary', productId],
    queryFn: () => getProductReviewSummaryApi(productId),
    staleTime: 60 * 1000,
  });

  const { data: reviewsPage, isLoading: isLoadingReviews } = useQuery({
    queryKey: ['reviews', productId, page],
    queryFn: () => getProductReviewsApi(productId, page, 5),
    staleTime: 30 * 1000,
  });

  const { data: eligibility, isLoading: isLoadingEligibility } = useQuery({
    queryKey: ['reviewEligibility', productId, isLoggedIn],
    queryFn: () => getReviewEligibilityApi(productId),
    enabled: isLoggedIn,
    staleTime: 30 * 1000,
  });

  const mutation = useMutation({
    mutationFn: () =>
      createProductReviewApi(productId, {
        rating,
        content: content.trim() ? content.trim() : null,
      }),
    onSuccess: () => {
      setContent('');
      setRating(5);
      setErrorMsg(null);
      setSuccessMsg(true);
      setTimeout(() => setSuccessMsg(false), 4000);
      queryClient.invalidateQueries({ queryKey: ['reviewSummary', productId] });
      queryClient.invalidateQueries({ queryKey: ['reviews', productId] });
      queryClient.invalidateQueries({ queryKey: ['reviewEligibility', productId] });
    },
    onError: (err: any) => {
      const status = err?.response?.status;
      const serverMsg = err?.response?.data?.message;
      if (status === 403) {
        setErrorMsg('Bạn chỉ có thể đánh giá sau khi đơn hàng đã được giao thành công.');
      } else if (status === 409) {
        setErrorMsg('Bạn đã đánh giá sản phẩm này rồi.');
      } else {
        setErrorMsg(serverMsg || 'Không thể gửi đánh giá. Vui lòng thử lại sau.');
      }
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    mutation.mutate();
  };

  const renderStars = (starCount: number, sizeClass = 'h-4 w-4') => {
    return (
      <div className="flex items-center gap-0.5">
        {[1, 2, 3, 4, 5].map((s) => (
          <Star
            key={s}
            className={`${sizeClass} ${
              s <= starCount
                ? 'fill-amber-400 text-amber-400'
                : 'fill-muted/20 text-muted-foreground/30'
            }`}
          />
        ))}
      </div>
    );
  };

  return (
    <section className="space-y-8 pt-10 border-t border-border">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl sm:text-2xl font-semibold tracking-tight text-foreground flex items-center gap-2">
            <MessageSquare className="h-5 w-5 text-foreground" />
            Đánh giá sản phẩm
          </h2>
          <p className="text-xs text-muted-foreground mt-1">
            Nhận xét thực tế từ các khách hàng đã mua và sử dụng sản phẩm
          </p>
        </div>
      </div>

      {/* Summary Box */}
      {summary && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 items-center bg-card p-6 rounded-lg border border-border">
          <div className="text-center md:border-r md:border-border pr-0 md:pr-6">
            <p className="text-4xl font-bold tracking-tight text-foreground">
              {summary.average.toFixed(1)}
              <span className="text-lg font-normal text-muted-foreground"> / 5</span>
            </p>
            <div className="flex justify-center mt-2">
              {renderStars(Math.round(summary.average), 'h-5 w-5')}
            </div>
            <p className="text-xs text-muted-foreground mt-2">
              Dựa trên {summary.count} lượt đánh giá
            </p>
          </div>

          <div className="md:col-span-2 space-y-2">
            {[5, 4, 3, 2, 1].map((star) => {
              const count = summary.distribution[String(star)] ?? 0;
              const percent = summary.count > 0 ? Math.round((count / summary.count) * 100) : 0;
              return (
                <div key={star} className="flex items-center gap-3 text-xs">
                  <span className="w-12 text-muted-foreground font-medium flex items-center gap-1">
                    {star} <Star className="h-3 w-3 fill-amber-400 text-amber-400" />
                  </span>
                  <div className="flex-1 h-2 bg-muted rounded-full overflow-hidden">
                    <div
                      className="h-full bg-amber-400 transition-all duration-300"
                      style={{ width: `${percent}%` }}
                    />
                  </div>
                  <span className="w-12 text-right text-muted-foreground">
                    {count} ({percent}%)
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Review Form or Eligibility Notice */}
      <div className="bg-card p-6 rounded-lg border border-border">
        {!isLoggedIn ? (
          <p className="text-sm text-muted-foreground">
            Vui lòng đăng nhập với tài khoản khách hàng đã mua sản phẩm để viết đánh giá.
          </p>
        ) : isLoadingEligibility ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            <span>Đang kiểm tra quyền đánh giá...</span>
          </div>
        ) : eligibility?.canReview ? (
          <form onSubmit={handleSubmit} className="space-y-4">
            <h3 className="font-semibold text-sm text-foreground">Viết nhận xét của bạn</h3>
            <div>
              <label className="block text-xs font-medium text-foreground mb-1.5">
                Chấm điểm sản phẩm:
              </label>
              <div className="flex items-center gap-2">
                {[1, 2, 3, 4, 5].map((star) => (
                  <button
                    key={star}
                    type="button"
                    onClick={() => setRating(star)}
                    className={`flex items-center gap-1 px-3 py-1.5 rounded-md border text-xs font-medium transition ${
                      rating >= star
                        ? 'bg-amber-50 border-amber-300 text-amber-800'
                        : 'bg-background border-input text-muted-foreground hover:bg-muted/50'
                    }`}
                  >
                    <span>{star}</span>
                    <Star
                      className={`h-3.5 w-3.5 ${
                        rating >= star
                          ? 'fill-amber-400 text-amber-400'
                          : 'fill-muted/20 text-muted-foreground/30'
                      }`}
                    />
                  </button>
                ))}
              </div>
            </div>

            <div>
              <div className="flex justify-between items-center mb-1.5">
                <label className="block text-xs font-medium text-foreground">
                  Nội dung đánh giá (tùy chọn):
                </label>
                <span className="text-[11px] text-muted-foreground">
                  {content.length}/1000
                </span>
              </div>
              <textarea
                rows={3}
                value={content}
                onChange={(e) => setContent(e.target.value)}
                maxLength={1000}
                placeholder="Chia sẻ trải nghiệm về chất liệu vải, độ vừa vặn, form dáng..."
                className="w-full border border-input rounded-md px-3 py-2 text-sm bg-background text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:ring-1 focus:ring-foreground"
              />
            </div>

            {errorMsg && (
              <div className="flex items-center gap-2 text-xs text-destructive bg-destructive/10 p-2.5 rounded-md border border-destructive/20">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{errorMsg}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={mutation.isPending}
              className="bg-foreground text-background px-4 py-2 rounded-md text-xs font-medium hover:bg-foreground/90 disabled:opacity-50 transition flex items-center gap-2"
            >
              {mutation.isPending && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
              {mutation.isPending ? 'Đang gửi đánh giá...' : 'Gửi đánh giá'}
            </button>
          </form>
        ) : (
          <div>
            {successMsg ? (
              <div className="flex items-center gap-2 text-xs text-emerald-600 bg-emerald-50 p-3 rounded-md border border-emerald-200">
                <CheckCircle2 className="h-4 w-4 shrink-0" />
                <span>Đánh giá của bạn đã được gửi thành công. Cảm ơn bạn!</span>
              </div>
            ) : eligibility?.reason === 'ALREADY_REVIEWED' ? (
              <p className="text-xs text-muted-foreground">
                Bạn đã gửi đánh giá cho sản phẩm này rồi. Mỗi khách hàng chỉ gửi đánh giá 1 lần cho mỗi sản phẩm.
              </p>
            ) : eligibility?.reason === 'FORBIDDEN_ROLE' ? (
              <p className="text-xs text-muted-foreground">
                Tài khoản quản trị viên không được gửi đánh giá sản phẩm.
              </p>
            ) : (
              <p className="text-xs text-muted-foreground">
                Chỉ những khách hàng đã đặt mua sản phẩm này và đơn hàng đã được giao thành công mới có thể gửi đánh giá.
              </p>
            )}
          </div>
        )}
      </div>

      {/* Reviews List */}
      <div className="space-y-4">
        {isLoadingReviews ? (
          <div className="flex items-center justify-center p-8 text-muted-foreground text-xs gap-2">
            <Loader2 className="h-4 w-4 animate-spin" />
            <span>Đang tải danh sách đánh giá...</span>
          </div>
        ) : !reviewsPage || reviewsPage.content.length === 0 ? (
          <div className="text-center p-8 bg-card rounded-lg border border-border">
            <p className="text-sm text-muted-foreground">Chưa có đánh giá nào cho sản phẩm này.</p>
            <p className="text-xs text-muted-foreground mt-1">Hãy là người đầu tiên trải nghiệm và chia sẻ nhận xét!</p>
          </div>
        ) : (
          <div className="space-y-3">
            {reviewsPage.content.map((r) => (
              <div key={r.id} className="p-4 bg-card rounded-lg border border-border space-y-2">
                <div className="flex items-center justify-between">
                  <span className="font-medium text-xs text-foreground">{r.userName}</span>
                  <span className="text-[11px] text-muted-foreground">
                    {new Date(r.createdAt).toLocaleDateString('vi-VN')}
                  </span>
                </div>
                <div>{renderStars(r.rating, 'h-3.5 w-3.5')}</div>
                {r.content && (
                  <p className="text-xs text-foreground/90 leading-relaxed whitespace-pre-line">
                    {r.content}
                  </p>
                )}
              </div>
            ))}

            {/* Pagination */}
            {reviewsPage.totalPages > 1 && (
              <div className="flex items-center justify-center gap-2 pt-4">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="flex items-center gap-1 px-2.5 py-1.5 border border-input rounded-md text-xs font-medium text-foreground hover:bg-muted/50 disabled:opacity-40 transition"
                >
                  <ChevronLeft className="h-3.5 w-3.5" />
                  Trước
                </button>
                <span className="text-xs text-muted-foreground px-2">
                  Trang {reviewsPage.page + 1} / {reviewsPage.totalPages}
                </span>
                <button
                  type="button"
                  disabled={page + 1 >= reviewsPage.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                  className="flex items-center gap-1 px-2.5 py-1.5 border border-input rounded-md text-xs font-medium text-foreground hover:bg-muted/50 disabled:opacity-40 transition"
                >
                  Sau
                  <ChevronRight className="h-3.5 w-3.5" />
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  );
};
