package com.shop.backend.review.dto;

public record ReviewEligibilityResponse(
        boolean canReview,
        String reason
) {}
