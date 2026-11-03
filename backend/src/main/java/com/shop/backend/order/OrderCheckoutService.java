package com.shop.backend.order;

import com.shop.backend.order.dto.CheckoutRequest;
import com.shop.backend.order.dto.CheckoutResponse;

public interface OrderCheckoutService {

    CheckoutResponse checkout(Long userId, CheckoutRequest request);
}
