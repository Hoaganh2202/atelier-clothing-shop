package com.shop.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.product.dto.ProductUpsertRequest;
import com.shop.backend.product.dto.VariantUpsertRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
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
class ProductAdminBugFixesRegressionTest {

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
    private ProductImageRepository imageRepository;

    @Autowired
    private EntityManager entityManager;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = categoryRepository.save(Category.builder()
                .name("Thời Trang Regression")
                .slug("thoi-trang-regression-" + System.currentTimeMillis())
                .isActive(true)
                .build());
    }

    private Product createSampleProduct(String name, Long basePrice) {
        String slug = com.shop.backend.common.util.SlugUtils.toSlug(name) + "-" + System.currentTimeMillis();
        return productRepository.save(Product.builder()
                .category(testCategory)
                .name(name)
                .slug(slug)
                .basePrice(BigDecimal.valueOf(basePrice))
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());
    }

    private ProductVariant createSampleVariant(Product product, String sku, String size, String color, int stock, Long price) {
        return variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku(sku)
                .size(size)
                .color(color)
                .stock(stock)
                .price(price != null ? BigDecimal.valueOf(price) : null)
                .build());
    }

    // ==========================================
    // BUG-01: GlobalExceptionHandler tests
    // ==========================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("BUG-01: HttpMessageNotReadableException trả về 400 kèm định dạng ErrorResponse")
    void shouldReturn400ForMalformedJsonOrTypeMismatchInBody() throws Exception {
        String invalidBody = "{\"name\": \"Áo Test\", \"categoryId\": \"khong-phai-so\"}";

        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Dữ liệu yêu cầu không hợp lệ hoặc sai định dạng")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("BUG-01: MethodArgumentTypeMismatchException (id là chữ) trả về 400 kèm ErrorResponse")
    void shouldReturn400ForPathVariableTypeMismatch() throws Exception {
        mockMvc.perform(get("/api/admin/products/chu-khong-phai-so"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Tham số yêu cầu không đúng định dạng")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("BUG-01: HttpMediaTypeNotSupportedException trả về 415 kèm ErrorResponse")
    void shouldReturn415ForUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_XML)
                .content("<product></product>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Định dạng phương tiện")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("BUG-01: HttpRequestMethodNotSupportedException trả về 405 kèm ErrorResponse")
    void shouldReturn405ForMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/api/admin/products/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Phương thức HTTP không được hỗ trợ")));
    }

    // ==========================================
    // BUG-02: Price boundary @Max(9999999999L)
    // ==========================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("BUG-02: basePrice hoặc variant.price vượt quá 9,999,999,999 trả về 400")
    void shouldRejectPricesExceedingMaxLimit() throws Exception {
        ProductUpsertRequest requestOverflowBase = ProductUpsertRequest.builder()
                .name("Áo Giá Quá Cao")
                .categoryId(testCategory.getId())
                .basePrice(10000000000L)
                .isActive(true)
                .variants(List.of(
                        VariantUpsertRequest.builder()
                                .sku("SKU-MAX-1-" + System.currentTimeMillis())
                                .size("M")
                                .color("Đen")
                                .stock(10)
                                .price(100000L)
                                .build()))
                .build();

        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestOverflowBase)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.basePrice").value(org.hamcrest.Matchers.containsString("9.999.999.999")));

        ProductUpsertRequest requestOverflowVariant = ProductUpsertRequest.builder()
                .name("Áo Variant Giá Quá Cao")
                .categoryId(testCategory.getId())
                .basePrice(100000L)
                .isActive(true)
                .variants(List.of(
                        VariantUpsertRequest.builder()
                                .sku("SKU-MAX-2-" + System.currentTimeMillis())
                                .size("M")
                                .color("Đen")
                                .stock(10)
                                .price(10000000000L)
                                .build()))
                .build();

        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestOverflowVariant)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['variants[0].price']").value(org.hamcrest.Matchers.containsString("9.999.999.999")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("BUG-02: basePrice bằng 9,999,999,999 tạo thành công 201")
    void shouldAcceptBasePriceAtMaxLimit() throws Exception {
        ProductUpsertRequest request = ProductUpsertRequest.builder()
                .name("Áo Giới Hạn Giá")
                .categoryId(testCategory.getId())
                .basePrice(9999999999L)
                .isActive(true)
                .variants(List.of(
                        VariantUpsertRequest.builder()
                                .sku("SKU-MAX-LIMIT-" + System.currentTimeMillis())
                                .size("XL")
                                .color("Vàng")
                                .stock(10)
                                .price(9999999999L)
                                .build()))
                .build();

        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.basePrice").value(9999999999L));
    }

    // ==========================================
    // BUG-03: updateProduct sequence
    // ==========================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("BUG-03: Cập nhật sản phẩm: xóa biến thể cũ và thêm biến thể mới có cùng (size, color) trong 1 PUT không bị 409")
    void shouldAllowReAddingSameSizeColorVariantWhenOldVariantIsDeletedInSamePut() throws Exception {
        Product product = createSampleProduct("Áo Sơ Mi Form Rộng", 200000L);
        ProductVariant oldVariant = createSampleVariant(product, "SKU-REPLACE-OLD-" + System.currentTimeMillis(), "L", "Xanh", 0, 200000L);

        entityManager.flush();
        entityManager.clear();

        ProductUpsertRequest putRequest = ProductUpsertRequest.builder()
                .name("Áo Sơ Mi Form Rộng")
                .categoryId(testCategory.getId())
                .basePrice(200000L)
                .isActive(true)
                .variants(List.of(
                        VariantUpsertRequest.builder()
                                .id(null)
                                .sku("SKU-REPLACE-NEW-" + System.currentTimeMillis())
                                .size("L")
                                .color("Xanh")
                                .stock(15)
                                .price(210000L)
                                .build()))
                .build();

        mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(putRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants").isArray())
                .andExpect(jsonPath("$.variants.length()").value(1))
                .andExpect(jsonPath("$.variants[0].sku").value(org.hamcrest.Matchers.startsWith("SKU-REPLACE-NEW-")));

        entityManager.flush();
        entityManager.clear();

        assertThat(variantRepository.findById(oldVariant.getId())).isEmpty();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("BUG-03: Cập nhật sản phẩm với variant id không thuộc sản phẩm trả về 400")
    void shouldReturn400WhenVariantIdDoesNotBelongToProduct() throws Exception {
        Product p1 = createSampleProduct("Sản phẩm 1", 100000L);
        Product p2 = createSampleProduct("Sản phẩm 2", 100000L);
        ProductVariant v2 = createSampleVariant(p2, "SKU-P2-" + System.currentTimeMillis(), "M", "Đen", 5, 100000L);

        ProductUpsertRequest putRequest = ProductUpsertRequest.builder()
                .name("Sản phẩm 1 Updated")
                .categoryId(testCategory.getId())
                .basePrice(100000L)
                .isActive(true)
                .variants(List.of(
                        VariantUpsertRequest.builder()
                                .id(v2.getId())
                                .sku(v2.getSku())
                                .size("M")
                                .color("Đen")
                                .stock(5)
                                .build()))
                .build();

        mockMvc.perform(put("/api/admin/products/{id}", p1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(putRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("không thuộc sản phẩm này")));
    }

    // ==========================================
    // BUG-06: Slug empty string auto generation
    // ==========================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("BUG-06: slug gửi chuỗi rỗng '' được chấp nhận và tự sinh từ tên sản phẩm")
    void shouldAutoGenerateSlugWhenEmptyStringProvided() throws Exception {
        String uniqueName = "Áo Hoodie Zip Nỉ " + System.currentTimeMillis();
        ProductUpsertRequest request = ProductUpsertRequest.builder()
                .name(uniqueName)
                .slug("")
                .categoryId(testCategory.getId())
                .basePrice(350000L)
                .isActive(true)
                .variants(List.of(
                        VariantUpsertRequest.builder()
                                .sku("SKU-HOODIE-" + System.currentTimeMillis())
                                .size("XL")
                                .color("Xám")
                                .stock(12)
                                .build()))
                .build();

        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(org.hamcrest.Matchers.startsWith("ao-hoodie-zip-ni-")));
    }

    // ==========================================
    // BUG-07: Search keyword without accent
    // ==========================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("BUG-07: Tìm kiếm từ khóa không dấu có dấu cách tìm thấy sản phẩm có dấu (match qua slug)")
    void shouldFindAccentedProductUsingAccentFreeMultiWordKeyword() throws Exception {
        Product product = Product.builder()
                .category(testCategory)
                .name("Áo Thun Đồng Phục Doanh Nghiệp " + System.currentTimeMillis())
                .slug("ao-thun-dong-phuc-doanh-nghiep-" + System.currentTimeMillis())
                .basePrice(BigDecimal.valueOf(180000L))
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build();
        productRepository.save(product);

        createSampleVariant(product, "SKU-DN-" + System.currentTimeMillis(), "L", "Trắng", 50, 180000L);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/products")
                .param("keyword", "ao thun dong phuc doanh nghiep"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value(product.getName()));
    }

    // ==========================================
    // BUG-04: Security filter blocks CUSTOMER with empty body
    // ==========================================

    @Test
    @DisplayName("BUG-04: CUSTOMER gọi POST /api/admin/products với body rỗng nhận 403 Forbidden thay vì 400")
    void shouldReturn403ForCustomerEvenWithEmptyBodyBeforeMvcBodyParsing() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("customer").roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("không có quyền")));
    }

    // ==========================================
    // Item 7: Primary image reassignment upon deletion
    // ==========================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Item 7: Xóa ảnh isPrimary=true, ảnh còn lại có sortOrder nhỏ nhất tự động được gán isPrimary=true")
    void shouldReassignPrimaryImageToNextImageWithSmallestSortOrderWhenPrimaryDeleted() throws Exception {
        Product product = createSampleProduct("Áo Test Image Deletion", 150000L);

        ProductImage img1 = imageRepository.save(ProductImage.builder()
                .product(product)
                .url("/uploads/products/img1.jpg")
                .sortOrder(0)
                .isPrimary(true)
                .build());

        ProductImage img2 = imageRepository.save(ProductImage.builder()
                .product(product)
                .url("/uploads/products/img2.jpg")
                .sortOrder(5)
                .isPrimary(false)
                .build());

        ProductImage img3 = imageRepository.save(ProductImage.builder()
                .product(product)
                .url("/uploads/products/img3.jpg")
                .sortOrder(2)
                .isPrimary(false)
                .build());

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(delete("/api/admin/products/{productId}/images/{imageId}", product.getId(), img1.getId()))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        ProductImage refreshedImg3 = imageRepository.findById(img3.getId()).orElseThrow();
        ProductImage refreshedImg2 = imageRepository.findById(img2.getId()).orElseThrow();

        assertThat(refreshedImg3.getIsPrimary()).isTrue();
        assertThat(refreshedImg2.getIsPrimary()).isFalse();
    }
}
