package com.shop.backend.order;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.exception.ConflictException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.inventory.InventoryStockService;
import com.shop.backend.inventory.dto.OrderStockItem;
import com.shop.backend.order.dto.CustomerOrderDetailResponse;
import com.shop.backend.order.dto.CustomerOrderItemResponse;
import com.shop.backend.order.dto.CustomerOrderListItemResponse;
import com.shop.backend.order.dto.CustomerOrderPreviewItemResponse;
import com.shop.backend.payment.Payment;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderHistoryServiceImpl implements OrderHistoryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort FIXED_ORDER_SORT = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final InventoryStockService inventoryStockService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerOrderListItemResponse> getOrders(Long userId, OrderStatus status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        Pageable pageable = PageRequest.of(safePage, safeSize, FIXED_ORDER_SORT);

        Page<Order> orderPage = (status != null)
                ? orderRepository.findByUserIdAndStatus(userId, status, pageable)
                : orderRepository.findByUserId(userId, pageable);

        List<Order> orders = orderPage.getContent();
        if (orders.isEmpty()) {
            return PageResponse.<CustomerOrderListItemResponse>builder()
                    .content(List.of())
                    .page(orderPage.getNumber())
                    .size(orderPage.getSize())
                    .totalElements(orderPage.getTotalElements())
                    .totalPages(orderPage.getTotalPages())
                    .build();
        }

        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderDetail>> detailsByOrderId = orderDetailRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(d -> d.getOrder().getId()));

        Map<Long, List<Payment>> paymentsByOrderId = orderRepository.findPaymentsByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(p -> p.getOrder().getId()));

        List<CustomerOrderListItemResponse> items = orders.stream()
                .map(order -> {
                    List<OrderDetail> details = detailsByOrderId.getOrDefault(order.getId(), List.of());
                    List<Payment> payments = paymentsByOrderId.getOrDefault(order.getId(), List.of());
                    return toListItemResponse(order, details, payments);
                })
                .toList();

        return PageResponse.<CustomerOrderListItemResponse>builder()
                .content(items)
                .page(orderPage.getNumber())
                .size(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerOrderDetailResponse getOrderDetail(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));

        List<OrderDetail> details = orderDetailRepository.findByOrderId(orderId);
        List<Payment> payments = orderRepository.findPaymentsByOrderIdIn(List.of(orderId));

        return toDetailResponse(order, details, payments);
    }

    @Override
    @Transactional
    public CustomerOrderDetailResponse cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng"));

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus != OrderStatus.PENDING) {
            throw new ConflictException("Đơn hàng không thể hủy ở trạng thái hiện tại. Vui lòng liên hệ cửa hàng để được hỗ trợ.");
        }

        List<Payment> payments = orderRepository.findPaymentsByOrderIdIn(List.of(orderId));
        if (isPaid(payments)) {
            throw new ConflictException("Đơn hàng đã được thanh toán, vui lòng liên hệ hỗ trợ để được xử lý");
        }

        List<OrderDetail> details = orderDetailRepository.findByOrderId(orderId);
        List<OrderStockItem> stockItems = details.stream()
                .map(detail -> new OrderStockItem(
                        detail.getId(),
                        detail.getVariant().getId(),
                        detail.getQuantity()))
                .toList();

        String reason = "Khách hàng hủy đơn";
        inventoryStockService.restoreStockForCancelledOrder(order.getId(), userId, reason, stockItems);

        order.setCancelledFromStatus(currentStatus);
        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);

        return toDetailResponse(savedOrder, details, payments);
    }

    private boolean isPaid(List<Payment> payments) {
        if (payments == null || payments.isEmpty()) {
            return false;
        }
        return payments.stream().anyMatch(p -> p.getStatus() == PaymentStatus.SUCCESS || p.getPaidAt() != null);
    }

    private Payment resolveRepresentativePayment(List<Payment> payments) {
        if (payments == null || payments.isEmpty()) {
            return null;
        }
        Optional<Payment> successPayment = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS || p.getPaidAt() != null)
                .max(Comparator.comparing(Payment::getId));
        if (successPayment.isPresent()) {
            return successPayment.get();
        }
        return payments.stream()
                .max(Comparator.comparing(Payment::getId))
                .orElse(null);
    }

    private PaymentStatus resolvePaymentStatus(List<Payment> payments) {
        Payment representative = resolveRepresentativePayment(payments);
        return representative != null ? representative.getStatus() : PaymentStatus.PENDING;
    }

    private LocalDateTime resolvePaidAt(List<Payment> payments) {
        Payment representative = resolveRepresentativePayment(payments);
        return representative != null ? representative.getPaidAt() : null;
    }

    private PaymentMethod resolvePaymentMethod(Order order, List<Payment> payments) {
        if (order.getPaymentMethod() != null) {
            return order.getPaymentMethod();
        }
        Payment representative = resolveRepresentativePayment(payments);
        return representative != null ? representative.getMethod() : null;
    }

    private Long toLong(BigDecimal value) {
        return value != null ? value.longValue() : 0L;
    }

    private CustomerOrderPreviewItemResponse toPreviewItem(OrderDetail detail) {
        return CustomerOrderPreviewItemResponse.builder()
                .productName(detail.getProductName())
                .variantSize(detail.getVariantSize())
                .variantColor(detail.getVariantColor())
                .quantity(detail.getQuantity())
                .build();
    }

    private CustomerOrderItemResponse toItemResponse(OrderDetail detail) {
        long unitPrice = toLong(detail.getUnitPrice());
        int quantity = detail.getQuantity() != null ? detail.getQuantity() : 0;
        long discountAmount = toLong(detail.getDiscountAmount());
        long lineTotal = (unitPrice * quantity) - discountAmount;

        return CustomerOrderItemResponse.builder()
                .orderDetailId(detail.getId())
                .productName(detail.getProductName())
                .variantSize(detail.getVariantSize())
                .variantColor(detail.getVariantColor())
                .quantity(quantity)
                .unitPrice(unitPrice)
                .discountAmount(discountAmount)
                .lineTotal(lineTotal)
                .build();
    }

    private CustomerOrderListItemResponse toListItemResponse(Order order, List<OrderDetail> details, List<Payment> payments) {
        List<CustomerOrderPreviewItemResponse> previewItems = details.stream()
                .sorted(Comparator.comparing(OrderDetail::getId))
                .limit(3)
                .map(this::toPreviewItem)
                .toList();

        boolean canCancel = order.getStatus() == OrderStatus.PENDING && !isPaid(payments);

        return CustomerOrderListItemResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .totalAmount(toLong(order.getTotalAmount()))
                .paymentMethod(resolvePaymentMethod(order, payments))
                .paymentStatus(resolvePaymentStatus(payments))
                .lineCount(details.size())
                .previewItems(previewItems)
                .canCancel(canCancel)
                .build();
    }

    private CustomerOrderDetailResponse toDetailResponse(Order order, List<OrderDetail> details, List<Payment> payments) {
        List<CustomerOrderItemResponse> items = details.stream()
                .sorted(Comparator.comparing(OrderDetail::getId))
                .map(this::toItemResponse)
                .toList();

        boolean canCancel = order.getStatus() == OrderStatus.PENDING && !isPaid(payments);

        return CustomerOrderDetailResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .completedAt(order.getCompletedAt())
                .subtotal(toLong(order.getSubtotal()))
                .discountAmount(toLong(order.getDiscountAmount()))
                .shippingFee(toLong(order.getShippingFee()))
                .totalAmount(toLong(order.getTotalAmount()))
                .paymentMethod(resolvePaymentMethod(order, payments))
                .paymentStatus(resolvePaymentStatus(payments))
                .paidAt(resolvePaidAt(payments))
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .addressLine(order.getAddressLine())
                .ward(order.getWard())
                .district(order.getDistrict())
                .province(order.getProvince())
                .note(order.getNote())
                .items(items)
                .canCancel(canCancel)
                .build();
    }
}
