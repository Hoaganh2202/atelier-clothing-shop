package com.shop.backend.order;

import com.shop.backend.order.dto.CategorySalesProjection;
import com.shop.backend.order.dto.CustomerSegmentProjection;
import com.shop.backend.order.dto.DailyRevenueProjection;
import com.shop.backend.order.dto.MonthlyRevenueProjection;
import com.shop.backend.order.dto.OrderStatusCountProjection;
import com.shop.backend.order.dto.PeriodRevenueProjection;
import com.shop.backend.order.dto.ReportSummaryProjection;
import com.shop.backend.order.dto.TopProductProjection;
import com.shop.backend.order.dto.DailyRevenueProjection;
import com.shop.backend.order.dto.MonthlyRevenueProjection;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.shop.backend.payment.Payment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.user.id = :userId")
    Optional<Order> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT p FROM Payment p WHERE p.order.id IN :orderIds")
    List<Payment> findPaymentsByOrderIdIn(@Param("orderIds") Collection<Long> orderIds);

    long countByStatus(OrderStatus status);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :start AND o.createdAt < :end")
    long countByCreatedAtBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status AND o.createdAt >= :start AND o.createdAt < :end")
    long countByStatusAndCreatedAtBetween(
            @Param("status") OrderStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status AND o.completedAt >= :start AND o.completedAt < :end")
    long countByStatusAndCompletedAtBetween(
            @Param("status") OrderStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = :status AND o.completedAt >= :start AND o.completedAt < :end")
    BigDecimal sumTotalAmountByStatusAndCompletedAtBetween(
            @Param("status") OrderStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    default BigDecimal sumCompletedRevenueBetween(LocalDateTime start, LocalDateTime end) {
        return sumTotalAmountByStatusAndCompletedAtBetween(OrderStatus.COMPLETED, start, end);
    }

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = :status AND o.createdAt >= :start AND o.createdAt < :end")
    BigDecimal sumTotalAmountByStatusAndCreatedAtBetween(
            @Param("status") OrderStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = :status")
    BigDecimal sumTotalAmountByStatus(@Param("status") OrderStatus status);

    @Query(value = "SELECT " +
           "  DATE(CONVERT_TZ(completed_at, '+00:00', '+07:00')) AS trendDate, " +
           "  COALESCE(SUM(total_amount), 0) AS totalRevenue, " +
           "  COUNT(id) AS totalOrders " +
           "FROM orders " +
           "WHERE status = 'completed' AND completed_at >= :start AND completed_at < :end " +
           "GROUP BY DATE(CONVERT_TZ(completed_at, '+00:00', '+07:00')) " +
           "ORDER BY trendDate ASC", nativeQuery = true)
    List<DailyRevenueProjection> getDailyRevenueTrend(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = "SELECT " +
           "  DATE_FORMAT(CONVERT_TZ(completed_at, '+00:00', '+07:00'), '%Y-%m') AS trendMonth, " +
           "  COALESCE(SUM(total_amount), 0) AS totalRevenue, " +
           "  COUNT(id) AS totalOrders " +
           "FROM orders " +
           "WHERE status = 'completed' AND completed_at >= :start AND completed_at < :end " +
           "GROUP BY DATE_FORMAT(CONVERT_TZ(completed_at, '+00:00', '+07:00'), '%Y-%m') " +
           "ORDER BY trendMonth ASC", nativeQuery = true)
    List<MonthlyRevenueProjection> getMonthlyRevenueTrend(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = "SELECT " +
           "  COUNT(CASE WHEN COALESCE(s.total_spent, 0) >= 5000000 THEN 1 END) AS vipCount, " +
           "  COUNT(CASE WHEN COALESCE(s.total_spent, 0) < 5000000 AND (COALESCE(s.order_count, 0) >= 3 OR COALESCE(s.total_spent, 0) >= 2000000) THEN 1 END) AS loyalCount, " +
           "  COUNT(CASE WHEN COALESCE(s.order_count, 0) BETWEEN 1 AND 2 AND COALESCE(s.total_spent, 0) < 2000000 THEN 1 END) AS newCount, " +
           "  COUNT(CASE WHEN s.user_id IS NULL OR COALESCE(s.order_count, 0) = 0 THEN 1 END) AS potentialCount " +
           "FROM users u " +
           "JOIN roles r ON u.role_id = r.id AND LOWER(r.name) = 'customer' " +
           "LEFT JOIN ( " +
           "  SELECT user_id, SUM(total_amount) AS total_spent, COUNT(id) AS order_count " +
           "  FROM orders " +
           "  WHERE status = 'completed' " +
           "  GROUP BY user_id " +
           ") s ON u.id = s.user_id", nativeQuery = true)
    CustomerSegmentProjection countCustomerSegments();

    default BigDecimal sumTotalCompletedRevenue() {
        return sumTotalAmountByStatus(OrderStatus.COMPLETED);
    }

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Order> findTop5ByOrderByCreatedAtDesc();

    @Query("SELECT o FROM Order o WHERE " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:keyword IS NULL OR LOWER(o.receiverName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR o.receiverPhone LIKE CONCAT('%', :keyword, '%') " +
           "OR CAST(o.id AS string) LIKE CONCAT('%', :keyword, '%')) AND " +
           "(:start IS NULL OR o.createdAt >= :start) AND " +
           "(:end IS NULL OR o.createdAt < :end)")
    Page<Order> searchOrdersAdmin(
            @Param("status") OrderStatus status,
            @Param("keyword") String keyword,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    @Query("SELECT o FROM Order o WHERE " +
           "o.status IN :statuses AND " +
           "(:keyword IS NULL OR LOWER(o.receiverName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR o.receiverPhone LIKE CONCAT('%', :keyword, '%') " +
           "OR CAST(o.id AS string) LIKE CONCAT('%', :keyword, '%')) AND " +
           "(:province IS NULL OR LOWER(o.province) = LOWER(:province)) AND " +
           "(:start IS NULL OR o.createdAt >= :start) AND " +
           "(:end IS NULL OR o.createdAt < :end)")
    Page<Order> searchShippingOrdersAdmin(
            @Param("statuses") List<OrderStatus> statuses,
            @Param("keyword") String keyword,
            @Param("province") String province,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    default Page<Order> searchOrdersAdmin(
            OrderStatus status,
            String keyword,
            Pageable pageable
    ) {
        return searchOrdersAdmin(status, keyword, null, null, pageable);
    }

    long countByUserId(Long userId);

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Query(value = "SELECT COUNT(*) FROM orders WHERE voucher_id = :voucherId AND status NOT IN ('cancelled', 'refunded') FOR SHARE", nativeQuery = true)
    long countActiveOrdersByVoucherId(@Param("voucherId") Long voucherId);

    @Query(value = "SELECT COUNT(*) FROM orders WHERE user_id = :userId AND voucher_id = :voucherId AND status NOT IN ('cancelled', 'refunded') FOR SHARE", nativeQuery = true)
    long countActiveOrdersByUserIdAndVoucherId(@Param("userId") Long userId, @Param("voucherId") Long voucherId);

    @Query(value = "SELECT " +
           "  (SELECT COALESCE(SUM(od.quantity * od.unit_price), 0) " +
           "   FROM order_details od " +
           "   JOIN orders o2 ON od.order_id = o2.id " +
           "   WHERE o2.status = 'completed' AND o2.completed_at >= :start AND o2.completed_at < :end " +
           "  ) AS grossRevenue, " +
           "  COALESCE(SUM(o.total_amount), 0) AS netRevenue, " +
           "  COALESCE(SUM(o.discount_amount), 0) AS totalDiscount, " +
           "  COALESCE(SUM(o.shipping_fee), 0) AS totalShippingFee, " +
           "  COUNT(o.id) AS completedOrderCount " +
           "FROM orders o " +
           "WHERE o.status = 'completed' AND o.completed_at >= :start AND o.completed_at < :end", nativeQuery = true)
    ReportSummaryProjection getReportSummary(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = "SELECT " +
           "  p.period AS period, " +
           "  p.netRevenue AS netRevenue, " +
           "  COALESCE(d.grossRevenue, 0) AS grossRevenue, " +
           "  p.completedOrderCount AS completedOrderCount " +
           "FROM ( " +
           "  SELECT " +
           "    DATE_FORMAT(CONVERT_TZ(completed_at, '+00:00', '+07:00'), '%Y-%m-%d') AS period, " +
           "    COALESCE(SUM(total_amount), 0) AS netRevenue, " +
           "    COUNT(id) AS completedOrderCount " +
           "  FROM orders " +
           "  WHERE status = 'completed' AND completed_at >= :start AND completed_at < :end " +
           "  GROUP BY DATE_FORMAT(CONVERT_TZ(completed_at, '+00:00', '+07:00'), '%Y-%m-%d') " +
           ") p " +
           "LEFT JOIN ( " +
           "  SELECT " +
           "    DATE_FORMAT(CONVERT_TZ(o.completed_at, '+00:00', '+07:00'), '%Y-%m-%d') AS period, " +
           "    COALESCE(SUM(od.quantity * od.unit_price), 0) AS grossRevenue " +
           "  FROM order_details od " +
           "  JOIN orders o ON od.order_id = o.id " +
           "  WHERE o.status = 'completed' AND o.completed_at >= :start AND o.completed_at < :end " +
           "  GROUP BY DATE_FORMAT(CONVERT_TZ(o.completed_at, '+00:00', '+07:00'), '%Y-%m-%d') " +
           ") d ON p.period = d.period " +
           "ORDER BY p.period ASC", nativeQuery = true)
    List<PeriodRevenueProjection> getDailyReportRevenueTrend(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = "SELECT " +
           "  p.period AS period, " +
           "  p.netRevenue AS netRevenue, " +
           "  COALESCE(d.grossRevenue, 0) AS grossRevenue, " +
           "  p.completedOrderCount AS completedOrderCount " +
           "FROM ( " +
           "  SELECT " +
           "    DATE_FORMAT(CONVERT_TZ(completed_at, '+00:00', '+07:00'), '%x-W%v') AS period, " +
           "    COALESCE(SUM(total_amount), 0) AS netRevenue, " +
           "    COUNT(id) AS completedOrderCount " +
           "  FROM orders " +
           "  WHERE status = 'completed' AND completed_at >= :start AND completed_at < :end " +
           "  GROUP BY DATE_FORMAT(CONVERT_TZ(completed_at, '+00:00', '+07:00'), '%x-W%v') " +
           ") p " +
           "LEFT JOIN ( " +
           "  SELECT " +
           "    DATE_FORMAT(CONVERT_TZ(o.completed_at, '+00:00', '+07:00'), '%x-W%v') AS period, " +
           "    COALESCE(SUM(od.quantity * od.unit_price), 0) AS grossRevenue " +
           "  FROM order_details od " +
           "  JOIN orders o ON od.order_id = o.id " +
           "  WHERE o.status = 'completed' AND o.completed_at >= :start AND o.completed_at < :end " +
           "  GROUP BY DATE_FORMAT(CONVERT_TZ(o.completed_at, '+00:00', '+07:00'), '%x-W%v') " +
           ") d ON p.period = d.period " +
           "ORDER BY p.period ASC", nativeQuery = true)
    List<PeriodRevenueProjection> getWeeklyReportRevenueTrend(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = "SELECT " +
           "  p.period AS period, " +
           "  p.netRevenue AS netRevenue, " +
           "  COALESCE(d.grossRevenue, 0) AS grossRevenue, " +
           "  p.completedOrderCount AS completedOrderCount " +
           "FROM ( " +
           "  SELECT " +
           "    DATE_FORMAT(CONVERT_TZ(completed_at, '+00:00', '+07:00'), '%Y-%m') AS period, " +
           "    COALESCE(SUM(total_amount), 0) AS netRevenue, " +
           "    COUNT(id) AS completedOrderCount " +
           "  FROM orders " +
           "  WHERE status = 'completed' AND completed_at >= :start AND completed_at < :end " +
           "  GROUP BY DATE_FORMAT(CONVERT_TZ(completed_at, '+00:00', '+07:00'), '%Y-%m') " +
           ") p " +
           "LEFT JOIN ( " +
           "  SELECT " +
           "    DATE_FORMAT(CONVERT_TZ(o.completed_at, '+00:00', '+07:00'), '%Y-%m') AS period, " +
           "    COALESCE(SUM(od.quantity * od.unit_price), 0) AS grossRevenue " +
           "  FROM order_details od " +
           "  JOIN orders o ON od.order_id = o.id " +
           "  WHERE o.status = 'completed' AND o.completed_at >= :start AND o.completed_at < :end " +
           "  GROUP BY DATE_FORMAT(CONVERT_TZ(o.completed_at, '+00:00', '+07:00'), '%Y-%m') " +
           ") d ON p.period = d.period " +
           "ORDER BY p.period ASC", nativeQuery = true)
    List<PeriodRevenueProjection> getMonthlyReportRevenueTrend(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = "SELECT " +
           "  pv.product_id AS productId, " +
           "  od.product_name AS productName, " +
           "  SUM(od.quantity) AS totalQuantitySold, " +
           "  SUM(od.quantity * od.unit_price) AS totalRevenue " +
           "FROM order_details od " +
           "JOIN orders o ON od.order_id = o.id " +
           "LEFT JOIN product_variants pv ON od.variant_id = pv.id " +
           "WHERE o.status = 'completed' AND o.completed_at >= :start AND o.completed_at < :end " +
           "GROUP BY pv.product_id, od.product_name " +
           "ORDER BY totalQuantitySold DESC, totalRevenue DESC " +
           "LIMIT :limit", nativeQuery = true)
    List<TopProductProjection> getTopSellingProducts(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("limit") int limit
    );

    @Query(value = "SELECT " +
           "  c.id AS categoryId, " +
           "  COALESCE(c.name, 'Chưa phân loại') AS categoryName, " +
           "  COALESCE(SUM(od.quantity), 0) AS totalQuantitySold, " +
           "  COALESCE(SUM(od.quantity * od.unit_price), 0) AS totalRevenue " +
           "FROM order_details od " +
           "JOIN orders o ON od.order_id = o.id " +
           "LEFT JOIN product_variants pv ON od.variant_id = pv.id " +
           "LEFT JOIN products p ON pv.product_id = p.id " +
           "LEFT JOIN categories c ON p.category_id = c.id " +
           "WHERE o.status = 'completed' AND o.completed_at >= :start AND o.completed_at < :end " +
           "GROUP BY c.id, c.name " +
           "ORDER BY totalRevenue DESC", nativeQuery = true)
    List<CategorySalesProjection> getCategorySales(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query(value = "SELECT " +
           "  status AS status, " +
           "  COUNT(id) AS orderCount " +
           "FROM orders " +
           "WHERE created_at >= :start AND created_at < :end " +
           "GROUP BY status", nativeQuery = true)
    List<OrderStatusCountProjection> getOrderStatusDistribution(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
