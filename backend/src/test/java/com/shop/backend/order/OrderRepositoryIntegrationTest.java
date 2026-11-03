package com.shop.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.payment.Payment;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.payment.PaymentMethodConverter;
import com.shop.backend.payment.PaymentRepository;
import com.shop.backend.payment.PaymentStatus;
import com.shop.backend.payment.PaymentStatusConverter;
import com.shop.backend.product.Category;
import com.shop.backend.product.CategoryRepository;
import com.shop.backend.product.Product;
import com.shop.backend.product.ProductRepository;
import com.shop.backend.product.ProductVariant;
import com.shop.backend.product.ProductVariantRepository;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.shop.backend.order.dto.CustomerSegmentProjection;
import com.shop.backend.order.dto.DailyRevenueProjection;
import com.shop.backend.order.dto.MonthlyRevenueProjection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderRepositoryIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private ProductVariant testVariant;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(new Role(null, "customer")));

        testUser = userRepository.save(User.builder()
                .role(customerRole)
                .name("Nguyễn Văn Đơn")
                .email("test.order." + System.currentTimeMillis() + "@example.com")
                .phone("0987654321")
                .passwordHash("hashed_secret")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        Category testCategory = categoryRepository.save(Category.builder()
                .name("Áo Sơ Mi Nam")
                .slug("ao-so-mi-nam-" + System.currentTimeMillis())
                .isActive(true)
                .build());

        Product testProduct = productRepository.save(Product.builder()
                .category(testCategory)
                .name("Áo Sơ Mi Oxford")
                .slug("ao-so-mi-oxford-" + System.currentTimeMillis())
                .basePrice(new BigDecimal("350000.00"))
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        testVariant = variantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku("OXFORD-WHITE-M-" + System.currentTimeMillis())
                .size("M")
                .color("Trắng")
                .stock(50)
                .price(new BigDecimal("350000.00"))
                .build());
    }

    @Test
    @DisplayName("Persist and query Order with OrderDetail and Payment cascade")
    void testPersistAndFindOrderWithDetailsAndPayment() {
        Order order = Order.builder()
                .user(testUser)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("700000.00"))
                .discountAmount(new BigDecimal("50000.00"))
                .shippingFee(new BigDecimal("30000.00"))
                .totalAmount(new BigDecimal("680000.00"))
                .paymentMethod(PaymentMethod.COD)
                .receiverName("Nguyễn Văn Đơn")
                .receiverPhone("0987654321")
                .addressLine("123 Đường Thời Trang")
                .ward("Phường Bến Nghé")
                .district("Quận 1")
                .province("Hồ Chí Minh")
                .note("Giao giờ hành chính")
                .salesChannel("website")
                .dataSource(DataSourceType.WEBSITE)
                .createdAt(LocalDateTime.now())
                .build();

        OrderDetail detail = OrderDetail.builder()
                .variant(testVariant)
                .productName("Áo Sơ Mi Oxford")
                .variantSize("M")
                .variantColor("Trắng")
                .quantity(2)
                .unitPrice(new BigDecimal("350000.00"))
                .discountAmount(BigDecimal.ZERO)
                .build();
        order.addOrderDetail(detail);

        Payment payment = Payment.builder()
                .method(PaymentMethod.COD)
                .amount(new BigDecimal("680000.00"))
                .status(PaymentStatus.PENDING)
                .build();
        order.addPayment(payment);

        Order saved = orderRepository.saveAndFlush(order);
        Long orderId = saved.getId();

        entityManager.clear();

        Order found = orderRepository.findById(orderId).orElseThrow();
        assertThat(found.getId()).isEqualTo(orderId);
        assertThat(found.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(found.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(found.getTotalAmount()).isEqualByComparingTo("680000.00");
        assertThat(found.getSubtotal()).isEqualByComparingTo("700000.00");
        assertThat(found.getReceiverName()).isEqualTo("Nguyễn Văn Đơn");
        assertThat(found.getProvince()).isEqualTo("Hồ Chí Minh");

        // Verify OrderDetail
        assertThat(found.getOrderDetails()).hasSize(1);
        OrderDetail foundDetail = found.getOrderDetails().get(0);
        assertThat(foundDetail.getProductName()).isEqualTo("Áo Sơ Mi Oxford");
        assertThat(foundDetail.getQuantity()).isEqualTo(2);
        assertThat(foundDetail.getVariant().getId()).isEqualTo(testVariant.getId());

        // Verify Payment
        assertThat(found.getPayments()).hasSize(1);
        Payment foundPayment = found.getPayments().get(0);
        assertThat(foundPayment.getMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(foundPayment.getAmount()).isEqualByComparingTo("680000.00");
        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("OrderStatusConverter converts enum to lowercase DB string and back")
    void testOrderStatusConverter() {
        OrderStatusConverter converter = new OrderStatusConverter();

        assertThat(converter.convertToDatabaseColumn(OrderStatus.PENDING)).isEqualTo("pending");
        assertThat(converter.convertToDatabaseColumn(OrderStatus.CONFIRMED)).isEqualTo("confirmed");
        assertThat(converter.convertToDatabaseColumn(OrderStatus.SHIPPING)).isEqualTo("shipping");
        assertThat(converter.convertToDatabaseColumn(OrderStatus.COMPLETED)).isEqualTo("completed");
        assertThat(converter.convertToDatabaseColumn(OrderStatus.CANCELLED)).isEqualTo("cancelled");
        assertThat(converter.convertToDatabaseColumn(OrderStatus.REFUNDED)).isEqualTo("refunded");

        assertThat(converter.convertToEntityAttribute("completed")).isEqualTo(OrderStatus.COMPLETED);
        assertThat(converter.convertToEntityAttribute("PENDING")).isEqualTo(OrderStatus.PENDING);

        assertThatThrownBy(() -> converter.convertToEntityAttribute("UNKNOWN_STATUS"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PaymentMethodConverter and PaymentStatusConverter contracts")
    void testPaymentConverters() {
        PaymentMethodConverter methodConverter = new PaymentMethodConverter();
        assertThat(methodConverter.convertToDatabaseColumn(PaymentMethod.COD)).isEqualTo("cod");
        assertThat(methodConverter.convertToDatabaseColumn(PaymentMethod.VNPAY)).isEqualTo("vnpay");
        assertThat(methodConverter.convertToEntityAttribute("vnpay")).isEqualTo(PaymentMethod.VNPAY);

        PaymentStatusConverter statusConverter = new PaymentStatusConverter();
        assertThat(statusConverter.convertToDatabaseColumn(PaymentStatus.PENDING)).isEqualTo("pending");
        assertThat(statusConverter.convertToDatabaseColumn(PaymentStatus.SUCCESS)).isEqualTo("success");
        assertThat(statusConverter.convertToEntityAttribute("success")).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("Count orders by status and date period using half-open interval [start, end)")
    void testCountOrdersHalfOpenIntervalBoundary() {
        LocalDateTime t0 = LocalDateTime.of(2026, 10, 28, 0, 0, 0);
        LocalDateTime t1 = LocalDateTime.of(2026, 10, 29, 0, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 10, 30, 0, 0, 0);

        // Order 1: exactly at t0 (start of period 1)
        createOrder(OrderStatus.COMPLETED, new BigDecimal("100000.00"), t0, t0);

        // Order 2: inside period 1
        createOrder(OrderStatus.COMPLETED, new BigDecimal("200000.00"), t0.plusHours(12), t0.plusHours(12));

        // Order 3: exactly at t1 (boundary: end of period 1, start of period 2)
        createOrder(OrderStatus.COMPLETED, new BigDecimal("300000.00"), t1, t1);

        // Order 4: inside period 2
        createOrder(OrderStatus.PENDING, new BigDecimal("400000.00"), t1.plusHours(12), null);

        // Order 5: exactly at t2 (end of period 2)
        createOrder(OrderStatus.COMPLETED, new BigDecimal("500000.00"), t2, t2);

        entityManager.flush();

        // 1. Period 1: [t0, t1) -> should include Order 1 and Order 2, NOT Order 3
        long countPeriod1 = orderRepository.countByCreatedAtBetween(t0, t1);
        assertThat(countPeriod1).isEqualTo(2);

        long countCompletedPeriod1 = orderRepository.countByStatusAndCreatedAtBetween(OrderStatus.COMPLETED, t0, t1);
        assertThat(countCompletedPeriod1).isEqualTo(2);

        // 2. Period 2: [t1, t2) -> should include Order 3 and Order 4, NOT Order 5
        long countPeriod2 = orderRepository.countByCreatedAtBetween(t1, t2);
        assertThat(countPeriod2).isEqualTo(2);

        long countCompletedPeriod2 = orderRepository.countByStatusAndCreatedAtBetween(OrderStatus.COMPLETED, t1, t2);
        assertThat(countCompletedPeriod2).isEqualTo(1); // only Order 3 (Order 4 is PENDING)

        // 3. Combined period: [t0, t2) -> exactly 4 orders, no double counting at boundary t1
        long countCombined = orderRepository.countByCreatedAtBetween(t0, t2);
        assertThat(countCombined).isEqualTo(4);
    }

    @Test
    @DisplayName("Sum revenue ONLY for COMPLETED orders using completedAt in half-open interval [start, end)")
    void testRevenueByCompletedAtWithHalfOpenInterval() {
        LocalDateTime tStart = LocalDateTime.of(2026, 10, 28, 0, 0, 0);
        LocalDateTime tEnd = LocalDateTime.of(2026, 10, 29, 0, 0, 0);

        // Case A: Created inside period, but COMPLETED outside period (tEnd + 2h) -> MUST NOT count in [tStart, tEnd)
        createOrder(OrderStatus.COMPLETED, new BigDecimal("100000.00"), tStart.plusHours(2), tEnd.plusHours(2));

        // Case B: Created BEFORE period (3 days ago), but COMPLETED inside period -> MUST count in [tStart, tEnd)
        createOrder(OrderStatus.COMPLETED, new BigDecimal("200000.00"), tStart.minusDays(3), tStart.plusHours(4));

        // Case C: Created and COMPLETED inside period -> MUST count in [tStart, tEnd)
        createOrder(OrderStatus.COMPLETED, new BigDecimal("300000.00"), tStart.plusHours(1), tStart.plusHours(6));

        // Case D: CANCELLED order with completedAt set inside period -> MUST NOT count in revenue
        createOrder(OrderStatus.CANCELLED, new BigDecimal("400000.00"), tStart.plusHours(3), tStart.plusHours(5));

        // Case E: PENDING order inside period (completedAt is null) -> MUST NOT count in revenue
        createOrder(OrderStatus.PENDING, new BigDecimal("500000.00"), tStart.plusHours(2), null);

        // Case F: Exactly at start boundary: completedAt == tStart -> MUST count in [tStart, tEnd)
        createOrder(OrderStatus.COMPLETED, new BigDecimal("50000.00"), tStart.minusHours(5), tStart);

        // Case G: Exactly at end boundary: completedAt == tEnd -> MUST NOT count in [tStart, tEnd) (belongs to next period)
        createOrder(OrderStatus.COMPLETED, new BigDecimal("70000.00"), tStart.plusHours(10), tEnd);

        entityManager.flush();

        // Target revenue in [tStart, tEnd): Case B (200,000) + Case C (300,000) + Case F (50,000) = 550,000
        BigDecimal periodRevenue = orderRepository.sumCompletedRevenueBetween(tStart, tEnd);
        assertThat(periodRevenue).isEqualByComparingTo("550000.00");

        // Next period: [tEnd, tEnd + 1 day): Case G (70,000) + Case A (100,000) = 170,000
        BigDecimal nextPeriodRevenue = orderRepository.sumCompletedRevenueBetween(tEnd, tEnd.plusDays(1));
        assertThat(nextPeriodRevenue).isEqualByComparingTo("170000.00");

        // Empty period in future -> MUST return ZERO, not null
        BigDecimal futureRevenue = orderRepository.sumCompletedRevenueBetween(
                tStart.plusDays(30), tStart.plusDays(31)
        );
        assertThat(futureRevenue).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Find top 5 orders and search orders admin with pagination")
    void testFindTop5AndSearchOrdersAdmin() {
        LocalDateTime baseTime = LocalDateTime.now().minusHours(10);

        for (int i = 1; i <= 7; i++) {
            Order o = createOrder(
                    i % 2 == 0 ? OrderStatus.COMPLETED : OrderStatus.PENDING,
                    new BigDecimal(i * 100000),
                    baseTime.plusHours(i),
                    i % 2 == 0 ? baseTime.plusHours(i) : null
            );
            o.setReceiverName("Khách Hàng " + i);
            o.setReceiverPhone("090000000" + i);
        }
        entityManager.flush();

        // 1. Top 5 orders
        List<Order> top5 = orderRepository.findTop5ByOrderByCreatedAtDesc();
        assertThat(top5).hasSize(5);
        // First order in top 5 must be the latest
        assertThat(top5.get(0).getCreatedAt()).isAfter(top5.get(1).getCreatedAt());

        // 2. Search by status COMPLETED
        Page<Order> completedPage = orderRepository.searchOrdersAdmin(
                OrderStatus.COMPLETED, null, PageRequest.of(0, 10)
        );
        assertThat(completedPage.getContent()).allMatch(o -> o.getStatus() == OrderStatus.COMPLETED);

        // 3. Search by keyword phone
        Page<Order> searchPhonePage = orderRepository.searchOrdersAdmin(
                null, "0900000003", PageRequest.of(0, 10)
        );
        assertThat(searchPhonePage.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(searchPhonePage.getContent().get(0).getReceiverPhone()).contains("0900000003");
    }

    private Order createOrder(OrderStatus status, BigDecimal totalAmount, LocalDateTime createdAt, LocalDateTime completedAt) {
        Order order = Order.builder()
                .user(testUser)
                .status(status)
                .subtotal(totalAmount)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .paymentMethod(PaymentMethod.COD)
                .receiverName(testUser.getName())
                .receiverPhone(testUser.getPhone())
                .province("Hồ Chí Minh")
                .dataSource(DataSourceType.WEBSITE)
                .createdAt(createdAt)
                .completedAt(completedAt)
                .build();
        return orderRepository.save(order);
    }

    @Test
    @DisplayName("Query daily and monthly revenue trend projections")
    void testDailyAndMonthlyRevenueTrendProjections() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDateTime todayEarly = today.atTime(1, 0);
        LocalDateTime todayLate = today.atTime(23, 30);
        LocalDateTime yesterdayLate = (today.getDayOfMonth() == 1)
                ? today.atTime(0, 30)
                : today.minusDays(1).atTime(23, 0);

        createOrder(OrderStatus.COMPLETED, new BigDecimal("500000.00"), todayEarly, todayEarly);
        createOrder(OrderStatus.COMPLETED, new BigDecimal("700000.00"), todayLate, todayLate);
        createOrder(OrderStatus.PENDING, new BigDecimal("400000.00"), todayEarly, null);
        createOrder(OrderStatus.COMPLETED, new BigDecimal("300000.00"), yesterdayLate, yesterdayLate);

        entityManager.flush();

        LocalDateTime queryStart = today.minusDays(2).atStartOfDay();
        LocalDateTime queryEnd = today.plusDays(1).atStartOfDay();

        List<DailyRevenueProjection> dailyTrends = orderRepository.getDailyRevenueTrend(queryStart, queryEnd);
        assertThat(dailyTrends).isNotEmpty();

        DailyRevenueProjection todayTrend = dailyTrends.stream()
                .filter(d -> d.getTrendDate() != null && d.getTrendDate().toLocalDate().equals(today))
                .findFirst()
                .orElse(null);

        assertThat(todayTrend).isNotNull();
        assertThat(todayTrend.getTotalRevenue()).isGreaterThanOrEqualTo(new BigDecimal("1200000.00"));
        assertThat(todayTrend.getTotalOrders()).isGreaterThanOrEqualTo(2L);

        LocalDateTime yearStart = LocalDate.of(today.getYear(), 1, 1).atStartOfDay();
        LocalDateTime yearEnd = LocalDate.of(today.getYear() + 1, 1, 1).atStartOfDay();

        List<MonthlyRevenueProjection> monthlyTrends = orderRepository.getMonthlyRevenueTrend(yearStart, yearEnd);
        assertThat(monthlyTrends).isNotEmpty();

        String currentMonthKey = String.format("%04d-%02d", today.getYear(), today.getMonthValue());
        MonthlyRevenueProjection currentMonthTrend = monthlyTrends.stream()
                .filter(m -> currentMonthKey.equals(m.getTrendMonth()))
                .findFirst()
                .orElse(null);

        assertThat(currentMonthTrend).isNotNull();
        assertThat(currentMonthTrend.getTotalRevenue()).isGreaterThanOrEqualTo(new BigDecimal("1500000.00"));
    }

    @Test
    @DisplayName("Count customer segments with VIP, loyal, new, and potential tiers")
    void testCountCustomerSegments() {
        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(new Role(null, "customer")));

        User vipUser = userRepository.save(User.builder()
                .role(customerRole)
                .name("VIP Customer")
                .email("vip.seg." + System.currentTimeMillis() + "@example.com")
                .passwordHash("secret")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        Order vipOrder = Order.builder()
                .user(vipUser)
                .status(OrderStatus.COMPLETED)
                .subtotal(new BigDecimal("6000000.00"))
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("6000000.00"))
                .paymentMethod(PaymentMethod.COD)
                .receiverName(vipUser.getName())
                .receiverPhone("0911111111")
                .province("Hồ Chí Minh")
                .dataSource(DataSourceType.WEBSITE)
                .createdAt(LocalDateTime.now().minusDays(3))
                .completedAt(LocalDateTime.now().minusDays(3))
                .build();
        orderRepository.save(vipOrder);

        User newUser = userRepository.save(User.builder()
                .role(customerRole)
                .name("New Customer")
                .email("new.seg." + System.currentTimeMillis() + "@example.com")
                .passwordHash("secret")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        Order newOrder = Order.builder()
                .user(newUser)
                .status(OrderStatus.COMPLETED)
                .subtotal(new BigDecimal("500000.00"))
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("500000.00"))
                .paymentMethod(PaymentMethod.COD)
                .receiverName(newUser.getName())
                .receiverPhone("0922222222")
                .province("Hồ Chí Minh")
                .dataSource(DataSourceType.WEBSITE)
                .createdAt(LocalDateTime.now().minusDays(2))
                .completedAt(LocalDateTime.now().minusDays(2))
                .build();
        orderRepository.save(newOrder);

        userRepository.save(User.builder()
                .role(customerRole)
                .name("Potential Customer")
                .email("potential.seg." + System.currentTimeMillis() + "@example.com")
                .passwordHash("secret")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        entityManager.flush();

        CustomerSegmentProjection segments = orderRepository.countCustomerSegments();
        assertThat(segments).isNotNull();
        assertThat(segments.getVipCount()).isGreaterThanOrEqualTo(1L);
        assertThat(segments.getNewCount()).isGreaterThanOrEqualTo(1L);
        assertThat(segments.getPotentialCount()).isGreaterThanOrEqualTo(1L);
    }
}
