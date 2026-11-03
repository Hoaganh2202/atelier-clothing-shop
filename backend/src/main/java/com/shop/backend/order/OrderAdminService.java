package com.shop.backend.order;

import com.shop.backend.common.dto.PageResponse;
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
import com.shop.backend.order.dto.OrderStatusUpdateRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderAdminService {

    PageResponse<OrderAdminListItemResponse> getOrders(
            OrderStatus status,
            String keyword,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    );

    OrderAdminDetailResponse getOrderById(Long id);

    OrderAdminDetailResponse updateOrderStatus(Long id, OrderStatusUpdateRequest request);

    OrderAdminDetailResponse updateOrderStatus(Long id, OrderStatusUpdateRequest request, Long actorUserId);

    OrderAdminDetailResponse updateOrderStatus(Long id, OrderStatusUpdateRequest request, Set<OrderStatus> allowedSourceStatuses);

    OrderAdminDetailResponse updateOrderStatus(Long id, OrderStatusUpdateRequest request, Set<OrderStatus> allowedSourceStatuses, Long actorUserId);

    com.shop.backend.order.dto.OrderReturnReceiptResponse processReturnReceipt(
            Long orderId,
            com.shop.backend.order.dto.OrderReturnReceiptRequest request,
            Long actorUserId
    );

    long countOrdersByStatus(OrderStatus status);

    long countCompletedOrdersBetween(LocalDateTime start, LocalDateTime end);

    Page<Order> searchShippingOrders(
            List<OrderStatus> statuses,
            String keyword,
            String province,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    Map<Long, List<OrderDetail>> getOrderDetailsByOrderIds(List<Long> orderIds);

    long countOrdersBetween(LocalDateTime start, LocalDateTime end);

    long sumCompletedRevenueBetween(LocalDateTime start, LocalDateTime end);

    List<DailyRevenueProjection> getDailyRevenueTrend(LocalDateTime start, LocalDateTime end);

    List<MonthlyRevenueProjection> getMonthlyRevenueTrend(LocalDateTime start, LocalDateTime end);

    CustomerSegmentProjection countCustomerSegments();

    ReportSummaryProjection getReportSummary(LocalDateTime start, LocalDateTime end);

    List<PeriodRevenueProjection> getDailyReportRevenueTrend(LocalDateTime start, LocalDateTime end);

    List<PeriodRevenueProjection> getWeeklyReportRevenueTrend(LocalDateTime start, LocalDateTime end);

    List<PeriodRevenueProjection> getMonthlyReportRevenueTrend(LocalDateTime start, LocalDateTime end);

    List<TopProductProjection> getTopSellingProducts(LocalDateTime start, LocalDateTime end, int limit);

    List<CategorySalesProjection> getCategorySales(LocalDateTime start, LocalDateTime end);

    List<OrderStatusCountProjection> getOrderStatusDistribution(LocalDateTime start, LocalDateTime end);

    Order getOrderEntity(Long id);

    OrderDetail getOrderDetailEntity(Long orderDetailId);
}
