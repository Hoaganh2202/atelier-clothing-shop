package com.shop.backend.order.dto;

public interface CustomerSegmentProjection {
    Long getVipCount();
    Long getLoyalCount();
    Long getNewCount();
    Long getPotentialCount();
}
