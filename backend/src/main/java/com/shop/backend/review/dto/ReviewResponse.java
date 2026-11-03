package com.shop.backend.review.dto;

import com.shop.backend.review.Review;
import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long productId,
        Long userId,
        String userName,
        int rating,
        String content,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(Review review) {
        String displayName = "Khách hàng";
        if (review.getUser() != null) {
            if (review.getUser().getName() != null && !review.getUser().getName().isBlank()) {
                displayName = review.getUser().getName();
            } else if (review.getUser().getEmail() != null) {
                displayName = review.getUser().getEmail().split("@")[0];
            }
        }
        return new ReviewResponse(
                review.getId(),
                review.getProduct() != null ? review.getProduct().getId() : null,
                review.getUser() != null ? review.getUser().getId() : null,
                displayName,
                review.getRating(),
                review.getContent(),
                review.getCreatedAt()
        );
    }
}
