package com.shop.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MvcResult;
import com.shop.backend.common.model.DataSourceType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
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
class ProductPublicIntegrationTest {

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

    private Category parentCategory;
    private Category childCategory;
    private Category otherCategory;

    private Product product1;
    private Product product2;
    private Product inactiveProduct;
    private Product outOfStockProduct;
    private Product crossVariantProduct;
    private final List<Product> relatedBatchProducts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // 1. Categories
        parentCategory = categoryRepository.save(Category.builder()
                .name("Áo Nam Test")
                .slug("ao-nam-test")
                .isActive(true)
                .build());

        childCategory = categoryRepository.save(Category.builder()
                .name("Áo Thun Test")
                .slug("ao-thun-test")
                .parent(parentCategory)
                .isActive(true)
                .build());

        otherCategory = categoryRepository.save(Category.builder()
                .name("Quần Nam Test")
                .slug("quan-nam-test")
                .isActive(true)
                .build());

        // 2. Active Product 1 (in childCategory): size M/Đỏ, size L/Xanh
        product1 = productRepository.save(Product.builder()
                .name("Áo thun phong cách basic")
                .slug("ao-thun-phong-cach-basic")
                .category(childCategory)
                .basePrice(new BigDecimal("200000"))
                .description("Mô tả áo thun basic")
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        variantRepository.save(ProductVariant.builder()
                .product(product1)
                .sku("TEST-AT1-M-RED")
                .size("M")
                .color("Đỏ")
                .stock(10)
                .price(new BigDecimal("200000"))
                .build());

        variantRepository.save(ProductVariant.builder()
                .product(product1)
                .sku("TEST-AT1-L-BLU")
                .size("L")
                .color("Xanh")
                .stock(5)
                .price(new BigDecimal("250000"))
                .build());

        imageRepository.save(ProductImage.builder()
                .product(product1)
                .url("/uploads/products/test-1.jpg")
                .alt("Áo thun basic")
                .isPrimary(true)
                .sortOrder(0)
                .build());

        // 3. Active Product 2 (in parentCategory): size M/Xanh, size L/Đỏ
        product2 = productRepository.save(Product.builder()
                .name("Áo sơ mi nam công sở")
                .slug("ao-so-mi-nam-cong-so")
                .category(parentCategory)
                .basePrice(new BigDecimal("300000"))
                .description("Mô tả áo sơ mi")
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        variantRepository.save(ProductVariant.builder()
                .product(product2)
                .sku("TEST-SM2-M-BLU")
                .size("M")
                .color("Xanh")
                .stock(8)
                .price(new BigDecimal("300000"))
                .build());

        variantRepository.save(ProductVariant.builder()
                .product(product2)
                .sku("TEST-SM2-L-RED")
                .size("L")
                .color("Đỏ")
                .stock(12)
                .price(new BigDecimal("320000"))
                .build());

        // 4. Inactive Product (isActive = false)
        inactiveProduct = productRepository.save(Product.builder()
                .name("Áo khoác gió ngừng bán")
                .slug("ao-khoac-gio-ngung-ban")
                .category(parentCategory)
                .basePrice(new BigDecimal("500000"))
                .description("Đã dừng kinh doanh")
                .isActive(false)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        variantRepository.save(ProductVariant.builder()
                .product(inactiveProduct)
                .sku("TEST-AK3-XL-BLK")
                .size("XL")
                .color("Đen")
                .stock(4)
                .price(new BigDecimal("500000"))
                .build());

        // 5. Out of stock Product
        outOfStockProduct = productRepository.save(Product.builder()
                .name("Quần jean ống rộng hết hàng")
                .slug("quan-jean-ong-rong-het-hang")
                .category(otherCategory)
                .basePrice(new BigDecimal("400000"))
                .description("Hết hàng")
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        variantRepository.save(ProductVariant.builder()
                .product(outOfStockProduct)
                .sku("TEST-QJ4-30-BLU")
                .size("30")
                .color("Xanh")
                .stock(0)
                .price(new BigDecimal("400000"))
                .build());

        // 6. Cross-variant test product (has size M and color Đỏ, but on DIFFERENT variants: M/Xanh, L/Đỏ)
        crossVariantProduct = productRepository.save(Product.builder()
                .name("Áo phối màu độc lạ")
                .slug("ao-phoi-mau-doc-la")
                .category(childCategory)
                .basePrice(new BigDecimal("150000"))
                .description("Cross variant test")
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        variantRepository.save(ProductVariant.builder()
                .product(crossVariantProduct)
                .sku("TEST-CV5-M-BLU")
                .size("M")
                .color("Xanh")
                .stock(10)
                .price(new BigDecimal("150000"))
                .build());

        variantRepository.save(ProductVariant.builder()
                .product(crossVariantProduct)
                .sku("TEST-CV5-L-RED")
                .size("L")
                .color("Đỏ")
                .stock(10)
                .price(new BigDecimal("180000"))
                .build());

        // 7. Seed 9 additional active products in childCategory for related products test (max 8)
        for (int i = 1; i <= 9; i++) {
            Product p = productRepository.save(Product.builder()
                    .name("Áo thun liên quan " + i)
                    .slug("ao-thun-lien-quan-" + i)
                    .category(childCategory)
                    .basePrice(new BigDecimal("180000"))
                    .description("Related product " + i)
                    .isActive(true)
                    .dataSource(DataSourceType.WEBSITE)
                    .build());
            variantRepository.save(ProductVariant.builder()
                    .product(p)
                    .sku("TEST-REL-" + i)
                    .size("M")
                    .color("Trắng")
                    .stock(5)
                    .price(new BigDecimal("180000"))
                    .build());
            relatedBatchProducts.add(p);
        }

        entityManager.flush();
    }

    @Test
    @DisplayName("Truy cập công khai không token: GET /api/products, /{slug}, /api/categories, /filter-options đều trả 200 OK")
    void testPublicAccessWithoutToken() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(get("/api/products/{slug}", product1.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product1.getId()))
                .andExpect(jsonPath("$.slug").value(product1.getSlug()))
                .andExpect(jsonPath("$.variants").isArray())
                .andExpect(jsonPath("$.images").isArray());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/products/filter-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizes").isArray())
                .andExpect(jsonPath("$.colors").isArray())
                .andExpect(jsonPath("$.minPrice").isNumber())
                .andExpect(jsonPath("$.maxPrice").isNumber());
    }

    @Test
    @DisplayName("Sản phẩm isActive=false không xuất hiện ở list và GET detail trả 404")
    void testInactiveProductExcludedFromListAndDetail404() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == '" + inactiveProduct.getSlug() + "')]").doesNotExist());

        mockMvc.perform(get("/api/products/{slug}", inactiveProduct.getSlug()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("ngừng kinh doanh")));
    }

