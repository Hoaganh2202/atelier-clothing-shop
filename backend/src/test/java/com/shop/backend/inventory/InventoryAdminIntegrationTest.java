package com.shop.backend.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.inventory.dto.InventoryStockUpdateRequest;
import com.shop.backend.product.Category;
import com.shop.backend.product.CategoryRepository;
import com.shop.backend.product.Product;
import com.shop.backend.product.ProductImage;
import com.shop.backend.product.ProductImageRepository;
import com.shop.backend.product.ProductRepository;
import com.shop.backend.product.ProductVariant;
import com.shop.backend.security.JwtService;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class InventoryAdminIntegrationTest {

    private static final String TEST_PREFIX = "inv-test-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private InventoryVariantRepository inventoryVariantRepository;

    private String adminToken;
    private String staffToken;
    private String customerToken;

    private Category testCategoryA;
    private Category testCategoryB;
    private Product testProductActive;
    private Product testProductInactive;

    private ProductVariant variantOutOfStock;
    private ProductVariant variantLowStock;
    private ProductVariant variantInStock;
    private ProductVariant variantCustomPrice;

    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName("admin")
                .orElseThrow(() -> new IllegalStateException("Role 'admin' not found"));
        Role staffRole = roleRepository.findByName("staff")
                .orElseThrow(() -> new IllegalStateException("Role 'staff' not found"));
        Role customerRole = roleRepository.findByName("customer")
                .orElseThrow(() -> new IllegalStateException("Role 'customer' not found"));

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        User admin = userRepository.save(User.builder()
                .name("Inv Admin " + uniqueSuffix)
                .email("inv_admin_" + uniqueSuffix + "@shop.com")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .role(adminRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());
        createdUserIds.add(admin.getId());
        adminToken = "Bearer " + jwtService.generateToken(admin);

        User staff = userRepository.save(User.builder()
                .name("Inv Staff " + uniqueSuffix)
                .email("inv_staff_" + uniqueSuffix + "@shop.com")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .role(staffRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());
        createdUserIds.add(staff.getId());
        staffToken = "Bearer " + jwtService.generateToken(staff);

        User customer = userRepository.save(User.builder()
                .name("Inv Customer " + uniqueSuffix)
                .email("inv_cust_" + uniqueSuffix + "@shop.com")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .role(customerRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());
        createdUserIds.add(customer.getId());
        customerToken = "Bearer " + jwtService.generateToken(customer);

        testCategoryA = categoryRepository.save(Category.builder()
                .name(TEST_PREFIX + "Cat A " + uniqueSuffix)
                .slug(TEST_PREFIX + "cat-a-" + uniqueSuffix)
                .build());

        testCategoryB = categoryRepository.save(Category.builder()
                .name(TEST_PREFIX + "Cat B " + uniqueSuffix)
                .slug(TEST_PREFIX + "cat-b-" + uniqueSuffix)
                .build());

        testProductActive = productRepository.save(Product.builder()
                .name(TEST_PREFIX + "Áo Thun Nam " + uniqueSuffix)
                .slug(TEST_PREFIX + "ao-thun-nam-" + uniqueSuffix)
                .category(testCategoryA)
                .basePrice(BigDecimal.valueOf(200000))
                .isActive(true)
                .build());

        testProductInactive = productRepository.save(Product.builder()
                .name(TEST_PREFIX + "Quần Jean Nữ " + uniqueSuffix)
                .slug(TEST_PREFIX + "quan-jean-nu-" + uniqueSuffix)
                .category(testCategoryB)
                .basePrice(BigDecimal.valueOf(350000))
                .isActive(false)
                .build());

        productImageRepository.save(ProductImage.builder()
                .product(testProductActive)
                .url("/uploads/products/" + TEST_PREFIX + "primary.jpg")
                .isPrimary(true)
                .sortOrder(0)
                .build());

        // 1. Variant Hết hàng (stock = 0)
        variantOutOfStock = inventoryVariantRepository.save(ProductVariant.builder()
                .product(testProductActive)
                .sku(TEST_PREFIX + "SKU-OUT-" + uniqueSuffix)
                .size("S")
                .color("Đen")
                .stock(0)
                .price(null) // Dùng basePrice = 200000
                .build());

        // 2. Variant Sắp hết hàng (0 < stock < 10, ví dụ 5)
        variantLowStock = inventoryVariantRepository.save(ProductVariant.builder()
                .product(testProductActive)
                .sku(TEST_PREFIX + "SKU-LOW-" + uniqueSuffix)
                .size("M")
                .color("Trắng")
                .stock(5)
                .price(null) // Dùng basePrice = 200000
                .build());

        // 3. Variant Còn nhiều hàng (stock >= 10, ví dụ 50)
        variantInStock = inventoryVariantRepository.save(ProductVariant.builder()
                .product(testProductActive)
                .sku(TEST_PREFIX + "SKU-IN-" + uniqueSuffix)
                .size("L")
                .color("Đen")
                .stock(50)
                .price(null) // Dùng basePrice = 200000
                .build());

        // 4. Variant của sản phẩm Inactive, có giá riêng
        variantCustomPrice = inventoryVariantRepository.save(ProductVariant.builder()
                .product(testProductInactive)
                .sku(TEST_PREFIX + "SKU-INACTIVE-" + uniqueSuffix)
                .size("29")
                .color("Xanh")
                .stock(15)
                .price(BigDecimal.valueOf(380000)) // Giá riêng 380.000
                .build());
    }

    @AfterEach
    void tearDown() {
        for (Long userId : createdUserIds) {
            userRepository.deleteById(userId);
        }
        createdUserIds.clear();

        if (variantOutOfStock != null && variantOutOfStock.getId() != null) {
            inventoryVariantRepository.deleteById(variantOutOfStock.getId());
        }
        if (variantLowStock != null && variantLowStock.getId() != null) {
            inventoryVariantRepository.deleteById(variantLowStock.getId());
        }
        if (variantInStock != null && variantInStock.getId() != null) {
            inventoryVariantRepository.deleteById(variantInStock.getId());
        }
        if (variantCustomPrice != null && variantCustomPrice.getId() != null) {
            inventoryVariantRepository.deleteById(variantCustomPrice.getId());
        }

        inventoryVariantRepository.findAll().stream()
                .filter(v -> v.getSku() != null && v.getSku().startsWith(TEST_PREFIX))
                .forEach(inventoryVariantRepository::delete);

        productImageRepository.findAll().stream()
                .filter(pi -> pi.getUrl() != null && pi.getUrl().contains(TEST_PREFIX))
                .forEach(productImageRepository::delete);

        if (testProductActive != null && testProductActive.getId() != null) {
            productRepository.deleteById(testProductActive.getId());
        }
        if (testProductInactive != null && testProductInactive.getId() != null) {
            productRepository.deleteById(testProductInactive.getId());
        }
        productRepository.findAll().stream()
                .filter(p -> p.getName() != null && p.getName().startsWith(TEST_PREFIX))
                .forEach(productRepository::delete);

        if (testCategoryA != null && testCategoryA.getId() != null) {
            categoryRepository.deleteById(testCategoryA.getId());
        }
        if (testCategoryB != null && testCategoryB.getId() != null) {
            categoryRepository.deleteById(testCategoryB.getId());
        }
        categoryRepository.findAll().stream()
                .filter(c -> c.getName() != null && c.getName().startsWith(TEST_PREFIX))
                .forEach(categoryRepository::delete);
    }

    @Test
    @DisplayName("GET /api/admin/inventory/variants: Lấy danh sách thành công có phân trang")
    void shouldGetVariantsListSuccessfully() throws Exception {
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    @DisplayName("GET /api/admin/inventory/variants: Lọc theo keyword tên sản phẩm và SKU")
    void shouldFilterByKeyword() throws Exception {
        // Lọc theo tên sản phẩm
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("keyword", "Áo Thun Nam"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.sku == '" + variantOutOfStock.getSku() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.sku == '" + variantCustomPrice.getSku() + "')]").doesNotExist());

        // Lọc theo SKU
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("keyword", variantLowStock.getSku()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.sku == '" + variantLowStock.getSku() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.sku == '" + variantInStock.getSku() + "')]").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/admin/inventory/variants: Lọc theo categoryId, size, color")
    void shouldFilterByCategorySizeAndColor() throws Exception {
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("categoryId", testCategoryA.getId().toString())
                        .param("size", "M")
                        .param("color", "Trắng"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.sku == '" + variantLowStock.getSku() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.sku == '" + variantOutOfStock.getSku() + "')]").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/admin/inventory/variants: Lọc theo trạng thái tồn kho OUT_OF_STOCK, LOW_STOCK, IN_STOCK")
    void shouldFilterByStockStatus() throws Exception {
        // Hết hàng: stock = 0
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("stockStatus", "OUT_OF_STOCK")
                        .param("keyword", TEST_PREFIX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.sku == '" + variantOutOfStock.getSku() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.sku == '" + variantLowStock.getSku() + "')]").doesNotExist());

        // Sắp hết hàng: 0 < stock < 10
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("stockStatus", "LOW_STOCK")
                        .param("keyword", TEST_PREFIX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.sku == '" + variantLowStock.getSku() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.sku == '" + variantInStock.getSku() + "')]").doesNotExist());

        // Còn hàng: stock >= 10
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("stockStatus", "IN_STOCK")
                        .param("keyword", TEST_PREFIX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.sku == '" + variantInStock.getSku() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.sku == '" + variantOutOfStock.getSku() + "')]").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/admin/inventory/variants: Lọc theo trạng thái sản phẩm productActive")
    void shouldFilterByProductActive() throws Exception {
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("productActive", "false")
                        .param("keyword", TEST_PREFIX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.sku == '" + variantCustomPrice.getSku() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.sku == '" + variantInStock.getSku() + "')]").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/admin/inventory/variants: Kiểm tra kế thừa giá base_price và giá riêng")
    void shouldInheritBasePriceWhenVariantPriceIsNull() throws Exception {
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("keyword", variantOutOfStock.getSku()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].price").value(200000));

        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("keyword", variantCustomPrice.getSku()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].price").value(380000));
    }

    @Test
    @DisplayName("GET /api/admin/inventory/variants: Sắp xếp whitelist và báo lỗi 400 khi sort sai")
    void shouldSortByWhitelistedPropertiesOrThrowBadRequest() throws Exception {
        // Hợp lệ: stock,desc
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("sort", "stock,desc"))
                .andExpect(status().isOk());

        // Hợp lệ: sku,asc
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("sort", "sku,asc"))
                .andExpect(status().isOk());

        // Hợp lệ: productName,asc
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("sort", "productName,asc"))
                .andExpect(status().isOk());

        // Hợp lệ: id,desc
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("sort", "id,desc"))
                .andExpect(status().isOk());

        // Sai property -> price không có trong whitelist -> 400
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("sort", "price,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tiêu chí sắp xếp không hợp lệ: price"));

        // Sai property -> createdAt không có trong whitelist (product_variants không có cột createdAt) -> 400
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("sort", "createdAt,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tiêu chí sắp xếp không hợp lệ: createdAt"));

        // Sai direction -> 400
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("sort", "stock,upward"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chiều sắp xếp không hợp lệ: upward"));
    }

    @Test
    @DisplayName("GET /api/admin/inventory/variants: stockStatus không hợp lệ trả về 400")
    void shouldReturnBadRequestWhenStockStatusIsInvalid() throws Exception {
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", adminToken)
                        .param("stockStatus", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Trạng thái tồn kho không hợp lệ: INVALID_STATUS"));
    }

    @Test
    @DisplayName("GET /api/admin/inventory/summary: Thống kê tổng quan đúng số liệu")
    void shouldGetSummarySuccessfully() throws Exception {
        mockMvc.perform(get("/api/admin/inventory/summary")
                        .header("Authorization", adminToken)
                        .param("threshold", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVariants").isNumber())
                .andExpect(jsonPath("$.totalStock").isNumber())
                .andExpect(jsonPath("$.outOfStockCount").isNumber())
                .andExpect(jsonPath("$.lowStockCount").isNumber())
                .andExpect(jsonPath("$.inStockCount").isNumber());
    }

    @Test
    @DisplayName("GET /api/admin/inventory/summary: threshold ngoài khoảng 1..1000 trả về 400")
    void shouldReturnBadRequestWhenSummaryThresholdOutOfRange() throws Exception {
        mockMvc.perform(get("/api/admin/inventory/summary")
                        .header("Authorization", adminToken)
                        .param("threshold", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ngưỡng tồn kho phải nằm trong khoảng từ 1 đến 1000"));

        mockMvc.perform(get("/api/admin/inventory/summary")
                        .header("Authorization", adminToken)
                        .param("threshold", "1001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ngưỡng tồn kho phải nằm trong khoảng từ 1 đến 1000"));
    }

    @Test
    @DisplayName("PATCH /api/admin/inventory/variants/{id}/stock: Cập nhật thành công khi expectedStock khớp")
    void shouldUpdateStockSuccessfullyWhenExpectedStockMatches() throws Exception {
        InventoryStockUpdateRequest request = InventoryStockUpdateRequest.builder()
                .expectedStock(50)
                .newStock(80)
                .build();

        mockMvc.perform(patch("/api/admin/inventory/variants/" + variantInStock.getId() + "/stock")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(variantInStock.getId()))
                .andExpect(jsonPath("$.stock").value(80))
                .andExpect(jsonPath("$.stockStatus").value("IN_STOCK"));

        ProductVariant updated = inventoryVariantRepository.findById(variantInStock.getId()).orElseThrow();
        assertThat(updated.getStock()).isEqualTo(80);
    }

    @Test
    @DisplayName("PATCH /api/admin/inventory/variants/{id}/stock: newStock = expectedStock thành công 200")
    void shouldSucceedWhenNewStockEqualsExpectedStock() throws Exception {
        InventoryStockUpdateRequest request = InventoryStockUpdateRequest.builder()
                .expectedStock(50)
                .newStock(50)
                .build();

        mockMvc.perform(patch("/api/admin/inventory/variants/" + variantInStock.getId() + "/stock")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(50));
    }

    @Test
    @DisplayName("PATCH /api/admin/inventory/variants/{id}/stock: 409 Conflict khi expectedStock không khớp DB")
    void shouldReturnConflictWhenExpectedStockDoesNotMatch() throws Exception {
        InventoryStockUpdateRequest request = InventoryStockUpdateRequest.builder()
                .expectedStock(999) // Sai so với giá trị 50 trong DB
                .newStock(80)
                .build();

        mockMvc.perform(patch("/api/admin/inventory/variants/" + variantInStock.getId() + "/stock")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Tồn kho đã bị thay đổi bởi thao tác khác. Hãy tải lại dữ liệu rồi thử lại."));

        // Xác nhận tồn kho trong DB không đổi
        ProductVariant notUpdated = inventoryVariantRepository.findById(variantInStock.getId()).orElseThrow();
        assertThat(notUpdated.getStock()).isEqualTo(50);
    }

    @Test
    @DisplayName("PATCH /api/admin/inventory/variants/{id}/stock: 404 khi ID biến thể không tồn tại")
    void shouldReturnNotFoundWhenVariantIdDoesNotExist() throws Exception {
        InventoryStockUpdateRequest request = InventoryStockUpdateRequest.builder()
                .expectedStock(10)
                .newStock(20)
                .build();

        mockMvc.perform(patch("/api/admin/inventory/variants/999999999/stock")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PATCH /api/admin/inventory/variants/{id}/stock: 400 khi số lượng âm hoặc vượt Max")
    void shouldReturnBadRequestWhenStockIsInvalid() throws Exception {
        // Số lượng âm
        InventoryStockUpdateRequest negativeReq = InventoryStockUpdateRequest.builder()
                .expectedStock(50)
                .newStock(-5)
                .build();

        mockMvc.perform(patch("/api/admin/inventory/variants/" + variantInStock.getId() + "/stock")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeReq)))
                .andExpect(status().isBadRequest());

        // Số lượng vượt quá 1.000.000
        InventoryStockUpdateRequest overflowReq = InventoryStockUpdateRequest.builder()
                .expectedStock(50)
                .newStock(1000001)
                .build();

        mockMvc.perform(patch("/api/admin/inventory/variants/" + variantInStock.getId() + "/stock")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overflowReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Phân quyền: 401 khi không có token, 403 với role CUSTOMER, 200 với ADMIN và STAFF")
    void shouldEnforceSecurityAuthorization() throws Exception {
        // 1. Không token -> 401
        mockMvc.perform(get("/api/admin/inventory/variants"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/inventory/summary"))
                .andExpect(status().isUnauthorized());

        // 2. Customer token -> 403
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/inventory/summary")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());

        // 3. Staff token -> 200
        mockMvc.perform(get("/api/admin/inventory/variants")
                        .header("Authorization", staffToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/inventory/summary")
                        .header("Authorization", staffToken))
                .andExpect(status().isOk());
    }
}
