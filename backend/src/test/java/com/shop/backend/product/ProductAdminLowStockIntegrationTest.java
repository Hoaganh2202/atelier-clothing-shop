package com.shop.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.common.util.SlugUtils;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ProductAdminLowStockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private EntityManager entityManager;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = categoryRepository.save(Category.builder()
                .name("Thời Trang Nam")
                .slug("thoi-trang-nam-" + System.currentTimeMillis())
                .isActive(true)
                .build());
    }

    private Product createProduct(String name, boolean isActive) {
        String slug = SlugUtils.toSlug(name) + "-" + System.nanoTime();
        return productRepository.save(Product.builder()
                .category(testCategory)
                .name(name)
                .slug(slug)
                .basePrice(BigDecimal.valueOf(200000L))
                .isActive(isActive)
                .dataSource(DataSourceType.WEBSITE)
                .build());
    }

    private ProductVariant createVariant(Product product, String sku, String size, String color, int stock) {
        return variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku(sku)
                .size(size)
                .color(color)
                .stock(stock)
                .price(BigDecimal.valueOf(200000L))
                .build());
    }

    @Test
    @DisplayName("T1: Phân quyền - 401 khi chưa đăng nhập, 403 với CUSTOMER, 200 với ADMIN và STAFF")
    void shouldEnforceSecurityRoles() throws Exception {
        mockMvc.perform(get("/api/admin/products/variants/low-stock"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/products/variants/low-stock")
                .with(SecurityMockMvcRequestPostProcessors.user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/products/variants/low-stock")
                .with(SecurityMockMvcRequestPostProcessors.user("staff").roles("STAFF")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/products/variants/low-stock")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("T2: Trả về biến thể có stock < threshold, loại trừ biến thể có stock >= threshold")
    void shouldReturnOnlyVariantsWithStockBelowThreshold() throws Exception {
        Product product = createProduct("Áo Sơ Mi Oxford", true);
        String prefix = "OXF-" + System.nanoTime();

        ProductVariant vLow1 = createVariant(product, prefix + "-S", "S", "Trắng", 3);
        ProductVariant vLow2 = createVariant(product, prefix + "-M", "M", "Trắng", 9);
        ProductVariant vBoundary = createVariant(product, prefix + "-L", "L", "Trắng", 10);
        ProductVariant vHigh = createVariant(product, prefix + "-XL", "XL", "Trắng", 25);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/products/variants/low-stock")
                .param("threshold", "10")
                .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.sku == '" + vLow1.getSku() + "')].stock").value(3))
                .andExpect(jsonPath("$.content[?(@.sku == '" + vLow2.getSku() + "')].stock").value(9))
                .andExpect(jsonPath("$.content[?(@.sku == '" + vBoundary.getSku() + "')]").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.sku == '" + vHigh.getSku() + "')]").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("T3: Không trả về biến thể thuộc sản phẩm không hoạt động (isActive = false)")
    void shouldNotReturnVariantsOfInactiveProducts() throws Exception {
        Product inactiveProduct = createProduct("Áo Khoác Cũ Ngừng Bán", false);
        Product activeProduct = createProduct("Áo Polo Mới", true);
        String prefix = "INACT-" + System.nanoTime();

        ProductVariant vInactive = createVariant(inactiveProduct, prefix + "-INACT", "M", "Đen", 2);
        ProductVariant vActive = createVariant(activeProduct, prefix + "-ACT", "M", "Đen", 4);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/products/variants/low-stock")
                .param("threshold", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.sku == '" + vActive.getSku() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.sku == '" + vInactive.getSku() + "')]").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("T4: Danh sách sắp xếp theo tồn kho tăng dần, thứ tự phụ ổn định theo ID")
    void shouldSortVariantsByStockAscendingWithStableIdSort() throws Exception {
        Product product = createProduct("Áo Thun Phân Loại", true);
        String prefix = "SORT-" + System.nanoTime();

        ProductVariant v1 = createVariant(product, prefix + "-MID", "M", "Xanh", 5);
        ProductVariant v2 = createVariant(product, prefix + "-LOW1", "S", "Xanh", 1);
        ProductVariant v3 = createVariant(product, prefix + "-HIGH", "L", "Xanh", 8);
        ProductVariant v4 = createVariant(product, prefix + "-LOW2", "XL", "Xanh", 1);

        entityManager.flush();
        entityManager.clear();

        var mvcResult = mockMvc.perform(get("/api/admin/products/variants/low-stock")
                .param("threshold", "10")
                .param("size", "100"))
                .andExpect(status().isOk())
                .andReturn();

        String json = mvcResult.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var node = mapper.readTree(json).get("content");

        // Filter to our test variants to verify strict ordering
        java.util.List<Long> variantIds = new java.util.ArrayList<>();
        java.util.List<Integer> variantStocks = new java.util.ArrayList<>();
        for (var item : node) {
            String sku = item.get("sku").asText();
            if (sku.startsWith(prefix)) {
                variantIds.add(item.get("id").asLong());
                variantStocks.add(item.get("stock").asInt());
            }
        }

        assertThat(variantStocks).containsExactly(1, 1, 5, 8);
        assertThat(variantIds.get(0)).isEqualTo(v2.getId());
        assertThat(variantIds.get(1)).isEqualTo(v4.getId());
        assertThat(variantIds.get(2)).isEqualTo(v1.getId());
        assertThat(variantIds.get(3)).isEqualTo(v3.getId());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("T5: Tham số mặc định threshold=10 và size=10 được áp dụng đúng")
    void shouldApplyDefaultThreshold10AndSize10() throws Exception {
        Product product = createProduct("Áo Test Mặc Định", true);
        String prefix = "DEF-" + System.nanoTime();

        ProductVariant vLow = createVariant(product, prefix + "-LOW", "M", "Đỏ", 7);
        ProductVariant vAtThreshold = createVariant(product, prefix + "-EQ", "L", "Đỏ", 10);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/products/variants/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.content[?(@.sku == '" + vLow.getSku() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.sku == '" + vAtThreshold.getSku() + "')]").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("T6 & T7: Giới hạn số bản ghi theo size và bảo toàn totalElements trong PageResponse")
    void shouldLimitResultsBySizeAndPreserveTotalElements() throws Exception {
        Product product = createProduct("Áo Test Phân Trang", true);
        String prefix = "PAGE-" + System.nanoTime();

        createVariant(product, prefix + "-1", "S", "Vàng", 1);
        createVariant(product, prefix + "-2", "M", "Vàng", 2);
        createVariant(product, prefix + "-3", "L", "Vàng", 3);
        createVariant(product, prefix + "-4", "XL", "Vàng", 4);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/products/variants/low-stock")
                .param("threshold", "10")
                .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.totalPages").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("T8: Biến thể có tồn kho = 0 được trả về bình thường ở vị trí ưu tiên cao nhất")
    void shouldIncludeZeroStockVariants() throws Exception {
        Product product = createProduct("Áo Hết Hàng Hoàn Toàn", true);
        String prefix = "ZERO-" + System.nanoTime();

        ProductVariant vZero = createVariant(product, prefix + "-0", "S", "Tím", 0);
        ProductVariant vFive = createVariant(product, prefix + "-5", "M", "Tím", 5);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/products/variants/low-stock")
                .param("threshold", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.sku == '" + vZero.getSku() + "')].stock").value(0))
                .andExpect(jsonPath("$.content[?(@.sku == '" + vFive.getSku() + "')].stock").value(5));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("T9: Kiểm tra validation tham số âm hoặc size không hợp lệ trả về 400")
    void shouldRejectInvalidParameters() throws Exception {
        mockMvc.perform(get("/api/admin/products/variants/low-stock")
                .param("threshold", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Ngưỡng tồn kho không được là số âm")));

        mockMvc.perform(get("/api/admin/products/variants/low-stock")
                .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Số lượng bản ghi")));
    }
}