    @Test
    @DisplayName("Lọc đồng thời size X và color Y chỉ khớp khi CÙNG 1 biến thể có cả X và Y")
    void testFilterSizeAndColorAppliedToSameVariant() throws Exception {
        // Query sizes=M AND colors=Đỏ:
        // product1 has variant TEST-AT1-M-RED (size M, color Đỏ) -> MATCHES
        // product2 has TEST-SM2-M-BLU (M/Xanh) and TEST-SM2-L-RED (L/Đỏ) -> MUST NOT MATCH
        // crossVariantProduct has TEST-CV5-M-BLU (M/Xanh) and TEST-CV5-L-RED (L/Đỏ) -> MUST NOT MATCH
        mockMvc.perform(get("/api/products")
                        .param("sizes", "M")
                        .param("colors", "Đỏ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug", is(product1.getSlug())));
    }

    @Test
    @DisplayName("Lọc theo categoryId bao gồm cả sản phẩm thuộc danh mục con")
    void testFilterCategoryIncludesDescendants() throws Exception {
        // Parent category (Áo Nam Test) has 1 direct product (product2) and 11 child category products (product1, crossVariantProduct, 9 related)
        mockMvc.perform(get("/api/products")
                        .param("categoryId", parentCategory.getId().toString())
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == '" + product1.getSlug() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == '" + product2.getSlug() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == '" + outOfStockProduct.getSlug() + "')]").doesNotExist());
    }

    @Test
    @DisplayName("Lọc theo khoảng giá minPrice và maxPrice")
    void testFilterPriceRange() throws Exception {
        // Range 290,000 - 330,000 matches product2 (variants: 300,000 and 320,000)
        mockMvc.perform(get("/api/products")
                        .param("minPrice", "290000")
                        .param("maxPrice", "330000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == '" + product2.getSlug() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.slug == '" + product1.getSlug() + "')]").doesNotExist());
    }

    @Test
    @DisplayName("Sắp xếp newest, price_asc, price_desc")
    void testSorting() throws Exception {
        MvcResult ascResult = mockMvc.perform(get("/api/products")
                        .param("sort", "price_asc")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ascContent = objectMapper.readTree(ascResult.getResponse().getContentAsString()).get("content");
        assertThat(ascContent.size()).isGreaterThanOrEqualTo(13);
        long prevAscPrice = 0L;
        for (JsonNode item : ascContent) {
            long currentPrice = item.get("minPrice").asLong();
            assertThat(currentPrice).isGreaterThanOrEqualTo(prevAscPrice);
            prevAscPrice = currentPrice;
        }

        MvcResult descResult = mockMvc.perform(get("/api/products")
                        .param("sort", "price_desc")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode descContent = objectMapper.readTree(descResult.getResponse().getContentAsString()).get("content");
        assertThat(descContent.size()).isGreaterThanOrEqualTo(13);
        long prevDescPrice = Long.MAX_VALUE;
        for (JsonNode item : descContent) {
            long currentPrice = item.get("minPrice").asLong();
            assertThat(currentPrice).isLessThanOrEqualTo(prevDescPrice);
            prevDescPrice = currentPrice;
        }

        mockMvc.perform(get("/api/products")
                        .param("sort", "newest")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(13))));

        mockMvc.perform(get("/api/products")
                        .param("categoryId", parentCategory.getId().toString())
                        .param("sort", "price_asc")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug", is(crossVariantProduct.getSlug())))
                .andExpect(jsonPath("$.content[0].minPrice", is(150000)))
                .andExpect(jsonPath("$.content[11].slug", is(product2.getSlug())))
                .andExpect(jsonPath("$.content[11].minPrice", is(300000)));

        mockMvc.perform(get("/api/products")
                        .param("categoryId", parentCategory.getId().toString())
                        .param("sort", "price_desc")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug", is(product2.getSlug())))
                .andExpect(jsonPath("$.content[0].minPrice", is(300000)))
                .andExpect(jsonPath("$.content[11].slug", is(crossVariantProduct.getSlug())))
                .andExpect(jsonPath("$.content[11].minPrice", is(150000)));
    }

    @Test
    @DisplayName("Phân trang trả đúng page, size, totalElements, totalPages")
    void testPagination() throws Exception {
        MvcResult unfilteredResult = mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(5)))
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andReturn();

        JsonNode rootNode = objectMapper.readTree(unfilteredResult.getResponse().getContentAsString());
        long totalElements = rootNode.get("totalElements").asLong();
        int totalPages = rootNode.get("totalPages").asInt();
        assertThat(totalElements).isGreaterThanOrEqualTo(13L);
        assertThat(totalPages).isEqualTo((int) Math.ceil((double) totalElements / 5.0));

        mockMvc.perform(get("/api/products")
                        .param("categoryId", parentCategory.getId().toString())
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(5)))
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.totalElements", is(12)))
                .andExpect(jsonPath("$.totalPages", is(3)));

        mockMvc.perform(get("/api/products")
                        .param("categoryId", parentCategory.getId().toString())
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(2)))
                .andExpect(jsonPath("$.size", is(5)))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(12)))
                .andExpect(jsonPath("$.totalPages", is(3)));
    }

    @Test
    @DisplayName("Tham số không hợp lệ trả 400 Bad Request kèm thông báo rõ ràng, không 500")
    void testInvalidParametersReturn400() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "invalid_sort"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Kiểu sắp xếp không hợp lệ")));

        mockMvc.perform(get("/api/products")
                        .param("minPrice", "500000")
                        .param("maxPrice", "100000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Giá tối thiểu không được lớn hơn")));

        mockMvc.perform(get("/api/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Số trang không hợp lệ")));

        mockMvc.perform(get("/api/products").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Kích thước trang không hợp lệ")));

        mockMvc.perform(get("/api/products").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Kích thước trang không hợp lệ")));
    }

    @Test
    @DisplayName("Tìm kiếm từ khóa không dấu khớp cả tên và slug")
    void testUnaccentedKeywordSearch() throws Exception {
        mockMvc.perform(get("/api/products").param("keyword", "ao thun"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == '" + product1.getSlug() + "')]").exists());

        mockMvc.perform(get("/api/products").param("keyword", "ao-thun"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == '" + product1.getSlug() + "')]").exists());
    }

    @Test
    @DisplayName("Sản phẩm liên quan (relatedProducts): tối đa 8 sản phẩm cùng danh mục, không chứa chính nó, chỉ lấy active")
    void testRelatedProducts() throws Exception {
        // product1 is in childCategory. There are 10 other products in childCategory (crossVariantProduct + 9 related products).
        mockMvc.perform(get("/api/products/{slug}", product1.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedProducts", hasSize(8)))
                .andExpect(jsonPath("$.relatedProducts[?(@.id == " + product1.getId() + ")]").doesNotExist())
                .andExpect(jsonPath("$.relatedProducts[?(@.slug == '" + inactiveProduct.getSlug() + "')]").doesNotExist());
    }

    @Test
    @DisplayName("Không có lỗi N+1: số lượng query không tăng theo size phân trang")
    void testNoNPlusOneQuery() throws Exception {
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        sessionFactory.getStatistics().setStatisticsEnabled(true);

        sessionFactory.getStatistics().clear();
        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk());
        long queriesForSize2 = sessionFactory.getStatistics().getPrepareStatementCount();

        sessionFactory.getStatistics().clear();
        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());
        long queriesForSize10 = sessionFactory.getStatistics().getPrepareStatementCount();

        assertThat(queriesForSize10).isEqualTo(queriesForSize2);
    }

    @Test
    @DisplayName("Các API admin vẫn được bảo vệ: 401 khi không có token, 403 khi là CUSTOMER")
    @WithMockUser(roles = "CUSTOMER")
    void testAdminEndpointsForbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Các API admin trả 401 khi không đăng nhập")
    void testAdminEndpointsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("NoResourceFoundException trả về 404 ErrorResponse với message tiếng Việt")
    void testNoResourceFoundReturns404ErrorResponse() throws Exception {
        mockMvc.perform(get("/uploads/products/file-khong-ton-tai-abc123.jpg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Tài nguyên không tồn tại")));
    }

    @Test
    @DisplayName("Sắp xếp price_desc dùng MIN(effectivePrice) giảm dần")
    void testPriceDescUsesMinEffectivePrice() throws Exception {
        // Create Product HighMin: variants 350k and 360k (min=350k, max=360k)
        // Create Product LowMinHighMax: variants 100k and 500k (min=100k, max=500k)
        Product highMin = productRepository.save(Product.builder()
                .name("Sản phẩm giá min cao")
                .slug("san-pham-gia-min-cao")
                .category(childCategory)
                .basePrice(new BigDecimal("350000"))
                .description("High min price")
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());
        variantRepository.save(ProductVariant.builder()
                .product(highMin)
                .sku("TEST-HMIN-1")
                .size("M")
                .color("Đen")
                .stock(5)
                .price(new BigDecimal("350000"))
                .build());

        Product lowMinHighMax = productRepository.save(Product.builder()
                .name("Sản phẩm giá min thấp max cao")
                .slug("san-pham-gia-min-thap-max-cao")
                .category(childCategory)
                .basePrice(new BigDecimal("100000"))
                .description("Low min high max")
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());
        variantRepository.save(ProductVariant.builder()
                .product(lowMinHighMax)
                .sku("TEST-LMIN-1")
                .size("M")
                .color("Đen")
                .stock(5)
                .price(new BigDecimal("100000"))
                .build());
        variantRepository.save(ProductVariant.builder()
                .product(lowMinHighMax)
                .sku("TEST-LMIN-2")
                .size("L")
                .color("Đen")
                .stock(5)
                .price(new BigDecimal("500000"))
                .build());

        entityManager.flush();

        // Under MIN(effectivePrice) DESC: highMin (min=350k) MUST come before lowMinHighMax (min=100k)
        String responseContent = mockMvc.perform(get("/api/products")
                        .param("sort", "price_desc")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int idxHighMin = responseContent.indexOf(highMin.getSlug());
        int idxLowMin = responseContent.indexOf(lowMinHighMax.getSlug());

        assertThat(idxHighMin).isGreaterThanOrEqualTo(0);
        assertThat(idxLowMin).isGreaterThanOrEqualTo(0);
        assertThat(idxHighMin).isLessThan(idxLowMin);
    }

    @Test
    @DisplayName("Sản phẩm thuộc danh mục inactive (hoặc có cha inactive) không xuất hiện ở catalog và chi tiết trả 404")
    void testInactiveCategoryProductsExcluded() throws Exception {
        // 1. Direct inactive category
        Category directInactiveCat = categoryRepository.save(Category.builder()
                .name("Danh mục ẩn trực tiếp")
                .slug("danh-muc-an-truc-tiep")
                .isActive(false)
                .build());

        Product prodInDirectInactive = productRepository.save(Product.builder()
                .name("Sản phẩm trong danh mục ẩn")
                .slug("san-pham-trong-danh-muc-an")
                .category(directInactiveCat)
                .basePrice(new BigDecimal("250000"))
                .description("In direct inactive cat")
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());
        variantRepository.save(ProductVariant.builder()
                .product(prodInDirectInactive)
                .sku("TEST-INACT-DIR-1")
                .size("XXS")
                .color("TimHoaCa")
                .stock(5)
                .price(new BigDecimal("250000"))
                .build());

        // 2. Child category of inactive parent
        Category inactiveParent = categoryRepository.save(Category.builder()
                .name("Cha ẩn")
                .slug("cha-an")
                .isActive(false)
                .build());
        Category childOfInactive = categoryRepository.save(Category.builder()
                .name("Con của cha ẩn")
                .slug("con-cua-cha-an")
                .parent(inactiveParent)
                .isActive(true)
                .build());

        Product prodInChildOfInactive = productRepository.save(Product.builder()
                .name("Sản phẩm nhánh cha ẩn")
                .slug("san-pham-nhanh-cha-an")
                .category(childOfInactive)
                .basePrice(new BigDecimal("260000"))
                .description("In branch of inactive parent")
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());
        variantRepository.save(ProductVariant.builder()
                .product(prodInChildOfInactive)
                .sku("TEST-INACT-BRANCH-1")
                .size("4XL")
                .color("VangCamDocLa")
                .stock(5)
                .price(new BigDecimal("260000"))
                .build());

        entityManager.flush();

        // GET /api/products should not include prodInDirectInactive or prodInChildOfInactive
        mockMvc.perform(get("/api/products").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == '" + prodInDirectInactive.getSlug() + "')]").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.slug == '" + prodInChildOfInactive.getSlug() + "')]").doesNotExist());

        // Detail returns 404
        mockMvc.perform(get("/api/products/{slug}", prodInDirectInactive.getSlug()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("ngừng kinh doanh")));

        mockMvc.perform(get("/api/products/{slug}", prodInChildOfInactive.getSlug()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("ngừng kinh doanh")));

        // Querying with inactive categoryId returns 404
        mockMvc.perform(get("/api/products").param("categoryId", directInactiveCat.getId().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/products").param("categoryId", childOfInactive.getId().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("filter-options không chứa size/màu của sản phẩm inactive hoặc trong danh mục inactive")
    void testFilterOptionsExcludesInactive() throws Exception {
        Category inactiveCat = categoryRepository.save(Category.builder()
                .name("Cat Inactive Filter")
                .slug("cat-inactive-filter")
                .isActive(false)
                .build());

        Product prodInInactiveCat = productRepository.save(Product.builder()
                .name("Sản phẩm ẩn filter")
                .slug("san-pham-an-filter")
                .category(inactiveCat)
                .basePrice(new BigDecimal("300000"))
                .description("Inactive filter")
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        variantRepository.save(ProductVariant.builder()
                .product(prodInInactiveCat)
                .sku("TEST-FLT-INACT-CAT")
                .size("4XL")
                .color("MauDocQuyen1")
                .stock(5)
                .price(new BigDecimal("300000"))
                .build());

        // And create variant for inactiveProduct (already isActive=false)
        variantRepository.save(ProductVariant.builder()
                .product(inactiveProduct)
                .sku("TEST-FLT-INACT-PROD")
                .size("XXS")
                .color("MauDocQuyen2")
                .stock(5)
                .price(new BigDecimal("500000"))
                .build());

        entityManager.flush();

        mockMvc.perform(get("/api/products/filter-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colors[?(@ == 'MauDocQuyen1')]").doesNotExist())
                .andExpect(jsonPath("$.colors[?(@ == 'MauDocQuyen2')]").doesNotExist())
                .andExpect(jsonPath("$.sizes[?(@ == '4XL')]").doesNotExist())
                .andExpect(jsonPath("$.sizes[?(@ == 'XXS')]").doesNotExist());
    }

    @Test
    @DisplayName("filter-options trả danh sách size được sắp xếp theo đúng thứ tự của SizeComparator")
    void testFilterOptionsSizesOrderedBySizeComparator() throws Exception {
        // Add variants with sizes: 29, 115cm, Freesize, XL to active products
        variantRepository.save(ProductVariant.builder()
                .product(product1)
                .sku("TEST-SZ-29")
                .size("29")
                .color("XanhNhat")
                .stock(5)
                .price(new BigDecimal("200000"))
                .build());
        variantRepository.save(ProductVariant.builder()
                .product(product1)
                .sku("TEST-SZ-115")
                .size("115cm")
                .color("Nau")
                .stock(5)
                .price(new BigDecimal("200000"))
                .build());
        variantRepository.save(ProductVariant.builder()
                .product(product1)
                .sku("TEST-SZ-FS")
                .size("Freesize")
                .color("Be")
                .stock(5)
                .price(new BigDecimal("200000"))
                .build());

        entityManager.flush();

        mockMvc.perform(get("/api/products/filter-options"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String json = result.getResponse().getContentAsString();
                    List<String> sizes = objectMapper.readTree(json).get("sizes").findValuesAsText("sizes");
                    // Read actual array from json
                    com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(json);
                    List<String> actualSizes = new ArrayList<>();
                    rootNode.get("sizes").forEach(node -> actualSizes.add(node.asText()));

                    SizeComparator cmp = new SizeComparator();
                    List<String> expectedSorted = new ArrayList<>(actualSizes);
                    expectedSorted.sort(cmp);

                    assertThat(actualSizes).isEqualTo(expectedSorted);
                });
    }

    @Test
    @DisplayName("Product detail trả variants được sắp xếp theo SizeComparator")
    void testProductDetailVariantsOrderedBySizeComparator() throws Exception {
        Product p = productRepository.save(Product.builder()
                .name("Áo nhiều size test order")
                .slug("ao-nhieu-size-test-order")
                .category(childCategory)
                .basePrice(new BigDecimal("200000"))
                .description("Variant order test")
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        // Insert in reverse/jumbled order: XL, 29, S, Freesize, M
        variantRepository.save(ProductVariant.builder().product(p).sku("ORD-XL").size("XL").color("Đen").stock(2).price(new BigDecimal("200000")).build());
        variantRepository.save(ProductVariant.builder().product(p).sku("ORD-29").size("29").color("Đen").stock(2).price(new BigDecimal("200000")).build());
        variantRepository.save(ProductVariant.builder().product(p).sku("ORD-S").size("S").color("Đen").stock(2).price(new BigDecimal("200000")).build());
        variantRepository.save(ProductVariant.builder().product(p).sku("ORD-FS").size("Freesize").color("Đen").stock(2).price(new BigDecimal("200000")).build());
        variantRepository.save(ProductVariant.builder().product(p).sku("ORD-M").size("M").color("Đen").stock(2).price(new BigDecimal("200000")).build());

        entityManager.flush();

        mockMvc.perform(get("/api/products/{slug}", p.getSlug()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String json = result.getResponse().getContentAsString();
                    com.fasterxml.jackson.databind.JsonNode variantsNode = objectMapper.readTree(json).get("variants");
                    List<String> variantSizes = new ArrayList<>();
                    variantsNode.forEach(v -> variantSizes.add(v.get("size").asText()));

                    // Expected order: Alpha ("S", "M", "XL") < Numeric ("29") < Other ("Freesize")
                    assertThat(variantSizes).containsExactly("S", "M", "XL", "29", "Freesize");
                });
    }
}
