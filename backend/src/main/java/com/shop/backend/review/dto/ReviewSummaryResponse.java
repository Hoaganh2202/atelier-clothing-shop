package com.shop.backend.review.dto;

import java.util.Map;

public record ReviewSummaryResponse(
        double average,
        long count,
        Map<Integer, Long> distribution
) {}
