package com.shop.backend.inventory;

import com.shop.backend.address.UserAddress;
import com.shop.backend.address.UserAddressRepository;
import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.inventory.dto.InventoryStockUpdateRequest;
import com.shop.backend.inventory.dto.OrderStockItem;
import com.shop.backend.inventory.dto.OrderStockRestoreResult;
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
import com.shop.backend.product.ProductVariantService;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class InventoryStockServiceIntegrationTest {

    private static final String TEST_PREFIX = "invrestore-test-";

    @Autowired
    private InventoryStockService inventoryStockService;

    @Autowired
    private InventoryAdminService inventoryAdminService;

    @Autowired
    private InventoryVariantRepository inventoryVariantRepository;

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductVariantService productVariantService;

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
    private UserAddressRepository userAddressRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private User testUser;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        cleanTestData();

        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(Role.builder().name("customer").build()));

        testUser = userRepository.save(User.builder()
                .name(TEST_PREFIX + "User")
                .email(TEST_PREFIX + System.nanoTime() + "@shop.com")
                .passwordHash("hashed-pw")
                .status(UserStatus.ACTIVE)
                .role(customerRole)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        testCategory = categoryRepository.save(Category.builder()
                .name(TEST_PREFIX + "Category")
                .slug(TEST_PREFIX + "category-" + System.nanoTime())
                .isActive(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
        verifyDbClean();
    }

    private void cleanTestData() {
        // FK cleanup order: inventory_transactions -> order_details -> orders -> user_addresses -> variants -> products -> categories -> users
        transactionTemplate.execute(status -> {
            entityManager.createQuery("DELETE FROM InventoryTransaction it WHERE it.idempotencyKey LIKE :prefix")
                    .setParameter("prefix", "%" + TEST_PREFIX + "%")
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

            entityManager.createQuery("DELETE FROM UserAddress ua WHERE ua.receiverName LIKE :prefix")
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
            long txCount = (long) entityManager.createQuery("SELECT COUNT(it) FROM InventoryTransaction it WHERE it.variant.sku LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .getSingleResult();
            long variantCount = (long) entityManager.createQuery("SELECT COUNT(pv) FROM ProductVariant pv WHERE pv.sku LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .getSingleResult();
            long productCount = (long) entityManager.createQuery("SELECT COUNT(p) FROM Product p WHERE p.name LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .getSingleResult();
            long orderCount = (long) entityManager.createQuery("SELECT COUNT(o) FROM Order o WHERE o.receiverName LIKE :prefix")
                    .setParameter("prefix", TEST_PREFIX + "%")
                    .getSingleResult();

            assertThat(txCount).as("Dangling test inventory_transactions").isZero();
            assertThat(variantCount).as("Dangling test variants").isZero();
            assertThat(productCount).as("Dangling test products").isZero();
            assertThat(orderCount).as("Dangling test orders").isZero();
            return null;
        });
    }

    private Product createProduct(String name, boolean isActive) {
        return productRepository.save(Product.builder()
                .name(TEST_PREFIX + name)
                .slug(TEST_PREFIX + name.toLowerCase().replace(" ", "-") + "-" + System.nanoTime())
                .category(testCategory)
                .basePrice(BigDecimal.valueOf(100000))
                .isActive(isActive)
                .dataSource(DataSourceType.WEBSITE)
                .build());
    }

    private ProductVariant createVariant(Product product, String skuSuffix, int stock) {
        return productVariantRepository.save(ProductVariant.builder()
                .product(product)
                .sku(TEST_PREFIX + skuSuffix + "-" + System.nanoTime())
                .size("Size-" + skuSuffix)
                .color("Color-" + skuSuffix)
                .stock(stock)
                .price(BigDecimal.valueOf(100000))
                .build());
    }

    private Order createOrder(OrderStatus status) {
        return orderRepository.save(Order.builder()
                .user(testUser)
                .status(status)
                .subtotal(BigDecimal.valueOf(200000))
                .totalAmount(BigDecimal.valueOf(200000))
                .paymentMethod(PaymentMethod.COD)
                .receiverName(TEST_PREFIX + "Receiver")
                .receiverPhone("0988000111")
                .province("Hanoi")
                .dataSource(DataSourceType.WEBSITE)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private OrderDetail createOrderDetail(Order order, ProductVariant variant, int quantity) {
        OrderDetail detail = OrderDetail.builder()
                .order(order)
                .variant(variant)
                .productName(variant.getProduct().getName())
                .variantSize(variant.getSize())
                .variantColor(variant.getColor())
                .quantity(quantity)
                .unitPrice(BigDecimal.valueOf(100000))
                .discountAmount(BigDecimal.ZERO)
                .build();
        return orderDetailRepository.saveAndFlush(detail);
    }

    @Test
    @DisplayName("1. Hoàn đúng số lượng từng dòng: 2 biến thể A:2, B:3 đã có checkout_deduct -> A+2, B+3, đúng 2 bản ghi cancel_restore, stock_before/after đúng")
    void test1_shouldRestoreStockForEachVariantWhenCheckoutDeductExists() {
        Product product = createProduct("P1", true);
        ProductVariant varA = createVariant(product, "VA", 10);
        ProductVariant varB = createVariant(product, "VB", 20);

        Order order = createOrder(OrderStatus.PENDING);
        OrderDetail detA = createOrderDetail(order, varA, 2);
        OrderDetail detB = createOrderDetail(order, varB, 3);

        transactionTemplate.execute(status -> {
            inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                    new OrderStockItem(detA.getId(), varA.getId(), 2),
                    new OrderStockItem(detB.getId(), varB.getId(), 3)
            ));
            return null;
        });

        OrderStockRestoreResult result = transactionTemplate.execute(status ->
                inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "Cancel order", List.of(
                        new OrderStockItem(detA.getId(), varA.getId(), 2),
                        new OrderStockItem(detB.getId(), varB.getId(), 3)
                ))
        );

        assertThat(result.restoredItemsCount()).isEqualTo(2);
        assertThat(result.skippedItemsCount()).isEqualTo(0);
        assertThat(result.restoredVariantIds()).containsExactlyInAnyOrder(varA.getId(), varB.getId());

        int stockA = inventoryVariantRepository.findStockById(varA.getId());
        int stockB = inventoryVariantRepository.findStockById(varB.getId());
        assertThat(stockA).isEqualTo(12);
        assertThat(stockB).isEqualTo(23);

        List<InventoryTransaction> restoreLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                order.getId(), InventoryMovementType.CANCEL_RESTORE
        );
        assertThat(restoreLogs).hasSize(2);
        for (InventoryTransaction tx : restoreLogs) {
            if (tx.getVariant().getId().equals(varA.getId())) {
                assertThat(tx.getQuantity()).isEqualTo(2);
                assertThat(tx.getStockBefore()).isEqualTo(10);
                assertThat(tx.getStockAfter()).isEqualTo(12);
                assertThat(tx.getIdempotencyKey()).isEqualTo("ORDER_CANCEL:" + detA.getId());
            } else {
                assertThat(tx.getQuantity()).isEqualTo(3);
                assertThat(tx.getStockBefore()).isEqualTo(20);
                assertThat(tx.getStockAfter()).isEqualTo(23);
                assertThat(tx.getIdempotencyKey()).isEqualTo("ORDER_CANCEL:" + detB.getId());
            }
        }
    }

    @Test
    @DisplayName("2. Đơn hỗn hợp: một dòng có checkout_deduct, một dòng không -> chỉ hoàn dòng có log, skippedItemsCount đúng")
    void test2_shouldRestoreOnlyItemsWithCheckoutDeductInMixedOrder() {
        Product product = createProduct("P2", true);
        ProductVariant varA = createVariant(product, "V2A", 15);
        ProductVariant varB = createVariant(product, "V2B", 25);

        Order order = createOrder(OrderStatus.PENDING);
        OrderDetail detA = createOrderDetail(order, varA, 2);
        OrderDetail detB = createOrderDetail(order, varB, 4);

        transactionTemplate.execute(status -> {
            inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                    new OrderStockItem(detA.getId(), varA.getId(), 2)
            ));
            return null;
        });

        OrderStockRestoreResult result = transactionTemplate.execute(status ->
                inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "Cancel mixed", List.of(
                        new OrderStockItem(detA.getId(), varA.getId(), 2),
                        new OrderStockItem(detB.getId(), varB.getId(), 4)
                ))
        );

        assertThat(result.restoredItemsCount()).isEqualTo(1);
        assertThat(result.skippedItemsCount()).isEqualTo(1);
        assertThat(result.restoredVariantIds()).containsExactly(varA.getId());

        assertThat(inventoryVariantRepository.findStockById(varA.getId())).isEqualTo(17);
        assertThat(inventoryVariantRepository.findStockById(varB.getId())).isEqualTo(25);
    }

    @Test
    @DisplayName("3. Đơn không có checkout_deduct nào -> restoredItemsCount = 0, stock giữ nguyên, không ghi log")
    void test3_shouldSkipRestorationWhenNoCheckoutDeductExists() {
        Product product = createProduct("P3", true);
        ProductVariant varA = createVariant(product, "V3A", 30);

        Order order = createOrder(OrderStatus.PENDING);
        OrderDetail detA = createOrderDetail(order, varA, 5);

        OrderStockRestoreResult result = transactionTemplate.execute(status ->
                inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "Cancel old order", List.of(
                        new OrderStockItem(detA.getId(), varA.getId(), 5)
                ))
        );

        assertThat(result.restoredItemsCount()).isEqualTo(0);
        assertThat(result.skippedItemsCount()).isEqualTo(1);
        assertThat(result.restoredVariantIds()).isEmpty();
        assertThat(inventoryVariantRepository.findStockById(varA.getId())).isEqualTo(30);

        List<InventoryTransaction> restoreLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                order.getId(), InventoryMovementType.CANCEL_RESTORE
        );
        assertThat(restoreLogs).isEmpty();
    }

    @Test
    @DisplayName("4. Gọi hoàn lần 2 tuần tự cho cùng đơn -> ném lỗi UNIQUE, stock không tăng lần hai, rollback")
    void test4_shouldFailAndRollbackWhenRestoringTwiceSequentially() {
        Product product = createProduct("P4", true);
        ProductVariant varA = createVariant(product, "V4A", 40);

        Order order = createOrder(OrderStatus.PENDING);
        OrderDetail detA = createOrderDetail(order, varA, 3);

        transactionTemplate.execute(status -> {
            inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                    new OrderStockItem(detA.getId(), varA.getId(), 3)
            ));
            return null;
        });

        transactionTemplate.execute(status ->
                inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "First cancel", List.of(
                        new OrderStockItem(detA.getId(), varA.getId(), 3)
                ))
        );
        assertThat(inventoryVariantRepository.findStockById(varA.getId())).isEqualTo(43);

        assertThatThrownBy(() ->
                transactionTemplate.execute(status ->
                        inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "Second cancel attempt", List.of(
                                new OrderStockItem(detA.getId(), varA.getId(), 3)
                        ))
                )
        ).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(inventoryVariantRepository.findStockById(varA.getId())).isEqualTo(43);
    }

    @Test
    @DisplayName("5. Hai lời gọi restore ĐỒNG THỜI cho cùng đơn (CountDownLatch, KHÔNG @Transactional) -> đúng 1 thành công, 1 lỗi, stock tăng đúng 1 lần")
    void test5_shouldHandleConcurrentRestoresCorrectlyWithExactlyOneSuccess() throws Exception {
        Product product = createProduct("P5", true);
        ProductVariant varA = createVariant(product, "V5A", 50);

        Order order = createOrder(OrderStatus.PENDING);
        OrderDetail detA = createOrderDetail(order, varA, 5);

        transactionTemplate.execute(status -> {
            inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                    new OrderStockItem(detA.getId(), varA.getId(), 5)
            ));
            return null;
        });

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    transactionTemplate.execute(status -> {
                        inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "Concurrent cancel", List.of(
                                new OrderStockItem(detA.getId(), varA.getId(), 5)
                        ));
                        return null;
                    });
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);
        assertThat(inventoryVariantRepository.findStockById(varA.getId())).isEqualTo(55);
    }

    @Test
    @DisplayName("6. Rollback toàn bộ khi một dòng lỗi (ví dụ quantity lệch log) -> dòng đầu không được cộng, không có log nào")
    void test6_shouldRollbackEntirelyWhenAnyItemFailsDuringRestoration() {
        Product product = createProduct("P6", true);
        ProductVariant varA = createVariant(product, "V6A", 60);
        ProductVariant varB = createVariant(product, "V6B", 70);

        Order order = createOrder(OrderStatus.PENDING);
        OrderDetail detA = createOrderDetail(order, varA, 2);
        OrderDetail detB = createOrderDetail(order, varB, 3);

        transactionTemplate.execute(status -> {
            inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                    new OrderStockItem(detA.getId(), varA.getId(), 2),
                    new OrderStockItem(detB.getId(), varB.getId(), 3)
            ));
            return null;
        });

        // detB passes quantity = 99 (mismatched with deduct log quantity = 3)
        assertThatThrownBy(() ->
                transactionTemplate.execute(status ->
                        inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "Cancel mismatch", List.of(
                                new OrderStockItem(detA.getId(), varA.getId(), 2),
                                new OrderStockItem(detB.getId(), varB.getId(), 99)
                        ))
                )
        ).isInstanceOf(BadRequestException.class);

        // Assert atomicity: varA stock was NOT changed, no restore log inserted
        assertThat(inventoryVariantRepository.findStockById(varA.getId())).isEqualTo(60);
        assertThat(inventoryVariantRepository.findStockById(varB.getId())).isEqualTo(70);

        List<InventoryTransaction> restoreLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                order.getId(), InventoryMovementType.CANCEL_RESTORE
        );
        assertThat(restoreLogs).isEmpty();
    }

    @Test
    @DisplayName("7. Deadlock: hai đơn khác nhau cùng chứa biến thể theo thứ tự ngược [1,2] và [2,1] hủy đồng thời -> không deadlock, stock cuối đúng")
    void test7_shouldAvoidDeadlockWhenConcurrentRestoresHaveReversedVariantOrder() throws Exception {
        Product product = createProduct("P7", true);
        ProductVariant var1 = createVariant(product, "V7_1", 100);
        ProductVariant var2 = createVariant(product, "V7_2", 100);

        Order order1 = createOrder(OrderStatus.PENDING);
        OrderDetail det1_1 = createOrderDetail(order1, var1, 2);
        OrderDetail det1_2 = createOrderDetail(order1, var2, 3);

        Order order2 = createOrder(OrderStatus.PENDING);
        OrderDetail det2_1 = createOrderDetail(order2, var1, 4);
        OrderDetail det2_2 = createOrderDetail(order2, var2, 5);

        transactionTemplate.execute(status -> {
            inventoryStockService.recordCheckoutDeduction(order1.getId(), testUser.getId(), List.of(
                    new OrderStockItem(det1_1.getId(), var1.getId(), 2),
                    new OrderStockItem(det1_2.getId(), var2.getId(), 3)
            ));
            inventoryStockService.recordCheckoutDeduction(order2.getId(), testUser.getId(), List.of(
                    new OrderStockItem(det2_1.getId(), var1.getId(), 4),
                    new OrderStockItem(det2_2.getId(), var2.getId(), 5)
            ));
            return null;
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        // Caller 1 passes [var1, var2]
        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    inventoryStockService.restoreStockForCancelledOrder(order1.getId(), testUser.getId(), "Cancel 1", List.of(
                            new OrderStockItem(det1_1.getId(), var1.getId(), 2),
                            new OrderStockItem(det1_2.getId(), var2.getId(), 3)
                    ));
                    return null;
                });
                successCount.incrementAndGet();
            } catch (Exception e) {
                // Should not happen
            } finally {
                doneLatch.countDown();
            }
        });

        // Caller 2 passes reversed list [var2, var1]
        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    inventoryStockService.restoreStockForCancelledOrder(order2.getId(), testUser.getId(), "Cancel 2", List.of(
                            new OrderStockItem(det2_2.getId(), var2.getId(), 5),
                            new OrderStockItem(det2_1.getId(), var1.getId(), 4)
                    ));
                    return null;
                });
                successCount.incrementAndGet();
            } catch (Exception e) {
                // Should not happen
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(2);

        // var1 was 100 + 2 + 4 = 106; var2 was 100 + 3 + 5 = 108
        assertThat(inventoryVariantRepository.findStockById(var1.getId())).isEqualTo(106);
        assertThat(inventoryVariantRepository.findStockById(var2.getId())).isEqualTo(108);
    }

    @Test
    @DisplayName("8. Hoàn đồng thời với trừ kho và CAS -> stock cuối khớp giá trị toán học, không lost update")
    void test8_shouldMaintainAccurateStockUnderConcurrentRestoreCheckoutAndCas() throws Exception {
        Product product = createProduct("P8", true);
        ProductVariant variant = createVariant(product, "V8", 100);

        Order order = createOrder(OrderStatus.PENDING);
        OrderDetail det = createOrderDetail(order, variant, 10);

        transactionTemplate.execute(status -> {
            inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                    new OrderStockItem(det.getId(), variant.getId(), 10)
            ));
            return null;
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        // Task A: restore (+10)
        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "Restore task", List.of(
                            new OrderStockItem(det.getId(), variant.getId(), 10)
                    ));
                    return null;
                });
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        // Task B: direct atomic deduct (-4)
        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    productVariantRepository.deductStock(variant.getId(), 4);
                    return null;
                });
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // 100 + 10 - 4 = 106
        assertThat(inventoryVariantRepository.findStockById(variant.getId())).isEqualTo(106);
    }

    @Test
    @DisplayName("9. Biến thể isActive = false vẫn hoàn kho bình thường")
    void test9_shouldRestoreStockEvenWhenVariantIsInactive() {
        Product inactiveProduct = createProduct("P9 Inactive", false);
        ProductVariant variant = createVariant(inactiveProduct, "V9", 5);

        Order order = createOrder(OrderStatus.PENDING);
        OrderDetail det = createOrderDetail(order, variant, 2);

        transactionTemplate.execute(status -> {
            inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                    new OrderStockItem(det.getId(), variant.getId(), 2)
            ));
            return null;
        });

        OrderStockRestoreResult result = transactionTemplate.execute(status ->
                inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "Cancel inactive", List.of(
                        new OrderStockItem(det.getId(), variant.getId(), 2)
                ))
        );

        assertThat(result.restoredItemsCount()).isEqualTo(1);
        assertThat(inventoryVariantRepository.findStockById(variant.getId())).isEqualTo(7);
    }

    @Test
    @DisplayName("10. recordCheckoutDeduction: đơn 2 biến thể -> 2 bản ghi checkout_deduct khớp detailId, qty, stock_after; gọi lặp bị UNIQUE chặn")
    void test10_shouldRecordCheckoutDeductionAccurately() {
        Product product = createProduct("P10", true);
        ProductVariant varA = createVariant(product, "V10A", 18);
        ProductVariant varB = createVariant(product, "V10B", 27);

        Order order = createOrder(OrderStatus.PENDING);
        OrderDetail detA = createOrderDetail(order, varA, 2);
        OrderDetail detB = createOrderDetail(order, varB, 3);

        transactionTemplate.execute(status -> {
            inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                    new OrderStockItem(detA.getId(), varA.getId(), 2),
                    new OrderStockItem(detB.getId(), varB.getId(), 3)
            ));
            return null;
        });

        List<InventoryTransaction> logs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                order.getId(), InventoryMovementType.CHECKOUT_DEDUCT
        );
        assertThat(logs).hasSize(2);

        for (InventoryTransaction tx : logs) {
            if (tx.getVariant().getId().equals(varA.getId())) {
                assertThat(tx.getQuantity()).isEqualTo(2);
                assertThat(tx.getStockAfter()).isEqualTo(18);
                assertThat(tx.getStockBefore()).isEqualTo(20);
                assertThat(tx.getIdempotencyKey()).isEqualTo("ORDER_CHECKOUT:" + detA.getId());
            } else {
                assertThat(tx.getQuantity()).isEqualTo(3);
                assertThat(tx.getStockAfter()).isEqualTo(27);
                assertThat(tx.getStockBefore()).isEqualTo(30);
                assertThat(tx.getIdempotencyKey()).isEqualTo("ORDER_CHECKOUT:" + detB.getId());
            }
        }

        // Calling duplicate deduction must violate UNIQUE idempotency_key
        assertThatThrownBy(() ->
                transactionTemplate.execute(status -> {
                    inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                            new OrderStockItem(detA.getId(), varA.getId(), 2)
                    ));
                    return null;
                })
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("11. Entity Order vẫn lưu đúng trạng thái sau khi gọi restore trong cùng transaction (KHÔNG detach do clearAutomatically)")
    void test11_shouldNotDetachOrderWhenRestoringInSameTransaction() {
        Product product = createProduct("P11", true);
        ProductVariant variant = createVariant(product, "V11", 50);

        Order order = createOrder(OrderStatus.PENDING);
        OrderDetail det = createOrderDetail(order, variant, 5);

        transactionTemplate.execute(status -> {
            inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                    new OrderStockItem(det.getId(), variant.getId(), 5)
            ));
            return null;
        });

        // Simulate updateOrderStatus sequence in one transaction:
        // 1. Lock/load order
        // 2. Call restoreStockForCancelledOrder
        // 3. Mutate order status to CANCELLED
        // 4. Save order
        // 5. Commit
        transactionTemplate.execute(status -> {
            Order managedOrder = orderRepository.findByIdForUpdate(order.getId()).orElseThrow();
            assertThat(managedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);

            OrderStockRestoreResult restoreResult = inventoryStockService.restoreStockForCancelledOrder(
                    managedOrder.getId(),
                    testUser.getId(),
                    "Admin cancellation",
                    List.of(new OrderStockItem(det.getId(), variant.getId(), 5))
            );
            assertThat(restoreResult.restoredItemsCount()).isEqualTo(1);

            // If clearAutomatically = true was present on restoreStock, managedOrder would be detached!
            // Here managedOrder is still attached and orderDetails collection is accessible
            assertThat(managedOrder.getOrderDetails()).hasSize(1);

            managedOrder.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(managedOrder);
            return null;
        });

        Order reloadedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(inventoryVariantRepository.findStockById(variant.getId())).isEqualTo(55);
    }

    @Test
    @DisplayName("12. recordCheckoutDeduction tham gia transaction hiện tại: transaction bên ngoài ném exception -> rollback toàn bộ, không có log checkout_deduct nào được lưu")
    void test12_shouldRollbackDeductionLogWhenExternalTransactionFails() {
        Product product = createProduct("P12 Rollback Deduct", true);
        ProductVariant variant = createVariant(product, "V12", 20);

        Order order = createOrder(OrderStatus.PENDING);
        OrderDetail det = createOrderDetail(order, variant, 2);

        assertThatThrownBy(() ->
                transactionTemplate.execute(status -> {
                    inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                            new OrderStockItem(det.getId(), variant.getId(), 2)
                    ));
                    throw new RuntimeException("Simulated external failure after recordCheckoutDeduction");
                })
        ).isInstanceOf(RuntimeException.class).hasMessageContaining("Simulated external failure");

        List<InventoryTransaction> logs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                order.getId(), InventoryMovementType.CHECKOUT_DEDUCT
        );
        assertThat(logs).isEmpty();

        assertThat(inventoryTransactionRepository.findByIdempotencyKey("ORDER_CHECKOUT:" + det.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("13. restoreStockForCancelledOrder tham gia transaction hiện tại: transaction bên ngoài ném exception -> rollback cả stock và log, stock không tăng, không có log cancel_restore")
    void test13_shouldRollbackRestoreStockAndLogWhenExternalTransactionFails() {
        Product product = createProduct("P13 Rollback Restore", true);
        ProductVariant variant = createVariant(product, "V13", 50);

        Order order = createOrder(OrderStatus.PENDING);
        OrderDetail det = createOrderDetail(order, variant, 5);

        transactionTemplate.execute(status -> {
            inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                    new OrderStockItem(det.getId(), variant.getId(), 5)
            ));
            return null;
        });

        Integer stockBeforeRestore = inventoryVariantRepository.findStockById(variant.getId());
        assertThat(stockBeforeRestore).isEqualTo(50);

        assertThatThrownBy(() ->
                transactionTemplate.execute(status -> {
                    inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "Cancel order test", List.of(
                            new OrderStockItem(det.getId(), variant.getId(), 5)
                    ));
                    throw new RuntimeException("Simulated external failure after restoreStockForCancelledOrder");
                })
        ).isInstanceOf(RuntimeException.class).hasMessageContaining("Simulated external failure");

        assertThat(inventoryVariantRepository.findStockById(variant.getId())).isEqualTo(50);

        List<InventoryTransaction> restoreLogs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                order.getId(), InventoryMovementType.CANCEL_RESTORE
        );
        assertThat(restoreLogs).isEmpty();

        assertThat(inventoryTransactionRepository.findByIdempotencyKey("ORDER_CANCEL:" + det.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("14. FK RESTRICT: Xóa biến thể có stock = 0 nhưng đã có log inventory_transactions -> Bị chặn bởi FK RESTRICT fk_inv_tx_variant (ném DataIntegrityViolationException)")
    void test14_variantDeletionWithExistingInventoryTransactionsFailsDueToFkRestrict() {
        Product product = createProduct("P14 FK Test", true);
        ProductVariant variant = createVariant(product, "V14", 0);

        transactionTemplate.execute(status -> {
            InventoryTransaction tx = InventoryTransaction.builder()
                    .variant(variant)
                    .movementType(InventoryMovementType.MANUAL_ADJUST)
                    .quantity(1)
                    .stockBefore(1)
                    .stockAfter(0)
                    .note("Manual adjust")
                    .idempotencyKey("TEST_MANUAL_ADJUST:" + System.nanoTime())
                    .build();
            inventoryTransactionRepository.save(tx);
            return null;
        });

        assertThatThrownBy(() ->
                transactionTemplate.execute(status -> {
                    productVariantRepository.deleteById(variant.getId());
                    entityManager.flush();
                    return null;
                })
        ).satisfies(ex -> {
            Throwable root = ex;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertThat(root).isInstanceOf(java.sql.SQLException.class);
            assertThat(((java.sql.SQLException) root).getErrorCode()).isEqualTo(1451);
        });
    }

    @Test
    @DisplayName("15. recordCheckoutDeduction: Nhiều dòng trong đơn cùng variantId -> Tính đúng stockBefore, stockAfter theo chuỗi lũy tiến")
    void test15_shouldAccumulateStockAccuratelyWhenMultipleItemsShareSameVariant() {
        Product product = createProduct("P15 Multi Same Variant", true);
        ProductVariant variant = createVariant(product, "V15", 15);

        Order order = createOrder(OrderStatus.PENDING);
        OrderDetail det1 = createOrderDetail(order, variant, 2);
        OrderDetail det2 = createOrderDetail(order, variant, 3);

        transactionTemplate.execute(status -> {
            inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                    new OrderStockItem(det1.getId(), variant.getId(), 2),
                    new OrderStockItem(det2.getId(), variant.getId(), 3)
            ));
            return null;
        });

        List<InventoryTransaction> logs = inventoryTransactionRepository.findByOrderIdAndMovementType(
                order.getId(), InventoryMovementType.CHECKOUT_DEDUCT
        );
        assertThat(logs).hasSize(2);

        InventoryTransaction tx1 = logs.stream()
                .filter(tx -> ("ORDER_CHECKOUT:" + det1.getId()).equals(tx.getIdempotencyKey()))
                .findFirst().orElseThrow();
        assertThat(tx1.getQuantity()).isEqualTo(2);
        assertThat(tx1.getStockBefore()).isEqualTo(20);
        assertThat(tx1.getStockAfter()).isEqualTo(18);

        InventoryTransaction tx2 = logs.stream()
                .filter(tx -> ("ORDER_CHECKOUT:" + det2.getId()).equals(tx.getIdempotencyKey()))
                .findFirst().orElseThrow();
        assertThat(tx2.getQuantity()).isEqualTo(3);
        assertThat(tx2.getStockBefore()).isEqualTo(18);
        assertThat(tx2.getStockAfter()).isEqualTo(15);
    }

    @Test
    @DisplayName("16. Validation recordCheckoutDeduction: orderId null, item null, orderDetailId null, quantity null/<=0 -> ném BadRequestException, không có key ORDER_CHECKOUT:null")
    void test16_validationErrorsInRecordCheckoutDeduction() {
        Product product = createProduct("P16 Val Deduct", true);
        ProductVariant variant = createVariant(product, "V16", 10);
        Order order = createOrder(OrderStatus.PENDING);
        OrderDetail det = createOrderDetail(order, variant, 2);

        // 1. orderId null
        assertThatThrownBy(() ->
                inventoryStockService.recordCheckoutDeduction(null, testUser.getId(), List.of(
                        new OrderStockItem(det.getId(), variant.getId(), 2)
                ))
        ).isInstanceOf(BadRequestException.class);

        // 2. item element null
        assertThatThrownBy(() -> {
            List<OrderStockItem> nullItemList = new ArrayList<>();
            nullItemList.add(null);
            inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), nullItemList);
        }).isInstanceOf(BadRequestException.class);

        // 3. orderDetailId null
        assertThatThrownBy(() ->
                inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                        new OrderStockItem(null, variant.getId(), 2)
                ))
        ).isInstanceOf(BadRequestException.class);

        // Ensure "ORDER_CHECKOUT:null" was never persisted
        assertThat(inventoryTransactionRepository.findByIdempotencyKey("ORDER_CHECKOUT:null")).isEmpty();

        // 4. variantId null
        assertThatThrownBy(() ->
                inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                        new OrderStockItem(det.getId(), null, 2)
                ))
        ).isInstanceOf(BadRequestException.class);

        // 5. quantity null
        assertThatThrownBy(() ->
                inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                        new OrderStockItem(det.getId(), variant.getId(), null)
                ))
        ).isInstanceOf(BadRequestException.class);

        // 6. quantity <= 0 (0 and negative)
        assertThatThrownBy(() ->
                inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                        new OrderStockItem(det.getId(), variant.getId(), 0)
                ))
        ).isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() ->
                inventoryStockService.recordCheckoutDeduction(order.getId(), testUser.getId(), List.of(
                        new OrderStockItem(det.getId(), variant.getId(), -5)
                ))
        ).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("17. Validation restoreStockForCancelledOrder: orderId null, item null, orderDetailId null, quantity null/<=0 -> ném BadRequestException")
    void test17_validationErrorsInRestoreStockForCancelledOrder() {
        Product product = createProduct("P17 Val Restore", true);
        ProductVariant variant = createVariant(product, "V17", 10);
        Order order = createOrder(OrderStatus.PENDING);
        OrderDetail det = createOrderDetail(order, variant, 2);

        // 1. orderId null
        assertThatThrownBy(() ->
                inventoryStockService.restoreStockForCancelledOrder(null, testUser.getId(), "Cancel", List.of(
                        new OrderStockItem(det.getId(), variant.getId(), 2)
                ))
        ).isInstanceOf(BadRequestException.class);

        // 2. item element null
        assertThatThrownBy(() -> {
            List<OrderStockItem> nullItemList = new ArrayList<>();
            nullItemList.add(null);
            inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "Cancel", nullItemList);
        }).isInstanceOf(BadRequestException.class);

        // 3. orderDetailId null (Must NOT be silently skipped!)
        assertThatThrownBy(() ->
                inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "Cancel", List.of(
                        new OrderStockItem(null, variant.getId(), 2)
                ))
        ).isInstanceOf(BadRequestException.class);

        // 4. variantId null
        assertThatThrownBy(() ->
                inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "Cancel", List.of(
                        new OrderStockItem(det.getId(), null, 2)
                ))
        ).isInstanceOf(BadRequestException.class);

        // 5. quantity null
        assertThatThrownBy(() ->
                inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "Cancel", List.of(
                        new OrderStockItem(det.getId(), variant.getId(), null)
                ))
        ).isInstanceOf(BadRequestException.class);

        // 6. quantity <= 0
        assertThatThrownBy(() ->
                inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "Cancel", List.of(
                        new OrderStockItem(det.getId(), variant.getId(), 0)
                ))
        ).isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() ->
                inventoryStockService.restoreStockForCancelledOrder(order.getId(), testUser.getId(), "Cancel", List.of(
                        new OrderStockItem(det.getId(), variant.getId(), -3)
                ))
        ).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("18. Checkout đồng thời ghi log: 2 thread độc lập deductStock và recordCheckoutDeduction cho cùng 1 biến thể -> chuỗi log liền mạch, stock cuối khớp")
    void test18_concurrentCheckoutDeductionLogging() throws Exception {
        Product product = createProduct("P18 Concurrent Checkout", true);
        ProductVariant variant = createVariant(product, "V18", 100);

        Order order1 = createOrder(OrderStatus.PENDING);
        OrderDetail det1 = createOrderDetail(order1, variant, 10);

        Order order2 = createOrder(OrderStatus.PENDING);
        OrderDetail det2 = createOrderDetail(order2, variant, 15);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    productVariantService.deductStock(variant.getId(), 10);
                    inventoryStockService.recordCheckoutDeduction(order1.getId(), testUser.getId(), List.of(
                            new OrderStockItem(det1.getId(), variant.getId(), 10)
                    ));
                    return null;
                });
                successCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                transactionTemplate.execute(status -> {
                    productVariantService.deductStock(variant.getId(), 15);
                    inventoryStockService.recordCheckoutDeduction(order2.getId(), testUser.getId(), List.of(
                            new OrderStockItem(det2.getId(), variant.getId(), 15)
                    ));
                    return null;
                });
                successCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(2);

        Integer finalStock = inventoryVariantRepository.findStockById(variant.getId());
        assertThat(finalStock).isEqualTo(75);

        List<InventoryTransaction> log1List = inventoryTransactionRepository.findByOrderIdAndMovementType(
                order1.getId(), InventoryMovementType.CHECKOUT_DEDUCT
        );
        List<InventoryTransaction> log2List = inventoryTransactionRepository.findByOrderIdAndMovementType(
                order2.getId(), InventoryMovementType.CHECKOUT_DEDUCT
        );
        assertThat(log1List).hasSize(1);
        assertThat(log2List).hasSize(1);

        InventoryTransaction tx1 = log1List.get(0);
        InventoryTransaction tx2 = log2List.get(0);

        assertThat(tx1.getStockBefore() - tx1.getQuantity()).isEqualTo(tx1.getStockAfter());
        assertThat(tx2.getStockBefore() - tx2.getQuantity()).isEqualTo(tx2.getStockAfter());

        InventoryTransaction firstTx = tx1.getStockBefore() > tx2.getStockBefore() ? tx1 : tx2;
        InventoryTransaction secondTx = tx1.getStockBefore() > tx2.getStockBefore() ? tx2 : tx1;

        assertThat(firstTx.getStockBefore()).isEqualTo(100);
        assertThat(firstTx.getStockAfter()).isEqualTo(secondTx.getStockBefore());
        assertThat(secondTx.getStockAfter()).isEqualTo(finalStock);
    }
}
