package com.shop.backend.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.address.UserAddress;
import com.shop.backend.address.UserAddressRepository;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.common.model.DiscountType;
import com.shop.backend.inventory.InventoryMovementType;
import com.shop.backend.inventory.InventoryStockService;
import com.shop.backend.inventory.InventoryTransactionRepository;
import com.shop.backend.inventory.dto.OrderStockItem;
import com.shop.backend.order.dto.CheckoutItemRequest;
import com.shop.backend.order.dto.CheckoutRequest;
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
import com.shop.backend.security.UserPrincipal;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import com.shop.backend.voucher.Voucher;
import com.shop.backend.voucher.VoucherRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OrderCustomerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private PaymentRepository paymentRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private InventoryStockService inventoryStockService;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private UserAddressRepository userAddressRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User customerA;
    private User customerB;
    private User adminUser;
    private User staffUser;

    private Category category;
    private Product product;
    private ProductVariant variant1;
    private ProductVariant variant2;

    @BeforeEach
    void setUp() {
        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(new Role(null, "customer")));
        Role adminRole = roleRepository.findByName("admin")
                .orElseGet(() -> roleRepository.save(new Role(null, "admin")));
        Role staffRole = roleRepository.findByName("staff")
                .orElseGet(() -> roleRepository.save(new Role(null, "staff")));

        customerA = userRepository.save(User.builder()
                .role(customerRole)
                .name("Khách Hàng A")
                .email("cust.a." + System.nanoTime() + "@shop.vn")
                .phone("091" + String.format("%07d", Math.abs(System.nanoTime() % 10000000L)))
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        customerB = userRepository.save(User.builder()
                .role(customerRole)
                .name("Khách Hàng B")
                .email("cust.b." + System.nanoTime() + "@shop.vn")
                .phone("092" + String.format("%07d", Math.abs(System.nanoTime() % 10000000L)))
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        adminUser = userRepository.save(User.builder()
                .role(adminRole)
                .name("Admin Shop")
                .email("admin.cust." + System.nanoTime() + "@shop.vn")
                .phone("093" + String.format("%07d", Math.abs(System.nanoTime() % 10000000L)))
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        staffUser = userRepository.save(User.builder()
                .role(staffRole)
                .name("Staff Shop")
                .email("staff.cust." + System.nanoTime() + "@shop.vn")
                .phone("094" + String.format("%07d", Math.abs(System.nanoTime() % 10000000L)))
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        category = categoryRepository.save(Category.builder()
                .name("Thời Trang Nam " + System.nanoTime())
                .slug("thoi-trang-nam-" + System.nanoTime())
                .build());

        product = productRepository.save(Product.builder()
                .category(category)
                .name("Áo Polo Nam Classic")
                .slug("ao-polo-nam-classic-" + System.nanoTime())
                .description("Chất liệu cotton thoáng mát")
                .basePrice(new BigDecimal("250000.00"))
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        variant1 = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("POLO-BLACK-M-" + System.nanoTime())
                .size("M")
                .color("Đen")
                .price(new BigDecimal("250000.00"))
                .stock(50)
                .build());

        variant2 = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("POLO-WHITE-L-" + System.nanoTime())
                .size("L")
                .color("Trắng")
                .price(new BigDecimal("300000.00"))
                .stock(40)
                .build());
    }

    @AfterEach
    void tearDown() {
        inventoryTransactionRepository.deleteAll();
        paymentRepository.deleteAll();
        orderDetailRepository.deleteAll();
        orderRepository.deleteAll();
        userAddressRepository.deleteAll();
        voucherRepository.deleteAll();
        variantRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    private UserPrincipal toPrincipal(User user) {
        User userWithRole = userRepository.findWithRoleById(user.getId()).orElseThrow();
        return new UserPrincipal(userWithRole);
    }

    private Order createTestOrder(
            User user,
            OrderStatus status,
            LocalDateTime createdAt,
            boolean isPaid,
            List<OrderDetailItemSpec> itemsSpec
    ) {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        return tt.execute(txStatus -> {
            BigDecimal subtotal = BigDecimal.ZERO;
            for (OrderDetailItemSpec spec : itemsSpec) {
                BigDecimal line = spec.unitPrice.multiply(BigDecimal.valueOf(spec.quantity));
                subtotal = subtotal.add(line);
            }
            BigDecimal discount = BigDecimal.ZERO;
            BigDecimal shippingFee = new BigDecimal("30000.00");
            BigDecimal total = subtotal.subtract(discount).add(shippingFee);

            Order order = Order.builder()
                    .user(user)
                    .status(status)
                    .createdAt(createdAt)
                    .completedAt(status == OrderStatus.COMPLETED ? createdAt.plusDays(2) : null)
                    .subtotal(subtotal)
                    .discountAmount(discount)
                    .shippingFee(shippingFee)
                    .totalAmount(total)
                    .paymentMethod(PaymentMethod.COD)
                    .receiverName(user.getName())
                    .receiverPhone(user.getPhone())
                    .addressLine("123 Phố Thời Trang")
                    .ward("Phường Bến Nghé")
                    .district("Quận 1")
                    .province("TP. Hồ Chí Minh")
                    .note("Giao nhanh giúp tôi")
                    .dataSource(DataSourceType.WEBSITE)
                    .build();

            Order savedOrder = orderRepository.save(order);

            List<OrderStockItem> stockItems = new ArrayList<>();
            for (OrderDetailItemSpec spec : itemsSpec) {
                variantRepository.deductStock(spec.variant.getId(), spec.quantity);
                OrderDetail detail = OrderDetail.builder()
                        .order(savedOrder)
                        .variant(spec.variant)
                        .productName(spec.productName)
                        .variantSize(spec.variantSize)
                        .variantColor(spec.variantColor)
                        .quantity(spec.quantity)
                        .unitPrice(spec.unitPrice)
                        .discountAmount(BigDecimal.ZERO)
                        .build();
                OrderDetail savedDetail = orderDetailRepository.save(detail);
                stockItems.add(new OrderStockItem(savedDetail.getId(), spec.variant.getId(), spec.quantity));
            }

            inventoryStockService.recordCheckoutDeduction(savedOrder.getId(), user.getId(), stockItems);

            Payment payment = Payment.builder()
                    .order(savedOrder)
                    .method(PaymentMethod.COD)
                    .amount(total)
                    .status(isPaid ? PaymentStatus.SUCCESS : PaymentStatus.PENDING)
                    .paidAt(isPaid ? createdAt : null)
                    .build();
            paymentRepository.save(payment);

            return savedOrder;
        });
    }

    private record OrderDetailItemSpec(
            ProductVariant variant,
            String productName,
            String variantSize,
            String variantColor,
            int quantity,
            BigDecimal unitPrice
    ) {}

    @Test
    @DisplayName("Kịch bản 1: Không token trả 401; token ADMIN/STAFF trả 403 trên cả 3 endpoint")
    void unauthenticatedOrWrongRoleShouldReturn401Or403() throws Exception {
        Order order = createTestOrder(customerA, OrderStatus.PENDING, LocalDateTime.now(), false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo", "M", "Đen", 1, new BigDecimal("250000.00"))
        ));

        // 1. Unauthenticated -> 401 Unauthorized
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/orders/" + order.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/cancel"))
                .andExpect(status().isUnauthorized());

        // 2. ADMIN role -> 403 Forbidden
        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/" + order.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/cancel")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser))))
                .andExpect(status().isForbidden());

        // 3. STAFF role -> 403 Forbidden
        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(staffUser))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/" + order.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(staffUser))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/orders/" + order.getId() + "/cancel")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(staffUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Kịch bản 2: Danh sách chỉ chứa đơn của chính mình, sắp xếp createdAt desc, phân trang đúng, lọc status đúng, size vượt 100 bị cap")
    void getOrdersShouldReturnOnlyOwnOrdersWithPaginationAndStatusFilterAndSizeCap() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Order orderA1 = createTestOrder(customerA, OrderStatus.PENDING, now.minusHours(3), false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo 1", "M", "Đen", 1, new BigDecimal("250000.00"))
        ));
        Order orderA2 = createTestOrder(customerA, OrderStatus.CONFIRMED, now.minusHours(2), false, List.of(
                new OrderDetailItemSpec(variant2, "Áo Polo 2", "L", "Trắng", 1, new BigDecimal("300000.00"))
        ));
        Order orderA3 = createTestOrder(customerA, OrderStatus.PENDING, now.minusHours(1), false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo 3", "M", "Đen", 2, new BigDecimal("250000.00"))
        ));

        createTestOrder(customerB, OrderStatus.PENDING, now, false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo B1", "M", "Đen", 1, new BigDecimal("250000.00"))
        ));
        createTestOrder(customerB, OrderStatus.CONFIRMED, now.plusMinutes(10), false, List.of(
                new OrderDetailItemSpec(variant2, "Áo Polo B2", "L", "Trắng", 1, new BigDecimal("300000.00"))
        ));

        // 1. Phân trang trang 0 size 2 -> Trả 2 đơn mới nhất của A (orderA3, orderA2)
        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].orderId").value(orderA3.getId()))
                .andExpect(jsonPath("$.content[1].orderId").value(orderA2.getId()));

        // 2. Phân trang trang 1 size 2 -> Trả 1 đơn còn lại của A (orderA1)
        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].orderId").value(orderA1.getId()));

        // 3. Lọc status=PENDING -> Chỉ trả 2 đơn PENDING của A (orderA3, orderA1)
        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].orderId").value(orderA3.getId()))
                .andExpect(jsonPath("$.content[1].orderId").value(orderA1.getId()));

        // 4. Lọc status=CONFIRMED -> Chỉ trả 1 đơn CONFIRMED của A (orderA2)
        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].orderId").value(orderA2.getId()));

        // 5. Size vượt 100 bị cap về 100
        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("Kịch bản 3: Chi tiết đơn trả đúng snapshot, chứng minh không join ngược khi sửa tên sản phẩm và giá biến thể")
    void getOrderDetailShouldReturnExactSnapshotEvenIfProductAndVariantMutateInDb() throws Exception {
        Order order = createTestOrder(customerA, OrderStatus.PENDING, LocalDateTime.now(), false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo Snapshot Gốc", "M", "Đen", 2, new BigDecimal("250000.00"))
        ));

        // Sửa sản phẩm và biến thể trong DB
        product.setName("Áo Polo Đã Đổi Tên Thành Áo Thun Mới");
        productRepository.save(product);

        variant1.setPrice(new BigDecimal("999000.00"));
        variantRepository.save(variant1);

        // Xem chi tiết đơn hàng: tên sản phẩm và giá phải giữ nguyên snapshot cũ
        mockMvc.perform(get("/api/orders/" + order.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(order.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productName").value("Áo Polo Snapshot Gốc"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(250000L))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal").value(500000L))
                .andExpect(jsonPath("$.subtotal").value(500000L))
                .andExpect(jsonPath("$.totalAmount").value(530000L))
                .andExpect(jsonPath("$.receiverName").value("Khách Hàng A"))
                .andExpect(jsonPath("$.receiverPhone").value(customerA.getPhone()))
                .andExpect(jsonPath("$.district").value("Quận 1"));
    }

    @Test
    @DisplayName("Kịch bản 4: Xem chi tiết đơn của người khác và đơn không tồn tại cùng trả 404 chống IDOR")
    void getOrderDetailForOtherUserOrNonExistentShouldReturn404() throws Exception {
        Order orderB = createTestOrder(customerB, OrderStatus.PENDING, LocalDateTime.now(), false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo B", "M", "Đen", 1, new BigDecimal("250000.00"))
        ));

        // Khách A xem đơn của khách B -> 404
        mockMvc.perform(get("/api/orders/" + orderB.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy đơn hàng"));

        // Khách A xem đơn không tồn tại -> 404
        mockMvc.perform(get("/api/orders/999999999")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy đơn hàng"));
    }

    @Test
    @DisplayName("Kịch bản 5: Tiền tệ là số nguyên Long và response không chứa các trường nội bộ (userId, idempotencyKey...)")
    void responseShouldContainIntegerCurrencyAndOmitInternalFields() throws Exception {
        Order order = createTestOrder(customerA, OrderStatus.PENDING, LocalDateTime.now(), false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo 1", "M", "Đen", 2, new BigDecimal("250000.00")),
                new OrderDetailItemSpec(variant2, "Áo Polo 2", "L", "Trắng", 1, new BigDecimal("300000.00"))
        ));

        // Kiểm tra endpoint detail
        mockMvc.perform(get("/api/orders/" + order.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").isNumber())
                .andExpect(jsonPath("$.totalAmount").isNumber())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.voucherId").doesNotExist())
                .andExpect(jsonPath("$.dataSource").doesNotExist())
                .andExpect(jsonPath("$.salesChannel").doesNotExist())
                .andExpect(jsonPath("$.cancelledFromStatus").doesNotExist());

        // Kiểm tra endpoint list
        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].totalAmount").isNumber())
                .andExpect(jsonPath("$.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.content[0].idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.content[0].dataSource").doesNotExist())
                .andExpect(jsonPath("$.content[0].voucherId").doesNotExist());
    }

    @Test
    @DisplayName("Kịch bản 6: Hủy đơn PENDING hoàn kho nguyên tử, ghi inventory_transactions CANCEL_RESTORE, canCancel=false sau khi hủy")
    void cancelPendingOrderShouldRestockAtomicallyAndSetStatusCancelled() throws Exception {
        Order order = createTestOrder(customerA, OrderStatus.PENDING, LocalDateTime.now(), false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo 1", "M", "Đen", 3, new BigDecimal("250000.00")),
                new OrderDetailItemSpec(variant2, "Áo Polo 2", "L", "Trắng", 2, new BigDecimal("300000.00"))
        ));

        int stockBeforeCancel1 = variantRepository.findById(variant1.getId()).orElseThrow().getStock();
        int stockBeforeCancel2 = variantRepository.findById(variant2.getId()).orElseThrow().getStock();

        mockMvc.perform(post("/api/orders/" + order.getId() + "/cancel")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(order.getId()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.canCancel").value(false));

        // Kiểm tra hoàn kho
        ProductVariant variantAfter1 = variantRepository.findById(variant1.getId()).orElseThrow();
        ProductVariant variantAfter2 = variantRepository.findById(variant2.getId()).orElseThrow();
        assertThat(variantAfter1.getStock()).isEqualTo(stockBeforeCancel1 + 3);
        assertThat(variantAfter2.getStock()).isEqualTo(stockBeforeCancel2 + 2);

        // Kiểm tra bản ghi giao dịch kho
        var transactions = inventoryTransactionRepository.findByOrderIdAndMovementType(
                order.getId(),
                InventoryMovementType.CANCEL_RESTORE
        );
        assertThat(transactions).hasSize(2);

        // Kiểm tra DB order
        Order orderInDb = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(orderInDb.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderInDb.getCancelledFromStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Kịch bản 7: Hủy đơn không ở PENDING (CONFIRMED, SHIPPING, COMPLETED, CANCELLED) trả 409, kho không đổi")
    void cancelNonPendingOrderShouldReturn409AndLeaveStockUnchanged() throws Exception {
        OrderStatus[] nonPendingStatuses = {
                OrderStatus.CONFIRMED,
                OrderStatus.SHIPPING,
                OrderStatus.COMPLETED,
                OrderStatus.CANCELLED
        };

        for (OrderStatus st : nonPendingStatuses) {
            Order order = createTestOrder(customerA, st, LocalDateTime.now(), false, List.of(
                    new OrderDetailItemSpec(variant1, "Áo Polo", "M", "Đen", 1, new BigDecimal("250000.00"))
            ));

            int stockBefore = variantRepository.findById(variant1.getId()).orElseThrow().getStock();

            mockMvc.perform(post("/api/orders/" + order.getId() + "/cancel")
                            .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("không thể hủy")));

            int stockAfter = variantRepository.findById(variant1.getId()).orElseThrow().getStock();
            assertThat(stockAfter).isEqualTo(stockBefore);
        }
    }

    @Test
    @DisplayName("Kịch bản 8: Hủy đơn của người khác trả 404 chống IDOR, trạng thái đơn và tồn kho không đổi")
    void cancelOtherUserOrderShouldReturn404AndLeaveStateUnchanged() throws Exception {
        Order orderB = createTestOrder(customerB, OrderStatus.PENDING, LocalDateTime.now(), false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo B", "M", "Đen", 2, new BigDecimal("250000.00"))
        ));

        int stockBefore = variantRepository.findById(variant1.getId()).orElseThrow().getStock();

        mockMvc.perform(post("/api/orders/" + orderB.getId() + "/cancel")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy đơn hàng"));

        // Trạng thái đơn B và tồn kho không đổi
        Order orderBAfter = orderRepository.findById(orderB.getId()).orElseThrow();
        assertThat(orderBAfter.getStatus()).isEqualTo(OrderStatus.PENDING);
        int stockAfter = variantRepository.findById(variant1.getId()).orElseThrow().getStock();
        assertThat(stockAfter).isEqualTo(stockBefore);
    }

    @Test
    @DisplayName("Kịch bản 9: Hủy đơn đã thanh toán trả 409 theo thông báo riêng, kho không đổi")
    void cancelPaidOrderShouldReturn409AndLeaveStockUnchanged() throws Exception {
        Order paidOrder = createTestOrder(customerA, OrderStatus.PENDING, LocalDateTime.now(), true, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo Paid", "M", "Đen", 1, new BigDecimal("250000.00"))
        ));

        int stockBefore = variantRepository.findById(variant1.getId()).orElseThrow().getStock();

        mockMvc.perform(post("/api/orders/" + paidOrder.getId() + "/cancel")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("đã được thanh toán")));

        int stockAfter = variantRepository.findById(variant1.getId()).orElseThrow().getStock();
        assertThat(stockAfter).isEqualTo(stockBefore);
    }

    @Test
    @DisplayName("Kịch bản 10: Hai luồng hủy đồng thời cùng một đơn -> đúng 1 request 200, 1 request 409, kho hoàn đúng 1 lần")
    void concurrentCancelRequestsShouldAllowExactlyOneSuccessAndRestockOnce() throws Exception {
        Order order = createTestOrder(customerA, OrderStatus.PENDING, LocalDateTime.now(), false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo Race", "M", "Đen", 2, new BigDecimal("250000.00"))
        ));

        int initialStock = variantRepository.findById(variant1.getId()).orElseThrow().getStock();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    var result = mockMvc.perform(post("/api/orders/" + order.getId() + "/cancel")
                                    .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                            .andReturn();
                    int code = result.getResponse().getStatus();
                    if (code == 200) {
                        successCount.incrementAndGet();
                    } else if (code == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        latch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        for (Future<?> f : futures) {
            f.get();
        }

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        // Kho chỉ được hoàn đúng 1 lần (+2)
        int stockAfter = variantRepository.findById(variant1.getId()).orElseThrow().getStock();
        assertThat(stockAfter).isEqualTo(initialStock + 2);
    }

    @Test
    @DisplayName("Kịch bản 11: Cờ canCancel trong danh sách và chi tiết khớp quy tắc (true khi PENDING chưa thanh toán; false còn lại)")
    void canCancelFlagShouldAccuratelyReflectBusinessRules() throws Exception {
        // 1. PENDING chưa thanh toán -> true
        Order orderPendingUnpaid = createTestOrder(customerA, OrderStatus.PENDING, LocalDateTime.now().minusHours(4), false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo 1", "M", "Đen", 1, new BigDecimal("250000.00"))
        ));

        // 2. PENDING đã thanh toán -> false
        Order orderPendingPaid = createTestOrder(customerA, OrderStatus.PENDING, LocalDateTime.now().minusHours(3), true, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo 2", "M", "Đen", 1, new BigDecimal("250000.00"))
        ));

        // 3. CONFIRMED chưa thanh toán -> false
        Order orderConfirmed = createTestOrder(customerA, OrderStatus.CONFIRMED, LocalDateTime.now().minusHours(2), false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo 3", "M", "Đen", 1, new BigDecimal("250000.00"))
        ));

        // 4. CANCELLED -> false
        Order orderCancelled = createTestOrder(customerA, OrderStatus.CANCELLED, LocalDateTime.now().minusHours(1), false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo 4", "M", "Đen", 1, new BigDecimal("250000.00"))
        ));

        // Kiểm tra trong list
        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(orderCancelled.getId()))
                .andExpect(jsonPath("$.content[0].canCancel").value(false))
                .andExpect(jsonPath("$.content[1].orderId").value(orderConfirmed.getId()))
                .andExpect(jsonPath("$.content[1].canCancel").value(false))
                .andExpect(jsonPath("$.content[2].orderId").value(orderPendingPaid.getId()))
                .andExpect(jsonPath("$.content[2].canCancel").value(false))
                .andExpect(jsonPath("$.content[3].orderId").value(orderPendingUnpaid.getId()))
                .andExpect(jsonPath("$.content[3].canCancel").value(true));

        // Kiểm tra trong detail
        mockMvc.perform(get("/api/orders/" + orderPendingUnpaid.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canCancel").value(true));

        mockMvc.perform(get("/api/orders/" + orderPendingPaid.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canCancel").value(false));

        mockMvc.perform(get("/api/orders/" + orderConfirmed.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canCancel").value(false));

        mockMvc.perform(get("/api/orders/" + orderCancelled.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canCancel").value(false));
    }

    @Test
    @DisplayName("Kịch bản 12 (Mới): Kiểm tra chính xác tập trường JSON và đảm bảo không tồn tại các khóa bị cấm")
    void jsonContractAndForbiddenFieldsVerification() throws Exception {
        Order order = createTestOrder(customerA, OrderStatus.PENDING, LocalDateTime.now(), false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo Mẫu", "M", "Đen", 2, new BigDecimal("250000.00"))
        ));

        // 1. Kiểm tra GET /api/orders (List response)
        MvcResult listResult = mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode listRoot = objectMapper.readTree(listResult.getResponse().getContentAsString());
        JsonNode content = listRoot.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isGreaterThanOrEqualTo(1);

        JsonNode listItem = content.get(0);
        Set<String> actualListItemFields = extractFieldNames(listItem);
        Set<String> expectedListItemFields = Set.of(
                "orderId", "status", "createdAt", "totalAmount", "paymentMethod",
                "paymentStatus", "lineCount", "previewItems", "canCancel"
        );
        assertThat(actualListItemFields).isEqualTo(expectedListItemFields);

        JsonNode previewItems = listItem.get("previewItems");
        assertThat(previewItems.isArray()).isTrue();
        assertThat(previewItems.size()).isGreaterThanOrEqualTo(1);
        JsonNode previewItem = previewItems.get(0);
        Set<String> actualPreviewItemFields = extractFieldNames(previewItem);
        Set<String> expectedPreviewItemFields = Set.of(
                "productName", "variantSize", "variantColor", "quantity"
        );
        assertThat(actualPreviewItemFields).isEqualTo(expectedPreviewItemFields);

        // 2. Kiểm tra GET /api/orders/{id} (Detail response)
        MvcResult detailResult = mockMvc.perform(get("/api/orders/" + order.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode detailRoot = objectMapper.readTree(detailResult.getResponse().getContentAsString());
        Set<String> actualDetailFields = extractFieldNames(detailRoot);
        Set<String> expectedDetailFields = Set.of(
                "orderId", "status", "createdAt", "completedAt", "subtotal", "discountAmount",
                "shippingFee", "totalAmount", "paymentMethod", "paymentStatus", "paidAt",
                "receiverName", "receiverPhone", "addressLine", "ward", "district", "province",
                "note", "items", "canCancel"
        );
        assertThat(actualDetailFields).isEqualTo(expectedDetailFields);

        JsonNode items = detailRoot.get("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isGreaterThanOrEqualTo(1);
        JsonNode item = items.get(0);
        Set<String> actualItemFields = extractFieldNames(item);
        Set<String> expectedItemFields = Set.of(
                "orderDetailId", "productName", "variantSize", "variantColor",
                "quantity", "unitPrice", "discountAmount", "lineTotal"
        );
        assertThat(actualItemFields).isEqualTo(expectedItemFields);

        // 3. Khẳng định không tồn tại các khóa bị cấm trong toàn bộ JSON
        List<String> forbiddenKeys = List.of(
                "orderCode", "finalAmount", "updatedAt", "carrierName", "trackingNumber",
                "productThumbnailUrl", "variantId", "userId", "idempotencyKey",
                "voucherId", "dataSource", "id", "totalItemCount"
        );

        for (String forbiddenKey : forbiddenKeys) {
            assertThat(listItem.has(forbiddenKey)).as("List item must not contain forbidden key: " + forbiddenKey).isFalse();
            assertThat(previewItem.has(forbiddenKey)).as("Preview item must not contain forbidden key: " + forbiddenKey).isFalse();
            assertThat(detailRoot.has(forbiddenKey)).as("Detail response must not contain forbidden key: " + forbiddenKey).isFalse();
            assertThat(item.has(forbiddenKey)).as("Item response must not contain forbidden key: " + forbiddenKey).isFalse();
        }
    }

    @Test
    @DisplayName("Kịch bản 13 (Mới): Hệ thức tiền subtotal - discountAmount + shippingFee == totalAmount trên đơn checkout có voucher và ship > 0")
    void moneyFormulaVerificationOnCheckoutWithVoucher() throws Exception {
        Voucher voucher = voucherRepository.save(Voucher.builder()
                .code("SALE-FORMULA-" + System.nanoTime())
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("50000.00"))
                .minOrder(new BigDecimal("100000.00"))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .usageLimit(100)
                .perUserLimit(10)
                .isActive(true)
                .build());

        UserAddress address = userAddressRepository.save(UserAddress.builder()
                .user(customerA)
                .receiverName("Khách Hàng A")
                .phone(customerA.getPhone())
                .province("TP. Hà Nội")
                .district("Quận Hoàn Kiếm")
                .ward("Phường Tràng Tiền")
                .address("Số 1 Tràng Tiền")
                .isDefault(true)
                .build());

        CheckoutRequest checkoutReq = CheckoutRequest.builder()
                .addressId(address.getId())
                .paymentMethod(PaymentMethod.COD)
                .voucherCode(voucher.getCode())
                .items(List.of(
                        CheckoutItemRequest.builder().variantId(variant1.getId()).quantity(1).build()
                ))
                .build();

        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode checkoutRoot = objectMapper.readTree(checkoutResult.getResponse().getContentAsString());
        Long createdOrderId = checkoutRoot.get("orderId").asLong();

        // Gọi GET /api/orders/{id}
        MvcResult detailResult = mockMvc.perform(get("/api/orders/" + createdOrderId)
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode detailNode = objectMapper.readTree(detailResult.getResponse().getContentAsString());
        long subtotal = detailNode.get("subtotal").asLong();
        long discountAmount = detailNode.get("discountAmount").asLong();
        long shippingFee = detailNode.get("shippingFee").asLong();
        long totalAmount = detailNode.get("totalAmount").asLong();

        assertThat(discountAmount).isGreaterThan(0L);
        assertThat(shippingFee).isGreaterThan(0L);
        assertThat(subtotal - discountAmount + shippingFee)
                .as("Hệ thức subtotal - discountAmount + shippingFee == totalAmount phải luôn đúng")
                .isEqualTo(totalAmount);
    }

    @Test
    @DisplayName("Kịch bản 14 (Mới): Kiểm tra không N+1 - getPrepareStatementCount() khi gọi GET /api/orders của 1 đơn bằng 5 đơn")
    void verifyNoNPlusOneQueriesOnOrderList() throws Exception {
        // Tài khoản A có đúng 1 đơn (1 mặt hàng)
        createTestOrder(customerA, OrderStatus.PENDING, LocalDateTime.now(), false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo A", "M", "Đen", 1, new BigDecimal("250000.00"))
        ));

        // Tài khoản B có 5 đơn (mỗi đơn 2-3 mặt hàng)
        LocalDateTime baseTime = LocalDateTime.now().minusHours(10);
        for (int i = 0; i < 5; i++) {
            createTestOrder(customerB, OrderStatus.PENDING, baseTime.plusHours(i), false, List.of(
                    new OrderDetailItemSpec(variant1, "Áo Polo B " + i, "M", "Đen", 1, new BigDecimal("250000.00")),
                    new OrderDetailItemSpec(variant2, "Áo Polo Trắng B " + i, "L", "Trắng", 2, new BigDecimal("300000.00"))
            ));
        }

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);

        try {
            // Đo cho customerA (1 đơn, 1 dòng)
            statistics.clear();
            mockMvc.perform(get("/api/orders")
                            .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
            long count1 = statistics.getPrepareStatementCount();

            // Đo cho customerB (5 đơn, 10 dòng)
            statistics.clear();
            mockMvc.perform(get("/api/orders")
                            .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerB))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(5)));
            long count5 = statistics.getPrepareStatementCount();

            System.out.println("STATEMENT_COUNTS: 1_order=" + count1 + ", 5_orders=" + count5);
            assertEquals(count1, count5, String.format(
                    "Số câu lệnh prepared statement phải bằng nhau bất kể số đơn (1 đơn: %d, 5 đơn: %d)",
                    count1, count5
            ));
        } finally {
            statistics.setStatisticsEnabled(false);
            statistics.clear();
        }
    }

    @Test
    @DisplayName("Kịch bản 15 (Mới): Tham số status sai trả 400 Tiếng Việt; tham số sort do client gửi bị bỏ qua")
    void invalidStatusShouldReturn400AndClientSortShouldBeIgnored() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Order order1 = createTestOrder(customerA, OrderStatus.PENDING, now.minusHours(2), false, List.of(
                new OrderDetailItemSpec(variant1, "Áo Polo 1", "M", "Đen", 1, new BigDecimal("250000.00"))
        ));
        Order order2 = createTestOrder(customerA, OrderStatus.CONFIRMED, now.minusHours(1), false, List.of(
                new OrderDetailItemSpec(variant2, "Áo Polo 2", "L", "Trắng", 1, new BigDecimal("300000.00"))
        ));

        // 1. Status không hợp lệ -> 400 với thông báo tiếng Việt từ GlobalExceptionHandler
        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Tham số yêu cầu không đúng định dạng"));

        // 2. Client gửi param sort=createdAt,asc -> Bị bỏ qua, kết quả vẫn sắp xếp createdAt desc, id desc (order2 trước order1)
        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerA)))
                        .param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].orderId").value(order2.getId()))
                .andExpect(jsonPath("$.content[1].orderId").value(order1.getId()));
    }

    private Set<String> extractFieldNames(JsonNode node) {
        Set<String> fields = new HashSet<>();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            fields.add(it.next());
        }
        return fields;
    }
}
