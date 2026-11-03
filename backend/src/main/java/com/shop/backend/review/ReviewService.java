package com.shop.backend.review;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.review.dto.CreateReviewRequest;
import com.shop.backend.review.dto.ReviewEligibilityResponse;
import com.shop.backend.review.dto.ReviewResponse;
import com.shop.backend.review.dto.ReviewSummaryResponse;

public interface ReviewService {

    PageResponse<ReviewResponse> listReviews(Long productId, int page, int size);

    ReviewSummaryResponse getReviewSummary(Long productId);

    ReviewEligibilityResponse checkEligibility(String userEmail, Long productId);

    ReviewResponse createReview(String userEmail, Long productId, CreateReviewRequest request);
}
