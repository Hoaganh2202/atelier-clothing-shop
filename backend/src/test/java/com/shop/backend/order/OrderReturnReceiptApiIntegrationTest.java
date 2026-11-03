package com.shop.backend.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.inventory.InventoryMovementType;
import com.shop.backend.inventory.InventoryStockService;
import com.shop.backend.inventory.InventoryTransaction;
import com.shop.backend.inventory.InventoryTransactionRepository;
import com.shop.backend.inventory.InventoryVariantRepository;
import com.shop.backend.inventory.OrderReturnReceipt;
import com.shop.backend.inventory.OrderReturnReceiptRepository;
import com.shop.backend.inventory.dto.OrderStockItem;
import com.shop.backend.inventory.dto.ReturnItemCondition;
import com.shop.backend.order.dto.OrderReturnReceiptItemRequest;
import com.shop.backend.order.dto.OrderReturnReceiptRequest;
import com.shop.backend.order.dto.OrderStatusUpdateRequest;
import com.shop.backend.payment.PaymentMethod;
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
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class OrderReturnReceiptApiIntegrationTest {

    private static final String TEST_PREFIX = "order-ret-test-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private OrderReturnReceiptRepository orderReturnReceiptRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private InventoryVariantRepository inventoryVariantRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @SpyBean
    private InventoryStockService inventoryStockService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private User customerUser;
    private User adminUser;
    private User staffUser;
    private Category testCategory;
    private Product testProduct;
    private ProductVariant testVariant1;
    private ProductVariant testVariant2;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        cleanTestData();

        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(new Role(null, "customer")));
        Role adminRole = roleRepository.findByName("admin")
                .orElseGet(() -> roleRepository.save(new Role(null, "admin")));
        Role staffRole = roleRepository.findByName("staff")
                .orElseGet(() -> roleRepository.save(new Role(null, "staff")));

        customerUser = userRepository.save(User.builder()
                .name(TEST_PREFIX + "Customer")
                .email(TEST_PREFIX + "cust-" + System.nanoTime() + "@shop.com")
                .passwordHash("hashed-pw")
                .status(UserStatus.ACTIVE)
                .role(customerRole)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        adminUser = userRepository.save(User.builder()
                .name(TEST_PREFIX + "Admin")
                .email(TEST_PREFIX + "admin-" + System.nanoTime() + "@shop.com")
                .passwordHash("hashed-pw")
                .status(UserStatus.ACTIVE)
                .role(adminRole)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        staffUser = userRepository.save(User.builder()
                .name(TEST_PREFIX + "Staff")
                .email(TEST_PREFIX + "staff-" + System.nanoTime() + "@shop.com")
                .passwordHash("hashed-pw")
                .status(UserStatus.ACTIVE)
                .role(staffRole)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        testCategory = categoryRepository.save(Category.builder()
                .name(TEST_PREFIX + "Category")
                .slug(TEST_PREFIX + "cat-" + System.nanoTime())
                .isActive(true)
                .build());

        testProduct = productRepository.save(Product.builder()
                .category(testCategory)
                .name(TEST_PREFIX + "Product")
                .slug(TEST_PREFIX + "prod-" + System.nanoTime())
                .basePrice(new BigDecimal("200000.00"))
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        testVariant1 = productVariantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku(TEST_PREFIX + "SKU1-" + System.nanoTime())
                .size("M")
                .color("Trắng")
                .stock(50)
                .price(new BigDecimal("200000.00"))
                .build());

        testVariant2 = productVariantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku(TEST_PREFIX + "SKU2-" + System.nanoTime())
                .size("L")
                .color("Đen")
                .stock(30)
                .price(new BigDecimal("250000.00"))
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
        verifyDbClean();
    }

    private void cleanTestData() {
        transactionTemplate.execute(status -> {
            entityManager.createQuery("DELETE FROM OrderReturnReceipt orr WHERE orr.receiptNumber LIKE :prefix")
                    .setParameter("prefix", "%" + TEST_PREFIX.toUpperCase() + "%")
                    .executeUpdate();

            entityManager.createQuery("DELETE FROM OrderReturnReceipt orr WHERE orr.order.receiverName LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();

            entityManager.createQuery("DELETE FROM InventoryTransaction it WHERE it.order.receiverName LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();

            entityManager.createQuery("DELETE FROM InventoryTransaction it WHERE it.variant.sku LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();

            entityManager.createQuery("DELETE FROM OrderDetail od WHERE od.productName LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();

            entityManager.createQuery("DELETE FROM Order o WHERE o.receiverName LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();

            entityManager.createQuery("DELETE FROM ProductVariant pv WHERE pv.sku LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();

            entityManager.createQuery("DELETE FROM Product p WHERE p.name LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();

            entityManager.createQuery("DELETE FROM Category c WHERE c.name LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();

            entityManager.createQuery("DELETE FROM User u WHERE u.name LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .executeUpdate();

            return null;
        });
    }

    private void verifyDbClean() {
        transactionTemplate.execute(status -> {
            long receiptCount = (long) entityManager.createQuery("SELECT COUNT(orr) FROM OrderReturnReceipt orr WHERE orr.receiptNumber LIKE :prefix")
                    .setParameter("prefix", "%" + TEST_PREFIX.toUpperCase() + "%")
                    .getSingleResult();
            long txCount = (long) entityManager.createQuery("SELECT COUNT(it) FROM InventoryTransaction it WHERE it.variant.sku LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .getSingleResult();
            long variantCount = (long) entityManager.createQuery("SELECT COUNT(pv) FROM ProductVariant pv WHERE pv.sku LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .getSingleResult();

            assertThat(receiptCount).as("Dangling test order_return_receipts").isZero();
            assertThat(txCount).as("Dangling test inventory_transactions").isZero();
            assertThat(variantCount).as("Dangling test variants").isZero();
            return null;
        });
    }

    private Order createTestOrderWithDeductions(OrderStatus status, OrderStatus cancelledFrom, int qty1, int qty2) {
        return transactionTemplate.execute(txStatus -> {
            Order order = Order.builder()
                    .user(customerUser)
                    .status(status)
                    .cancelledFromStatus(cancelledFrom)
                    .subtotal(new BigDecimal("1000000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .shippingFee(BigDecimal.ZERO)
                    .totalAmount(new BigDecimal("1000000.00"))
                    .paymentMethod(PaymentMethod.COD)
                    .receiverName(TEST_PREFIX + "Receiver")
                    .receiverPhone("0988776655")
                    .province("Hà Nội")
                    .dataSource(DataSourceType.WEBSITE)
                    .createdAt(LocalDateTime.now())
                    .build();

            OrderDetail d1 = OrderDetail.builder()
                    .variant(testVariant1)
                    .productName(TEST_PREFIX + "Detail1")
                    .variantSize("M")
                    .variantColor("Trắng")
                    .quantity(qty1)
                    .unitPrice(new BigDecimal("200000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            order.addOrderDetail(d1);

            OrderDetail d2 = OrderDetail.builder()
                    .variant(testVariant2)
                    .productName(TEST_PREFIX + "Detail2")
                    .variantSize("L")
                    .variantColor("Đen")
                    .quantity(qty2)
                    .unitPrice(new BigDecimal("250000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            order.addOrderDetail(d2);

            Order savedOrder = orderRepository.save(order);

            // Deduct stock in DB as checkout would
            productVariantRepository.deductStock(testVariant1.getId(), qty1);
            productVariantRepository.deductStock(testVariant2.getId(), qty2);

            // Record CHECKOUT_DEDUCT logs
            List<OrderStockItem> items = List.of(
                    new OrderStockItem(d1.getId(), testVariant1.getId(), qty1),
                    new OrderStockItem(d2.getId(), testVariant2.getId(), qty2)
            );
            inventoryStockService.recordCheckoutDeduction(savedOrder.getId(), customerUser.getId(), items);

            return savedOrder;
        });
    }

    private UserPrincipal toPrincipal(User user) {
        return new UserPrincipal(user);
    }

    // =========================================================================
    // 1. Status & Provenance Tracking Tests
    // =========================================================================

    @Test
    @DisplayName("1.1. SHIPPING -> CANCELLED: lưu cancelled_from_status = SHIPPING, không tự động hoàn kho")
    void updateOrderStatus_ShippingToCancelled_SavesCancelledFromStatusShipping() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.SHIPPING, null, 2, 1);

        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.CANCELLED);

        mockMvc.perform(patch("/api/admin/orders/" + order.getId() + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledFromStatus").value("SHIPPING"));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(updatedOrder.getCancelledFromStatus()).isEqualTo(OrderStatus.SHIPPING);

        // Xác nhận Phase 1: SHIPPING -> CANCELLED KHÔNG tự động hoàn kho
        List<InventoryTransaction> cancelRestores = inventoryTransactionRepository
                .findByOrderIdAndMovementType(order.getId(), InventoryMovementType.CANCEL_RESTORE);
        assertThat(cancelRestores).isEmpty();
        // Stock vẫn là 48 và 29
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(48);
        assertThat(inventoryVariantRepository.findStockById(testVariant2.getId())).isEqualTo(29);
    }

    @Test
    @DisplayName("1.2. PENDING -> CANCELLED: lưu cancelled_from_status = PENDING và hoàn kho Phase 1")
    void updateOrderStatus_PendingToCancelled_SavesCancelledFromStatusPending() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.PENDING, null, 2, 1);

        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.CANCELLED);

        mockMvc.perform(patch("/api/admin/orders/" + order.getId() + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledFromStatus").value("PENDING"));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getCancelledFromStatus()).isEqualTo(OrderStatus.PENDING);

        // PENDING -> CANCELLED tự động hoàn kho theo Phase 1
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(50);
        assertThat(inventoryVariantRepository.findStockById(testVariant2.getId())).isEqualTo(30);
    }

    @Test
    @DisplayName("1.3. CONFIRMED -> CANCELLED: lưu cancelled_from_status = CONFIRMED và hoàn kho Phase 1")
    void updateOrderStatus_ConfirmedToCancelled_SavesCancelledFromStatusConfirmed() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.CONFIRMED, null, 2, 1);

        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.CANCELLED);

        mockMvc.perform(patch("/api/admin/orders/" + order.getId() + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledFromStatus").value("CONFIRMED"));

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getCancelledFromStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("1.4. Chuyển trạng thái khác (PENDING -> CONFIRMED): không ghi đè cancelled_from_status")
    void updateOrderStatus_NonCancelledTransition_DoesNotSetCancelledFromStatus() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.PENDING, null, 1, 1);

        OrderStatusUpdateRequest req = new OrderStatusUpdateRequest(OrderStatus.CONFIRMED);

        mockMvc.perform(patch("/api/admin/orders/" + order.getId() + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.cancelledFromStatus").doesNotExist());

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getCancelledFromStatus()).isNull();
    }

    // =========================================================================
    // 2. Return Receipt Eligibility Tests
    // =========================================================================

    @Test
    @DisplayName("2.1. Đơn CANCELLED từ SHIPPING (chưa có CANCEL_RESTORE): Được phép tạo return receipt")
    void processReturnReceipt_ShippingCancelledOrder_Success() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.CANCELLED, OrderStatus.SHIPPING, 2, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rcNum = TEST_PREFIX.toUpperCase() + "SHIP-RC-1";
        OrderReturnReceiptRequest request = new OrderReturnReceiptRequest(
                rcNum, "Nhận hàng hoàn từ giao hàng thất bại",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 2, ReturnItemCondition.RESTOCKABLE, "Nguyên vẹn"))
        );

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(order.getId()))
                .andExpect(jsonPath("$.receiptNumber").value(rcNum))
                .andExpect(jsonPath("$.restockedQuantity").value(2))
                .andExpect(jsonPath("$.discardedQuantity").value(0));

        // Variant 1 stock tăng lại: 48 + 2 = 50
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(50);
    }

    @Test
    @DisplayName("2.2. Đơn REFUNDED: Được phép tạo return receipt")
    void processReturnReceipt_RefundedOrder_Success() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.REFUNDED, null, 2, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rcNum = TEST_PREFIX.toUpperCase() + "REFUND-RC-1";
        OrderReturnReceiptRequest request = new OrderReturnReceiptRequest(
                rcNum, "Nhận hàng hoàn sau khi hoàn tiền",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Tốt"))
        );

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(staffUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNumber").value(rcNum))
                .andExpect(jsonPath("$.restockedQuantity").value(1));

        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(49);
    }

    @Test
    @DisplayName("2.3. Đơn CANCELLED từ PENDING: Từ chối 400 Bad Request")
    void processReturnReceipt_CancelledFromPending_RejectedBadRequest() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.CANCELLED, OrderStatus.PENDING, 2, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).get(0);

        OrderReturnReceiptRequest request = new OrderReturnReceiptRequest(
                TEST_PREFIX.toUpperCase() + "RC-PENDING", "Note",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Không thể nhận hàng hoàn cho đơn hàng đã hủy từ trạng thái: PENDING")));
    }

    @Test
    @DisplayName("2.4. Đơn CANCELLED từ CONFIRMED: Từ chối 400 Bad Request")
    void processReturnReceipt_CancelledFromConfirmed_RejectedBadRequest() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.CANCELLED, OrderStatus.CONFIRMED, 2, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).get(0);

        OrderReturnReceiptRequest request = new OrderReturnReceiptRequest(
                TEST_PREFIX.toUpperCase() + "RC-CONFIRMED", "Note",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Không thể nhận hàng hoàn cho đơn hàng đã hủy từ trạng thái: CONFIRMED")));
    }

    @Test
    @DisplayName("2.5. Đơn lịch sử CANCELLED có nguồn gốc NULL: Từ chối 400 và yêu cầu kiểm tra thủ công")
    void processReturnReceipt_HistoricalCancelledNullProvenance_RejectedBadRequest() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.CANCELLED, null, 2, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).get(0);

        OrderReturnReceiptRequest request = new OrderReturnReceiptRequest(
                TEST_PREFIX.toUpperCase() + "RC-HIST", "Note",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("nguồn gốc không xác định")));
    }

    @Test
    @DisplayName("2.6. Đơn CANCELLED từ SHIPPING nhưng đã có CANCEL_RESTORE: Từ chối 400")
    void processReturnReceipt_ShippingCancelledWithExistingCancelRestore_RejectedBadRequest() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.CANCELLED, OrderStatus.SHIPPING, 2, 1);
        List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
        OrderDetail d1 = details.get(0);

        // Giả lập đã có log CANCEL_RESTORE cho đơn này
        transactionTemplate.execute(status -> {
            InventoryTransaction cancelTx = InventoryTransaction.builder()
                    .variant(testVariant1)
                    .order(order)
                    .orderDetail(d1)
                    .movementType(InventoryMovementType.CANCEL_RESTORE)
                    .quantity(2)
                    .stockBefore(48)
                    .stockAfter(50)
                    .actorUser(adminUser)
                    .note("Đã hoàn kho trước đó")
                    .idempotencyKey("ORDER_CANCEL:" + d1.getId())
                    .build();
            return inventoryTransactionRepository.save(cancelTx);
        });

        OrderReturnReceiptRequest request = new OrderReturnReceiptRequest(
                TEST_PREFIX.toUpperCase() + "RC-ALREADY-RESTORED", "Note",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("CANCEL_RESTORE")));
    }

    @Test
    @DisplayName("2.7. Các trạng thái khác (PENDING, CONFIRMED, SHIPPING, COMPLETED): Từ chối 400")
    void processReturnReceipt_IneligibleStatuses_RejectedBadRequest() throws Exception {
        for (OrderStatus st : List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPING, OrderStatus.COMPLETED)) {
            Order order = createTestOrderWithDeductions(st, null, 1, 1);
            OrderDetail d = orderDetailRepository.findByOrderId(order.getId()).get(0);

            OrderReturnReceiptRequest req = new OrderReturnReceiptRequest(
                    TEST_PREFIX.toUpperCase() + "RC-" + st.name(), "Note",
                    List.of(new OrderReturnReceiptItemRequest(d.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Ok"))
            );

            mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                            .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Không thể tiếp nhận hàng hoàn cho đơn hàng ở trạng thái: " + st.name())));
        }
    }

    // =========================================================================
    // 3. Security & RBAC Tests
    // =========================================================================

    @Test
    @DisplayName("3.1. Customer không có quyền truy cập: 403 Forbidden")
    void processReturnReceipt_CustomerRole_Forbidden() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.REFUNDED, null, 1, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).get(0);

        OrderReturnReceiptRequest request = new OrderReturnReceiptRequest(
                TEST_PREFIX.toUpperCase() + "CUST-FORBIDDEN", "Note",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(customerUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3.2. Không xác thực (Unauthenticated): 401 Unauthorized")
    void processReturnReceipt_Unauthenticated_Unauthorized() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.REFUNDED, null, 1, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).get(0);

        OrderReturnReceiptRequest request = new OrderReturnReceiptRequest(
                TEST_PREFIX.toUpperCase() + "UNAUTH", "Note",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("3.3. Actor user ID được lấy từ authenticated principal và lưu chính xác")
    void processReturnReceipt_ActorUserIdRecordedCorrectly() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.REFUNDED, null, 2, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rcNum = TEST_PREFIX.toUpperCase() + "ACTOR-TEST";
        OrderReturnReceiptRequest request = new OrderReturnReceiptRequest(
                rcNum, "Kiểm tra actor user",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(staffUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        OrderReturnReceipt receipt = orderReturnReceiptRepository
                .findByOrderIdAndReceiptNumber(order.getId(), rcNum).orElseThrow();
        assertThat(receipt.getActorUser()).isNotNull();
        assertThat(receipt.getActorUser().getId()).isEqualTo(staffUser.getId());

        List<InventoryTransaction> txs = inventoryTransactionRepository
                .findByOrderIdAndMovementType(order.getId(), InventoryMovementType.RETURN_RESTOCK);
        assertThat(txs).isNotEmpty();
        assertThat(txs.get(0).getActorUser()).isNotNull();
        assertThat(txs.get(0).getActorUser().getId()).isEqualTo(staffUser.getId());
    }

    // =========================================================================
    // 4. Return Business Logic & Conditions Tests
    // =========================================================================

    @Test
    @DisplayName("4.1. DAMAGED không làm tăng stock bán được và lưu lý do đầy đủ")
    void processReturnReceipt_DamagedCondition_DoesNotIncreaseSellableStock() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.REFUNDED, null, 2, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rcNum = TEST_PREFIX.toUpperCase() + "DMG-LOG";
        OrderReturnReceiptRequest request = new OrderReturnReceiptRequest(
                rcNum, "Hàng hỏng",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 2, ReturnItemCondition.DAMAGED, "Bị ướt và rách tem"))
        );

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restockedQuantity").value(0))
                .andExpect(jsonPath("$.discardedQuantity").value(2));

        // Stock variant 1 không đổi: vẫn 48
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(48);

        List<InventoryTransaction> discards = inventoryTransactionRepository
                .findByOrderIdAndMovementType(order.getId(), InventoryMovementType.RETURN_DISCARD);
        assertThat(discards).hasSize(1);
        assertThat(discards.get(0).getNote()).isEqualTo("Bị ướt và rách tem");
    }

    @Test
    @DisplayName("4.2. DAMAGED không có lý do bị từ chối 400 Bad Request")
    void processReturnReceipt_DamagedWithoutReason_BadRequest() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.REFUNDED, null, 2, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).get(0);

        OrderReturnReceiptRequest request = new OrderReturnReceiptRequest(
                TEST_PREFIX.toUpperCase() + "DMG-NO-REASON", "Note",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 1, ReturnItemCondition.DAMAGED, null))
        );

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Lý do tiếp nhận hàng hỏng/lỗi không được để trống")));
    }

    @Test
    @DisplayName("4.3. Partial return nhiều receipt trên cùng 1 đơn hàng hoạt động chính xác")
    void processReturnReceipt_PartialReturn_MultipleReceipts_Success() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.REFUNDED, null, 3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        // Lần 1: Nhận 1 cái
        OrderReturnReceiptRequest req1 = new OrderReturnReceiptRequest(
                TEST_PREFIX.toUpperCase() + "PART-1", "Lần 1",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );
        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk());
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(48); // 47 + 1

        // Lần 2: Nhận 2 cái còn lại
        OrderReturnReceiptRequest req2 = new OrderReturnReceiptRequest(
                TEST_PREFIX.toUpperCase() + "PART-2", "Lần 2",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 2, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );
        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk());
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(50); // 48 + 2
    }

    @Test
    @DisplayName("4.4. Nhận vượt quá số lượng đã mua bị từ chối 400 Bad Request")
    void processReturnReceipt_OverReturnExceedingPurchased_RejectedBadRequest() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.REFUNDED, null, 2, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        // Mua 2, nhưng gửi nhận 3 cái
        OrderReturnReceiptRequest req = new OrderReturnReceiptRequest(
                TEST_PREFIX.toUpperCase() + "OVER-RETURN", "Lỗi",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 3, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("vượt quá số lượng đã mua")));
    }

    @Test
    @DisplayName("4.5. Replay cùng receiptNumber và payload: trả kết quả thành công cũ, không cộng stock lần 2")
    void processReturnReceipt_DuplicateReceiptNumberSamePayload_IdempotentReplay() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.REFUNDED, null, 2, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rcNum = TEST_PREFIX.toUpperCase() + "REPLAY-API";
        OrderReturnReceiptRequest req = new OrderReturnReceiptRequest(
                rcNum, "Replay test",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );

        // Gọi lần 1
        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restockedQuantity").value(1));
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(49);

        // Gọi lần 2
        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restockedQuantity").value(1));

        // Tồn kho KHÔNG tăng tiếp!
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(49);
    }

    @Test
    @DisplayName("4.6. Trùng receiptNumber nhưng khác payload: trả HTTP 409 Conflict")
    void processReturnReceipt_DuplicateReceiptNumberDifferentPayload_Conflict() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.REFUNDED, null, 3, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rcNum = TEST_PREFIX.toUpperCase() + "CONFLICT-API";
        OrderReturnReceiptRequest req1 = new OrderReturnReceiptRequest(
                rcNum, "Gốc",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );
        OrderReturnReceiptRequest req2 = new OrderReturnReceiptRequest(
                rcNum, "Khác payload",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 2, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("với nội dung khác")));
    }

    @Test
    @DisplayName("4.7. Variant inactive vẫn được tiếp nhận và hoàn tồn kho")
    void processReturnReceipt_InactiveVariant_StillProcessed() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.REFUNDED, null, 2, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        // Deactivate variant's product
        transactionTemplate.execute(status -> {
            Product p = productRepository.findById(testProduct.getId()).orElseThrow();
            p.setIsActive(false);
            productRepository.save(p);
            return null;
        });

        OrderReturnReceiptRequest req = new OrderReturnReceiptRequest(
                TEST_PREFIX.toUpperCase() + "INACTIVE-API", "Restock inactive",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Vẫn restock"))
        );

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Stock vẫn tăng: 48 + 1 = 49
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(49);
    }

    @Test
    @DisplayName("4.8. Sau khi nhận return receipt, trạng thái đơn hàng không bị thay đổi")
    void processReturnReceipt_DoesNotChangeOrderStatus() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.CANCELLED, OrderStatus.SHIPPING, 2, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).get(0);

        OrderReturnReceiptRequest req = new OrderReturnReceiptRequest(
                TEST_PREFIX.toUpperCase() + "KEEP-STATUS", "Note",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Order reloadedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(reloadedOrder.getCancelledFromStatus()).isEqualTo(OrderStatus.SHIPPING);
    }

    @Test
    @DisplayName("4.9. Lỗi giữa transaction: rollback toàn bộ, không có receipt hay movement log nào được lưu")
    void processReturnReceipt_RollbackOnFailure_NoStockOrLogCreated() throws Exception {
        Order order = createTestOrderWithDeductions(OrderStatus.REFUNDED, null, 2, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).get(0);

        String rcNum = TEST_PREFIX.toUpperCase() + "ROLLBACK-API";
        OrderReturnReceiptRequest req = new OrderReturnReceiptRequest(
                rcNum, "Rollback",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );

        // Spy throw exception khi inventoryStockService.processReturnReceipt được gọi
        doThrow(new RuntimeException("Simulated error in inventory engine"))
                .when(inventoryStockService).processReturnReceipt(any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                        .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isInternalServerError());

        // Stock không đổi
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(48);
        // Không có receipt
        assertThat(orderReturnReceiptRepository.findByOrderIdAndReceiptNumber(order.getId(), rcNum)).isEmpty();
    }

    // =========================================================================
    // 5. Concurrency Testing
    // =========================================================================

    @Test
    @DisplayName("5.1. Hai requests đồng thời gửi partial return trên các receipt khác nhau không vượt quá stock đã mua")
    void processReturnReceipt_ConcurrentRequests_CannotExceedPurchasedStock() throws Exception {
        // Đơn mua 3 cái
        Order order = createTestOrderWithDeductions(OrderStatus.REFUNDED, null, 3, 1);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        // Request A: nhận 2 cái, Request B: nhận 2 cái (Tổng 4 > 3 -> 1 request phải thất bại)
        String rcA = TEST_PREFIX.toUpperCase() + "CONC-A";
        String rcB = TEST_PREFIX.toUpperCase() + "CONC-B";

        OrderReturnReceiptRequest reqA = new OrderReturnReceiptRequest(
                rcA, "A",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 2, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );
        OrderReturnReceiptRequest reqB = new OrderReturnReceiptRequest(
                rcB, "B",
                List.of(new OrderReturnReceiptItemRequest(d1.getId(), 2, ReturnItemCondition.RESTOCKABLE, "Ok"))
        );

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger badRequestCount = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        futures.add(executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                                .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reqA)))
                        .andDo(result -> {
                            if (result.getResponse().getStatus() == 200) {
                                successCount.incrementAndGet();
                            } else if (result.getResponse().getStatus() == 400) {
                                badRequestCount.incrementAndGet();
                            }
                        });
            } catch (Exception ignored) {}
        }));

        futures.add(executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                mockMvc.perform(post("/api/admin/orders/" + order.getId() + "/returns/receipt")
                                .with(SecurityMockMvcRequestPostProcessors.user(toPrincipal(adminUser)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(reqB)))
                        .andDo(result -> {
                            if (result.getResponse().getStatus() == 200) {
                                successCount.incrementAndGet();
                            } else if (result.getResponse().getStatus() == 400) {
                                badRequestCount.incrementAndGet();
                            }
                        });
            } catch (Exception ignored) {}
        }));

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(badRequestCount.get()).isEqualTo(1);

        // Stock tăng đúng 2 (47 + 2 = 49), không bao giờ thành 51
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(49);
    }
}
