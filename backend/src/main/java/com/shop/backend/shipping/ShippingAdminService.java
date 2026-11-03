package com.shop.backend.shipping;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.order.OrderStatus;
import com.shop.backend.shipping.dto.ShippingDetailResponse;
import com.shop.backend.shipping.dto.ShippingListItemResponse;
import com.shop.backend.shipping.dto.ShippingStatusUpdateRequest;
import com.shop.backend.shipping.dto.ShippingSummaryResponse;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;

public interface ShippingAdminService {

    ShippingSummaryResponse getShippingSummary();

    PageResponse<ShippingListItemResponse> getShippingOrders(
            OrderStatus status,
            String keyword,
            String province,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    );

    ShippingDetailResponse getShippingOrderDetail(Long orderId);

    ShippingDetailResponse updateShippingStatus(Long orderId, ShippingStatusUpdateRequest request);

    ShippingDetailResponse updateShippingStatus(Long orderId, ShippingStatusUpdateRequest request, Long actorUserId);
}
