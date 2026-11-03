package com.shop.backend.order;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.inventory.InventoryStockService;
import com.shop.backend.inventory.dto.OrderReturnReceiptItem;
import com.shop.backend.inventory.dto.OrderReturnReceiptResult;
import com.shop.backend.inventory.dto.OrderStockItem;
import com.shop.backend.order.dto.OrderReturnReceiptItemRequest;
import com.shop.backend.order.dto.OrderReturnReceiptRequest;
import com.shop.backend.order.dto.OrderReturnReceiptResponse;
import com.shop.backend.order.dto.CategorySalesProjection;
import com.shop.backend.order.dto.CustomerSegmentProjection;
import com.shop.backend.order.dto.OrderStatusCountProjection;
import com.shop.backend.order.dto.PeriodRevenueProjection;
import com.shop.backend.order.dto.ReportSummaryProjection;
import com.shop.backend.order.dto.TopProductProjection;
import com.shop.backend.order.dto.DailyRevenueProjection;
import com.shop.backend.order.dto.MonthlyRevenueProjection;
import com.shop.backend.order.dto.OrderAdminDetailResponse;
import com.shop.backend.order.dto.OrderAdminListItemResponse;
import com.shop.backend.order.dto.OrderItemAdminResponse;
import com.shop.backend.order.dto.OrderPaymentAdminResponse;
import com.shop.backend.order.dto.OrderStatusUpdateRequest;
import com.shop.backend.payment.Payment;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.payment.PaymentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
public class OrderAdminServiceImpl implements OrderAdminService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final InventoryStockService inventoryStockService;
    private final PaymentService paymentService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderAdminListItemResponse> getOrders(
            OrderStatus status,
            String keyword,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    ) {
        Pageable effectivePageable = resolvePageable(pageable);

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BadRequestException("Ngày bắt đầu không được sau ngày kết thúc");
        }

        LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : null;
        LocalDateTime end = (endDate != null) ? endDate.plusDays(1).atStartOfDay() : null;
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        Page<Order> orderPage = orderRepository.searchOrdersAdmin(
                status,
                cleanKeyword,
                start,
                end,
                effectivePageable
        );

        List<Order> orders = orderPage.getContent();
        if (orders.isEmpty()) {
            return PageResponse.<OrderAdminListItemResponse>builder()
                    .content(Collections.emptyList())
                    .page(orderPage.getNumber())
                    .size(orderPage.getSize())
                    .totalElements(orderPage.getTotalElements())
                    .totalPages(orderPage.getTotalPages())
                    .build();
        }

        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderDetail>> detailsByOrderId = orderDetailRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(d -> d.getOrder().getId()));

        List<OrderAdminListItemResponse> content = orders.stream()
                .map(order -> {
                    List<OrderDetail> items = detailsByOrderId.getOrDefault(order.getId(), Collections.emptyList());
                    int itemCount = items.stream().mapToInt(OrderDetail::getQuantity).sum();
                    String itemsSummary = buildItemsSummary(items);

                    return OrderAdminListItemResponse.builder()
                            .id(order.getId())
                            .orderCode("ORD-" + order.getId())
                            .customerName(order.getReceiverName())
                            .customerPhone(order.getReceiverPhone())
                            .createdAt(order.getCreatedAt())
                            .itemsSummary(itemsSummary)
                            .itemCount(itemCount)
                            .totalAmount(toLong(order.getTotalAmount()))
                            .status(order.getStatus())
                            .build();
                })
                .toList();

        return PageResponse.<OrderAdminListItemResponse>builder()
                .content(content)
                .page(orderPage.getNumber())
                .size(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderAdminDetailResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + id));

        return toDetailResponse(order);
    }

    @Override
    @Transactional
    public OrderAdminDetailResponse updateOrderStatus(Long id, OrderStatusUpdateRequest request) {
        return updateOrderStatus(id, request, null, null);
    }

    @Override
    @Transactional
    public OrderAdminDetailResponse updateOrderStatus(Long id, OrderStatusUpdateRequest request, Long actorUserId) {
        return updateOrderStatus(id, request, null, actorUserId);
    }

    @Override
    @Transactional
    public OrderAdminDetailResponse updateOrderStatus(Long id, OrderStatusUpdateRequest request, Set<OrderStatus> allowedSourceStatuses) {
        return updateOrderStatus(id, request, allowedSourceStatuses, null);
    }

    @Override
    @Transactional
    public OrderAdminDetailResponse updateOrderStatus(
            Long id,
            OrderStatusUpdateRequest request,
            Set<OrderStatus> allowedSourceStatuses,
            Long actorUserId
    ) {
        Order order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + id));

        OrderStatus currentStatus = order.getStatus();
        OrderStatus newStatus = request.getStatus();

        if (allowedSourceStatuses != null && !allowedSourceStatuses.contains(currentStatus)) {
            throw new BadRequestException("Không thể cập nhật vận chuyển cho đơn hàng ở trạng thái: "
                    + currentStatus.name().toLowerCase());
        }

        if (currentStatus == newStatus) {
            throw new BadRequestException("Đơn hàng hiện tại đã ở trạng thái: " + currentStatus.name().toLowerCase());
        }

        boolean isValidTransition = switch (currentStatus) {
            case PENDING -> (newStatus == OrderStatus.CONFIRMED || newStatus == OrderStatus.CANCELLED);
            case CONFIRMED -> (newStatus == OrderStatus.SHIPPING || newStatus == OrderStatus.CANCELLED);
            case SHIPPING -> (newStatus == OrderStatus.COMPLETED || newStatus == OrderStatus.CANCELLED);
            case COMPLETED -> (newStatus == OrderStatus.REFUNDED);
            case CANCELLED, REFUNDED -> false;
        };

        if (!isValidTransition) {
            throw new BadRequestException("Không thể chuyển trạng thái đơn hàng từ "
                    + currentStatus.name().toLowerCase() + " sang " + newStatus.name().toLowerCase());
        }

        if (newStatus == OrderStatus.COMPLETED && order.getCompletedAt() == null) {
            order.setCompletedAt(LocalDateTime.now());
        }

        if ((currentStatus == OrderStatus.PENDING || currentStatus == OrderStatus.CONFIRMED)
                && newStatus == OrderStatus.CANCELLED) {
            List<OrderStockItem> stockItems = order.getOrderDetails() == null
                    ? List.of()
                    : order.getOrderDetails().stream()
                            .map(detail -> new OrderStockItem(
                                    detail.getId(),
                                    detail.getVariant().getId(),
                                    detail.getQuantity()))
                            .toList();
            String reason = "Hoàn tồn kho do hủy đơn hàng #" + order.getId();
            inventoryStockService.restoreStockForCancelledOrder(order.getId(), actorUserId, reason, stockItems);
        }

        if (newStatus == OrderStatus.CANCELLED) {
            order.setCancelledFromStatus(currentStatus);
        }

        order.setStatus(newStatus);
        Order savedOrder = orderRepository.save(order);

        if (newStatus == OrderStatus.COMPLETED && order.getPaymentMethod() == PaymentMethod.COD) {
            paymentService.processCodPaymentCompletion(savedOrder.getId());
        }

        return toDetailResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderReturnReceiptResponse processReturnReceipt(
            Long orderId,
            OrderReturnReceiptRequest request,
            Long actorUserId
    ) {
        if (orderId == null) {
            throw new BadRequestException("Mã đơn hàng không được để trống");
        }
        if (request == null) {
            throw new BadRequestException("Dữ liệu biên bản tiếp nhận không được để trống");
        }

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + orderId));

        OrderStatus status = order.getStatus();
        OrderStatus cancelledFrom = order.getCancelledFromStatus();

        if (status == OrderStatus.CANCELLED) {
            if (cancelledFrom == null) {
                throw new BadRequestException("Đơn hàng đã hủy trước đó không có thông tin nguồn gốc hủy (nguồn gốc không xác định). Vui lòng kiểm tra thủ công.");
            }
            if (cancelledFrom != OrderStatus.SHIPPING) {
                throw new BadRequestException("Không thể nhận hàng hoàn cho đơn hàng đã hủy từ trạng thái: "
                        + cancelledFrom.name() + ". Chỉ chấp nhận nhận hàng hoàn cho đơn hàng bị hủy từ trạng thái SHIPPING.");
            }
            if (inventoryStockService.hasCancellationRestoreForOrder(orderId)) {
                throw new BadRequestException("Đơn hàng này đã có giao dịch hoàn tồn kho khi hủy (CANCEL_RESTORE). Không thể nhận hàng hoàn.");
            }
        } else if (status == OrderStatus.REFUNDED) {
            // Case B: Allowed
        } else {
            throw new BadRequestException("Không thể tiếp nhận hàng hoàn cho đơn hàng ở trạng thái: "
                    + status.name() + ". Chỉ chấp nhận đơn REFUNDED hoặc đơn CANCELLED từ SHIPPING.");
        }

        List<OrderReturnReceiptItem> inventoryItems = (request.items() == null ? List.<OrderReturnReceiptItemRequest>of() : request.items())
                .stream()
                .map(item -> new OrderReturnReceiptItem(
                        item.orderDetailId(),
                        item.quantity(),
                        item.condition(),
                        item.reason()))
                .toList();

        OrderReturnReceiptResult result = inventoryStockService.processReturnReceipt(
                orderId,
                actorUserId,
                request.receiptNumber(),
                request.note(),
                inventoryItems
        );

        return OrderReturnReceiptResponse.from(result);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOrdersByStatus(OrderStatus status) {
        return orderRepository.countByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public long countCompletedOrdersBetween(LocalDateTime start, LocalDateTime end) {
        return orderRepository.countByStatusAndCompletedAtBetween(OrderStatus.COMPLETED, start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> searchShippingOrders(
            List<OrderStatus> statuses,
            String keyword,
            String province,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    ) {
        return orderRepository.searchShippingOrdersAdmin(statuses, keyword, province, start, end, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<OrderDetail>> getOrderDetailsByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderDetailRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(d -> d.getOrder().getId()));
    }

    private Pageable resolvePageable(Pageable pageable) {
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = pageable.getPageSize() > 0 ? Math.min(pageable.getPageSize(), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        Sort sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        }

        return PageRequest.of(page, size, sort);
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

    private OrderAdminDetailResponse toDetailResponse(Order order) {
        List<OrderItemAdminResponse> items = order.getOrderDetails().stream()
                .map(this::toItemResponse)
                .toList();

        List<OrderPaymentAdminResponse> payments = order.getPayments().stream()
                .map(this::toPaymentResponse)
                .toList();

        return OrderAdminDetailResponse.builder()
                .id(order.getId())
                .orderCode("ORD-" + order.getId())
                .status(order.getStatus())
                .cancelledFromStatus(order.getCancelledFromStatus())
                .createdAt(order.getCreatedAt())
                .completedAt(order.getCompletedAt())
                .subtotal(toLong(order.getSubtotal()))
                .discountAmount(toLong(order.getDiscountAmount()))
                .shippingFee(toLong(order.getShippingFee()))
                .totalAmount(toLong(order.getTotalAmount()))
                .paymentMethod(order.getPaymentMethod())
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .addressLine(order.getAddressLine())
                .ward(order.getWard())
                .district(order.getDistrict())
                .province(order.getProvince())
                .note(order.getNote())
                .salesChannel(order.getSalesChannel())
                .dataSource(order.getDataSource())
                .voucherId(order.getVoucherId())
                .items(items)
                .payments(payments)
                .build();
    }

    private OrderItemAdminResponse toItemResponse(OrderDetail detail) {
        Long unitPrice = toLong(detail.getUnitPrice());
        Long discount = toLong(detail.getDiscountAmount());
        int quantity = detail.getQuantity() != null ? detail.getQuantity() : 0;
        long totalPrice = Math.max((unitPrice * quantity) - discount, 0L);

        return OrderItemAdminResponse.builder()
                .id(detail.getId())
                .variantId(detail.getVariant() != null ? detail.getVariant().getId() : null)
                .productName(detail.getProductName())
                .variantSize(detail.getVariantSize())
                .variantColor(detail.getVariantColor())
                .quantity(quantity)
                .unitPrice(unitPrice)
                .discountAmount(discount)
                .totalPrice(totalPrice)
                .build();
    }

    private OrderPaymentAdminResponse toPaymentResponse(Payment payment) {
        return OrderPaymentAdminResponse.builder()
                .id(payment.getId())
                .method(payment.getMethod())
                .amount(toLong(payment.getAmount()))
                .status(payment.getStatus())
                .transactionCode(payment.getTransactionCode())
                .paidAt(payment.getPaidAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public long countOrdersBetween(LocalDateTime start, LocalDateTime end) {
        return orderRepository.countByCreatedAtBetween(start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public long sumCompletedRevenueBetween(LocalDateTime start, LocalDateTime end) {
        BigDecimal revenue = orderRepository.sumCompletedRevenueBetween(start, end);
        return revenue != null ? revenue.longValue() : 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyRevenueProjection> getDailyRevenueTrend(LocalDateTime start, LocalDateTime end) {
        return orderRepository.getDailyRevenueTrend(start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MonthlyRevenueProjection> getMonthlyRevenueTrend(LocalDateTime start, LocalDateTime end) {
        return orderRepository.getMonthlyRevenueTrend(start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerSegmentProjection countCustomerSegments() {
        return orderRepository.countCustomerSegments();
    }

    @Override
    @Transactional(readOnly = true)
    public ReportSummaryProjection getReportSummary(LocalDateTime start, LocalDateTime end) {
        return orderRepository.getReportSummary(start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PeriodRevenueProjection> getDailyReportRevenueTrend(LocalDateTime start, LocalDateTime end) {
        return orderRepository.getDailyReportRevenueTrend(start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PeriodRevenueProjection> getWeeklyReportRevenueTrend(LocalDateTime start, LocalDateTime end) {
        return orderRepository.getWeeklyReportRevenueTrend(start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PeriodRevenueProjection> getMonthlyReportRevenueTrend(LocalDateTime start, LocalDateTime end) {
        return orderRepository.getMonthlyReportRevenueTrend(start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopProductProjection> getTopSellingProducts(LocalDateTime start, LocalDateTime end, int limit) {
        return orderRepository.getTopSellingProducts(start, end, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategorySalesProjection> getCategorySales(LocalDateTime start, LocalDateTime end) {
        return orderRepository.getCategorySales(start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusCountProjection> getOrderStatusDistribution(LocalDateTime start, LocalDateTime end) {
        return orderRepository.getOrderStatusDistribution(start, end);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrderEntity(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetail getOrderDetailEntity(Long orderDetailId) {
        return orderDetailRepository.findById(orderDetailId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi tiết đơn hàng với ID: " + orderDetailId));
    }

    private Long toLong(BigDecimal value) {
        return value != null ? value.longValue() : 0L;
    }
}
