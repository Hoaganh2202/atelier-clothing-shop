package com.shop.backend.product;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.product.dto.ProductUpsertRequest;
import com.shop.backend.product.dto.VariantUpsertRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ProductStockRegressionIntegrationTest {

    private static final String TEST_PREFIX = "prodstock-test-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Category testCategory;
    private TransactionTemplate requiresNewTemplate;

    @BeforeEach
    void setUp() {
        requiresNewTemplate = new TransactionTemplate(transactionManager);
        requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        cleanupTestData();

        requiresNewTemplate.execute(status -> {
            testCategory = categoryRepository.save(Category.builder()
                    .name("Category " + TEST_PREFIX + System.currentTimeMillis())
                    .slug(TEST_PREFIX + "cat-" + System.currentTimeMillis())
                    .isActive(true)
                    .build());
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
        verifyNoTestDataLeaks();
    }

    private void cleanupTestData() {
        requiresNewTemplate.execute(status -> {
            jdbcTemplate.update("DELETE FROM product_images WHERE product_id IN (SELECT id FROM products WHERE slug LIKE ?)", TEST_PREFIX + "%");
            jdbcTemplate.update("DELETE FROM product_variants WHERE sku LIKE ? OR product_id IN (SELECT id FROM products WHERE slug LIKE ?)", TEST_PREFIX + "%", TEST_PREFIX + "%");
            jdbcTemplate.update("DELETE FROM products WHERE slug LIKE ?", TEST_PREFIX + "%");
            jdbcTemplate.update("DELETE FROM categories WHERE slug LIKE ?", TEST_PREFIX + "%");
            return null;
        });
    }

    private void verifyNoTestDataLeaks() {
        Integer variantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_variants WHERE sku LIKE ?", Integer.class, TEST_PREFIX + "%");
        Integer productCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE slug LIKE ?", Integer.class, TEST_PREFIX + "%");
        assertEquals(0, variantCount, "Leaked variants found in clothing_shop_test with prefix " + TEST_PREFIX);
        assertEquals(0, productCount, "Leaked products found in clothing_shop_test with prefix " + TEST_PREFIX);
    }

    private Product createProductWithVariant(String sku, int initialStock, long basePrice) {
        return requiresNewTemplate.execute(status -> {
            Product product = productRepository.save(Product.builder()
                    .category(testCategory)
                    .name("Product " + sku)
                    .slug(TEST_PREFIX + "slug-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000))
                    .basePrice(BigDecimal.valueOf(basePrice))
                    .isActive(true)
                    .build());

            ProductVariant variant = variantRepository.save(ProductVariant.builder()
                    .product(product)
                    .sku(sku)
                    .size("M")
                    .color("Đen")
                    .stock(initialStock)
                    .price(BigDecimal.valueOf(basePrice))
                    .build());

            return productRepository.findById(product.getId()).orElseThrow();
        });
    }

    @Test
    @DisplayName("a. Test đo SQL: khi updateProduct chỉ đổi price, câu UPDATE product_variants KHÔNG được ghi cột stock")
    void shouldNotIncludeStockColumnInUpdateSqlWhenOnlyPriceChanges() {
        String sku = TEST_PREFIX + "sql-measure-" + System.currentTimeMillis();
        Product product = createProductWithVariant(sku, 10, 100000L);
        ProductVariant variant = variantRepository.findByProductId(product.getId()).get(0);

        Logger hibernateLogger = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
        Level oldLevel = hibernateLogger.getLevel();
        hibernateLogger.setLevel(Level.DEBUG);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        hibernateLogger.addAppender(listAppender);

        try {
            requiresNewTemplate.execute(status -> {
                ProductVariant managedVariant = variantRepository.findById(variant.getId()).orElseThrow();
                managedVariant.setPrice(BigDecimal.valueOf(180000L));
                variantRepository.save(managedVariant);
                return null;
            });

            String updateSql = listAppender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .map(msg -> msg.replaceAll("\\s+", " "))
                    .filter(msg -> msg.toLowerCase().contains("update product_variants"))
                    .findFirst()
                    .orElse("");

            System.out.println(">>> [SQL CAPTURED]: " + updateSql);
            assertFalse(updateSql.isEmpty(), "Expected an UPDATE product_variants statement to be executed");
            assertFalse(updateSql.toLowerCase().contains("stock="),
                    "UPDATE product_variants must NOT contain stock column, but found: " + updateSql);
        } finally {
            hibernateLogger.setLevel(oldLevel);
            hibernateLogger.detachAppender(listAppender);
        }
    }

    @Test
    @DisplayName("b. Test race ÉP CỬA SỔ: luồng Admin đổi price không làm mất lượt trừ kho của Checkout")
    void shouldPreserveConcurrentCheckoutStockDeductionDuringProductUpdate() throws Exception {
        String sku = TEST_PREFIX + "race-" + System.currentTimeMillis();
        Product product = createProductWithVariant(sku, 10, 100000L);
        ProductVariant initialVariant = variantRepository.findByProductId(product.getId()).get(0);
        Long variantId = initialVariant.getId();

        CountDownLatch adminReadLatch = new CountDownLatch(1);
        CountDownLatch checkoutDoneLatch = new CountDownLatch(1);

        CompletableFuture<Void> adminFuture = CompletableFuture.runAsync(() -> {
            requiresNewTemplate.execute(status -> {
                ProductVariant managed = variantRepository.findById(variantId).orElseThrow();
                assertEquals(10, managed.getStock());

                adminReadLatch.countDown();

                try {
                    boolean ok = checkoutDoneLatch.await(5, TimeUnit.SECONDS);
                    assertTrue(ok, "Timeout waiting for checkout thread");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }

                managed.setPrice(BigDecimal.valueOf(150000L));
                variantRepository.save(managed);
                return null;
            });
        });

        CompletableFuture<Void> checkoutFuture = CompletableFuture.runAsync(() -> {
            try {
                boolean ok = adminReadLatch.await(5, TimeUnit.SECONDS);
                assertTrue(ok, "Timeout waiting for admin read");

                requiresNewTemplate.execute(status -> {
                    int affected = variantRepository.deductStock(variantId, 3);
                    assertEquals(1, affected, "Deduct stock must affect 1 row");
                    return null;
                });

                checkoutDoneLatch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        CompletableFuture.allOf(adminFuture, checkoutFuture).get(10, TimeUnit.SECONDS);

        ProductVariant finalVariant = variantRepository.findById(variantId).orElseThrow();
        assertEquals(new BigDecimal("150000.00"), finalVariant.getPrice());
        assertEquals(7, finalVariant.getStock(),
                "Stock must be exactly 7 (10 - 3). If stock is 10, a lost update occurred because updateProduct overwrote DB stock!");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("c. Test: stock trong request cho biến thể cũ không có tác dụng (DB giữ nguyên dù gửi 999)")
    void shouldIgnoreStockInRequestForExistingVariant() throws Exception {
        String sku = TEST_PREFIX + "ignore-stock-" + System.currentTimeMillis();
        Product product = createProductWithVariant(sku, 15, 100000L);
        ProductVariant variant = variantRepository.findByProductId(product.getId()).get(0);

        ProductUpsertRequest request = ProductUpsertRequest.builder()
                .name("Updated " + sku)
                .categoryId(testCategory.getId())
                .basePrice(120000L)
                .isActive(true)
                .variants(List.of(VariantUpsertRequest.builder()
                        .id(variant.getId())
                        .sku(sku)
                        .size("M")
                        .color("Đen")
                        .stock(999)
                        .price(120000L)
                        .build()))
                .build();

        mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        ProductVariant updatedVariant = variantRepository.findById(variant.getId()).orElseThrow();
        assertEquals(15, updatedVariant.getStock(),
                "Stock in database must remain 15, ignoring the 999 value submitted in request!");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("d. Test: biến thể mới nhận stock ban đầu; thiếu stock -> 400; stock âm hoặc > 1.000.000 -> 400")
    void shouldValidateInitialStockForNewVariants() throws Exception {
        String skuBase = TEST_PREFIX + "newvar-" + System.currentTimeMillis();
        Product product = createProductWithVariant(skuBase + "-exist", 10, 100000L);
        ProductVariant existingVariant = variantRepository.findByProductId(product.getId()).get(0);

        // Case 1: Thiếu stock cho biến thể mới -> 400
        ProductUpsertRequest missingStockReq = ProductUpsertRequest.builder()
                .name("Product With New Variant")
                .categoryId(testCategory.getId())
                .basePrice(100000L)
                .isActive(true)
                .variants(List.of(
                        VariantUpsertRequest.builder()
                                .id(existingVariant.getId())
                                .sku(existingVariant.getSku())
                                .size("M")
                                .color("Đen")
                                .price(100000L)
                                .build(),
                        VariantUpsertRequest.builder()
                                .sku(skuBase + "-new")
                                .size("L")
                                .color("Trắng")
                                .stock(null)
                                .price(100000L)
                                .build()
                ))
                .build();

        mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingStockReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Tồn kho ban đầu không được để trống khi tạo biến thể mới")));

        // Case 2: Stock âm (-5) -> 400
        ProductUpsertRequest negativeStockReq = ProductUpsertRequest.builder()
                .name("Product With Negative Stock")
                .categoryId(testCategory.getId())
                .basePrice(100000L)
                .isActive(true)
                .variants(List.of(
                        VariantUpsertRequest.builder()
                                .sku(skuBase + "-neg")
                                .size("L")
                                .color("Trắng")
                                .stock(-5)
                                .price(100000L)
                                .build()
                ))
                .build();

        mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeStockReq)))
                .andExpect(status().isBadRequest());

        // Case 3: Stock vượt quá 1.000.000 -> 400
        ProductUpsertRequest excessiveStockReq = ProductUpsertRequest.builder()
                .name("Product With Excessive Stock")
                .categoryId(testCategory.getId())
                .basePrice(100000L)
                .isActive(true)
                .variants(List.of(
                        VariantUpsertRequest.builder()
                                .sku(skuBase + "-excess")
                                .size("L")
                                .color("Trắng")
                                .stock(1000001)
                                .price(100000L)
                                .build()
                ))
                .build();

        mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(excessiveStockReq)))
                .andExpect(status().isBadRequest());

        // Case 4: Stock hợp lệ (stock = 50) -> thành công 200
        ProductUpsertRequest validStockReq = ProductUpsertRequest.builder()
                .name("Product With Valid Stock")
                .categoryId(testCategory.getId())
                .basePrice(100000L)
                .isActive(true)
                .variants(List.of(
                        VariantUpsertRequest.builder()
                                .id(existingVariant.getId())
                                .sku(existingVariant.getSku())
                                .size("M")
                                .color("Đen")
                                .price(100000L)
                                .build(),
                        VariantUpsertRequest.builder()
                                .sku(skuBase + "-valid")
                                .size("L")
                                .color("Trắng")
                                .stock(50)
                                .price(100000L)
                                .build()
                ))
                .build();

        mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validStockReq)))
                .andExpect(status().isOk());

        ProductVariant addedVariant = variantRepository.findByProductId(product.getId()).stream()
                .filter(v -> (skuBase + "-valid").equals(v.getSku()))
                .findFirst()
                .orElseThrow();
        assertEquals(50, addedVariant.getStock());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("e. Test: xóa biến thể còn stock > 0 -> 409 với thông báo đúng; stock = 0 -> xóa được")
    void shouldRejectDeletingVariantWithPositiveStock() throws Exception {
        String sku1 = TEST_PREFIX + "del-pos-" + System.currentTimeMillis();
        String sku2 = TEST_PREFIX + "del-zero-" + System.currentTimeMillis();

        Product product = createProductWithVariant(sku1, 8, 100000L);
        ProductVariant variant1 = variantRepository.findByProductId(product.getId()).get(0);

        // Thêm biến thể 2 có stock = 0
        ProductVariant variant2 = requiresNewTemplate.execute(status -> variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku(sku2)
                .size("L")
                .color("Trắng")
                .stock(0)
                .price(BigDecimal.valueOf(100000L))
                .build()));

        // Case 1: Bỏ variant 1 (stock = 8) khỏi request -> 409 Conflict
        ProductUpsertRequest deletePosReq = ProductUpsertRequest.builder()
                .name(product.getName())
                .categoryId(testCategory.getId())
                .basePrice(100000L)
                .isActive(true)
                .variants(List.of(VariantUpsertRequest.builder()
                        .id(variant2.getId())
                        .sku(sku2)
                        .size("L")
                        .color("Trắng")
                        .price(100000L)
                        .build()))
                .build();

        mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deletePosReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Không thể xóa biến thể SKU '" + sku1 + "' vì còn 8 sản phẩm tồn kho. Hãy điều chỉnh tồn kho về 0 tại trang Kho hàng trước khi xóa.")));

        // Case 2: Bỏ variant 2 (stock = 0) khỏi request -> thành công 200, variant 2 bị xóa
        ProductUpsertRequest deleteZeroReq = ProductUpsertRequest.builder()
                .name(product.getName())
                .categoryId(testCategory.getId())
                .basePrice(100000L)
                .isActive(true)
                .variants(List.of(VariantUpsertRequest.builder()
                        .id(variant1.getId())
                        .sku(sku1)
                        .size("M")
                        .color("Đen")
                        .price(100000L)
                        .build()))
                .build();

        mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deleteZeroReq)))
                .andExpect(status().isOk());

        assertFalse(variantRepository.existsById(variant2.getId()), "Variant with stock = 0 should be deleted successfully");
        assertTrue(variantRepository.existsById(variant1.getId()), "Variant 1 should remain in database");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("f. Test: stock null cho biến thể cũ hợp lệ (200)")
    void shouldAllowNullStockForExistingVariant() throws Exception {
        String sku = TEST_PREFIX + "null-stock-" + System.currentTimeMillis();
        Product product = createProductWithVariant(sku, 20, 100000L);
        ProductVariant variant = variantRepository.findByProductId(product.getId()).get(0);

        ProductUpsertRequest request = ProductUpsertRequest.builder()
                .name("Updated " + sku)
                .categoryId(testCategory.getId())
                .basePrice(130000L)
                .isActive(true)
                .variants(List.of(VariantUpsertRequest.builder()
                        .id(variant.getId())
                        .sku(sku)
                        .size("M")
                        .color("Đen")
                        .stock(null) // null cho biến thể cũ
                        .price(130000L)
                        .build()))
                .build();

        mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        ProductVariant updatedVariant = variantRepository.findById(variant.getId()).orElseThrow();
        assertEquals(20, updatedVariant.getStock(), "Stock should remain unchanged at 20");
    }
    @Test
    @DisplayName("g. Test OpenAPI documentation: VariantUpsertRequest schema reflects optional stock and description")
    void shouldReflectStockSchemaInOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.VariantUpsertRequest.properties.stock.description")
                        .value(containsString("Số lượng tồn kho ban đầu (chỉ bắt buộc và có tác dụng khi tạo biến thể mới")))
                .andExpect(jsonPath("$.components.schemas.VariantUpsertRequest.properties.stock.minimum").value(0))
                .andExpect(jsonPath("$.components.schemas.VariantUpsertRequest.properties.stock.maximum").value(1000000))
                .andExpect(jsonPath("$.components.schemas.VariantUpsertRequest.required")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("stock"))));
    }
}