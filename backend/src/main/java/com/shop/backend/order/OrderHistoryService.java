package com.shop.backend.order;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.order.dto.CustomerOrderDetailResponse;
import com.shop.backend.order.dto.CustomerOrderListItemResponse;

public interface OrderHistoryService {

    PageResponse<CustomerOrderListItemResponse> getOrders(Long userId, OrderStatus status, int page, int size);

    CustomerOrderDetailResponse getOrderDetail(Long userId, Long orderId);

    CustomerOrderDetailResponse cancelOrder(Long userId, Long orderId);
}
