package com.shop.backend.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.annotation.Propagation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderDetail;
import com.shop.backend.order.OrderDetailRepository;
import com.shop.backend.order.OrderRepository;
import com.shop.backend.order.OrderStatus;
import com.shop.backend.payment.Payment;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.payment.PaymentRepository;
import com.shop.backend.payment.PaymentStatus;
import com.shop.backend.product.Category;
import com.shop.backend.product.CategoryRepository;
import com.shop.backend.product.Product;
import com.shop.backend.product.ProductRepository;
import com.shop.backend.product.ProductVariant;
import com.shop.backend.product.ProductVariantRepository;
import com.shop.backend.shipping.dto.ShippingStatusUpdateRequest;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ShippingAdminIntegrationTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private User testCustomer;
    private ProductVariant testVariant1;
    private ProductVariant testVariant2;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(new Role(null, "customer")));
        roleRepository.findByName("admin")
                .orElseGet(() -> roleRepository.save(new Role(null, "admin")));
        roleRepository.findByName("staff")
                .orElseGet(() -> roleRepository.save(new Role(null, "staff")));

        testCustomer = userRepository.save(User.builder()
                .role(customerRole)
                .name("Nguyễn Văn Khách")
                .email("shipping.test." + System.nanoTime() + "@example.com")
                .phone("091" + String.format("%07d", Math.abs(System.nanoTime() % 10000000L)))
                .passwordHash("hashed_password")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Thời Trang Nam Shipping")
                .slug("thoi-trang-nam-shipping-" + System.nanoTime())
                .isActive(true)
                .build());

        Product product = productRepository.save(Product.builder()
                .category(category)
                .name("Áo Thun Cotton Compact")
                .slug("ao-thun-cotton-compact-" + System.nanoTime())
                .basePrice(new BigDecimal("250000.00"))
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        testVariant1 = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("COTTON-WHITE-L-" + System.nanoTime())
                .size("L")
                .color("Trắng")
                .stock(100)
                .price(new BigDecimal("250000.00"))
                .build());

        testVariant2 = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("COTTON-BLACK-XL-" + System.nanoTime())
                .size("XL")
                .color("Đen")
                .stock(100)
                .price(new BigDecimal("270000.00"))
                .build());
    }

    private Order createTestOrder(
            String receiverName,
            String receiverPhone,
            String province,
            OrderStatus status,
            PaymentMethod paymentMethod,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {
        Order order = Order.builder()
                .user(testCustomer)
                .status(status)
                .subtotal(new BigDecimal("520000.00"))
                .discountAmount(new BigDecimal("20000.00"))
                .shippingFee(new BigDecimal("30000.00"))
                .totalAmount(new BigDecimal("530000.00"))
                .paymentMethod(paymentMethod)
                .receiverName(receiverName)
                .receiverPhone(receiverPhone)
                .addressLine("456 Đường Lê Lợi")
                .ward("Phường Bến Thành")
                .district("Quận 1")
                .province(province)
                .note("Gọi trước khi giao hàng")
                .salesChannel("website")
                .dataSource(DataSourceType.WEBSITE)
                .createdAt(createdAt != null ? createdAt : LocalDateTime.now())
                .completedAt(completedAt)
                .build();

        OrderDetail item1 = OrderDetail.builder()
                .variant(testVariant1)
                .productName("Áo Thun Cotton Compact")
                .variantSize("L")
                .variantColor("Trắng")
                .quantity(1)
                .unitPrice(new BigDecimal("250000.00"))
                .discountAmount(BigDecimal.ZERO)
                .build();
        order.addOrderDetail(item1);

        OrderDetail item2 = OrderDetail.builder()
                .variant(testVariant2)
                .productName("Áo Thun Cotton Compact")
                .variantSize("XL")
                .variantColor("Đen")
                .quantity(1)
                .unitPrice(new BigDecimal("270000.00"))
                .discountAmount(new BigDecimal("20000.00"))
                .build();
        order.addOrderDetail(item2);

        Payment payment = Payment.builder()
                .method(paymentMethod)
                .amount(new BigDecimal("530000.00"))
                .status(paymentMethod == PaymentMethod.COD ? PaymentStatus.PENDING : PaymentStatus.SUCCESS)
                .build();
        order.addPayment(payment);

        Order saved = orderRepository.save(order);

        if (createdAt != null) {
            entityManager.flush();
            entityManager.createNativeQuery("UPDATE orders SET created_at = :createdAt WHERE id = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", saved.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.clear();
            saved = orderRepository.findById(saved.getId()).orElseThrow();
        }

        return saved;
    }

    // ==========================================
    // 1. BẢO MẬT & PHÂN QUYỀN
    // ==========================================

    @Test
    @DisplayName("1. Bảo mật: Chưa xác thực trả về 401 Unauthorized trên toàn bộ endpoints")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/shipping/summary"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/shipping"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/shipping/1"))
                .andExpect(status().isUnauthorized());

        ShippingStatusUpdateRequest req = new ShippingStatusUpdateRequest(OrderStatus.SHIPPING);
        mockMvc.perform(patch("/api/admin/shipping/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("2. Bảo mật: Khách hàng (CUSTOMER) bị từ chối 403 Forbidden")
    void shouldReturn403WhenCustomerAccessesShipping() throws Exception {
        mockMvc.perform(get("/api/admin/shipping/summary"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/shipping"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/shipping/1"))
                .andExpect(status().isForbidden());

        ShippingStatusUpdateRequest req = new ShippingStatusUpdateRequest(OrderStatus.SHIPPING);
        mockMvc.perform(patch("/api/admin/shipping/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("3. Phân quyền: Nhân viên (STAFF) có quyền truy cập Shipping")
    void shouldAllowStaffAccess() throws Exception {
        mockMvc.perform(get("/api/admin/shipping/summary"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/shipping"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("4. Phân quyền: Quản trị viên (ADMIN) có quyền truy cập Shipping")
    void shouldAllowAdminAccess() throws Exception {
        mockMvc.perform(get("/api/admin/shipping/summary"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/shipping"))
                .andExpect(status().isOk());
    }

    // ==========================================
    // 2. TỔNG QUAN VẬN CHUYỂN (SUMMARY)
    // ==========================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("5. Summary: Đếm chính xác readyToShip, delivering và deliveredToday theo múi giờ Việt Nam")
    void shouldReturnAccurateShippingSummary() throws Exception {
        LocalDate todayVn = LocalDate.now(VIETNAM_ZONE);
        LocalDateTime todayMorning = todayVn.atTime(9, 30);
        LocalDateTime yesterday = todayVn.minusDays(1).atTime(15, 0);

        // Đơn CONFIRMED (readyToShip)
        createTestOrder("Khách Chờ Giao 1", "0901000001", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, todayMorning, null);
        createTestOrder("Khách Chờ Giao 2", "0901000002", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, todayMorning, null);

        // Đơn SHIPPING (delivering)
        createTestOrder("Khách Đang Giao", "0902000001", "Hồ Chí Minh", OrderStatus.SHIPPING, PaymentMethod.COD, todayMorning, null);

        // Đơn COMPLETED hôm nay
        createTestOrder("Khách Đã Giao Hôm Nay", "0903000001", "Đà Nẵng", OrderStatus.COMPLETED, PaymentMethod.COD, todayMorning, todayMorning);

        // Đơn COMPLETED hôm qua (không được tính vào deliveredToday)
        createTestOrder("Khách Đã Giao Hôm Qua", "0903000002", "Đà Nẵng", OrderStatus.COMPLETED, PaymentMethod.COD, yesterday, yesterday);

        // Đơn PENDING và CANCELLED (không làm sai lệch KPI)
        createTestOrder("Khách Chờ Duyệt", "0904000001", "Hà Nội", OrderStatus.PENDING, PaymentMethod.COD, todayMorning, null);
        createTestOrder("Khách Hủy", "0905000001", "Hải Phòng", OrderStatus.CANCELLED, PaymentMethod.COD, todayMorning, null);

        mockMvc.perform(get("/api/admin/shipping/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyToShipCount").value(2))
                .andExpect(jsonPath("$.deliveringCount").value(1))
                .andExpect(jsonPath("$.deliveredTodayCount").value(1));
    }

    // ==========================================
    // 3. DANH SÁCH ĐƠN HÀNG VẬN CHUYỂN
    // ==========================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("6. Danh sách: Mặc định loại trừ hoàn toàn đơn PENDING và REFUNDED")
    void shouldExcludePendingAndRefundedFromDefaultList() throws Exception {
        Order confirmed = createTestOrder("Khách Đã Xác Nhận", "0911000001", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, null, null);
        Order shipping = createTestOrder("Khách Đang Vận Chuyển", "0911000002", "Hà Nội", OrderStatus.SHIPPING, PaymentMethod.COD, null, null);
        Order completed = createTestOrder("Khách Hoàn Thành", "0911000003", "Hồ Chí Minh", OrderStatus.COMPLETED, PaymentMethod.COD, null, LocalDateTime.now());
        Order cancelled = createTestOrder("Khách Hủy Đơn", "0911000004", "Đà Nẵng", OrderStatus.CANCELLED, PaymentMethod.COD, null, null);

        Order pending = createTestOrder("Khách Chờ Duyệt", "0911000005", "Hà Nội", OrderStatus.PENDING, PaymentMethod.COD, null, null);
        Order refunded = createTestOrder("Khách Đã Hoàn Tiền", "0911000006", "Hải Phòng", OrderStatus.REFUNDED, PaymentMethod.COD, null, LocalDateTime.now());

        mockMvc.perform(get("/api/admin/shipping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[?(@.orderId == " + confirmed.getId() + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.orderId == " + shipping.getId() + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.orderId == " + completed.getId() + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.orderId == " + cancelled.getId() + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.orderId == " + pending.getId() + ")]").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.orderId == " + refunded.getId() + ")]").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("7. Danh sách: Lọc theo status hợp lệ (CONFIRMED, SHIPPING, COMPLETED, CANCELLED)")
    void shouldFilterByValidShippingStatus() throws Exception {
        createTestOrder("Khách Chờ Giao", "0912000001", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, null, null);
        createTestOrder("Khách Đang Giao", "0912000002", "Hồ Chí Minh", OrderStatus.SHIPPING, PaymentMethod.COD, null, null);

        mockMvc.perform(get("/api/admin/shipping").param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));

        mockMvc.perform(get("/api/admin/shipping").param("status", "SHIPPING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("SHIPPING"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("8. Danh sách: Lọc status PENDING hoặc REFUNDED bị từ chối 400 Bad Request")
    void shouldRejectInvalidStatusFilter() throws Exception {
        mockMvc.perform(get("/api/admin/shipping").param("status", "PENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Trạng thái đơn hàng không hợp lệ cho phân hệ vận chuyển: pending"));

        mockMvc.perform(get("/api/admin/shipping").param("status", "REFUNDED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Trạng thái đơn hàng không hợp lệ cho phân hệ vận chuyển: refunded"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("9. Danh sách: Tìm kiếm theo keyword chuẩn hóa (ORD-xxx, số ID, tên, SĐT)")
    void shouldSearchByNormalizedKeyword() throws Exception {
        Order order1 = createTestOrder("Trần Thị Hoa", "0988777666", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, null, null);
        Order order2 = createTestOrder("Lê Văn Nam", "0911222333", "Hồ Chí Minh", OrderStatus.CONFIRMED, PaymentMethod.COD, null, null);

        // Tìm theo ORD-{id}
        mockMvc.perform(get("/api/admin/shipping").param("keyword", "ORD-" + order1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderId").value(order1.getId()));

        // Tìm theo số ID
        mockMvc.perform(get("/api/admin/shipping").param("keyword", String.valueOf(order1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(order1.getId()));

        // Tìm theo tên
        mockMvc.perform(get("/api/admin/shipping").param("keyword", "Thị Hoa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].receiverName").value("Trần Thị Hoa"));

        // Tìm theo SĐT
        mockMvc.perform(get("/api/admin/shipping").param("keyword", "0911222333"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].receiverPhone").value("0911222333"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("10. Danh sách: Lọc theo tỉnh/thành phố")
    void shouldFilterByProvince() throws Exception {
        createTestOrder("Khách Hà Nội", "0913000001", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, null, null);
        createTestOrder("Khách Sài Gòn", "0913000002", "Hồ Chí Minh", OrderStatus.CONFIRMED, PaymentMethod.COD, null, null);

        mockMvc.perform(get("/api/admin/shipping").param("province", "Hà Nội"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].province").value("Hà Nội"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("11. Danh sách: Lọc theo khoảng ngày và kiểm tra startDate > endDate ném 400")
    void shouldFilterByDateRangeAndRejectInvalidRange() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDateTime yesterday = today.minusDays(1).atTime(10, 0);
        LocalDateTime threeDaysAgo = today.minusDays(3).atTime(10, 0);

        createTestOrder("Đơn Hôm Qua", "0914000001", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, yesterday, null);
        createTestOrder("Đơn 3 Ngày Trước", "0914000002", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, threeDaysAgo, null);

        mockMvc.perform(get("/api/admin/shipping")
                        .param("startDate", today.minusDays(1).toString())
                        .param("endDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].receiverName").value("Đơn Hôm Qua"));

        mockMvc.perform(get("/api/admin/shipping")
                        .param("startDate", today.toString())
                        .param("endDate", today.minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ngày bắt đầu không được sau ngày kết thúc"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("12. Danh sách: Kiểm tra tính toán COD (COD = totalAmount, Online = 0) và itemCount (tổng số lượng)")
    void shouldCalculateCodAmountAndItemCountCorrectly() throws Exception {
        Order codOrder = createTestOrder("Khách COD", "0915000001", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, null, null);
        Order onlineOrder = createTestOrder("Khách Online", "0915000002", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.VNPAY, null, null);

        mockMvc.perform(get("/api/admin/shipping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.orderId == " + codOrder.getId() + ")].codAmount").value(530000))
                .andExpect(jsonPath("$.content[?(@.orderId == " + codOrder.getId() + ")].itemCount").value(2))
                .andExpect(jsonPath("$.content[?(@.orderId == " + onlineOrder.getId() + ")].codAmount").value(0))
                .andExpect(jsonPath("$.content[?(@.orderId == " + onlineOrder.getId() + ")].itemCount").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("13. Danh sách: Phân trang và kiểm tra fallback sắp xếp")
    void shouldHandlePaginationAndSortFallback() throws Exception {
        for (int i = 1; i <= 25; i++) {
            createTestOrder("Khách " + i, "0916" + String.format("%06d", i), "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, null, null);
        }

        mockMvc.perform(get("/api/admin/shipping")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.totalElements").value(25));

        // Sort property không hợp lệ phải fallback an toàn về createdAt desc
        mockMvc.perform(get("/api/admin/shipping")
                        .param("sort", "unsupportedField,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ==========================================
    // 4. CHI TIẾT ĐƠN HÀNG VẬN CHUYỂN
    // ==========================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("14. Chi tiết: Trả về đầy đủ thông tin giao nhận, fullAddress và items")
    void shouldReturnShippingOrderDetail() throws Exception {
        Order order = createTestOrder("Nguyễn Thị Chi Tiết", "0917000001", "Hồ Chí Minh", OrderStatus.SHIPPING, PaymentMethod.COD, null, null);

        mockMvc.perform(get("/api/admin/shipping/" + order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(order.getId()))
                .andExpect(jsonPath("$.orderCode").value("ORD-" + order.getId()))
                .andExpect(jsonPath("$.status").value("SHIPPING"))
                .andExpect(jsonPath("$.receiverName").value("Nguyễn Thị Chi Tiết"))
                .andExpect(jsonPath("$.receiverPhone").value("0917000001"))
                .andExpect(jsonPath("$.fullAddress").value("456 Đường Lê Lợi, Phường Bến Thành, Quận 1, Hồ Chí Minh"))
                .andExpect(jsonPath("$.customerNote").value("Gọi trước khi giao hàng"))
                .andExpect(jsonPath("$.codAmount").value(530000))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("15. Chi tiết: Đơn không tồn tại trả về 404 Not Found")
    void shouldReturn404WhenOrderNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/shipping/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("16. Chi tiết: Đơn có trạng thái ngoài phạm vi (PENDING/REFUNDED) trả về 400 Bad Request")
    void shouldReturn400WhenOrderNotInShippingScope() throws Exception {
        Order pendingOrder = createTestOrder("Khách Chưa Duyệt", "0918000001", "Hà Nội", OrderStatus.PENDING, PaymentMethod.COD, null, null);

        mockMvc.perform(get("/api/admin/shipping/" + pendingOrder.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Đơn hàng không thuộc phạm vi quản lý vận chuyển: pending"));
    }

    // ==========================================
    // 5. CẬP NHẬT TRẠNG THÁI VẬN CHUYỂN
    // ==========================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("17. Cập nhật: CONFIRMED -> SHIPPING thành công")
    void shouldUpdateStatusFromConfirmedToShipping() throws Exception {
        Order order = createTestOrder("Khách Xác Nhận", "0919000001", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, null, null);

        ShippingStatusUpdateRequest request = new ShippingStatusUpdateRequest(OrderStatus.SHIPPING);

        mockMvc.perform(patch("/api/admin/shipping/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPING"))
                .andExpect(jsonPath("$.customerNote").value("Gọi trước khi giao hàng"));

        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(updated.getNote()).isEqualTo("Gọi trước khi giao hàng");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("18. Cập nhật: CONFIRMED -> CANCELLED thành công")
    void shouldUpdateStatusFromConfirmedToCancelled() throws Exception {
        Order order = createTestOrder("Khách Xác Nhận Hủy", "0919000002", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, null, null);

        ShippingStatusUpdateRequest request = new ShippingStatusUpdateRequest(OrderStatus.CANCELLED);

        mockMvc.perform(patch("/api/admin/shipping/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("19. Cập nhật: SHIPPING -> COMPLETED thành công và tự động gán completedAt")
    void shouldUpdateStatusFromShippingToCompleted() throws Exception {
        Order order = createTestOrder("Khách Đang Giao Xong", "0919000003", "Hồ Chí Minh", OrderStatus.SHIPPING, PaymentMethod.COD, null, null);

        ShippingStatusUpdateRequest request = new ShippingStatusUpdateRequest(OrderStatus.COMPLETED);

        mockMvc.perform(patch("/api/admin/shipping/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(updated.getCompletedAt()).isNotNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("20. Cập nhật: SHIPPING -> CANCELLED thành công (giao thất bại/khách hủy khi giao)")
    void shouldUpdateStatusFromShippingToCancelled() throws Exception {
        Order order = createTestOrder("Khách Hủy Khi Giao", "0919000004", "Đà Nẵng", OrderStatus.SHIPPING, PaymentMethod.COD, null, null);

        ShippingStatusUpdateRequest request = new ShippingStatusUpdateRequest(OrderStatus.CANCELLED);

        mockMvc.perform(patch("/api/admin/shipping/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Order updated = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("21. CRITICAL: Shipping API tuyệt đối từ chối hủy hoặc chuyển trạng thái đơn hàng PENDING")
    void shouldRejectUpdatingOrCancellingPendingOrderViaShippingApi() throws Exception {
        Order pendingOrder = createTestOrder("Khách Chờ Duyệt", "0920000001", "Hà Nội", OrderStatus.PENDING, PaymentMethod.COD, null, null);

        // Thử hủy đơn PENDING qua Shipping API
        ShippingStatusUpdateRequest cancelReq = new ShippingStatusUpdateRequest(OrderStatus.CANCELLED);
        mockMvc.perform(patch("/api/admin/shipping/" + pendingOrder.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Không thể cập nhật vận chuyển cho đơn hàng ở trạng thái: pending"));

        // Thử chuyển PENDING -> SHIPPING qua Shipping API
        ShippingStatusUpdateRequest shipReq = new ShippingStatusUpdateRequest(OrderStatus.SHIPPING);
        mockMvc.perform(patch("/api/admin/shipping/" + pendingOrder.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shipReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Không thể cập nhật vận chuyển cho đơn hàng ở trạng thái: pending"));

        // Đảm bảo đơn PENDING vẫn giữ nguyên trạng thái
        Order notModified = orderRepository.findById(pendingOrder.getId()).orElseThrow();
        assertThat(notModified.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("22. Cập nhật: Từ chối target status ngoài phạm vi cho phép (REFUNDED, PENDING, CONFIRMED)")
    void shouldRejectDisallowedTargetStatuses() throws Exception {
        Order order = createTestOrder("Khách Test Target", "0921000001", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, null, null);

        // Target REFUNDED
        ShippingStatusUpdateRequest refundedReq = new ShippingStatusUpdateRequest(OrderStatus.REFUNDED);
        mockMvc.perform(patch("/api/admin/shipping/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundedReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Không thể chuyển trạng thái sang: refunded"));

        // Target PENDING
        ShippingStatusUpdateRequest pendingReq = new ShippingStatusUpdateRequest(OrderStatus.PENDING);
        mockMvc.perform(patch("/api/admin/shipping/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pendingReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Không thể chuyển trạng thái sang: pending"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("23. Cập nhật: Trùng trạng thái hiện tại trả về 400 Bad Request")
    void shouldRejectUpdatingToSameStatus() throws Exception {
        Order order = createTestOrder("Khách Trùng Trạng Thái", "0922000001", "Hà Nội", OrderStatus.SHIPPING, PaymentMethod.COD, null, null);

        ShippingStatusUpdateRequest sameReq = new ShippingStatusUpdateRequest(OrderStatus.SHIPPING);
        mockMvc.perform(patch("/api/admin/shipping/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sameReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Đơn hàng hiện tại đã ở trạng thái: shipping"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("24. Cập nhật: Vi phạm quy tắc chuyển trạng thái trả về 400 Bad Request")
    void shouldRejectInvalidStatusTransition() throws Exception {
        Order order = createTestOrder("Khách Nhảy Bước", "0923000001", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, null, null);

        // CONFIRMED không thể nhảy thẳng sang COMPLETED mà không qua SHIPPING
        ShippingStatusUpdateRequest jumpReq = new ShippingStatusUpdateRequest(OrderStatus.COMPLETED);
        mockMvc.perform(patch("/api/admin/shipping/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jumpReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Không thể chuyển trạng thái đơn hàng từ confirmed sang completed"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("25. Cập nhật: Đơn hàng không tồn tại trả về 404 Not Found")
    void shouldReturn404WhenUpdatingNonExistentOrder() throws Exception {
        ShippingStatusUpdateRequest req = new ShippingStatusUpdateRequest(OrderStatus.SHIPPING);
        mockMvc.perform(patch("/api/admin/shipping/999999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ==========================================
    // 6. KIỂM THỬ BIÊN PHÂN TRANG (PAGINATION BOUNDARIES)
    // ==========================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("26. Phân trang biên: page âm (?page=-1) được chuẩn hóa về 0 và trả về 200 OK")
    void shouldNormalizeNegativePageToZero() throws Exception {
        createTestOrder("Khách Page Âm", "0924000001", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, null, null);

        mockMvc.perform(get("/api/admin/shipping")
                        .param("page", "-1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("27. Phân trang biên: size vượt quá giới hạn (?size=150) được giới hạn tối đa 100 và trả về 200 OK")
    void shouldCapPageSizeToMaximumLimit() throws Exception {
        createTestOrder("Khách Max Size", "0924000002", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, null, null);

        mockMvc.perform(get("/api/admin/shipping")
                        .param("page", "0")
                        .param("size", "150"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100));
    }

    // ==========================================
    // 7. KIỂM THỬ ĐỒNG THỜI (CONCURRENCY INTEGRATION TEST)
    // ==========================================

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("28. Concurrency: Hai request đồng thời cập nhật CONFIRMED -> SHIPPING được tuần tự hóa an toàn qua PESSIMISTIC_WRITE lock")
    void shouldHandleConcurrentStatusUpdateUnderPessimisticLock() throws Exception {
        Order order = createTestOrder("Khách Cạnh Tranh", "0925000001", "Hà Nội", OrderStatus.CONFIRMED, PaymentMethod.COD, null, null);
        Long orderId = order.getId();

        try {
            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger badRequestCount = new AtomicInteger(0);
            AtomicInteger otherErrors = new AtomicInteger(0);

            ShippingStatusUpdateRequest request = new ShippingStatusUpdateRequest(OrderStatus.SHIPPING);
            String requestJson = objectMapper.writeValueAsString(request);

            Callable<Integer> task = () -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    var result = mockMvc.perform(patch("/api/admin/shipping/" + orderId + "/status")
                                    .with(user("admin").roles("ADMIN"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestJson))
                            .andReturn();

                    int httpStatus = result.getResponse().getStatus();
                    if (httpStatus == 200) {
                        successCount.incrementAndGet();
                    } else if (httpStatus == 400) {
                        badRequestCount.incrementAndGet();
                    } else {
                        otherErrors.incrementAndGet();
                    }
                    return httpStatus;
                } catch (Exception e) {
                    otherErrors.incrementAndGet();
                    return -1;
                }
            };

            Future<Integer> f1 = executor.submit(task);
            Future<Integer> f2 = executor.submit(task);

            boolean ready = readyLatch.await(5, TimeUnit.SECONDS);
            assertThat(ready).isTrue();

            startLatch.countDown();

            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);

            executor.shutdown();
            boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
            assertThat(terminated).isTrue();

            assertThat(otherErrors.get()).isEqualTo(0);
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(badRequestCount.get()).isEqualTo(1);

            Order finalOrder = orderRepository.findById(orderId).orElseThrow();
            assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        } finally {
            orderRepository.deleteById(orderId);
        }
    }
}
