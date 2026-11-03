import apiClient from '@/api/client';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ReviewItem {
  id: number;
  productId: number;
  userId: number;
  userName: string;
  rating: number;
  content: string | null;
  createdAt: string;
}

export interface ReviewSummary {
  average: number;
  count: number;
  distribution: Record<string, number>;
}

export interface ReviewEligibility {
  canReview: boolean;
  reason: 'OK' | 'NOT_LOGGED_IN' | 'ALREADY_REVIEWED' | 'NOT_COMPLETED_ORDER' | 'FORBIDDEN_ROLE';
}

export interface CreateReviewPayload {
  rating: number;
  content?: string | null;
}

export async function getProductReviewsApi(
  productId: number,
  page = 0,
  size = 5
): Promise<PageResponse<ReviewItem>> {
  const res = await apiClient.get<PageResponse<ReviewItem>>(`/products/${productId}/reviews`, {
    params: { page, size },
  });
  return res.data;
}

export async function getProductReviewSummaryApi(productId: number): Promise<ReviewSummary> {
  const res = await apiClient.get<ReviewSummary>(`/products/${productId}/reviews/summary`);
  return res.data;
}

export async function getReviewEligibilityApi(productId: number): Promise<ReviewEligibility> {
  const res = await apiClient.get<ReviewEligibility>(`/products/${productId}/reviews/eligibility`);
  return res.data;
}

export async function createProductReviewApi(
  productId: number,
  data: CreateReviewPayload
): Promise<ReviewItem> {
  const res = await apiClient.post<ReviewItem>(`/products/${productId}/reviews`, data);
  return res.data;
}
