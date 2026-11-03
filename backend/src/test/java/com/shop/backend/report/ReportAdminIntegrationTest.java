package com.shop.backend.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.common.util.SlugUtils;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderDetail;
import com.shop.backend.order.OrderDetailRepository;
import com.shop.backend.order.OrderRepository;
import com.shop.backend.order.OrderStatus;
import com.shop.backend.payment.PaymentMethod;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ReportAdminIntegrationTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private MockMvc mockMvc;

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private EntityManager entityManager;

    private Role customerRole;
    private User testCustomer;
    private Category testCategoryA;
    private Category testCategoryB;
    private ProductVariant defaultVariant;

    @BeforeEach
    void setUp() {
        customerRole = roleRepository.findByName("customer")
                .orElseThrow(() -> new IllegalStateException("Role 'customer' not found in test DB"));

        testCustomer = userRepository.save(User.builder()
                .role(customerRole)
                .name("Report Test Customer")
                .email("rep_cust_" + System.nanoTime() + "@test.com")
                .phone("09" + String.format("%08d", Math.abs(System.nanoTime() % 100000000L)))
                .passwordHash("password_hash")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        testCategoryA = categoryRepository.save(Category.builder()
                .name("Áo Sơ Mi Nam")
                .slug("ao-so-mi-nam-" + System.nanoTime())
                .isActive(true)
                .build());

        testCategoryB = categoryRepository.save(Category.builder()
                .name("Quần Tây Nam")
                .slug("quan-tay-nam-" + System.nanoTime())
                .isActive(true)
                .build());

        Product defaultProduct = createProduct("Sản Phẩm Mặc Định", testCategoryA, new BigDecimal("100000"));
        defaultVariant = createVariant(defaultProduct, "SKU-DEFAULT-" + System.nanoTime(), new BigDecimal("100000"));
    }

    private Product createProduct(String name, Category category, BigDecimal basePrice) {
        return productRepository.save(Product.builder()
                .category(category)
                .name(name)
                .slug(SlugUtils.toSlug(name) + "-" + System.nanoTime())
                .basePrice(basePrice)
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());
    }

    private ProductVariant createVariant(Product product, String sku, BigDecimal price) {
        return createVariant(product, sku, "L", "Trắng-" + System.nanoTime(), price);
    }

    private ProductVariant createVariant(Product product, String sku, String size, String color, BigDecimal price) {
        return variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku(sku)
                .size(size)
                .color(color)
                .stock(100)
                .price(price)
                .build());
    }

    private Order createOrder(
            OrderStatus status,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal shippingFee,
            BigDecimal totalAmount,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {
        return createOrder(status, subtotal, discount, shippingFee, totalAmount, createdAt, completedAt, true);
    }

    private Order createOrder(
            OrderStatus status,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal shippingFee,
            BigDecimal totalAmount,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            boolean withDefaultDetail
    ) {
        Order order = Order.builder()
                .user(testCustomer)
                .status(status)
                .subtotal(subtotal)
                .discountAmount(discount != null ? discount : BigDecimal.ZERO)
                .shippingFee(shippingFee != null ? shippingFee : BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .paymentMethod(PaymentMethod.COD)
                .receiverName(testCustomer.getName())
                .receiverPhone(testCustomer.getPhone())
                .province("Hồ Chí Minh")
                .dataSource(DataSourceType.WEBSITE)
                .createdAt(createdAt != null ? createdAt : LocalDateTime.now())
                .completedAt(completedAt)
                .build();

        if (withDefaultDetail && subtotal != null && subtotal.compareTo(BigDecimal.ZERO) > 0) {
            OrderDetail detail = OrderDetail.builder()
                    .order(order)
                    .variant(defaultVariant)
                    .productName("Sản phẩm mặc định")
                    .variantSize("L")
                    .variantColor("Trắng")
                    .quantity(1)
                    .unitPrice(subtotal)
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            order.addOrderDetail(detail);
        }

        Order saved = orderRepository.save(order);

        if (createdAt != null || completedAt != null) {
            entityManager.flush();
            entityManager.createNativeQuery("UPDATE orders SET created_at = :createdAt, completed_at = :completedAt WHERE id = :id")
                    .setParameter("createdAt", createdAt != null ? createdAt : LocalDateTime.now())
                    .setParameter("completedAt", completedAt)
                    .setParameter("id", saved.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.clear();
            saved = orderRepository.findById(saved.getId()).orElseThrow();
        }
        return saved;
    }

    private OrderDetail createDetail(Order order, ProductVariant variant, String snapshotName, int quantity, BigDecimal unitPrice) {
        OrderDetail detail = orderDetailRepository.save(OrderDetail.builder()
                .order(order)
                .variant(variant)
                .productName(snapshotName)
                .variantSize(variant.getSize() != null ? variant.getSize() : "L")
                .variantColor(variant.getColor() != null ? variant.getColor() : "Trắng")
                .quantity(quantity)
                .unitPrice(unitPrice)
                .discountAmount(BigDecimal.ZERO)
                .build());
        entityManager.flush();
        return detail;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 1: Summary chỉ tính đơn COMPLETED")
    void summaryShouldOnlyIncludeCompletedOrders() throws Exception {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 31);
        LocalDateTime compTime = LocalDateTime.of(2026, 5, 15, 10, 0);

        createOrder(OrderStatus.COMPLETED, new BigDecimal("500000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500000"), compTime, compTime);

        mockMvc.perform(get("/api/admin/reports/summary")
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netRevenue").value(500000))
                .andExpect(jsonPath("$.grossRevenue").value(500000))
                .andExpect(jsonPath("$.completedOrderCount").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 2: Đơn PENDING, CONFIRMED, SHIPPING, CANCELLED, REFUNDED không được tính doanh thu")
    void nonCompletedStatusesShouldNotBeCountedAsRevenue() throws Exception {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 31);
        LocalDateTime compTime = LocalDateTime.of(2026, 5, 15, 10, 0);

        createOrder(OrderStatus.PENDING, new BigDecimal("300000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("300000"), compTime, null);
        createOrder(OrderStatus.CONFIRMED, new BigDecimal("200000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200000"), compTime, null);
        createOrder(OrderStatus.SHIPPING, new BigDecimal("400000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("400000"), compTime, null);
        createOrder(OrderStatus.CANCELLED, new BigDecimal("600000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("600000"), compTime, compTime);
        createOrder(OrderStatus.REFUNDED, new BigDecimal("700000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("700000"), compTime, compTime);

        mockMvc.perform(get("/api/admin/reports/summary")
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netRevenue").value(0))
                .andExpect(jsonPath("$.grossRevenue").value(0))
                .andExpect(jsonPath("$.completedOrderCount").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 3: Doanh thu dùng completed_at, không dùng created_at")
    void revenueShouldUseCompletedAtNotCreatedAt() throws Exception {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);

        LocalDateTime createdInMay = LocalDateTime.of(2026, 5, 20, 10, 0);
        LocalDateTime completedInJune = LocalDateTime.of(2026, 6, 5, 14, 0);
        createOrder(OrderStatus.COMPLETED, new BigDecimal("1000000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000000"), createdInMay, completedInJune);

        LocalDateTime createdInJune = LocalDateTime.of(2026, 6, 25, 10, 0);
        LocalDateTime completedInJuly = LocalDateTime.of(2026, 7, 2, 14, 0);
        createOrder(OrderStatus.COMPLETED, new BigDecimal("2000000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("2000000"), createdInJune, completedInJuly);

        mockMvc.perform(get("/api/admin/reports/summary")
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netRevenue").value(1000000))
                .andExpect(jsonPath("$.completedOrderCount").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 4: Đơn hoàn tất trong ngày đầu và ngày cuối được tính đúng")
    void shouldIncludeOrdersAtFirstAndLastDayOfRange() throws Exception {
        LocalDate start = LocalDate.of(2026, 7, 10);
        LocalDate end = LocalDate.of(2026, 7, 12);

        LocalDateTime firstDayMorning = LocalDateTime.of(2026, 7, 10, 0, 1);
        createOrder(OrderStatus.COMPLETED, new BigDecimal("250000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("250000"), firstDayMorning, firstDayMorning);

        LocalDateTime lastDayNight = LocalDateTime.of(2026, 7, 12, 23, 59, 58);
        createOrder(OrderStatus.COMPLETED, new BigDecimal("350000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("350000"), lastDayNight, lastDayNight);

        LocalDateTime afterLastDay = LocalDateTime.of(2026, 7, 13, 0, 0, 1);
        createOrder(OrderStatus.COMPLETED, new BigDecimal("450000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("450000"), afterLastDay, afterLastDay);

        mockMvc.perform(get("/api/admin/reports/summary")
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netRevenue").value(600000))
                .andExpect(jsonPath("$.completedOrderCount").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 5: Timestamp sát ranh giới timezone +07:00 được phân nhóm chuẩn")
    void shouldHandleTimezoneBoundaryAccurately() throws Exception {
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 1);

        LocalDateTime justInDay = LocalDateTime.of(2026, 8, 1, 0, 0, 1);
        createOrder(OrderStatus.COMPLETED, new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"), justInDay, justInDay);

        mockMvc.perform(get("/api/admin/reports/revenue")
                .param("startDate", start.toString())
                .param("endDate", end.toString())
                .param("granularity", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].period").value("2026-08-01"))
                .andExpect(jsonPath("$[0].netRevenue").value(100000))
                .andExpect(jsonPath("$[0].completedOrderCount").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 6: Khoảng ngày không có đơn trả về 0 an toàn")
    void shouldHandleEmptyDateIntervalGracefully() throws Exception {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 5);

        mockMvc.perform(get("/api/admin/reports/summary")
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossRevenue").value(0))
                .andExpect(jsonPath("$.netRevenue").value(0))
                .andExpect(jsonPath("$.totalDiscount").value(0))
                .andExpect(jsonPath("$.totalShippingFee").value(0))
                .andExpect(jsonPath("$.completedOrderCount").value(0))
                .andExpect(jsonPath("$.averageOrderValue").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 7: Chỉ có đơn hủy không làm tăng doanh thu")
    void onlyCancelledOrdersShouldResultInZeroRevenue() throws Exception {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 5);
        LocalDateTime time = LocalDateTime.of(2026, 9, 2, 10, 0);

        createOrder(OrderStatus.CANCELLED, new BigDecimal("800000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("800000"), time, null);

        mockMvc.perform(get("/api/admin/reports/summary")
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netRevenue").value(0))
                .andExpect(jsonPath("$.completedOrderCount").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 8: Gross, Net, Discount, Shipping Fee tính đúng các thành phần")
    void shouldCalculateGrossNetDiscountAndShippingFeeAccurately() throws Exception {
        LocalDate start = LocalDate.of(2026, 10, 10);
        LocalDate end = LocalDate.of(2026, 10, 10);
        LocalDateTime time = LocalDateTime.of(2026, 10, 10, 11, 0);

        BigDecimal subtotal = new BigDecimal("1000000");
        BigDecimal discount = new BigDecimal("100000");
        BigDecimal shipping = new BigDecimal("30000");
        BigDecimal net = new BigDecimal("930000");
        createOrder(OrderStatus.COMPLETED, subtotal, discount, shipping, net, time, time);

        mockMvc.perform(get("/api/admin/reports/summary")
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossRevenue").value(1000000))
                .andExpect(jsonPath("$.totalDiscount").value(100000))
                .andExpect(jsonPath("$.totalShippingFee").value(30000))
                .andExpect(jsonPath("$.netRevenue").value(930000))
                .andExpect(jsonPath("$.completedOrderCount").value(1))
                .andExpect(jsonPath("$.averageOrderValue").value(930000));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 9: Average Order Value xử lý mẫu số bằng 0 an toàn không lỗi chia 0")
    void averageOrderValueShouldBeZeroWhenNoCompletedOrders() throws Exception {
        LocalDate start = LocalDate.of(2024, 2, 1);
        LocalDate end = LocalDate.of(2024, 2, 2);

        mockMvc.perform(get("/api/admin/reports/summary")
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageOrderValue").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 10: Revenue trend grouping day, week, month hoạt động đầy đủ")
    void revenueTrendShouldSupportDayWeekMonthGranularity() throws Exception {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 7);
        LocalDateTime compTime = LocalDateTime.of(2026, 10, 2, 12, 0);
        createOrder(OrderStatus.COMPLETED, new BigDecimal("500000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500000"), compTime, compTime);

        mockMvc.perform(get("/api/admin/reports/revenue")
                .param("startDate", start.toString())
                .param("endDate", end.toString())
                .param("granularity", "day"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[1].period").value("2026-10-02"))
                .andExpect(jsonPath("$[1].netRevenue").value(500000));

        mockMvc.perform(get("/api/admin/reports/revenue")
                .param("startDate", start.toString())
                .param("endDate", end.toString())
                .param("granularity", "week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].period").isNotEmpty())
                .andExpect(jsonPath("$[0].netRevenue").isNumber());

        mockMvc.perform(get("/api/admin/reports/revenue")
                .param("startDate", start.toString())
                .param("endDate", end.toString())
                .param("granularity", "month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].period").value("2026-10"))
                .andExpect(jsonPath("$[0].netRevenue").value(500000));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 11: Top products dùng snapshot order_details, không dùng giá products hiện tại")
    void topProductsShouldUseSnapshotPriceNotCurrentProductPrice() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 11, 5);
        LocalDateTime time = LocalDateTime.of(2026, 11, 2, 10, 0);

        Product prod = createProduct("Áo Polo Snapshot Test", testCategoryA, new BigDecimal("999000"));
        ProductVariant var = createVariant(prod, "POLO-SNAP-1", new BigDecimal("999000"));

        Order order = createOrder(OrderStatus.COMPLETED, new BigDecimal("300000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("300000"), time, time, false);
        createDetail(order, var, "Áo Polo Snapshot Test", 2, new BigDecimal("150000"));

        mockMvc.perform(get("/api/admin/reports/top-products")
                .param("startDate", start.toString())
                .param("endDate", end.toString())
                .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName").value("Áo Polo Snapshot Test"))
                .andExpect(jsonPath("$[0].totalQuantitySold").value(2))
                .andExpect(jsonPath("$[0].totalRevenue").value(300000));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 12: Top products sắp xếp theo số lượng bán và giới hạn limit đúng")
    void topProductsShouldSortByQuantityDescAndRespectLimit() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 10);
        LocalDate end = LocalDate.of(2026, 11, 15);
        LocalDateTime time = LocalDateTime.of(2026, 11, 11, 10, 0);

        Product prod1 = createProduct("Top Prod 1", testCategoryA, new BigDecimal("100000"));
        ProductVariant var1 = createVariant(prod1, "SKU-TOP-1", new BigDecimal("100000"));

        Product prod2 = createProduct("Top Prod 2", testCategoryB, new BigDecimal("100000"));
        ProductVariant var2 = createVariant(prod2, "SKU-TOP-2", new BigDecimal("100000"));

        Order order = createOrder(OrderStatus.COMPLETED, new BigDecimal("700000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("700000"), time, time, false);
        createDetail(order, var1, "Top Prod 1", 2, new BigDecimal("100000"));
        createDetail(order, var2, "Top Prod 2", 5, new BigDecimal("100000"));

        mockMvc.perform(get("/api/admin/reports/top-products")
                .param("startDate", start.toString())
                .param("endDate", end.toString())
                .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Top Prod 2"))
                .andExpect(jsonPath("$[0].totalQuantitySold").value(5));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 13: Category sales không nhân đôi dữ liệu do JOIN")
    void categorySalesShouldNotDuplicateDataOnJoin() throws Exception {
        LocalDate start = LocalDate.of(2026, 12, 1);
        LocalDate end = LocalDate.of(2026, 12, 5);
        LocalDateTime time = LocalDateTime.of(2026, 12, 2, 10, 0);

        Product prodA = createProduct("Cat Test A", testCategoryA, new BigDecimal("200000"));
        ProductVariant varA1 = createVariant(prodA, "CAT-A1", "M", "Trắng", new BigDecimal("200000"));
        ProductVariant varA2 = createVariant(prodA, "CAT-A2", "L", "Đen", new BigDecimal("200000"));

        Order order = createOrder(OrderStatus.COMPLETED, new BigDecimal("400000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("400000"), time, time, false);
        createDetail(order, varA1, "Cat Test A (White)", 1, new BigDecimal("200000"));
        createDetail(order, varA2, "Cat Test A (Black)", 1, new BigDecimal("200000"));

        mockMvc.perform(get("/api/admin/reports/category-sales")
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value(testCategoryA.getName()))
                .andExpect(jsonPath("$[0].totalQuantitySold").value(2))
                .andExpect(jsonPath("$[0].totalRevenue").value(400000))
                .andExpect(jsonPath("$[0].percentage").value(100.0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 14: Order status distribution dùng created_at, không phụ thuộc completed_at")
    void orderStatusDistributionShouldUseCreatedAt() throws Exception {
        LocalDate start = LocalDate.of(2026, 12, 20);
        LocalDate end = LocalDate.of(2026, 12, 22);

        LocalDateTime created1 = LocalDateTime.of(2026, 12, 21, 9, 0);
        LocalDateTime created2 = LocalDateTime.of(2026, 12, 21, 11, 0);
        LocalDateTime created3 = LocalDateTime.of(2026, 12, 21, 14, 0);

        createOrder(OrderStatus.PENDING, new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"), created1, null);
        createOrder(OrderStatus.CONFIRMED, new BigDecimal("200000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("200000"), created2, null);
        createOrder(OrderStatus.CANCELLED, new BigDecimal("300000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("300000"), created3, null);

        mockMvc.perform(get("/api/admin/reports/order-status-distribution")
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[?(@.status == 'PENDING')].orderCount").value(Matchers.contains(1)))
                .andExpect(jsonPath("$[?(@.status == 'CONFIRMED')].orderCount").value(Matchers.contains(1)))
                .andExpect(jsonPath("$[?(@.status == 'CANCELLED')].orderCount").value(Matchers.contains(1)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 15: Percentage tổng hợp đúng và xử lý tổng đơn bằng 0 an toàn")
    void percentageShouldCalculateCorrectlyAndHandleZeroOrdersSafely() throws Exception {
        LocalDate start = LocalDate.of(2023, 1, 1);
        LocalDate end = LocalDate.of(2023, 1, 5);

        mockMvc.perform(get("/api/admin/reports/order-status-distribution")
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].percentage").value(0.0))
                .andExpect(jsonPath("$[0].orderCount").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 16: Validation ngày bắt đầu sau ngày kết thúc, khoảng ngày > 366 ngày, granularity sai, limit sai")
    void shouldValidateInputParametersProperly() throws Exception {
        mockMvc.perform(get("/api/admin/reports/summary")
                .param("startDate", "2026-10-10")
                .param("endDate", "2026-10-01"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/reports/summary")
                .param("startDate", "2025-01-01")
                .param("endDate", "2026-02-15"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/reports/revenue")
                .param("startDate", "2026-10-01")
                .param("endDate", "2026-10-10")
                .param("granularity", "invalid_granularity"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/reports/top-products")
                .param("startDate", "2026-10-01")
                .param("endDate", "2026-10-10")
                .param("limit", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/reports/top-products")
                .param("startDate", "2026-10-01")
                .param("endDate", "2026-10-10")
                .param("limit", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Test 17: Phân quyền - ADMIN và STAFF được phép truy cập")
    void shouldAllowAdminAndStaffAccess() throws Exception {
        mockMvc.perform(get("/api/admin/reports/summary")
                .param("startDate", "2026-10-01")
                .param("endDate", "2026-10-10")
                .with(SecurityMockMvcRequestPostProcessors.user("admin_user").roles("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/reports/summary")
                .param("startDate", "2026-10-01")
                .param("endDate", "2026-10-10")
                .with(SecurityMockMvcRequestPostProcessors.user("staff_user").roles("STAFF")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Test 18: Phân quyền - CUSTOMER hoặc unauthenticated không được truy cập (401, 403)")
    void shouldDenyCustomerAndUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/admin/reports/summary")
                .param("startDate", "2026-10-01")
                .param("endDate", "2026-10-10"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/reports/summary")
                .param("startDate", "2026-10-01")
                .param("endDate", "2026-10-10")
                .with(SecurityMockMvcRequestPostProcessors.user("customer_user").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 19: Gross Revenue lấy từ order_details, Net Revenue lấy từ orders.total_amount, không nhân đôi discount/shipping fee")
    void shouldVerifyGrossFromOrderDetailsAndNetFromTotalAmountWithoutDuplication() throws Exception {
        LocalDate start = LocalDate.of(2026, 12, 10);
        LocalDate end = LocalDate.of(2026, 12, 10);
        LocalDateTime compTime = LocalDateTime.of(2026, 12, 10, 15, 0);

        Product prod = createProduct("Financial Audit Prod", testCategoryA, new BigDecimal("200000"));
        ProductVariant var1 = createVariant(prod, "FIN-1", "M", "Xanh", new BigDecimal("200000"));
        ProductVariant var2 = createVariant(prod, "FIN-2", "L", "Đỏ", new BigDecimal("350000"));

        Order order = createOrder(OrderStatus.COMPLETED, new BigDecimal("1000000"), new BigDecimal("100000"), new BigDecimal("30000"), new BigDecimal("930000"), compTime, compTime, false);

        createDetail(order, var1, "Financial Audit Prod Item 1", 2, new BigDecimal("200000"));
        createDetail(order, var2, "Financial Audit Prod Item 2", 1, new BigDecimal("350000"));

        mockMvc.perform(get("/api/admin/reports/summary")
                .param("startDate", start.toString())
                .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossRevenue").value(750000))
                .andExpect(jsonPath("$.netRevenue").value(930000))
                .andExpect(jsonPath("$.totalDiscount").value(100000))
                .andExpect(jsonPath("$.totalShippingFee").value(30000))
                .andExpect(jsonPath("$.completedOrderCount").value(1))
                .andExpect(jsonPath("$.averageOrderValue").value(930000));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Test 20: Revenue trend tuần ISO-8601 giao giữa hai năm gom nhóm đúng vào 2025-W01")
    void shouldHandleIsoWeekAcrossYearBoundaryCorrectly() throws Exception {
        LocalDate start = LocalDate.of(2024, 12, 28);
        LocalDate end = LocalDate.of(2025, 1, 5);

        LocalDateTime time2024 = LocalDateTime.of(2024, 12, 31, 10, 0);
        createOrder(OrderStatus.COMPLETED, new BigDecimal("400000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("400000"), time2024, time2024);

        LocalDateTime time2025 = LocalDateTime.of(2025, 1, 2, 14, 0);
        createOrder(OrderStatus.COMPLETED, new BigDecimal("600000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("600000"), time2025, time2025);

        mockMvc.perform(get("/api/admin/reports/revenue")
                .param("startDate", start.toString())
                .param("endDate", end.toString())
                .param("granularity", "week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.period == '2025-W01')].netRevenue").value(org.hamcrest.Matchers.contains(1000000)))
                .andExpect(jsonPath("$[?(@.period == '2025-W01')].grossRevenue").value(org.hamcrest.Matchers.contains(1000000)))
                .andExpect(jsonPath("$[?(@.period == '2025-W01')].completedOrderCount").value(org.hamcrest.Matchers.contains(2)));
    }
}
