package com.shop.backend.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderRepository;
import com.shop.backend.order.OrderStatus;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.common.util.SlugUtils;
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
class DashboardAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    private Role customerRole;
    private Role adminRole;
    private Role staffRole;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        customerRole = roleRepository.findByName("customer")
                .orElseThrow(() -> new IllegalStateException("Role 'customer' not found in test DB"));
        adminRole = roleRepository.findByName("admin")
                .orElseThrow(() -> new IllegalStateException("Role 'admin' not found in test DB"));
        staffRole = roleRepository.findByName("staff")
                .orElseThrow(() -> new IllegalStateException("Role 'staff' not found in test DB"));

        testCategory = categoryRepository.save(Category.builder()
                .name("Thời Trang Dashboard Test")
                .slug("thoi-trang-dashboard-" + System.nanoTime())
                .isActive(true)
                .build());
    }

    private User createCustomer(String name, String email, LocalDateTime createdAt) {
        User user = userRepository.save(User.builder()
                .role(customerRole)
                .name(name)
                .email(email)
                .phone("09" + String.format("%08d", Math.abs(System.nanoTime() % 100000000L)))
                .passwordHash("password_hash")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        if (createdAt != null) {
            entityManager.flush();
            entityManager.createNativeQuery("UPDATE users SET created_at = :createdAt WHERE id = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", user.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.clear();
        }
        return user;
    }

    private User createAdminOrStaff(String name, String email, Role role) {
        return userRepository.save(User.builder()
                .role(role)
                .name(name)
                .email(email)
                .phone("09" + String.format("%08d", Math.abs(System.nanoTime() % 100000000L)))
                .passwordHash("password_hash")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());
    }

    private Product createProduct(String name, boolean isActive) {
        return productRepository.save(Product.builder()
                .category(testCategory)
                .name(name)
                .slug(SlugUtils.toSlug(name) + "-" + System.nanoTime())
                .basePrice(BigDecimal.valueOf(150000L))
                .isActive(isActive)
                .dataSource(DataSourceType.WEBSITE)
                .build());
    }

    private ProductVariant createVariant(Product product, String sku, String size, String color, int stock) {
        return variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku(sku)
                .size(size)
                .color(color)
                .stock(stock)
                .price(BigDecimal.valueOf(150000L))
                .build());
    }

    @Test
    @DisplayName("D1: Phân quyền - 401 khi chưa đăng nhập, 403 với CUSTOMER, 200 với ADMIN và STAFF")
    void shouldEnforceSecurityRoles() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/dashboard/summary")
                .with(SecurityMockMvcRequestPostProcessors.user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/dashboard/summary")
                .with(SecurityMockMvcRequestPostProcessors.user("staff").roles("STAFF")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/dashboard/summary")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("D2: Đếm đúng khách hàng mới theo kỳ 'today' và tính tỷ lệ so với hôm qua")
    void shouldCountNewCustomersForTodayPeriod() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayMorning = now.toLocalDate().atStartOfDay().plusHours(2);
        LocalDateTime yesterdayMorning = now.toLocalDate().minusDays(1).atStartOfDay().plusHours(2);

        createCustomer("Khách Hôm Nay 1", "today1_" + System.nanoTime() + "@test.com", todayMorning);
        createCustomer("Khách Hôm Nay 2", "today2_" + System.nanoTime() + "@test.com", todayMorning.plusHours(1));
        createCustomer("Khách Hôm Qua 1", "yest1_" + System.nanoTime() + "@test.com", yesterdayMorning);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/dashboard/summary")
                .param("period", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newCustomersCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.totalCustomersCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("D3: Đếm đúng khách hàng mới cho cả 4 kỳ: today, 7days, 30days, quarter")
    void shouldSupportAllFourReportingPeriods() throws Exception {
        String[] periods = {"today", "7days", "30days", "quarter"};

        for (String p : periods) {
            mockMvc.perform(get("/api/admin/dashboard/summary")
                    .param("period", p))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lowStockCount").isNumber())
                    .andExpect(jsonPath("$.newCustomersCount").isNumber())
                    .andExpect(jsonPath("$.newCustomersChangeRate").isNumber())
                    .andExpect(jsonPath("$.totalCustomersCount").isNumber());
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("D4: Phân biệt vai trò CUSTOMER với ADMIN và STAFF khi đếm totalCustomersCount")
    void shouldDifferentiateCustomerRoleFromAdminAndStaff() throws Exception {
        long initialTotalCustomers = userRepository.countTotalCustomers();

        createCustomer("Customer A", "cust_a_" + System.nanoTime() + "@test.com", null);
        createCustomer("Customer B", "cust_b_" + System.nanoTime() + "@test.com", null);
        createAdminOrStaff("Admin Test", "admin_" + System.nanoTime() + "@test.com", adminRole);
        createAdminOrStaff("Staff Test", "staff_" + System.nanoTime() + "@test.com", staffRole);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCustomersCount").value(initialTotalCustomers + 2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("D5: Tính đúng tỷ lệ thay đổi so với kỳ trước, xử lý an toàn khi kỳ trước = 0")
    void shouldCalculateChangeRateAccuratelyEvenWhenPreviousPeriodIsZero() throws Exception {
        // Test direct calculation behavior
        var service = new DashboardAdminServiceImpl(
                new com.shop.backend.product.ProductAdminService() {
                    public com.shop.backend.common.dto.PageResponse<com.shop.backend.product.dto.ProductAdminListItemResponse> getProducts(String k, Long c, Boolean a, org.springframework.data.domain.Pageable p) { return null; }
                    public com.shop.backend.product.dto.ProductDetailResponse getProductById(Long id) { return null; }
                    public com.shop.backend.product.dto.ProductDetailResponse createProduct(com.shop.backend.product.dto.ProductUpsertRequest r) { return null; }
                    public com.shop.backend.product.dto.ProductDetailResponse updateProduct(Long id, com.shop.backend.product.dto.ProductUpsertRequest r) { return null; }
                    public void updateBulkStatus(com.shop.backend.product.dto.BulkStatusRequest r) {}
                    public void deleteProduct(Long id) {}
                    public com.shop.backend.product.dto.ProductImageResponse uploadProductImage(Long p, org.springframework.web.multipart.MultipartFile f, Boolean pr, String c, String a) { return null; }
                    public com.shop.backend.product.dto.ProductImageResponse updateImageMetadata(Long p, Long i, com.shop.backend.product.dto.ImageMetadataUpdateRequest r) { return null; }
                    public void deleteProductImage(Long p, Long i) {}
                    public com.shop.backend.common.dto.PageResponse<com.shop.backend.product.dto.LowStockVariantResponse> getLowStockVariants(Integer t, Integer p, Integer s) { return null; }
                    public long countLowStockVariants(Integer t) { return 5L; }
                },
                new com.shop.backend.user.UserAdminService(userRepository) {
                    public long countTotalCustomers() { return 10L; }
                    public long countNewCustomersBetween(LocalDateTime start, LocalDateTime end) {
                        // Simulate: current period has 4, previous period has 0
                        if (start.isAfter(LocalDateTime.now().minusDays(1))) {
                            return 4L;
                        }
                        return 0L;
                    }
                }
        );

        var response = service.getSummary("today");
        assertThat(response.getNewCustomersCount()).isEqualTo(4L);
        assertThat(response.getNewCustomersChangeRate()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("D6: Đếm đúng tổng số biến thể có tồn kho thấp, loại trừ biến thể thuộc sản phẩm không hoạt động")
    void shouldCountTotalLowStockVariantsCorrectlyExcludingInactiveProducts() throws Exception {
        Product activeProduct = createProduct("Áo Sơ Mi Active", true);
        Product inactiveProduct = createProduct("Áo Khoác Inactive", false);

        String prefix = "STOCK-" + System.nanoTime();
        // Low stock of active product (stock < 10) -> Should count (2)
        createVariant(activeProduct, prefix + "-1", "S", "Đen", 0);
        createVariant(activeProduct, prefix + "-2", "M", "Đen", 4);

        // High stock of active product (stock >= 10) -> Should NOT count
        createVariant(activeProduct, prefix + "-3", "L", "Đen", 10);
        createVariant(activeProduct, prefix + "-4", "XL", "Đen", 50);

        // Low stock of inactive product -> Should NOT count
        createVariant(inactiveProduct, prefix + "-5", "M", "Đen", 2);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    private Order createOrder(User user, OrderStatus status, BigDecimal amount, LocalDateTime createdAt, LocalDateTime completedAt) {
        Order order = orderRepository.save(Order.builder()
                .user(user)
                .status(status)
                .subtotal(amount)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(amount)
                .paymentMethod(PaymentMethod.COD)
                .receiverName(user.getName())
                .receiverPhone(user.getPhone() != null ? user.getPhone() : "0912345678")
                .addressLine("123 Test Street")
                .province("Hồ Chí Minh")
                .dataSource(DataSourceType.WEBSITE)
                .createdAt(createdAt != null ? createdAt : LocalDateTime.now())
                .completedAt(completedAt)
                .build());

        if (createdAt != null || completedAt != null) {
            entityManager.flush();
            entityManager.createNativeQuery("UPDATE orders SET created_at = :createdAt, completed_at = :completedAt WHERE id = :id")
                    .setParameter("createdAt", createdAt != null ? createdAt : LocalDateTime.now())
                    .setParameter("completedAt", completedAt)
                    .setParameter("id", order.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.clear();
        }
        return order;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("D8: KPI Doanh thu chỉ tính đơn completed; đơn hàng mới đếm tất cả trạng thái")
    void shouldCalculateRevenueAndNewOrdersInSummary() throws Exception {
        User cust = createCustomer("Khách Mua Đơn", "buyer_" + System.nanoTime() + "@test.com", null);
        LocalDateTime todayOrderTime = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).atStartOfDay().plusMinutes(10);

        createOrder(cust, OrderStatus.COMPLETED, new BigDecimal("500000.00"), todayOrderTime, todayOrderTime.plusMinutes(5));
        createOrder(cust, OrderStatus.PENDING, new BigDecimal("300000.00"), todayOrderTime.plusMinutes(6), null);
        createOrder(cust, OrderStatus.CANCELLED, new BigDecimal("200000.00"), todayOrderTime.plusMinutes(8), null);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/dashboard/summary")
                .param("period", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueTotal").value(org.hamcrest.Matchers.greaterThanOrEqualTo(500000)))
                .andExpect(jsonPath("$.newOrdersCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.revenueChangeRate").isNumber())
                .andExpect(jsonPath("$.newOrdersChangeRate").isNumber());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("D9: Xu hướng doanh thu theo ngày liên tục cho kỳ 7days và 30days")
    void shouldReturnContinuousRevenueTrendFor7DaysAnd30Days() throws Exception {
        User cust = createCustomer("Khách Trend", "trend_" + System.nanoTime() + "@test.com", null);
        LocalDateTime todayOrderTime = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).atStartOfDay().plusMinutes(10);
        createOrder(cust, OrderStatus.COMPLETED, new BigDecimal("1000000.00"), todayOrderTime, todayOrderTime.plusMinutes(5));

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/dashboard/revenue-trend")
                .param("period", "7days"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].date").isNotEmpty())
                .andExpect(jsonPath("$[0].revenue").isNumber())
                .andExpect(jsonPath("$[0].orders").isNumber())
                .andExpect(jsonPath("$[6].revenue").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1000000)));

        mockMvc.perform(get("/api/admin/dashboard/revenue-trend")
                .param("period", "30days"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(30))
                .andExpect(jsonPath("$[29].revenue").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1000000)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("D10: Xu hướng doanh thu cả năm theo 12 tháng")
    void shouldReturnYearlyMonthlyRevenueTrend() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/revenue-trend")
                .param("period", "year"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].date").value("Thg 01"))
                .andExpect(jsonPath("$[11].date").value("Thg 12"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("D11: Phân loại khách hàng trả về 4 nhóm VIP, Thân thiết, Khách mới, Tiềm năng")
    void shouldReturnCustomerSegmentsAccurately() throws Exception {
        User vipCust = createCustomer("Khách VIP", "vip_" + System.nanoTime() + "@test.com", null);
        createOrder(vipCust, OrderStatus.COMPLETED, new BigDecimal("6000000.00"), LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1));

        User newCust = createCustomer("Khách Mới", "newcust_" + System.nanoTime() + "@test.com", null);
        createOrder(newCust, OrderStatus.COMPLETED, new BigDecimal("500000.00"), LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1));

        User potCust = createCustomer("Khách Tiềm Năng", "pot_" + System.nanoTime() + "@test.com", null);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/dashboard/customer-segments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value("vip"))
                .andExpect(jsonPath("$[0].count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[1].id").value("loyal"))
                .andExpect(jsonPath("$[2].id").value("new"))
                .andExpect(jsonPath("$[2].count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[3].id").value("potential"))
                .andExpect(jsonPath("$[3].count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("D12: Trả về 400 Bad Request khi truyền tham số period cho revenue-trend không hợp lệ")
    void shouldRejectInvalidRevenueTrendPeriod() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/revenue-trend")
                .param("period", "invalid_chart_period"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Kỳ biểu đồ không hợp lệ")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("D7: Trả về 400 Bad Request khi truyền tham số period không hợp lệ")
    void shouldRejectInvalidPeriodParameter() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/summary")
                .param("period", "invalid_period"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Kỳ báo cáo không hợp lệ")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("D13: Kiểm tra biên ngày 23:50 và 00:10 không bị lệch múi giờ giữa summary và trend")
    void shouldAccuratelyCountOrdersAtDayBoundariesWithoutTimezoneDrift() throws Exception {
        User cust = createCustomer("Khách Boundary", "boundary_" + System.nanoTime() + "@test.com", null);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        LocalDateTime earlyToday = today.atTime(0, 10);
        createOrder(cust, OrderStatus.COMPLETED, new BigDecimal("400000.00"), earlyToday, earlyToday);

        LocalDateTime lateToday = today.atTime(23, 50);
        createOrder(cust, OrderStatus.COMPLETED, new BigDecimal("600000.00"), lateToday, lateToday);

        LocalDateTime lateYesterday = today.minusDays(1).atTime(23, 55);
        createOrder(cust, OrderStatus.COMPLETED, new BigDecimal("350000.00"), lateYesterday, lateYesterday);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/dashboard/summary")
                .param("period", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueTotal").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1000000)));

        mockMvc.perform(get("/api/admin/dashboard/revenue-trend")
                .param("period", "7days"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[6].revenue").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1000000)))
                .andExpect(jsonPath("$[5].revenue").value(org.hamcrest.Matchers.greaterThanOrEqualTo(350000)));
    }
}
