import apiClient from '@/api/client';
import type { components } from '@/api/schema';

export type CategoryPublicResponse = components['schemas']['CategoryPublicResponse'];
export type ProductCardResponse = components['schemas']['ProductCardResponse'];
export type ProductPublicDetailResponse = components['schemas']['ProductPublicDetailResponse'];
export type ProductVariantPublicResponse = components['schemas']['ProductVariantPublicResponse'];
export type ProductImageResponse = components['schemas']['ProductImageResponse'];
export type ProductCategoryResponse = components['schemas']['ProductCategoryResponse'];
export type FilterOptionsResponse = components['schemas']['FilterOptionsResponse'];
export type PageResponseProductCardResponse = components['schemas']['PageResponseProductCardResponse'];

export type ProductSortType = 'newest' | 'price_asc' | 'price_desc';

export interface GetProductsParams {
  keyword?: string;
  categoryId?: number;
  minPrice?: number;
  maxPrice?: number;
  sizes?: string[];
  colors?: string[];
  sort?: ProductSortType;
  page?: number;
  size?: number;
}

export async function getCategoriesApi(): Promise<CategoryPublicResponse[]> {
  const res = await apiClient.get<CategoryPublicResponse[]>('/categories');
  return res.data;
}

export async function getProductsApi(
  params: GetProductsParams = {}
): Promise<PageResponseProductCardResponse> {
  const res = await apiClient.get<PageResponseProductCardResponse>('/products', {
    params,
    paramsSerializer: { indexes: null },
  });
  return res.data;
}

export async function getProductBySlugApi(slug: string): Promise<ProductPublicDetailResponse> {
  const res = await apiClient.get<ProductPublicDetailResponse>(`/products/${encodeURIComponent(slug)}`);
  return res.data;
}

export async function getFilterOptionsApi(categoryId?: number): Promise<FilterOptionsResponse> {
  const res = await apiClient.get<FilterOptionsResponse>('/products/filter-options', {
    params: categoryId ? { categoryId } : undefined,
  });
  return res.data;
}
