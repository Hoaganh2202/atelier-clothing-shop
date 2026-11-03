package com.shop.backend.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.backend.common.exception.ConflictException;
import com.shop.backend.inventory.dto.InventoryStockUpdateRequest;
import com.shop.backend.product.Category;
import com.shop.backend.product.CategoryRepository;
import com.shop.backend.product.Product;
import com.shop.backend.product.ProductRepository;
import com.shop.backend.product.ProductVariant;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

@SpringBootTest
@ActiveProfiles("test")
class InventoryConcurrencyIntegrationTest {

    private static final String TEST_PREFIX = "inv-test-conc-";

    @Autowired
    private InventoryAdminService inventoryAdminService;

    @Autowired
    private InventoryVariantRepository inventoryVariantRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Category testCategory;
    private Product testProduct;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        testCategory = categoryRepository.save(Category.builder()
                .name(TEST_PREFIX + "Cat " + uniqueSuffix)
                .slug(TEST_PREFIX + "cat-" + uniqueSuffix)
                .build());

        testProduct = productRepository.save(Product.builder()
                .name(TEST_PREFIX + "Product " + uniqueSuffix)
                .slug(TEST_PREFIX + "prod-" + uniqueSuffix)
                .category(testCategory)
                .basePrice(BigDecimal.valueOf(100000))
                .isActive(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        // Dọn dẹp dữ liệu test sạch sẽ sau mỗi test case
        transactionTemplate.executeWithoutResult(status -> {
            inventoryVariantRepository.findAll().stream()
                    .filter(v -> v.getSku() != null && v.getSku().startsWith(TEST_PREFIX))
                    .forEach(inventoryVariantRepository::delete);

            productRepository.findAll().stream()
                    .filter(p -> p.getName() != null && p.getName().startsWith(TEST_PREFIX))
                    .forEach(productRepository::delete);

            categoryRepository.findAll().stream()
                    .filter(c -> c.getName() != null && c.getName().startsWith(TEST_PREFIX))
                    .forEach(categoryRepository::delete);
        });
    }

    private ProductVariant createVariant(String skuSuffix, int stock) {
        return transactionTemplate.execute(status -> inventoryVariantRepository.save(ProductVariant.builder()
                .product(testProduct)
                .sku(TEST_PREFIX + skuSuffix + "-" + UUID.randomUUID().toString().substring(0, 6))
                .size("M")
                .color("Đen")
                .stock(stock)
                .price(BigDecimal.valueOf(100000))
                .build()));
    }

    private int executeAtomicCheckoutDeduct(Long variantId, int quantity) {
        Integer affected = transactionTemplate.execute(status -> entityManager.createQuery(
                "UPDATE ProductVariant pv SET pv.stock = pv.stock - :quantity WHERE pv.id = :id AND pv.stock >= :quantity")
                .setParameter("quantity", quantity)
                .setParameter("id", variantId)
                .executeUpdate());
        return affected != null ? affected : 0;
    }

    @Test
    @DisplayName("Ca 1: stock = 100, 20 luồng checkout trừ 1 + 1 luồng admin CAS (expected 100, new 500)")
    void testConcurrentCheckoutAndAdminCas() throws InterruptedException {
        ProductVariant variant = createVariant("TEST1", 100);
        Long variantId = variant.getId();

        int numberOfCheckoutThreads = 20;
        int totalThreads = numberOfCheckoutThreads + 1; // 20 checkout + 1 admin CAS
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successfulCheckouts = new AtomicInteger(0);
        AtomicBoolean casSuccess = new AtomicBoolean(false);
        AtomicBoolean casConflict = new AtomicBoolean(false);

        // 20 luồng trừ kho Checkout
        for (int i = 0; i < numberOfCheckoutThreads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    int affected = executeAtomicCheckoutDeduct(variantId, 1);
                    if (affected == 1) {
                        successfulCheckouts.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 1 luồng Admin CAS: expectedStock = 100, newStock = 500
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                inventoryAdminService.updateStock(
                        variantId,
                        InventoryStockUpdateRequest.builder()
                                .expectedStock(100)
                                .newStock(500)
                                .build()
                );
                casSuccess.set(true);
            } catch (ConflictException e) {
                casConflict.set(true);
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Bắn còi đồng loạt
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successfulCheckouts.get()).isEqualTo(numberOfCheckoutThreads);

        ProductVariant finalVariant = inventoryVariantRepository.findById(variantId).orElseThrow();
        int finalStock = finalVariant.getStock();

        if (casSuccess.get()) {
            // Admin CAS chạy thành công trước -> stock đổi thành 500, sau đó 20 luồng trừ 1 -> còn 480
            assertThat(finalStock).isEqualTo(480);
        } else {
            // Có ít nhất 1 luồng trừ kho chạy trước làm stock khác 100 -> CAS nhận 409 Conflict
            assertThat(casConflict.get()).isTrue();
            // 20 luồng trừ 1 từ 100 ban đầu -> còn 80
            assertThat(finalStock).isEqualTo(80);
        }

        assertThat(finalStock).isIn(80, 480);
    }

    @Test
    @DisplayName("Ca 2: stock = 5, 20 luồng cùng trừ 1 -> đúng 5 thành công, stock cuối = 0, không bao giờ âm")
    void testConcurrentCheckoutExceedingStock() throws InterruptedException {
        ProductVariant variant = createVariant("TEST2", 5);
        Long variantId = variant.getId();

        int numberOfThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successfulDeductions = new AtomicInteger(0);
        AtomicInteger failedDeductions = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    int affected = executeAtomicCheckoutDeduct(variantId, 1);
                    if (affected == 1) {
                        successfulDeductions.incrementAndGet();
                    } else {
                        failedDeductions.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successfulDeductions.get()).isEqualTo(5);
        assertThat(failedDeductions.get()).isEqualTo(15);

        ProductVariant finalVariant = inventoryVariantRepository.findById(variantId).orElseThrow();
        assertThat(finalVariant.getStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("Ca 3: Hai luồng admin CAS cùng expectedStock -> đúng một thành công, một nhận 409")
    void testConcurrentAdminCasRace() throws InterruptedException {
        ProductVariant variant = createVariant("TEST3", 20);
        Long variantId = variant.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger winningStock = new AtomicInteger(0);

        // Luồng 1 muốn đặt stock thành 35
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                inventoryAdminService.updateStock(
                        variantId,
                        InventoryStockUpdateRequest.builder()
                                .expectedStock(20)
                                .newStock(35)
                                .build()
                );
                successCount.incrementAndGet();
                winningStock.set(35);
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        // Luồng 2 muốn đặt stock thành 60
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                inventoryAdminService.updateStock(
                        variantId,
                        InventoryStockUpdateRequest.builder()
                                .expectedStock(20)
                                .newStock(60)
                                .build()
                );
                successCount.incrementAndGet();
                winningStock.set(60);
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        ProductVariant finalVariant = inventoryVariantRepository.findById(variantId).orElseThrow();
        assertThat(finalVariant.getStock()).isEqualTo(winningStock.get());
        assertThat(finalVariant.getStock()).isIn(35, 60);
    }
}
