package com.shop.backend.shipping;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderAdminService;
import com.shop.backend.order.OrderDetail;
import com.shop.backend.order.OrderStatus;
import com.shop.backend.order.dto.OrderAdminDetailResponse;
import com.shop.backend.order.dto.OrderStatusUpdateRequest;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.shipping.dto.ShippingDetailResponse;
import com.shop.backend.shipping.dto.ShippingItemResponse;
import com.shop.backend.shipping.dto.ShippingListItemResponse;
import com.shop.backend.shipping.dto.ShippingStatusUpdateRequest;
import com.shop.backend.shipping.dto.ShippingSummaryResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShippingAdminServiceImpl implements ShippingAdminService {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt", "totalAmount", "id");

    private static final Set<OrderStatus> SHIPPING_MANAGED_STATUSES = Set.of(
            OrderStatus.CONFIRMED,
            OrderStatus.SHIPPING,
            OrderStatus.COMPLETED,
            OrderStatus.CANCELLED
    );

    private static final Set<OrderStatus> ALLOWED_SOURCE_STATUSES = Set.of(
            OrderStatus.CONFIRMED,
            OrderStatus.SHIPPING
    );

    private static final Set<OrderStatus> ALLOWED_TARGET_STATUSES = Set.of(
            OrderStatus.SHIPPING,
            OrderStatus.COMPLETED,
            OrderStatus.CANCELLED
    );

    private final OrderAdminService orderAdminService;
    private final Clock clock;

    @Autowired
    public ShippingAdminServiceImpl(OrderAdminService orderAdminService) {
        this(orderAdminService, Clock.system(VIETNAM_ZONE));
    }

    public ShippingAdminServiceImpl(OrderAdminService orderAdminService, Clock clock) {
        this.orderAdminService = orderAdminService;
        this.clock = clock != null ? clock : Clock.system(VIETNAM_ZONE);
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingSummaryResponse getShippingSummary() {
        long readyToShipCount = orderAdminService.countOrdersByStatus(OrderStatus.CONFIRMED);
        long deliveringCount = orderAdminService.countOrdersByStatus(OrderStatus.SHIPPING);

        LocalDate today = LocalDate.now(clock);
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();
        long deliveredTodayCount = orderAdminService.countCompletedOrdersBetween(startOfDay, endOfDay);

        return ShippingSummaryResponse.builder()
                .readyToShipCount(readyToShipCount)
                .deliveringCount(deliveringCount)
                .deliveredTodayCount(deliveredTodayCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShippingListItemResponse> getShippingOrders(
            OrderStatus status,
            String keyword,
            String province,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    ) {
        if (status != null && !SHIPPING_MANAGED_STATUSES.contains(status)) {
            throw new BadRequestException("Trạng thái đơn hàng không hợp lệ cho phân hệ vận chuyển: "
                    + status.name().toLowerCase());
        }

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BadRequestException("Ngày bắt đầu không được sau ngày kết thúc");
        }

        List<OrderStatus> targetStatuses = (status != null)
                ? List.of(status)
                : List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPING, OrderStatus.COMPLETED, OrderStatus.CANCELLED);

        LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : null;
        LocalDateTime end = (endDate != null) ? endDate.plusDays(1).atStartOfDay() : null;
        String searchKeyword = normalizeKeyword(keyword);
        String cleanProvince = (province != null && !province.isBlank()) ? province.trim() : null;

        Pageable effectivePageable = resolvePageable(pageable);

        Page<Order> orderPage = orderAdminService.searchShippingOrders(
                targetStatuses,
                searchKeyword,
                cleanProvince,
                start,
                end,
                effectivePageable
        );

        List<Order> orders = orderPage.getContent();
        if (orders.isEmpty()) {
            return PageResponse.<ShippingListItemResponse>builder()
                    .content(Collections.emptyList())
                    .page(orderPage.getNumber())
                    .size(orderPage.getSize())
                    .totalElements(orderPage.getTotalElements())
                    .totalPages(orderPage.getTotalPages())
                    .build();
        }

        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderDetail>> detailsByOrderId = orderAdminService.getOrderDetailsByOrderIds(orderIds);

        List<ShippingListItemResponse> content = orders.stream()
                .map(order -> {
                    List<OrderDetail> items = detailsByOrderId.getOrDefault(order.getId(), Collections.emptyList());
                    int itemCount = items.stream().mapToInt(OrderDetail::getQuantity).sum();
                    String itemsSummary = buildItemsSummary(items);
                    Long codAmount = resolveCodAmount(order.getPaymentMethod(), order.getTotalAmount());

                    return ShippingListItemResponse.builder()
                            .orderId(order.getId())
                            .orderCode("ORD-" + order.getId())
                            .status(order.getStatus())
                            .receiverName(order.getReceiverName())
                            .receiverPhone(order.getReceiverPhone())
                            .fullAddress(buildFullAddress(order.getAddressLine(), order.getWard(), order.getDistrict(), order.getProvince()))
                            .province(order.getProvince())
                            .paymentMethod(order.getPaymentMethod())
                            .codAmount(codAmount)
                            .shippingFee(toLong(order.getShippingFee()))
                            .totalAmount(toLong(order.getTotalAmount()))
                            .itemCount(itemCount)
                            .itemsSummary(itemsSummary)
                            .customerNote(order.getNote())
                            .createdAt(order.getCreatedAt())
                            .completedAt(order.getCompletedAt())
                            .build();
                })
                .toList();

        return PageResponse.<ShippingListItemResponse>builder()
                .content(content)
                .page(orderPage.getNumber())
                .size(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingDetailResponse getShippingOrderDetail(Long orderId) {
        OrderAdminDetailResponse order = orderAdminService.getOrderById(orderId);

        if (!SHIPPING_MANAGED_STATUSES.contains(order.getStatus())) {
            throw new BadRequestException("Đơn hàng không thuộc phạm vi quản lý vận chuyển: "
                    + order.getStatus().name().toLowerCase());
        }

        return toShippingDetailResponse(order);
    }

    @Override
    @Transactional
    public ShippingDetailResponse updateShippingStatus(Long orderId, ShippingStatusUpdateRequest request) {
        return updateShippingStatus(orderId, request, null);
    }

    @Override
    @Transactional
    public ShippingDetailResponse updateShippingStatus(Long orderId, ShippingStatusUpdateRequest request, Long actorUserId) {
        OrderStatus targetStatus = request.getStatus();

        if (!ALLOWED_TARGET_STATUSES.contains(targetStatus)) {
            throw new BadRequestException("Không thể chuyển trạng thái sang: " + targetStatus.name().toLowerCase());
        }

        OrderStatusUpdateRequest orderRequest = OrderStatusUpdateRequest.builder()
                .status(targetStatus)
                .build();

        OrderAdminDetailResponse updated = orderAdminService.updateOrderStatus(
                orderId,
                orderRequest,
                ALLOWED_SOURCE_STATUSES,
                actorUserId
        );

        return toShippingDetailResponse(updated);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String clean = keyword.trim();
        if (clean.toUpperCase().startsWith("ORD-")) {
            String sub = clean.substring(4).trim();
            if (!sub.isEmpty()) {
                return sub;
            }
        }
        return clean;
    }

    private Pageable resolvePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = pageable.getPageSize() > 0 ? Math.min(pageable.getPageSize(), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        Sort sort = pageable.getSort();
        if (sort.isUnsorted() || !isSortValid(sort)) {
            sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        }

        return PageRequest.of(page, size, sort);
    }

    private boolean isSortValid(Sort sort) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                return false;
            }
        }
        return true;
    }

    private String buildFullAddress(String addressLine, String ward, String district, String province) {
        return Stream.of(addressLine, ward, district, province)
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }

    private Long resolveCodAmount(PaymentMethod method, BigDecimal totalAmount) {
        if (method == PaymentMethod.COD && totalAmount != null) {
            return totalAmount.longValue();
        }
        return 0L;
    }

    private String buildItemsSummary(List<OrderDetail> items) {
        if (items == null || items.isEmpty()) {
            return "0 sản phẩm";
        }
        OrderDetail firstItem = items.get(0);
        String firstName = firstItem.getProductName();
        if (items.size() == 1) {
            int qty = firstItem.getQuantity() != null ? firstItem.getQuantity() : 1;
            return qty > 1 ? firstName + " (x" + qty + ")" : firstName;
        }
        return firstName + " (+" + (items.size() - 1) + " sản phẩm khác)";
    }

    private ShippingDetailResponse toShippingDetailResponse(OrderAdminDetailResponse order) {
        Long codAmount = resolveCodAmount(order.getPaymentMethod(),
                order.getTotalAmount() != null ? BigDecimal.valueOf(order.getTotalAmount()) : null);

        String fullAddress = buildFullAddress(
                order.getAddressLine(),
                order.getWard(),
                order.getDistrict(),
                order.getProvince()
        );

        List<ShippingItemResponse> items = order.getItems() != null
                ? order.getItems().stream()
                        .map(item -> ShippingItemResponse.builder()
                                .id(item.getId())
                                .variantId(item.getVariantId())
                                .productName(item.getProductName())
                                .variantSize(item.getVariantSize())
                                .variantColor(item.getVariantColor())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .totalPrice(item.getTotalPrice())
                                .build())
                        .toList()
                : Collections.emptyList();

        return ShippingDetailResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus())
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .addressLine(order.getAddressLine())
                .ward(order.getWard())
                .district(order.getDistrict())
                .province(order.getProvince())
                .fullAddress(fullAddress)
                .paymentMethod(order.getPaymentMethod())
                .codAmount(codAmount)
                .shippingFee(order.getShippingFee())
                .subtotal(order.getSubtotal())
                .discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount())
                .customerNote(order.getNote())
                .createdAt(order.getCreatedAt())
                .completedAt(order.getCompletedAt())
                .items(items)
                .build();
    }

    private Long toLong(BigDecimal value) {
        return value != null ? value.longValue() : 0L;
    }
}
