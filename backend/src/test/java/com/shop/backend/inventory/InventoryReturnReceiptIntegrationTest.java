package com.shop.backend.inventory;

import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.exception.ConflictException;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.inventory.dto.OrderReturnReceiptItem;
import com.shop.backend.inventory.dto.OrderReturnReceiptResult;
import com.shop.backend.inventory.dto.OrderStockItem;
import com.shop.backend.inventory.dto.OrderStockRestoreResult;
import com.shop.backend.inventory.dto.ReturnItemCondition;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class InventoryReturnReceiptIntegrationTest {

    private static final String TEST_PREFIX = "invreturn-test-";

    @Autowired
    private InventoryStockService inventoryStockService;

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private User testUser;
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
                .orElseGet(() -> roleRepository.save(Role.builder().name("customer").build()));
        Role staffRole = roleRepository.findByName("staff")
                .orElseGet(() -> roleRepository.save(Role.builder().name("staff").build()));

        testUser = userRepository.save(User.builder()
                .name(TEST_PREFIX + "Customer")
                .email(TEST_PREFIX + "cust-" + System.nanoTime() + "@shop.com")
                .passwordHash("hashed-pw")
                .status(UserStatus.ACTIVE)
                .role(customerRole)
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

            entityManager.createQuery("DELETE FROM InventoryTransaction it WHERE it.idempotencyKey LIKE :prefix")
                    .setParameter("prefix", "%" + TEST_PREFIX.toUpperCase() + "%")
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

    private Order createTestOrderWithDeductions(int qty1, int qty2) {
        return transactionTemplate.execute(status -> {
            Order order = Order.builder()
                    .user(testUser)
                    .status(OrderStatus.REFUNDED)
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
            inventoryStockService.recordCheckoutDeduction(savedOrder.getId(), testUser.getId(), items);

            return savedOrder;
        });
    }

    // =========================================================================
    // 18 Integration Tests for PR-C1
    // =========================================================================

    @Test
    @DisplayName("1. Nhận hàng RESTOCKABLE toàn bộ, cộng stock đúng, ghi log RETURN_RESTOCK")
    void returnReceiptFullRestockShouldRestoreStockAndCreateLogs() {
        Order order = createTestOrderWithDeductions(3, 2);
        List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
        OrderDetail d1 = details.stream().filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();
        OrderDetail d2 = details.stream().filter(d -> d.getVariant().getId().equals(testVariant2.getId())).findFirst().orElseThrow();

        String receiptNum = TEST_PREFIX.toUpperCase() + "RC-01";
        List<OrderReturnReceiptItem> items = List.of(
                new OrderReturnReceiptItem(d1.getId(), 3, ReturnItemCondition.RESTOCKABLE, "Nguyên vẹn"),
                new OrderReturnReceiptItem(d2.getId(), 2, ReturnItemCondition.RESTOCKABLE, "Nguyên vẹn")
        );

        OrderReturnReceiptResult result = inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), receiptNum, "Nhận hàng trả đầy đủ", items
        );

        assertThat(result.totalReceivedQuantity()).isEqualTo(5);
        assertThat(result.restockedQuantity()).isEqualTo(5);
        assertThat(result.discardedQuantity()).isEqualTo(0);

        // Verify reloaded stock restored to initial (50, 30)
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(50);
        assertThat(inventoryVariantRepository.findStockById(testVariant2.getId())).isEqualTo(30);

        // Verify receipt header
        OrderReturnReceipt receipt = orderReturnReceiptRepository
                .findByOrderIdAndReceiptNumber(order.getId(), receiptNum).orElseThrow();
        assertThat(receipt.getTotalReceivedQuantity()).isEqualTo(5);
        assertThat(receipt.getRestockedQuantity()).isEqualTo(5);
        assertThat(receipt.getDiscardedQuantity()).isEqualTo(0);
        assertThat(receipt.getActorUser().getId()).isEqualTo(staffUser.getId());

        // Verify inventory transactions
        List<InventoryTransaction> returnLogs = inventoryTransactionRepository
                .findByOrderIdAndMovementType(order.getId(), InventoryMovementType.RETURN_RESTOCK);
        assertThat(returnLogs).hasSize(2);
    }

    @Test
    @DisplayName("2. Partial return nhiều receipt: nhận lần 1 rồi nhận phần còn lại lần 2")
    void returnReceiptPartialShouldAllowMultipleReceipts() {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        // Lần 1: Nhận 1 cái
        String rc1 = TEST_PREFIX.toUpperCase() + "PART-01";
        inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc1, "Đợt 1",
                List.of(new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Áo 1"))
        );
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(48); // 47 + 1

        // Lần 2: Nhận 2 cái còn lại
        String rc2 = TEST_PREFIX.toUpperCase() + "PART-02";
        inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc2, "Đợt 2",
                List.of(new OrderReturnReceiptItem(d1.getId(), 2, ReturnItemCondition.RESTOCKABLE, "Áo 2, 3"))
        );
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(50); // 48 + 2
    }

    @Test
    @DisplayName("3. Vượt số lượng đã mua bị từ chối 400 Bad Request")
    void returnReceiptExceedingPurchasedQuantityShouldFail() {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d2 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant2.getId())).findFirst().orElseThrow();

        // Mua 2 cái, nhưng request nhận 3 cái
        String rc = TEST_PREFIX.toUpperCase() + "EXCEED-01";
        assertThatThrownBy(() -> inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Quá số lượng",
                List.of(new OrderReturnReceiptItem(d2.getId(), 3, ReturnItemCondition.RESTOCKABLE, "Lỗi"))
        )).isInstanceOf(BadRequestException.class)
          .hasMessageContaining("vượt quá số lượng đã mua");
    }

    @Test
    @DisplayName("4. Replay cùng receipt và payload: trả kết quả cũ, không cộng kho lần hai")
    void returnReceiptReplaySamePayloadShouldNotDoubleRestock() {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rc = TEST_PREFIX.toUpperCase() + "REPLAY-01";
        List<OrderReturnReceiptItem> items = List.of(
                new OrderReturnReceiptItem(d1.getId(), 2, ReturnItemCondition.RESTOCKABLE, "Áo đẹp")
        );

        OrderReturnReceiptResult res1 = inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Replay test", items
        );
        assertThat(res1.restockedQuantity()).isEqualTo(2);
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(49); // 47 + 2

        // Replay lần 2
        OrderReturnReceiptResult res2 = inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Replay test", items
        );
        assertThat(res2.restockedQuantity()).isEqualTo(2);
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(49); // Không tăng tiếp!
    }

    @Test
    @DisplayName("5. Cùng receipt nhưng đổi quantity bị 409 Conflict")
    void returnReceiptSameReceiptDifferentQuantityShouldFailConflict() {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rc = TEST_PREFIX.toUpperCase() + "DIFF-QTY";
        inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Test",
                List.of(new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Gốc"))
        );

        assertThatThrownBy(() -> inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Test",
                List.of(new OrderReturnReceiptItem(d1.getId(), 2, ReturnItemCondition.RESTOCKABLE, "Gốc"))
        )).isInstanceOf(ConflictException.class)
          .hasMessageContaining("với nội dung khác");
    }

    @Test
    @DisplayName("6. Cùng receipt nhưng đổi condition bị 409 Conflict")
    void returnReceiptSameReceiptDifferentConditionShouldFailConflict() {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rc = TEST_PREFIX.toUpperCase() + "DIFF-COND";
        inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Test",
                List.of(new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Gốc"))
        );

        assertThatThrownBy(() -> inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Test",
                List.of(new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.DAMAGED, "Gốc"))
        )).isInstanceOf(ConflictException.class)
          .hasMessageContaining("với nội dung khác");
    }

    @Test
    @DisplayName("7. Cùng receipt nhưng đổi reason/note bị 409 Conflict")
    void returnReceiptSameReceiptDifferentReasonShouldFailConflict() {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rc = TEST_PREFIX.toUpperCase() + "DIFF-REASON";
        inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Ghi chú ban đầu",
                List.of(new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Gốc"))
        );

        assertThatThrownBy(() -> inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Ghi chú đã bị sửa",
                List.of(new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Gốc"))
        )).isInstanceOf(ConflictException.class)
          .hasMessageContaining("với nội dung khác");
    }

    @Test
    @DisplayName("8. Thêm hoặc bớt item trong retry cùng receipt bị 409 Conflict")
    void returnReceiptSameReceiptAddedOrRemovedItemShouldFailConflict() {
        Order order = createTestOrderWithDeductions(3, 2);
        List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
        OrderDetail d1 = details.stream().filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();
        OrderDetail d2 = details.stream().filter(d -> d.getVariant().getId().equals(testVariant2.getId())).findFirst().orElseThrow();

        String rc = TEST_PREFIX.toUpperCase() + "DIFF-ITEMS";
        inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Note",
                List.of(new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Detail 1"))
        );

        // Thêm item detail 2
        assertThatThrownBy(() -> inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Note",
                List.of(
                        new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Detail 1"),
                        new OrderReturnReceiptItem(d2.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Detail 2")
                )
        )).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("9. Hai item cùng detail nhưng khác condition trong cùng receipt được xử lý đúng")
    void returnReceiptSameDetailDifferentConditionShouldSucceed() {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rc = TEST_PREFIX.toUpperCase() + "MIX-COND-DETAIL";
        List<OrderReturnReceiptItem> items = List.of(
                new OrderReturnReceiptItem(d1.getId(), 2, ReturnItemCondition.RESTOCKABLE, "Áo lành lặn"),
                new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.DAMAGED, "Áo bị rách")
        );

        OrderReturnReceiptResult res = inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Hỗn hợp condition", items
        );

        assertThat(res.totalReceivedQuantity()).isEqualTo(3);
        assertThat(res.restockedQuantity()).isEqualTo(2);
        assertThat(res.discardedQuantity()).isEqualTo(1);

        // Variant 1 stock chỉ tăng 2: 47 + 2 = 49
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(49);
    }

    @Test
    @DisplayName("10. Duplicate item cùng detail và condition trong cùng receipt bị từ chối 400 Bad Request")
    void returnReceiptDuplicateSameDetailAndConditionShouldFail() {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rc = TEST_PREFIX.toUpperCase() + "DUP-ITEM";
        List<OrderReturnReceiptItem> items = List.of(
                new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Lần a"),
                new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Lần b trùng lặp")
        );

        assertThatThrownBy(() -> inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Dup", items
        )).isInstanceOf(BadRequestException.class)
          .hasMessageContaining("bị lặp lại trong cùng biên bản tiếp nhận");
    }

    @Test
    @DisplayName("11a. Concurrent duplicate receipt cùng payload: cả hai cùng thành công, chỉ 1 receipt và stock tăng 1 lần")
    void returnReceiptConcurrentDuplicateSamePayloadShouldBothSucceedWithReplay() throws Exception {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rc = TEST_PREFIX.toUpperCase() + "CONC-SAME";
        List<OrderReturnReceiptItem> items = List.of(
                new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Áo")
        );

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<OrderReturnReceiptResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return inventoryStockService.processReturnReceipt(order.getId(), staffUser.getId(), rc, "Concurrent same", items);
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        OrderReturnReceiptResult res1 = futures.get(0).get(10, TimeUnit.SECONDS);
        OrderReturnReceiptResult res2 = futures.get(1).get(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(res1).isNotNull();
        assertThat(res2).isNotNull();
        assertThat(res1.receiptNumber()).isEqualTo(rc);
        assertThat(res2.receiptNumber()).isEqualTo(rc);
        assertThat(res1.restockedQuantity()).isEqualTo(1);
        assertThat(res2.restockedQuantity()).isEqualTo(1);

        // Tồn kho chỉ được cộng đúng 1 lần (từ 47 lên 48)
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(48);

        // Số bản ghi receipt trong DB đúng 1
        long receiptCount = orderReturnReceiptRepository
                .findByOrderIdAndReceiptNumber(order.getId(), rc).stream().count();
        assertThat(receiptCount).isEqualTo(1);

        // Số bản ghi transaction RETURN_RESTOCK đúng 1
        List<InventoryTransaction> txs = inventoryTransactionRepository
                .findByOrderIdAndMovementType(order.getId(), InventoryMovementType.RETURN_RESTOCK);
        assertThat(txs).hasSize(1);
    }

    @Test
    @DisplayName("11b. Concurrent duplicate receipt khác payload: 1 request thành công, 1 request bị ConflictException")
    void returnReceiptConcurrentDuplicateDifferentPayloadShouldHaveOneSucceedAndOneConflict() throws Exception {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rc = TEST_PREFIX.toUpperCase() + "CONC-DIFF";
        List<OrderReturnReceiptItem> items1 = List.of(
                new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Áo 1 cái")
        );
        List<OrderReturnReceiptItem> items2 = List.of(
                new OrderReturnReceiptItem(d1.getId(), 2, ReturnItemCondition.RESTOCKABLE, "Áo 2 cái")
        );

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<OrderReturnReceiptResult>> futures = new ArrayList<>();
        futures.add(executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            return inventoryStockService.processReturnReceipt(order.getId(), staffUser.getId(), rc, "Concurrent diff", items1);
        }));
        futures.add(executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            return inventoryStockService.processReturnReceipt(order.getId(), staffUser.getId(), rc, "Concurrent diff", items2);
        }));

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        int successCount = 0;
        int conflictCount = 0;
        OrderReturnReceiptResult successResult = null;

        for (Future<OrderReturnReceiptResult> future : futures) {
            try {
                successResult = future.get(10, TimeUnit.SECONDS);
                successCount++;
            } catch (Exception ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof ConflictException) {
                    conflictCount++;
                } else {
                    throw ex;
                }
            }
        }
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(1);
        assertThat(successResult).isNotNull();

        // Số bản ghi receipt trong DB đúng 1
        long receiptCount = orderReturnReceiptRepository
                .findByOrderIdAndReceiptNumber(order.getId(), rc).stream().count();
        assertThat(receiptCount).isEqualTo(1);

        // Stock tăng đúng theo request thành công
        int expectedStock = 47 + successResult.restockedQuantity();
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(expectedStock);

        // Số bản ghi transaction đúng 1
        List<InventoryTransaction> txs = inventoryTransactionRepository
                .findByOrderIdAndMovementType(order.getId(), InventoryMovementType.RETURN_RESTOCK);
        assertThat(txs).hasSize(1);
    }

    @Test
    @DisplayName("12. DAMAGED ghi RETURN_DISCARD nhưng stock không đổi")
    void returnReceiptDamagedItemsShouldLogDiscardWithoutChangingStock() {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rc = TEST_PREFIX.toUpperCase() + "DAMAGED-ONLY";
        List<OrderReturnReceiptItem> items = List.of(
                new OrderReturnReceiptItem(d1.getId(), 2, ReturnItemCondition.DAMAGED, "Hàng bị ẩm mốc rách tem")
        );

        OrderReturnReceiptResult res = inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Damaged only", items
        );

        assertThat(res.restockedQuantity()).isEqualTo(0);
        assertThat(res.discardedQuantity()).isEqualTo(2);

        // Stock variant 1 KHÔNG ĐỔI: 47
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(47);

        // Có transaction log RETURN_DISCARD
        List<InventoryTransaction> discardLogs = inventoryTransactionRepository
                .findByOrderIdAndMovementType(order.getId(), InventoryMovementType.RETURN_DISCARD);
        assertThat(discardLogs).hasSize(1);
        InventoryTransaction tx = discardLogs.get(0);
        assertThat(tx.getStockBefore()).isEqualTo(47);
        assertThat(tx.getStockAfter()).isEqualTo(47);
        assertThat(tx.getNote()).isEqualTo("Hàng bị ẩm mốc rách tem");
    }

    @Test
    @DisplayName("13. Mixed RESTOCKABLE và DAMAGED trên nhiều biến thể cập nhật chọn lọc")
    void returnReceiptMixedRestockableAndDamagedShouldUpdateStockSelectively() {
        Order order = createTestOrderWithDeductions(3, 2);
        List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
        OrderDetail d1 = details.stream().filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();
        OrderDetail d2 = details.stream().filter(d -> d.getVariant().getId().equals(testVariant2.getId())).findFirst().orElseThrow();

        String rc = TEST_PREFIX.toUpperCase() + "MIX-MULTI-VAR";
        List<OrderReturnReceiptItem> items = List.of(
                new OrderReturnReceiptItem(d1.getId(), 2, ReturnItemCondition.RESTOCKABLE, "Variant 1 lành lặn"),
                new OrderReturnReceiptItem(d2.getId(), 1, ReturnItemCondition.DAMAGED, "Variant 2 rách")
        );

        OrderReturnReceiptResult res = inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Mixed items", items
        );

        assertThat(res.totalReceivedQuantity()).isEqualTo(3);
        assertThat(res.restockedQuantity()).isEqualTo(2);
        assertThat(res.discardedQuantity()).isEqualTo(1);

        // Variant 1 stock tăng 2: 47 -> 49
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(49);
        // Variant 2 stock không đổi: 28
        assertThat(inventoryVariantRepository.findStockById(testVariant2.getId())).isEqualTo(28);
    }

    @Test
    @DisplayName("14. Variant inactive vẫn restock chính xác")
    void returnReceiptWithInactiveVariantShouldStillRestock() {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        // Deactivate product
        transactionTemplate.execute(status -> {
            Product p = productRepository.findById(testProduct.getId()).orElseThrow();
            p.setIsActive(false);
            productRepository.save(p);
            return null;
        });

        String rc = TEST_PREFIX.toUpperCase() + "INACTIVE-RESTOCK";
        inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Inactive restock",
                List.of(new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Vẫn restock"))
        );

        // Stock vẫn tăng: 47 + 1 = 48
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(48);
    }

    @Test
    @DisplayName("15. Checkout deduction không tồn tại hoặc không khớp thì từ chối 400 Bad Request")
    void returnReceiptMissingCheckoutDeductionLogShouldFail() {
        Order order = createTestOrderWithDeductions(3, 2);

        // Tạo 1 detail thứ 3 giả không hề có CHECKOUT_DEDUCT log
        OrderDetail fakeDetail = transactionTemplate.execute(status -> {
            OrderDetail d3 = OrderDetail.builder()
                    .order(order)
                    .variant(testVariant1)
                    .productName(TEST_PREFIX + "FakeDetail")
                    .variantSize("M")
                    .variantColor("Trắng")
                    .quantity(1)
                    .unitPrice(new BigDecimal("200000.00"))
                    .discountAmount(BigDecimal.ZERO)
                    .build();
            return orderDetailRepository.save(d3);
        });

        String rc = TEST_PREFIX.toUpperCase() + "NO-LOG";
        assertThatThrownBy(() -> inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "No log",
                List.of(new OrderReturnReceiptItem(fakeDetail.getId(), 1, ReturnItemCondition.RESTOCKABLE, "No log"))
        )).isInstanceOf(BadRequestException.class)
          .hasMessageContaining("Không tìm thấy dữ liệu trừ kho ban đầu");
    }

    @Test
    @DisplayName("16. Transaction rollback khi có lỗi xảy ra ở giữa quá trình")
    void processReturnReceiptShouldRollbackWhenStockRestorationFails() {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rc = TEST_PREFIX.toUpperCase() + "ROLLBACK-TEST";

        // Bọc trong transaction và cố ý ném exception sau khi gọi processReturnReceipt
        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            inventoryStockService.processReturnReceipt(
                    order.getId(), staffUser.getId(), rc, "Sẽ rollback",
                    List.of(new OrderReturnReceiptItem(d1.getId(), 2, ReturnItemCondition.RESTOCKABLE, "Ok"))
            );
            throw new RuntimeException("Cố ý ném lỗi để kiểm tra rollback");
        })).isInstanceOf(RuntimeException.class);

        // Xác minh rollback: Stock không tăng (vẫn 47)
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(47);

        // Không có receipt nào được lưu
        assertThat(orderReturnReceiptRepository.findByOrderIdAndReceiptNumber(order.getId(), rc)).isEmpty();
    }

    @Test
    @DisplayName("17. Receipt header và movement records cùng commit nguyên tử")
    void returnReceiptReceiptHeaderAndMovementMustCommitTogether() {
        Order order = createTestOrderWithDeductions(3, 2);
        List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
        OrderDetail d1 = details.stream().filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();
        OrderDetail d2 = details.stream().filter(d -> d.getVariant().getId().equals(testVariant2.getId())).findFirst().orElseThrow();

        String rc = TEST_PREFIX.toUpperCase() + "ATOMIC-COMMIT";
        inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Atomic",
                List.of(
                        new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Item 1"),
                        new OrderReturnReceiptItem(d2.getId(), 1, ReturnItemCondition.DAMAGED, "Item 2")
                )
        );

        OrderReturnReceipt receipt = orderReturnReceiptRepository
                .findByOrderIdAndReceiptNumber(order.getId(), rc).orElseThrow();
        assertThat(receipt.getTotalReceivedQuantity()).isEqualTo(2);

        String prefix = "RETURN_RECEIPT:" + receipt.getId() + ":";
        List<InventoryTransaction> txs = inventoryTransactionRepository.findByIdempotencyKeyStartingWith(prefix);
        assertThat(txs).hasSize(2);
    }

    @Test
    @DisplayName("18. Không ảnh hưởng Phase 1 cancellation restoration và checkout deduction")
    void returnReceiptShouldNotAffectPhase1CancellationRestoration() {
        // Test checkout deduction
        Order order = createTestOrderWithDeductions(2, 1);
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(48);
        assertThat(inventoryVariantRepository.findStockById(testVariant2.getId())).isEqualTo(29);

        // Test cancellation restoration Phase 1
        List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
        List<OrderStockItem> cancelItems = details.stream()
                .map(d -> new OrderStockItem(d.getId(), d.getVariant().getId(), d.getQuantity()))
                .toList();

        OrderStockRestoreResult restoreRes = inventoryStockService.restoreStockForCancelledOrder(
                order.getId(), staffUser.getId(), "Hủy đơn Phase 1", cancelItems
        );

        assertThat(restoreRes.restoredItemsCount()).isEqualTo(2);
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(50);
        assertThat(inventoryVariantRepository.findStockById(testVariant2.getId())).isEqualTo(30);
    }

    @Test
    @DisplayName("19. Replay với cùng các item nhưng đảo thứ tự trong danh sách: vẫn nhận diện cùng hash và replay")
    void returnReceiptSamePayloadDifferentItemOrderShouldReplay() {
        Order order = createTestOrderWithDeductions(3, 2);
        List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());
        OrderDetail d1 = details.stream().filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();
        OrderDetail d2 = details.stream().filter(d -> d.getVariant().getId().equals(testVariant2.getId())).findFirst().orElseThrow();

        String rc = TEST_PREFIX.toUpperCase() + "ORDER-PERM";
        List<OrderReturnReceiptItem> itemsOrder1 = List.of(
                new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Item 1"),
                new OrderReturnReceiptItem(d2.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Item 2")
        );
        List<OrderReturnReceiptItem> itemsOrder2 = List.of(
                new OrderReturnReceiptItem(d2.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Item 2"),
                new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Item 1")
        );

        OrderReturnReceiptResult res1 = inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Replay permutation", itemsOrder1
        );
        assertThat(res1.restockedQuantity()).isEqualTo(2);
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(48);
        assertThat(inventoryVariantRepository.findStockById(testVariant2.getId())).isEqualTo(29);

        // Lần 2 với itemsOrder2 (đảo thứ tự item trong danh sách)
        OrderReturnReceiptResult res2 = inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Replay permutation", itemsOrder2
        );
        assertThat(res2.restockedQuantity()).isEqualTo(2);

        // Stock không tăng lần 2!
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(48);
        assertThat(inventoryVariantRepository.findStockById(testVariant2.getId())).isEqualTo(29);
    }

    @Test
    @DisplayName("20. receiptNumber có khoảng trắng và chữ thường được trim và uppercase, nhận diện đúng receipt để replay")
    void returnReceiptNormalizationTrimAndUppercaseShouldRecognizeSameReceipt() {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rawRc1 = TEST_PREFIX.toLowerCase() + "norm-01";
        String rawRc2 = "  " + TEST_PREFIX.toUpperCase() + "NORM-01  ";

        List<OrderReturnReceiptItem> items = List.of(
                new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Item 1")
        );

        OrderReturnReceiptResult res1 = inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rawRc1, "Norm test", items
        );
        assertThat(res1.receiptNumber()).isEqualTo(TEST_PREFIX.toUpperCase() + "NORM-01");
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(48);

        // Lần 2 gọi với rawRc2 chứa khoảng trắng và uppercase
        OrderReturnReceiptResult res2 = inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rawRc2, "Norm test", items
        );
        assertThat(res2.receiptNumber()).isEqualTo(TEST_PREFIX.toUpperCase() + "NORM-01");

        // Không tăng stock lần 2
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(48);

        // Chỉ có 1 receipt header
        long count = orderReturnReceiptRepository
                .findByOrderIdAndReceiptNumber(order.getId(), TEST_PREFIX.toUpperCase() + "NORM-01").stream().count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("21. DAMAGED không có lý do (null hoặc blank) bị từ chối 400 Bad Request, không đổi stock")
    void returnReceiptDamagedMissingReasonShouldFailValidation() {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String rc1 = TEST_PREFIX.toUpperCase() + "DMG-NULL";
        assertThatThrownBy(() -> inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc1, "Note",
                List.of(new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.DAMAGED, null))
        )).isInstanceOf(BadRequestException.class)
          .hasMessageContaining("Lý do tiếp nhận hàng hỏng/lỗi không được để trống");

        String rc2 = TEST_PREFIX.toUpperCase() + "DMG-BLANK";
        assertThatThrownBy(() -> inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc2, "Note",
                List.of(new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.DAMAGED, "   "))
        )).isInstanceOf(BadRequestException.class)
          .hasMessageContaining("Lý do tiếp nhận hàng hỏng/lỗi không được để trống");

        // Verify không đổi stock
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(47);
        // Verify không có receipt
        assertThat(orderReturnReceiptRepository.findByOrderIdAndReceiptNumber(order.getId(), rc1)).isEmpty();
        assertThat(orderReturnReceiptRepository.findByOrderIdAndReceiptNumber(order.getId(), rc2)).isEmpty();
        // Verify không có transaction log
        assertThat(inventoryTransactionRepository.findByOrderIdAndMovementType(order.getId(), InventoryMovementType.RETURN_DISCARD)).isEmpty();
    }

    @Test
    @DisplayName("22. DAMAGED có lý do vượt quá 500 ký tự bị từ chối 400 Bad Request, không truncate ngầm")
    void returnReceiptDamagedReasonExceedingMaxLengthShouldFail() {
        Order order = createTestOrderWithDeductions(3, 2);
        OrderDetail d1 = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String longReason = "A".repeat(501);
        String rc = TEST_PREFIX.toUpperCase() + "DMG-TOO-LONG";
        assertThatThrownBy(() -> inventoryStockService.processReturnReceipt(
                order.getId(), staffUser.getId(), rc, "Note",
                List.of(new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.DAMAGED, longReason))
        )).isInstanceOf(BadRequestException.class)
          .hasMessageContaining("Lý do tiếp nhận tối đa 500 ký tự");

        // Verify không đổi stock
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(47);
        // Verify không có receipt
        assertThat(orderReturnReceiptRepository.findByOrderIdAndReceiptNumber(order.getId(), rc)).isEmpty();
    }

    @Test
    @DisplayName("23. Cùng receiptNumber trên hai Order khác nhau không bị va chạm movement idempotency key")
    void returnReceiptSameReceiptNumberAcrossDifferentOrdersShouldNotCollide() {
        Order order1 = createTestOrderWithDeductions(2, 1);
        Order order2 = createTestOrderWithDeductions(1, 2);

        OrderDetail d1 = orderDetailRepository.findByOrderId(order1.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();
        OrderDetail d2 = orderDetailRepository.findByOrderId(order2.getId()).stream()
                .filter(d -> d.getVariant().getId().equals(testVariant1.getId())).findFirst().orElseThrow();

        String commonReceiptNumber = TEST_PREFIX.toUpperCase() + "COMMON-RC";

        // Order 1 return receipt
        OrderReturnReceiptResult res1 = inventoryStockService.processReturnReceipt(
                order1.getId(), staffUser.getId(), commonReceiptNumber, "Order 1 return",
                List.of(new OrderReturnReceiptItem(d1.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Lành"))
        );

        // Order 2 return receipt với cùng receiptNumber
        OrderReturnReceiptResult res2 = inventoryStockService.processReturnReceipt(
                order2.getId(), staffUser.getId(), commonReceiptNumber, "Order 2 return",
                List.of(new OrderReturnReceiptItem(d2.getId(), 1, ReturnItemCondition.RESTOCKABLE, "Lành"))
        );

        assertThat(res1).isNotNull();
        assertThat(res2).isNotNull();

        OrderReturnReceipt receipt1 = orderReturnReceiptRepository
                .findByOrderIdAndReceiptNumber(order1.getId(), commonReceiptNumber).orElseThrow();
        OrderReturnReceipt receipt2 = orderReturnReceiptRepository
                .findByOrderIdAndReceiptNumber(order2.getId(), commonReceiptNumber).orElseThrow();

        // Xác nhận hai receipt header có ID khác nhau
        assertThat(receipt1.getId()).isNotEqualTo(receipt2.getId());

        // Xác nhận movement idempotency keys khác nhau (được scope theo receiptId)
        String key1 = res1.itemResults().get(0).idempotencyKey();
        String key2 = res2.itemResults().get(0).idempotencyKey();
        assertThat(key1).isEqualTo("RETURN_RECEIPT:" + receipt1.getId() + ":" + d1.getId() + ":RESTOCKABLE");
        assertThat(key2).isEqualTo("RETURN_RECEIPT:" + receipt2.getId() + ":" + d2.getId() + ":RESTOCKABLE");
        assertThat(key1).isNotEqualTo(key2);

        // Tồn kho: ban đầu 50, order1 trừ 2 (còn 48), order2 trừ 1 (còn 47), res1 +1 (48), res2 +1 (49)
        assertThat(inventoryVariantRepository.findStockById(testVariant1.getId())).isEqualTo(49);

        // Xác nhận cả 2 movement records đều được lưu trong DB độc lập
        Optional<InventoryTransaction> tx1 = inventoryTransactionRepository.findByIdempotencyKey(key1);
        Optional<InventoryTransaction> tx2 = inventoryTransactionRepository.findByIdempotencyKey(key2);
        assertThat(tx1).isPresent();
        assertThat(tx2).isPresent();
        assertThat(tx1.get().getOrder().getId()).isEqualTo(order1.getId());
        assertThat(tx2.get().getOrder().getId()).isEqualTo(order2.getId());
    }
}
