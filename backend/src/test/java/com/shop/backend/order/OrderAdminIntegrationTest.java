package com.shop.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.order.dto.OrderStatusUpdateRequest;
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
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.shop.backend.inventory.InventoryStockService;
import com.shop.backend.inventory.InventoryTransactionRepository;
import com.shop.backend.inventory.dto.OrderStockItem;
import com.shop.backend.inventory.InventoryMovementType;
import com.shop.backend.inventory.InventoryTransaction;
import com.shop.backend.security.UserPrincipal;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class OrderAdminIntegrationTest {

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
    private OrderAdminService orderAdminService;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @SpyBean
    private InventoryStockService inventoryStockService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private User testUser;
    private User adminUser;
    private User staffUser;
    private ProductVariant testVariant1;
    private ProductVariant testVariant2;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(new Role(null, "customer")));
        Role adminRole = roleRepository.findByName("admin")
                .orElseGet(() -> roleRepository.save(new Role(null, "admin")));
        Role staffRole = roleRepository.findByName("staff")
                .orElseGet(() -> roleRepository.save(new Role(null, "staff")));

        testUser = userRepository.save(User.builder()
                .role(customerRole)
                .name("Nguyễn Văn Khách")
                .email("admin.order.test." + System.nanoTime() + "@example.com")
                .phone("098" + String.format("%07d", Math.abs(System.nanoTime() % 10000000L)))
                .passwordHash("hashed_password")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        adminUser = userRepository.save(User.builder()
                .role(adminRole)
                .name("Quản Trị Viên Test")
                .email("admin.cancel." + System.nanoTime() + "@example.com")
                .phone("097" + String.format("%07d", Math.abs(System.nanoTime() % 10000000L)))
                .passwordHash("hashed_password")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        staffUser = userRepository.save(User.builder()
                .role(staffRole)
                .name("Nhân Viên Test")
                .email("staff.cancel." + System.nanoTime() + "@example.com")
                .phone("096" + String.format("%07d", Math.abs(System.nanoTime() % 10000000L)))
                .passwordHash("hashed_password")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Thời Trang Nam Test")
                .slug("thoi-trang-nam-test-" + System.nanoTime())
                .isActive(true)
                .build());

        Product product = productRepository.save(Product.builder()
                .category(category)
                .name("Áo Sơ Mi Nam Oxford")
                .slug("ao-so-mi-nam-oxford-" + System.nanoTime())
                .basePrice(new BigDecimal("300000.00"))
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        testVariant1 = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("OXFORD-WHITE-M-" + System.nanoTime())
                .size("M")
                .color("Trắng")
                .stock(50)
                .price(new BigDecimal("300000.00"))
                .build());

        testVariant2 = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("OXFORD-BLUE-L-" + System.nanoTime())
                .size("L")
                .color("Xanh Biển")
                .stock(30)
                .price(new BigDecimal("320000.00"))
                .build());
    }

    private UserPrincipal toPrincipal(User user) {
        return new UserPrincipal(user);
    }

    private Order createTestOrder(
            String receiverName,
            String receiverPhone,
            OrderStatus status,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {
        Order order = Order.builder()
                .user(testUser)
                .status(status)
                .subtotal(new BigDecimal("600000.00"))
                .discountAmount(new BigDecimal("50000.00"))
                .shippingFee(new BigDecimal("30000.00"))
                .totalAmount(new BigDecimal("580000.00"))
                .paymentMethod(PaymentMethod.COD)
                .receiverName(receiverName)
                .receiverPhone(receiverPhone)
                .addressLine("123 Phố Huế")
                .ward("Phường Hàng Bài")
                .district("Quận Hoàn Kiếm")
                .province("Hà Nội")
                .note("Giao vào giờ hành chính")
                .salesChannel("website")
                .dataSource(DataSourceType.WEBSITE)
                .createdAt(createdAt != null ? createdAt : LocalDateTime.now())
                .completedAt(completedAt)
                .build();

        OrderDetail detail1 = OrderDetail.builder()
                .variant(testVariant1)
                .productName("Áo Sơ Mi Nam Oxford")
                .variantSize("M")
                .variantColor("Trắng")
                .quantity(2)
                .unitPrice(new BigDecimal("300000.00"))
                .discountAmount(BigDecimal.ZERO)
                .build();
        order.addOrderDetail(detail1);

        Payment payment = Payment.builder()
                .method(PaymentMethod.COD)
                .amount(new BigDecimal("580000.00"))
                .status(PaymentStatus.PENDING)
                .build();
        order.addPayment(payment);

        Order savedOrder = orderRepository.save(order);

        if (createdAt != null) {
            entityManager.flush();
            entityManager.createNativeQuery("UPDATE orders SET created_at = :createdAt WHERE id = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", savedOrder.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.flush();
        entityManager.clear();
            savedOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();
        }

        return savedOrder;
    }

    @Test
    @DisplayName("1. Bảo mật: Chưa xác thực trả về 401 Unauthorized")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/orders/1"))
                .andExpect(status().isUnauthorized());

        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.CONFIRMED);
        mockMvc.perform(patch("/api/admin/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("2. Bảo mật: Role CUSTOMER bị từ chối 403 Forbidden")
    void shouldReturn403WhenRoleCustomer() throws Exception {
        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/orders/1"))
                .andExpect(status().isForbidden());

        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.CONFIRMED);
        mockMvc.perform(patch("/api/admin/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("3. Bảo mật: Role STAFF truy cập thành công")
    void shouldAllowStaffRole() throws Exception {
        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("4. Danh sách: Phân trang và sort mặc định createdAt DESC")
    void shouldGetOrdersWithDefaultPaginationAndSort() throws Exception {
        Order o1 = createTestOrder("Trần Thị A", "0911000001", OrderStatus.PENDING, LocalDateTime.now().minusHours(2), null);
        Order o2 = createTestOrder("Lê Văn B", "0911000002", OrderStatus.CONFIRMED, LocalDateTime.now().minusHours(1), null);
        Order o3 = createTestOrder("Phạm Văn C", "0911000003", OrderStatus.COMPLETED, LocalDateTime.now(), LocalDateTime.now());

        mockMvc.perform(get("/api/admin/orders")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.content[0].id").value(o3.getId()))
                .andExpect(jsonPath("$.content[0].orderCode").value("ORD-" + o3.getId()))
                .andExpect(jsonPath("$.content[0].customerName").value("Phạm Văn C"))
                .andExpect(jsonPath("$.content[0].totalAmount").value(580000L))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].itemCount").value(2))
                .andExpect(jsonPath("$.content[0].itemsSummary").value("Áo Sơ Mi Nam Oxford (x2)"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("5. Danh sách: Giới hạn size tối đa 100")
    void shouldCapMaxPageSizeAt100() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .param("size", "250"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("6. Danh sách: Lọc theo trạng thái đơn hàng")
    void shouldFilterOrdersByStatus() throws Exception {
        createTestOrder("Khách Chờ Duyệt", "0922000001", OrderStatus.PENDING, null, null);
        createTestOrder("Khách Đã Xác Nhận", "0922000002", OrderStatus.CONFIRMED, null, null);
        createTestOrder("Khách Hoàn Tất", "0922000003", OrderStatus.COMPLETED, null, LocalDateTime.now());

        mockMvc.perform(get("/api/admin/orders")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("PENDING"))));

        mockMvc.perform(get("/api/admin/orders")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("COMPLETED"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("7. Danh sách: Tìm kiếm theo từ khóa (tên, SĐT, mã ID)")
    void shouldFilterOrdersByKeyword() throws Exception {
        Order targetOrder = createTestOrder("Vũ Hoàng Nam Long", "0933887766", OrderStatus.CONFIRMED, null, null);
        createTestOrder("Đặng Văn Khác", "0944112233", OrderStatus.CONFIRMED, null, null);

        // Tìm theo tên
        mockMvc.perform(get("/api/admin/orders")
                        .param("keyword", "Nam Long"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(targetOrder.getId()))
                .andExpect(jsonPath("$.content[0].customerName").value("Vũ Hoàng Nam Long"));

        // Tìm theo số điện thoại
        mockMvc.perform(get("/api/admin/orders")
                        .param("keyword", "0933887766"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(targetOrder.getId()));

        // Tìm theo ID
        mockMvc.perform(get("/api/admin/orders")
                        .param("keyword", String.valueOf(targetOrder.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(targetOrder.getId()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("8. Danh sách: Kết hợp status và keyword")
    void shouldFilterByStatusAndKeywordCombined() throws Exception {
        Order match = createTestOrder("Ngô Bảo Châu", "0955667788", OrderStatus.SHIPPING, null, null);
        createTestOrder("Ngô Bảo Châu", "0955667788", OrderStatus.CANCELLED, null, null);
        createTestOrder("Hoàng Tự Lập", "0977889900", OrderStatus.SHIPPING, null, null);

        mockMvc.perform(get("/api/admin/orders")
                        .param("status", "SHIPPING")
                        .param("keyword", "Bảo Châu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(match.getId()))
                .andExpect(jsonPath("$.content[0].status").value("SHIPPING"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("9. Danh sách: Lọc theo khoảng thời gian nửa mở [start, end)")
    void shouldFilterOrdersByDateRangeHalfOpen() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);

        Order yestOrder = createTestOrder("Hôm Qua", "0966000001", OrderStatus.PENDING, yesterday.atTime(14, 0), null);
        Order todayOrder = createTestOrder("Hôm Nay", "0966000002", OrderStatus.CONFIRMED, today.atTime(11, 0), null);
        Order tmrwOrder = createTestOrder("Ngày Mai", "0966000003", OrderStatus.PENDING, tomorrow.atTime(9, 0), null);

        // Lọc [yesterday, today]: phải bao gồm đơn hôm qua và đơn hôm nay (vì end = today + 1 lúc 00:00:00), loại đơn ngày mai
        mockMvc.perform(get("/api/admin/orders")
                        .param("startDate", yesterday.toString())
                        .param("endDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.hasItems(yestOrder.getId().intValue(), todayOrder.getId().intValue())))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(tmrwOrder.getId().intValue()))));

        // Lọc duy nhất ngày hôm nay [today, today]: chỉ bao gồm đơn hôm nay
        mockMvc.perform(get("/api/admin/orders")
                        .param("startDate", today.toString())
                        .param("endDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.hasItem(todayOrder.getId().intValue())))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(yestOrder.getId().intValue()))))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(tmrwOrder.getId().intValue()))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("10. Danh sách: Từ chối khi startDate > endDate với 400 Bad Request")
    void shouldRejectInvalidDateRange() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .param("startDate", "2026-10-20")
                        .param("endDate", "2026-10-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Ngày bắt đầu không được sau ngày kết thúc")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("11. Danh sách: Hiển thị đúng itemsSummary và itemCount cho đơn nhiều sản phẩm")
    void shouldFormatItemsSummaryMultiProduct() throws Exception {
        Order multiOrder = Order.builder()
                .user(testUser)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("940000.00"))
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(new BigDecimal("30000.00"))
                .totalAmount(new BigDecimal("970000.00"))
                .receiverName("Khách Nhiều Món")
                .receiverPhone("0988776655")
                .province("Hồ Chí Minh")
                .createdAt(LocalDateTime.now())
                .build();

        multiOrder.addOrderDetail(OrderDetail.builder()
                .variant(testVariant1)
                .productName("Áo Sơ Mi Nam Oxford")
                .variantSize("M")
                .variantColor("Trắng")
                .quantity(1)
                .unitPrice(new BigDecimal("300000.00"))
                .build());

        multiOrder.addOrderDetail(OrderDetail.builder()
                .variant(testVariant2)
                .productName("Áo Khoác Gió")
                .variantSize("L")
                .variantColor("Xanh Biển")
                .quantity(2)
                .unitPrice(new BigDecimal("320000.00"))
                .build());

        Order saved = orderRepository.save(multiOrder);

        mockMvc.perform(get("/api/admin/orders")
                        .param("keyword", "Nhiều Món"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(saved.getId()))
                .andExpect(jsonPath("$.content[0].itemCount").value(3))
                .andExpect(jsonPath("$.content[0].itemsSummary").value("Áo Sơ Mi Nam Oxford (+1 sản phẩm khác)"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("12. Chi tiết: Lấy đầy đủ thông tin snapshot và payments")
    void shouldGetOrderDetailSuccessfully() throws Exception {
        Order order = createTestOrder("Dương Quá", "0999888777", OrderStatus.PENDING, null, null);

        mockMvc.perform(get("/api/admin/orders/" + order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId()))
                .andExpect(jsonPath("$.orderCode").value("ORD-" + order.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.receiverName").value("Dương Quá"))
                .andExpect(jsonPath("$.receiverPhone").value("0999888777"))
                .andExpect(jsonPath("$.addressLine").value("123 Phố Huế"))
                .andExpect(jsonPath("$.province").value("Hà Nội"))
                .andExpect(jsonPath("$.subtotal").value(600000L))
                .andExpect(jsonPath("$.discountAmount").value(50000L))
                .andExpect(jsonPath("$.shippingFee").value(30000L))
                .andExpect(jsonPath("$.totalAmount").value(580000L))
                .andExpect(jsonPath("$.paymentMethod").value("COD"))
                .andExpect(jsonPath("$.items[0].productName").value("Áo Sơ Mi Nam Oxford"))
                .andExpect(jsonPath("$.items[0].variantSize").value("M"))
                .andExpect(jsonPath("$.items[0].variantColor").value("Trắng"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(300000L))
                .andExpect(jsonPath("$.items[0].totalPrice").value(600000L))
                .andExpect(jsonPath("$.payments[0].method").value("COD"))
                .andExpect(jsonPath("$.payments[0].amount").value(580000L))
                .andExpect(jsonPath("$.payments[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("13. Chi tiết: ID không tồn tại trả về 404")
    void shouldReturn404WhenOrderNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/orders/99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Không tìm thấy đơn hàng với ID: 99999999")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("14. Cập nhật trạng thái: Toàn bộ chuỗi chuyển trạng thái hợp lệ")
    void shouldSupportValidStatusTransitions() throws Exception {
        // PENDING -> CONFIRMED
        Order order = createTestOrder("Khách Test Flow", "0911223344", OrderStatus.PENDING, null, null);
        mockMvc.perform(patch("/api/admin/orders/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // CONFIRMED -> SHIPPING
        mockMvc.perform(patch("/api/admin/orders/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.SHIPPING))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPING"));

        // SHIPPING -> COMPLETED
        mockMvc.perform(patch("/api/admin/orders/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.COMPLETED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        // COMPLETED -> REFUNDED
        mockMvc.perform(patch("/api/admin/orders/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.REFUNDED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("15. Cập nhật trạng thái: Từ chối các chuyển đổi sai luồng với 400 Bad Request")
    void shouldRejectInvalidStatusTransitions() throws Exception {
        Order pendingOrder = createTestOrder("Khách Nhảy Bước", "0988001122", OrderStatus.PENDING, null, null);

        // PENDING -> COMPLETED (nhảy cóc)
        mockMvc.perform(patch("/api/admin/orders/" + pendingOrder.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.COMPLETED))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Không thể chuyển trạng thái")));

        // PENDING -> PENDING (trùng trạng thái)
        mockMvc.perform(patch("/api/admin/orders/" + pendingOrder.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.PENDING))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Đơn hàng hiện tại đã ở trạng thái: pending")));

        // CANCELLED -> CONFIRMED (từ terminal)
        Order cancelledOrder = createTestOrder("Khách Đã Hủy", "0988001123", OrderStatus.CANCELLED, null, null);
        mockMvc.perform(patch("/api/admin/orders/" + cancelledOrder.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Không thể chuyển trạng thái")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("16. An toàn tồn kho: Không tự động cộng khống tồn kho khi chưa có luồng giảm kho (PENDING -> CANCELLED)")
    void shouldNotInflateStockWhenOrderIsCancelledWithoutPriorDeduction() throws Exception {
        int initialStock = testVariant1.getStock(); // 50
        Order order = createTestOrder("Khách Hủy Đơn", "0977112233", OrderStatus.PENDING, null, null);

        mockMvc.perform(patch("/api/admin/orders/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.CANCELLED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        entityManager.flush();
        entityManager.flush();
        entityManager.clear();

        ProductVariant updatedVariant = variantRepository.findById(testVariant1.getId()).orElseThrow();
        assertThat(updatedVariant.getStock()).isEqualTo(initialStock); // Giữ nguyên 50, không bị cộng khống
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("17. An toàn tồn kho: Hoàn tiền COMPLETED -> REFUNDED không tự ý nhập kho khi chưa kiểm định")
    void shouldNotInflateStockWhenOrderIsRefunded() throws Exception {
        int initialStock = testVariant1.getStock(); // 50
        Order order = createTestOrder("Khách Hoàn Tiền", "0977112244", OrderStatus.COMPLETED, null, LocalDateTime.now());

        mockMvc.perform(patch("/api/admin/orders/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.REFUNDED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        entityManager.flush();
        entityManager.flush();
        entityManager.clear();

        ProductVariant updatedVariant = variantRepository.findById(testVariant1.getId()).orElseThrow();
        assertThat(updatedVariant.getStock()).isEqualTo(initialStock); // Giữ nguyên 50
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("18. Contract JSON: Chấp nhận cả payload chữ thường và response luôn trả về chữ hoa")
    void shouldAcceptLowercasePayloadAndReturnUppercaseResponse() throws Exception {
        Order order = createTestOrder("Khách Test Case", "0911556677", OrderStatus.PENDING, null, null);

        // Gửi body JSON với status chữ thường
        mockMvc.perform(patch("/api/admin/orders/" + order.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"confirmed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED")); // Response chuẩn chữ hoa
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("19. Cập nhật trạng thái: Không tìm thấy đơn hàng trả 404")
    void shouldReturn404WhenUpdatingNonExistentOrder() throws Exception {
        mockMvc.perform(patch("/api/admin/orders/99999999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderStatusUpdateRequest(OrderStatus.CONFIRMED))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("20. Cập nhật trạng thái: Dữ liệu enum không hợp lệ trả về 400 Bad Request")
    void shouldRejectInvalidEnumPayload() throws Exception {
        String invalidPayload = "{\"status\": \"INVALID_STATUS\"}";

        mockMvc.perform(patch("/api/admin/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("21. Concurrency & Idempotency: Xử lý an toàn hai request cập nhật trạng thái cạnh tranh qua pessimistic locking")
    void shouldHandleConcurrentStatusUpdateSafely() throws Exception {
        Order order = createTestOrder("Khách Concurrency", "0999112233", OrderStatus.PENDING, null, null);
        Long orderId = order.getId();

        try {
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
            java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(2);
            java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger badRequestCount = new java.util.concurrent.atomic.AtomicInteger(0);

            Runnable task = () -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    orderAdminService.updateOrderStatus(orderId, new OrderStatusUpdateRequest(OrderStatus.CONFIRMED));
                    successCount.incrementAndGet();
                } catch (com.shop.backend.common.exception.BadRequestException e) {
                    badRequestCount.incrementAndGet();
                } catch (Exception e) {
                    // concurrency exception expected
                }
            };

            executor.submit(task);
            executor.submit(task);

            readyLatch.await();
            startLatch.countDown();
            executor.shutdown();
            boolean finished = executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(finished).isTrue();

            // Chính xác 1 request thành công, request thứ hai bị từ chối 400 do Pessimistic Lock tuần tự hóa
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(badRequestCount.get()).isEqualTo(1);

            Order finalOrder = orderRepository.findById(orderId).orElseThrow();
            assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        } finally {
            orderRepository.deleteById(orderId);
        }
    }

    // =========================================================================
    // INV-1.3 PR-B2: Order Cancellation Stock Restoration Tests
    // =========================================================================

    private Order createTwoItemOrderWithDeductionLogs(OrderStatus initialStatus, int qty1, int qty2) {
        Order order = Order.builder()
                .user(testUser)
                .status(initialStatus)
                .subtotal(new BigDecimal("1260000.00"))
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("1260000.00"))
                .paymentMethod(PaymentMethod.COD)
                .receiverName("Khách Hàng Hủy Test")
                .receiverPhone("0912999888")
                .addressLine("123 Phố Huế")
                .ward("Phường Hàng Bài")
                .district("Quận Hoàn Kiếm")
                .province("Hà Nội")
                .salesChannel("website")
                .dataSource(DataSourceType.WEBSITE)
                .createdAt(LocalDateTime.now())
                .build();

        OrderDetail d1 = OrderDetail.builder()
                .variant(testVariant1)
                .productName("Áo Sơ Mi Nam Oxford")
                .variantSize("M")
                .variantColor("Trắng")
                .quantity(qty1)
                .unitPrice(new BigDecimal("300000.00"))
                .discountAmount(BigDecimal.ZERO)
                .build();
        order.addOrderDetail(d1);

        OrderDetail d2 = OrderDetail.builder()
                .variant(testVariant2)
                .productName("Áo Sơ Mi Nam Oxford")
                .variantSize("L")
                .variantColor("Xanh Biển")
                .quantity(qty2)
                .unitPrice(new BigDecimal("320000.00"))
                .discountAmount(BigDecimal.ZERO)
                .build();
        order.addOrderDetail(d2);

        Payment payment = Payment.builder()
                .method(PaymentMethod.COD)
                .amount(new BigDecimal("1260000.00"))
                .status(PaymentStatus.PENDING)
                .build();
        order.addPayment(payment);

        Order savedOrder = orderRepository.saveAndFlush(order);

        variantRepository.deductStock(testVariant1.getId(), qty1);
        variantRepository.deductStock(testVariant2.getId(), qty2);

        List<OrderDetail> details = orderDetailRepository.findByOrderId(savedOrder.getId());
        List<OrderStockItem> items = details.stream()
                .map(d -> new OrderStockItem(d.getId(), d.getVariant().getId(), d.getQuantity()))
                .toList();
        inventoryStockService.recordCheckoutDeduction(savedOrder.getId(), testUser.getId(), items);

        return savedOrder;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("B2-1. cancelPendingOrderShouldRestoreStockAndCreateLogs: Hủy PENDING 2 biến thể -> stock tăng đúng, 2 log cancel_restore, đơn = CANCELLED")
    void cancelPendingOrderShouldRestoreStockAndCreateLogs() throws Exception {
        int initialStock1 = testVariant1.getStock(); // 50
        int initialStock2 = testVariant2.getStock(); // 30
        Order order = createTwoItemOrderWithDeductionLogs(OrderStatus.PENDING, 2, 3);
        Long orderId = order.getId();

        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.CANCELLED);

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        entityManager.flush();
        entityManager.clear();

        Order cancelledOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        ProductVariant reloadedV1 = variantRepository.findById(testVariant1.getId()).orElseThrow();
        ProductVariant reloadedV2 = variantRepository.findById(testVariant2.getId()).orElseThrow();
        assertThat(reloadedV1.getStock()).isEqualTo(initialStock1);
        assertThat(reloadedV2.getStock()).isEqualTo(initialStock2);

        List<InventoryTransaction> restoreLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                orderId, InventoryMovementType.CANCEL_RESTORE
        );
        assertThat(restoreLogs).hasSize(2);

        for (InventoryTransaction tx : restoreLogs) {
            assertThat(tx.getMovementType()).isEqualTo(InventoryMovementType.CANCEL_RESTORE);
            assertThat(tx.getOrder().getId()).isEqualTo(orderId);
            assertThat(tx.getActorUser()).isNotNull();
            assertThat(tx.getActorUser().getId()).isEqualTo(adminUser.getId());
            assertThat(tx.getNote()).contains("Hoàn tồn kho do hủy đơn hàng #" + orderId);
            assertThat(tx.getIdempotencyKey()).startsWith("ORDER_CANCEL:");

            if (tx.getVariant().getId().equals(testVariant1.getId())) {
                assertThat(tx.getQuantity()).isEqualTo(2);
                assertThat(tx.getStockBefore()).isEqualTo(initialStock1 - 2);
                assertThat(tx.getStockAfter()).isEqualTo(initialStock1);
            } else {
                assertThat(tx.getQuantity()).isEqualTo(3);
                assertThat(tx.getStockBefore()).isEqualTo(initialStock2 - 3);
                assertThat(tx.getStockAfter()).isEqualTo(initialStock2);
            }
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("B2-2. cancelConfirmedOrderShouldRestoreStockAndCreateLogs: Hủy CONFIRMED 2 biến thể -> hoàn kho và sinh 2 log cancel_restore")
    void cancelConfirmedOrderShouldRestoreStockAndCreateLogs() throws Exception {
        int initialStock1 = testVariant1.getStock();
        int initialStock2 = testVariant2.getStock();
        Order order = createTwoItemOrderWithDeductionLogs(OrderStatus.CONFIRMED, 2, 3);
        Long orderId = order.getId();

        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.CANCELLED);

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        entityManager.flush();
        entityManager.clear();

        assertThat(variantRepository.findById(testVariant1.getId()).orElseThrow().getStock()).isEqualTo(initialStock1);
        assertThat(variantRepository.findById(testVariant2.getId()).orElseThrow().getStock()).isEqualTo(initialStock2);

        List<InventoryTransaction> restoreLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                orderId, InventoryMovementType.CANCEL_RESTORE
        );
        assertThat(restoreLogs).hasSize(2);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("B2-3. cancelRepeatedlyShouldFailWithBadRequestAndNotRestoreStockAgain: Hủy lặp lại -> lần 2 nhận 400, stock không cộng tiếp, không log mới")
    void cancelRepeatedlyShouldFailWithBadRequestAndNotRestoreStockAgain() throws Exception {
        int initialStock1 = testVariant1.getStock();
        int initialStock2 = testVariant2.getStock();
        Order order = createTwoItemOrderWithDeductionLogs(OrderStatus.PENDING, 2, 3);
        Long orderId = order.getId();

        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.CANCELLED);

        // Lần 1: Thành công
        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        assertThat(variantRepository.findById(testVariant1.getId()).orElseThrow().getStock()).isEqualTo(initialStock1);

        // Lần 2: Thất bại 400
        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Đơn hàng hiện tại đã ở trạng thái: cancelled")));

        entityManager.flush();
        entityManager.clear();

        // Stock không đổi, số log vẫn là 2
        assertThat(variantRepository.findById(testVariant1.getId()).orElseThrow().getStock()).isEqualTo(initialStock1);
        assertThat(variantRepository.findById(testVariant2.getId()).orElseThrow().getStock()).isEqualTo(initialStock2);

        List<InventoryTransaction> restoreLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                orderId, InventoryMovementType.CANCEL_RESTORE
        );
        assertThat(restoreLogs).hasSize(2);
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName("B2-4. cancelConcurrentShouldSerializeAndSucceedExactlyOnce: 2 request hủy đồng thời -> đúng 1 request 200, request kia 400, stock tăng 1 lần")
    void cancelConcurrentShouldSerializeAndSucceedExactlyOnce() throws Exception {
        org.springframework.transaction.support.TransactionTemplate tt =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);

        int initialStock1 = testVariant1.getStock();
        int initialStock2 = testVariant2.getStock();
        Order order = tt.execute(status -> createTwoItemOrderWithDeductionLogs(OrderStatus.PENDING, 2, 3));
        Long orderId = order.getId();

        try {
            int threadCount = 2;
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
            java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
            java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger badRequestCount = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger otherErrors = new java.util.concurrent.atomic.AtomicInteger(0);

            OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.CANCELLED);
            String jsonPayload = objectMapper.writeValueAsString(req);

            Runnable task = () -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    var result = mockMvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonPayload))
                            .andReturn();
                    int code = result.getResponse().getStatus();
                    if (code == 200) {
                        successCount.incrementAndGet();
                    } else if (code == 400) {
                        badRequestCount.incrementAndGet();
                    } else {
                        otherErrors.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherErrors.incrementAndGet();
                }
            };

            executor.submit(task);
            executor.submit(task);

            readyLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            startLatch.countDown();
            executor.shutdown();
            boolean terminated = executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(terminated).isTrue();

            assertThat(otherErrors.get()).isZero();
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(badRequestCount.get()).isEqualTo(1);

            assertThat(variantRepository.findById(testVariant1.getId()).orElseThrow().getStock()).isEqualTo(initialStock1);
            assertThat(variantRepository.findById(testVariant2.getId()).orElseThrow().getStock()).isEqualTo(initialStock2);

            List<InventoryTransaction> restoreLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                    orderId, InventoryMovementType.CANCEL_RESTORE
            );
            assertThat(restoreLogs).hasSize(2);
        } finally {
            tt.execute(status -> {
                inventoryTransactionRepository.deleteAll();
                paymentRepository.deleteAll();
                orderDetailRepository.deleteAll();
                orderRepository.deleteAll();
                return null;
            });
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("B2-5. cancelOldOrderWithoutDeductionLogsShouldCancelSuccessfullyWithoutRestoringStock: Đơn cũ không có log -> hủy thành công, stock giữ nguyên, 0 log")
    void cancelOldOrderWithoutDeductionLogsShouldCancelSuccessfullyWithoutRestoringStock() throws Exception {
        int initialStock = testVariant1.getStock();
        Order order = createTestOrder("Khách Đơn Cũ", "0911000111", OrderStatus.PENDING, null, null);
        Long orderId = order.getId();

        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.CANCELLED);

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Order cancelledOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        assertThat(variantRepository.findById(testVariant1.getId()).orElseThrow().getStock()).isEqualTo(initialStock);
        assertThat(inventoryTransactionRepository.findByOrderIdAndMovementType(orderId, InventoryMovementType.CANCEL_RESTORE)).isEmpty();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("B2-6. cancelOrderWithMixedDeductionLogsShouldOnlyRestoreLoggedItems: Đơn hỗn hợp 1 dòng có log 1 dòng không -> chỉ hoàn dòng có log")
    void cancelOrderWithMixedDeductionLogsShouldOnlyRestoreLoggedItems() throws Exception {
        int initialStock1 = testVariant1.getStock(); // 50
        int initialStock2 = testVariant2.getStock(); // 30
        int qty1 = 2;
        int qty2 = 3;

        Order order = Order.builder()
                .user(testUser)
                .status(OrderStatus.PENDING)
                .subtotal(new BigDecimal("1260000.00"))
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("1260000.00"))
                .paymentMethod(PaymentMethod.COD)
                .receiverName("Khách Đơn Hỗn Hợp")
                .receiverPhone("0912333444")
                .addressLine("123 Phố Huế")
                .ward("Phường Hàng Bài")
                .district("Quận Hoàn Kiếm")
                .province("Hà Nội")
                .salesChannel("website")
                .dataSource(DataSourceType.WEBSITE)
                .createdAt(LocalDateTime.now())
                .build();

        OrderDetail d1 = OrderDetail.builder()
                .variant(testVariant1)
                .productName("Áo Sơ Mi Nam Oxford")
                .variantSize("M")
                .variantColor("Trắng")
                .quantity(qty1)
                .unitPrice(new BigDecimal("300000.00"))
                .discountAmount(BigDecimal.ZERO)
                .build();
        order.addOrderDetail(d1);

        OrderDetail d2 = OrderDetail.builder()
                .variant(testVariant2)
                .productName("Áo Sơ Mi Nam Oxford")
                .variantSize("L")
                .variantColor("Xanh Biển")
                .quantity(qty2)
                .unitPrice(new BigDecimal("320000.00"))
                .discountAmount(BigDecimal.ZERO)
                .build();
        order.addOrderDetail(d2);

        Payment payment = Payment.builder()
                .method(PaymentMethod.COD)
                .amount(new BigDecimal("1260000.00"))
                .status(PaymentStatus.PENDING)
                .build();
        order.addPayment(payment);

        Order savedOrder = orderRepository.saveAndFlush(order);

        // Trừ kho cả 2 biến thể
        variantRepository.deductStock(testVariant1.getId(), qty1);
        variantRepository.deductStock(testVariant2.getId(), qty2);

        // CHỈ ghi deduction log cho item 1
        List<OrderDetail> details = orderDetailRepository.findByOrderId(savedOrder.getId());
        OrderDetail detail1 = details.stream().filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();
        inventoryStockService.recordCheckoutDeduction(
                savedOrder.getId(), testUser.getId(),
                List.of(new OrderStockItem(detail1.getId(), detail1.getVariant().getId(), detail1.getQuantity()))
        );

        Long orderId = savedOrder.getId();
        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.CANCELLED);

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();

        // Item 1 có log -> hoàn lại stock (50 - 2 + 2 = 50)
        assertThat(variantRepository.findById(testVariant1.getId()).orElseThrow().getStock()).isEqualTo(initialStock1);
        // Item 2 KHÔNG có log -> KHÔNG hoàn lại stock (vẫn 30 - 3 = 27)
        assertThat(variantRepository.findById(testVariant2.getId()).orElseThrow().getStock()).isEqualTo(initialStock2 - qty2);

        List<InventoryTransaction> restoreLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                orderId, InventoryMovementType.CANCEL_RESTORE
        );
        assertThat(restoreLogs).hasSize(1);
        assertThat(restoreLogs.get(0).getVariant().getId()).isEqualTo(testVariant1.getId());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("B2-7. cancelOrderShouldRollbackWhenStockRestorationFails: Hoàn kho thất bại -> rollback toàn bộ, đơn giữ trạng thái cũ, stock không đổi")
    void cancelOrderShouldRollbackWhenStockRestorationFails() throws Exception {
        int initialStock1 = testVariant1.getStock();
        int initialStock2 = testVariant2.getStock();
        Order order = createTwoItemOrderWithDeductionLogs(OrderStatus.CONFIRMED, 2, 3);
        Long orderId = order.getId();

        try {
            doThrow(new RuntimeException("Simulated stock restoration failure"))
                    .when(inventoryStockService).restoreStockForCancelledOrder(any(), any(), any(), any());

            OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.CANCELLED);

            mockMvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().is5xxServerError());

            entityManager.flush();
            entityManager.clear();

            Order reloadedOrder = orderRepository.findById(orderId).orElseThrow();
            assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

            assertThat(variantRepository.findById(testVariant1.getId()).orElseThrow().getStock()).isEqualTo(initialStock1 - 2);
            assertThat(variantRepository.findById(testVariant2.getId()).orElseThrow().getStock()).isEqualTo(initialStock2 - 3);

            List<InventoryTransaction> restoreLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                    orderId, InventoryMovementType.CANCEL_RESTORE
            );
            assertThat(restoreLogs).isEmpty();
        } finally {
            org.mockito.Mockito.reset(inventoryStockService);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("B2-8. cancelShippingOrderShouldNotRestoreStock: SHIPPING -> CANCELLED: đổi trạng thái thành công, stock KHÔNG đổi, không gọi restore")
    void cancelShippingOrderShouldNotRestoreStock() throws Exception {
        int initialStock1 = testVariant1.getStock();
        int initialStock2 = testVariant2.getStock();
        Order order = createTwoItemOrderWithDeductionLogs(OrderStatus.SHIPPING, 2, 3);
        Long orderId = order.getId();

        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.CANCELLED);

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        entityManager.flush();
        entityManager.clear();

        Order reloadedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // Stock KHÔNG được hoàn
        assertThat(variantRepository.findById(testVariant1.getId()).orElseThrow().getStock()).isEqualTo(initialStock1 - 2);
        assertThat(variantRepository.findById(testVariant2.getId()).orElseThrow().getStock()).isEqualTo(initialStock2 - 3);

        List<InventoryTransaction> restoreLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                orderId, InventoryMovementType.CANCEL_RESTORE
        );
        assertThat(restoreLogs).isEmpty();

        verify(inventoryStockService, never()).restoreStockForCancelledOrder(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("B2-9. refundCompletedOrderShouldNotRestoreStock: COMPLETED -> REFUNDED: đổi trạng thái thành công, stock không đổi, không gọi restore")
    void refundCompletedOrderShouldNotRestoreStock() throws Exception {
        int initialStock1 = testVariant1.getStock();
        int initialStock2 = testVariant2.getStock();
        Order order = createTwoItemOrderWithDeductionLogs(OrderStatus.COMPLETED, 2, 3);
        Long orderId = order.getId();

        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.REFUNDED);

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        entityManager.flush();
        entityManager.clear();

        Order reloadedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.REFUNDED);

        // Stock KHÔNG đổi
        assertThat(variantRepository.findById(testVariant1.getId()).orElseThrow().getStock()).isEqualTo(initialStock1 - 2);
        assertThat(variantRepository.findById(testVariant2.getId()).orElseThrow().getStock()).isEqualTo(initialStock2 - 3);

        List<InventoryTransaction> restoreLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                orderId, InventoryMovementType.CANCEL_RESTORE
        );
        assertThat(restoreLogs).isEmpty();

        verify(inventoryStockService, never()).restoreStockForCancelledOrder(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("B2-10. cancelOrderWithInactiveVariantShouldStillRestoreStock: Biến thể inactive vẫn được hoàn kho chính xác")
    void cancelOrderWithInactiveVariantShouldStillRestoreStock() throws Exception {
        int initialStock1 = testVariant1.getStock();
        int initialStock2 = testVariant2.getStock();
        Order order = createTwoItemOrderWithDeductionLogs(OrderStatus.PENDING, 2, 3);
        Long orderId = order.getId();

        // Giả sử variant1 bị vô hiệu hóa ngừng kinh doanh
        Product product1 = productRepository.findById(testVariant1.getProduct().getId()).orElseThrow();
        product1.setIsActive(false);
        productRepository.saveAndFlush(product1);

        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.CANCELLED);

        mockMvc.perform(patch("/api/admin/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        entityManager.flush();
        entityManager.clear();

        assertThat(variantRepository.findById(testVariant1.getId()).orElseThrow().getStock()).isEqualTo(initialStock1);
        assertThat(variantRepository.findById(testVariant2.getId()).orElseThrow().getStock()).isEqualTo(initialStock2);

        List<InventoryTransaction> restoreLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                orderId, InventoryMovementType.CANCEL_RESTORE
        );
        assertThat(restoreLogs).hasSize(2);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("B2-11. cancelOrderViaShippingEndpointShouldRestoreStockWhenSourceIsConfirmed: Qua Shipping endpoint hủy đơn CONFIRMED -> hoàn kho thành công")
    void cancelOrderViaShippingEndpointShouldRestoreStockWhenSourceIsConfirmed() throws Exception {
        int initialStock1 = testVariant1.getStock();
        int initialStock2 = testVariant2.getStock();
        Order order = createTwoItemOrderWithDeductionLogs(OrderStatus.CONFIRMED, 2, 3);
        Long orderId = order.getId();

        // Shipping endpoint: PATCH /api/admin/shipping/{orderId}/status
        String shippingCancelPayload = "{\"status\": \"CANCELLED\"}";

        mockMvc.perform(patch("/api/admin/shipping/" + orderId + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(staffUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shippingCancelPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        entityManager.flush();
        entityManager.clear();

        assertThat(variantRepository.findById(testVariant1.getId()).orElseThrow().getStock()).isEqualTo(initialStock1);
        assertThat(variantRepository.findById(testVariant2.getId()).orElseThrow().getStock()).isEqualTo(initialStock2);

        List<InventoryTransaction> restoreLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                orderId, InventoryMovementType.CANCEL_RESTORE
        );
        assertThat(restoreLogs).hasSize(2);
        for (InventoryTransaction tx : restoreLogs) {
            assertThat(tx.getActorUser()).isNotNull();
            assertThat(tx.getActorUser().getId()).isEqualTo(staffUser.getId());
        }

        // Trường hợp Shipping endpoint từ chối PENDING:
        Order pendingOrder = createTwoItemOrderWithDeductionLogs(OrderStatus.PENDING, 1, 1);
        mockMvc.perform(patch("/api/admin/shipping/" + pendingOrder.getId() + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(staffUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shippingCancelPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Không thể cập nhật vận chuyển cho đơn hàng ở trạng thái: pending")));
    }
}
