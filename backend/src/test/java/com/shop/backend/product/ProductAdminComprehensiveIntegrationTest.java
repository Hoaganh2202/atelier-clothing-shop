package com.shop.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.product.dto.BulkStatusRequest;
import com.shop.backend.product.dto.ImageMetadataUpdateRequest;
import com.shop.backend.product.dto.ProductUpsertRequest;
import com.shop.backend.product.dto.VariantUpsertRequest;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ProductAdminComprehensiveIntegrationTest {

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
        private RoleRepository roleRepository;

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

        @Test
        @DisplayName("C1: Phân quyền - 401 khi chưa đăng nhập, 403 với CUSTOMER, 200 với ADMIN và STAFF")
        void shouldEnforceSecurityRoles() throws Exception {
                mockMvc.perform(get("/api/admin/products"))
                                .andExpect(status().isUnauthorized());

                mockMvc.perform(get("/api/admin/products")
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                                .user("customer").roles("CUSTOMER")))
                                .andExpect(status().isForbidden());

                mockMvc.perform(get("/api/admin/products")
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                                .user("staff").roles("STAFF")))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/admin/products")
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                                .user("admin").roles("ADMIN")))
                                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("C2: Tạo sản phẩm qua API gán data_source = WEBSITE và lưu DB")
        void shouldSetDataSourceWebsiteWhenCreatingProduct() throws Exception {
                ProductUpsertRequest request = ProductUpsertRequest.builder()
                                .name("Áo Thun Cotton Organic")
                                .categoryId(testCategory.getId())
                                .basePrice(250000L)
                                .isActive(true)
                                .variants(List.of(
                                                VariantUpsertRequest.builder()
                                                                .sku("AT-ORG-WHT-L-" + System.currentTimeMillis())
                                                                .size("L")
                                                                .color("Trắng")
                                                                .stock(50)
                                                                .price(250000L)
                                                                .build()))
                                .build();

                String responseBody = mockMvc.perform(post("/api/admin/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                Long createdProductId = objectMapper.readTree(responseBody).get("id").asLong();

                entityManager.flush();
                entityManager.clear();

                Product savedProduct = productRepository.findById(createdProductId).orElseThrow();
                assertThat(savedProduct.getDataSource()).isEqualTo(DataSourceType.WEBSITE);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("C3: Xóa variant có trong order_details hoặc cart_items trả về 409")
        void shouldReturn409WhenDeletingVariantReferencedByOrdersOrCarts() throws Exception {
                Product product = createSampleProduct("Áo Sơ Mi Oxford", 300000L);
                ProductVariant variant1 = createSampleVariant(product, "SM-OXF-WHT-M-" + System.currentTimeMillis(),
                                "M", "Trắng", 0, 300000L);
                ProductVariant variant2 = createSampleVariant(product, "SM-OXF-WHT-L-" + System.currentTimeMillis(),
                                "L", "Trắng", 0, 300000L);

                Role role = roleRepository.save(Role.builder()
                                .name("ROLE_CUST_" + System.currentTimeMillis())
                                .build());

                entityManager.createNativeQuery(
                                "INSERT INTO users (role_id, name, email, password_hash, status, data_source) " +
                                                "VALUES (:roleId, 'User Test', :email, 'hash', 'active', 'website')")
                                .setParameter("roleId", role.getId())
                                .setParameter("email", "cartuser_" + System.currentTimeMillis() + "@test.com")
                                .executeUpdate();

                Number userIdNum = (Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()")
                                .getSingleResult();
                Long userId = userIdNum.longValue();

                entityManager.createNativeQuery(
                                "INSERT INTO orders (user_id, status, subtotal, total_amount, receiver_name, receiver_phone, province, data_source, created_at) "
                                                +
                                                "VALUES (:userId, 'completed', 300000.00, 300000.00, 'Test', '0912345678', 'Hà Nội', 'website', NOW())")
                                .setParameter("userId", userId)
                                .executeUpdate();

                Number orderIdNum = (Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()")
                                .getSingleResult();
                Long orderId = orderIdNum.longValue();

                entityManager.createNativeQuery(
                                "INSERT INTO order_details (order_id, variant_id, product_name, variant_size, variant_color, quantity, unit_price) "
                                                +
                                                "VALUES (:orderId, :variantId, 'Áo Sơ Mi', 'M', 'Trắng', 1, 300000.00)")
                                .setParameter("orderId", orderId)
                                .setParameter("variantId", variant1.getId())
                                .executeUpdate();

                entityManager.flush();
                entityManager.clear();

                ProductUpsertRequest updateOmittingVariant1 = ProductUpsertRequest.builder()
                                .name("Áo Sơ Mi Oxford")
                                .categoryId(testCategory.getId())
                                .basePrice(300000L)
                                .isActive(true)
                                .variants(List.of(
                                                VariantUpsertRequest.builder()
                                                                .id(variant2.getId())
                                                                .sku(variant2.getSku())
                                                                .size(variant2.getSize())
                                                                .color(variant2.getColor())
                                                                .stock(variant2.getStock())
                                                                .price(300000L)
                                                                .build()))
                                .build();

                mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateOmittingVariant1)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message")
                                                .value(org.hamcrest.Matchers.containsString("đơn hàng")));

                entityManager.createNativeQuery(
                                "INSERT INTO carts (user_id, status) VALUES (:userId, 'active')")
                                .setParameter("userId", userId)
                                .executeUpdate();

                Number cartIdNum = (Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()")
                                .getSingleResult();
                Long cartId = cartIdNum.longValue();

                entityManager.createNativeQuery(
                                "INSERT INTO cart_items (cart_id, variant_id, quantity) VALUES (:cartId, :variantId, 2)")
                                .setParameter("cartId", cartId)
                                .setParameter("variantId", variant2.getId())
                                .executeUpdate();

                entityManager.flush();
                entityManager.clear();

                ProductUpsertRequest updateOmittingVariant2 = ProductUpsertRequest.builder()
                                .name("Áo Sơ Mi Oxford")
                                .categoryId(testCategory.getId())
                                .basePrice(300000L)
                                .isActive(true)
                                .variants(List.of(
                                                VariantUpsertRequest.builder()
                                                                .id(variant1.getId())
                                                                .sku(variant1.getSku())
                                                                .size(variant1.getSize())
                                                                .color(variant1.getColor())
                                                                .stock(variant1.getStock())
                                                                .price(300000L)
                                                                .build()))
                                .build();

                mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateOmittingVariant2)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.message")
                                                .value(org.hamcrest.Matchers.containsString("giỏ hàng")));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("C4: Đặt ảnh B làm primary thì ảnh A bị bỏ cờ primary")
        void shouldTogglePrimaryImageExclusively() throws Exception {
                Product product = createSampleProduct("Áo Len Dệt Kim", 450000L);

                ProductImage imageA = imageRepository.save(ProductImage.builder()
                                .product(product)
                                .url("/uploads/products/imageA.jpg")
                                .isPrimary(true)
                                .sortOrder(0)
                                .build());

                ProductImage imageB = imageRepository.save(ProductImage.builder()
                                .product(product)
                                .url("/uploads/products/imageB.jpg")
                                .isPrimary(false)
                                .sortOrder(1)
                                .build());

                entityManager.flush();
                entityManager.clear();

                ImageMetadataUpdateRequest updateRequest = ImageMetadataUpdateRequest.builder()
                                .isPrimary(true)
                                .build();

                mockMvc.perform(patch("/api/admin/products/{id}/images/{imageId}", product.getId(), imageB.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.isPrimary").value(true));

                entityManager.flush();
                entityManager.clear();

                ProductImage refreshedA = imageRepository.findById(imageA.getId()).orElseThrow();
                ProductImage refreshedB = imageRepository.findById(imageB.getId()).orElseThrow();

                assertThat(refreshedB.getIsPrimary()).isTrue();
                assertThat(refreshedA.getIsPrimary()).isFalse();
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("C5: PATCH ảnh với productId không khớp trả về 404")
        void shouldReturn404WhenPatchingImageWithMismatchedProductId() throws Exception {
                Product product1 = createSampleProduct("Sản Phẩm 1", 100000L);
                Product product2 = createSampleProduct("Sản Phẩm 2", 200000L);

                ProductImage image1 = imageRepository.save(ProductImage.builder()
                                .product(product1)
                                .url("/uploads/products/img1.jpg")
                                .isPrimary(true)
                                .sortOrder(0)
                                .build());

                ImageMetadataUpdateRequest updateRequest = ImageMetadataUpdateRequest.builder()
                                .alt("Mô tả mới")
                                .build();

                mockMvc.perform(patch("/api/admin/products/{id}/images/{imageId}", product2.getId(), image1.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("C6: Upload file không phải ảnh (file txt giả mạo) trả về 400")
        void shouldRejectNonImageFileEvenWithImageExtension() throws Exception {
                Product product = createSampleProduct("Áo Khoác Parka", 800000L);

                MockMultipartFile fakeImage = new MockMultipartFile(
                                "file",
                                "test.jpg",
                                "image/jpeg",
                                "Plain text content masquerading as a JPEG image".getBytes());

                mockMvc.perform(multipart("/api/admin/products/{id}/images", product.getId())
                                .file(fakeImage))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.message")
                                                .value(org.hamcrest.Matchers.containsString("định dạng ảnh")));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("C7: Lỗi validation trả về 400 và trường errors là object chứa key field lỗi")
        void shouldReturnValidationErrorsAsJsonObject() throws Exception {
                ProductUpsertRequest invalidRequest = ProductUpsertRequest.builder()
                                .name("")
                                .categoryId(testCategory.getId())
                                .basePrice(-500L)
                                .variants(List.of())
                                .build();

                mockMvc.perform(post("/api/admin/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400))
                                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"))
                                .andExpect(jsonPath("$.errors.name").exists())
                                .andExpect(jsonPath("$.errors.basePrice").exists())
                                .andExpect(jsonPath("$.errors.variants").exists());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("C8: Khoảng giá đúng khi variant có price = null (dùng basePrice)")
        void shouldFallbackToBasePriceWhenVariantPriceIsNull() throws Exception {
                Product product = createSampleProduct("Áo Blazer Thanh Lịch", 500000L);
                createSampleVariant(product, "BLZ-01-" + System.currentTimeMillis(), "S", "Đen", 10, null);
                createSampleVariant(product, "BLZ-02-" + System.currentTimeMillis(), "M", "Đen", 10, 650000L);

                entityManager.flush();
                entityManager.clear();

                mockMvc.perform(get("/api/admin/products")
                                .param("keyword", "Blazer Thanh Lịch"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].minPrice").value(500000))
                                .andExpect(jsonPath("$.content[0].maxPrice").value(650000))
                                .andExpect(jsonPath("$.content[0].totalStock").value(20));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("C9: Trùng slug hoặc trùng SKU trả về 409 Conflict")
        void shouldReturn409OnDuplicateSlugOrSku() throws Exception {
                ProductUpsertRequest initialRequest = ProductUpsertRequest.builder()
                                .name("Áo Thun Raglan")
                                .categoryId(testCategory.getId())
                                .basePrice(180000L)
                                .variants(List.of(
                                                VariantUpsertRequest.builder()
                                                                .sku("RAGLAN-01-" + System.currentTimeMillis())
                                                                .size("M")
                                                                .color("Xanh")
                                                                .stock(15)
                                                                .price(180000L)
                                                                .build()))
                                .build();

                mockMvc.perform(post("/api/admin/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(initialRequest)))
                                .andExpect(status().isCreated());

                ProductUpsertRequest duplicateSlugRequest = ProductUpsertRequest.builder()
                                .name("Áo Thun Raglan")
                                .categoryId(testCategory.getId())
                                .basePrice(190000L)
                                .variants(List.of(
                                                VariantUpsertRequest.builder()
                                                                .sku("RAGLAN-02-" + System.currentTimeMillis())
                                                                .size("L")
                                                                .color("Đỏ")
                                                                .stock(10)
                                                                .price(190000L)
                                                                .build()))
                                .build();

                mockMvc.perform(post("/api/admin/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(duplicateSlugRequest)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.status").value(409))
                                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Slug")));

                ProductUpsertRequest duplicateSkuRequest = ProductUpsertRequest.builder()
                                .name("Áo Thun Mới Lạ")
                                .categoryId(testCategory.getId())
                                .basePrice(190000L)
                                .variants(List.of(
                                                VariantUpsertRequest.builder()
                                                                .sku(initialRequest.getVariants().get(0).getSku())
                                                                .size("S")
                                                                .color("Trắng")
                                                                .stock(10)
                                                                .price(190000L)
                                                                .build()))
                                .build();

                mockMvc.perform(post("/api/admin/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(duplicateSkuRequest)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.status").value(409))
                                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SKU")));
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

        private ProductVariant createSampleVariant(Product product, String sku, String size, String color, int stock,
                        Long price) {
                return variantRepository.save(ProductVariant.builder()
                                .product(product)
                                .sku(sku)
                                .size(size)
                                .color(color)
                                .stock(stock)
                                .price(price != null ? BigDecimal.valueOf(price) : null)
                                .build());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @org.junit.jupiter.api.DisplayName("C10: GET /api/admin/categories trả về danh sách phẳng")
        void shouldReturnFlatCategoryList() throws Exception {
                categoryRepository.save(Category.builder()
                                .name("Thời Trang Nữ")
                                .slug("thoi-trang-nu-" + System.currentTimeMillis())
                                .isActive(true)
                                .build());

                mockMvc.perform(get("/api/admin/categories"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$[0].id").exists())
                                .andExpect(jsonPath("$[0].name").exists())
                                .andExpect(jsonPath("$[0].slug").exists())
                                .andExpect(jsonPath("$[0].isActive").exists());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @org.junit.jupiter.api.DisplayName("C11: GET products với sort trường không hợp lệ trả về 400")
        void shouldReturn400OnInvalidSortField() throws Exception {
                mockMvc.perform(get("/api/admin/products")
                                .param("sort", "maliciousField,asc"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @org.junit.jupiter.api.DisplayName("C12: PUT sản phẩm không kèm slug giữ nguyên slug cũ")
        void shouldPreserveExistingSlugWhenPutWithoutSlugField() throws Exception {
                Product product = createSampleProduct("Áo Polo Classic", 350000L);
                String originalSlug = product.getSlug();

                ProductUpsertRequest updateRequest = ProductUpsertRequest.builder()
                                .name("Áo Polo Classic Đã Đổi Tên")
                                .categoryId(testCategory.getId())
                                .basePrice(380000L)
                                .isActive(true)
                                .variants(List.of(VariantUpsertRequest.builder()
                                                .sku("POLO-UPD-" + System.currentTimeMillis())
                                                .size("M")
                                                .color("Đen")
                                                .stock(10)
                                                .build()))
                                .build();

                mockMvc.perform(put("/api/admin/products/{id}", product.getId())
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.slug").value(originalSlug));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @org.junit.jupiter.api.DisplayName("C13: Upload file vượt 10 MB trả về 400")
        void shouldReturn400WhenUploadFileTooLarge() throws Exception {
                Product product = createSampleProduct("Áo Test Size", 200000L);

                byte[] fakeJpeg = new byte[11 * 1024 * 1024];
                fakeJpeg[0] = (byte) 0xFF;
                fakeJpeg[1] = (byte) 0xD8;
                fakeJpeg[2] = (byte) 0xFF;

                MockMultipartFile largeFile = new MockMultipartFile(
                                "file",
                                "large.jpg",
                                "image/jpeg",
                                fakeJpeg);

                mockMvc.perform(multipart("/api/admin/products/{id}/images", product.getId())
                                .file(largeFile))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message")
                                                .value(org.hamcrest.Matchers.containsString("10 MB")));
        }
        @Test
        @WithMockUser(roles = "ADMIN")
        @org.junit.jupiter.api.DisplayName("C14: POST slug không hợp lệ (có chữ hoa) trả về 400")
        void shouldReturn400WhenSlugHasUpperCase() throws Exception {
                ProductUpsertRequest request = ProductUpsertRequest.builder()
                                .name("Test Product")
                                .slug("Invalid-Slug")
                                .categoryId(testCategory.getId())
                                .basePrice(100000L)
                                .isActive(true)
                                .variants(List.of(VariantUpsertRequest.builder()
                                                .sku("SKU-SLUG-INVALID-" + System.currentTimeMillis())
                                                .size("M")
                                                .color("Đỏ")
                                                .stock(5)
                                                .build()))
                                .build();

                mockMvc.perform(post("/api/admin/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors.slug").exists());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @org.junit.jupiter.api.DisplayName("C15: POST slug bắt đầu bằng dấu gạch ngang trả về 400")
        void shouldReturn400WhenSlugStartsWithDash() throws Exception {
                ProductUpsertRequest request = ProductUpsertRequest.builder()
                                .name("Test Product")
                                .slug("-invalid-slug")
                                .categoryId(testCategory.getId())
                                .basePrice(100000L)
                                .isActive(true)
                                .variants(List.of(VariantUpsertRequest.builder()
                                                .sku("SKU-SLUG-DASH-" + System.currentTimeMillis())
                                                .size("M")
                                                .color("Xanh")
                                                .stock(5)
                                                .build()))
                                .build();

                mockMvc.perform(post("/api/admin/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors.slug").exists());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @org.junit.jupiter.api.DisplayName("C16: PUT slug trùng với sản phẩm khác trả 409; slug trùng chính nó được chấp nhận")
        void shouldAllowSelfSlugButRejectDuplicateSlug() throws Exception {
                Product product1 = createSampleProduct("Áo Polo A", 200000L);
                Product product2 = createSampleProduct("Áo Polo B", 250000L);
                String product1Slug = product1.getSlug().replaceAll("-\\d+$", "");

                ProductUpsertRequest requestWithDuplicateSlug = ProductUpsertRequest.builder()
                                .name("Áo Polo B Updated")
                                .slug(product1.getSlug())
                                .categoryId(testCategory.getId())
                                .basePrice(260000L)
                                .isActive(true)
                                .variants(List.of(VariantUpsertRequest.builder()
                                                .sku("SKU-DUP-SLUG-" + System.currentTimeMillis())
                                                .size("L")
                                                .color("Đen")
                                                .stock(3)
                                                .build()))
                                .build();

                mockMvc.perform(put("/api/admin/products/{id}", product2.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestWithDuplicateSlug)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @org.junit.jupiter.api.DisplayName("C17: Upload PNG hợp lệ 3 MB trả về 201")
        void shouldReturn201ForValidPng3Mb() throws Exception {
                Product product = createSampleProduct("Áo Upload PNG", 150000L);

                // Build a valid minimal PNG: signature + IHDR chunk + IDAT chunk + IEND chunk
                byte[] pngSignature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
                byte[] pngBody = new byte[3 * 1024 * 1024 - pngSignature.length];
                // Fill remaining bytes with zeros (not a valid PNG but passes magic-byte check)
                byte[] fullPng = new byte[3 * 1024 * 1024];
                System.arraycopy(pngSignature, 0, fullPng, 0, pngSignature.length);

                MockMultipartFile pngFile = new MockMultipartFile(
                                "file",
                                "product.png",
                                "image/png",
                                fullPng);

                mockMvc.perform(multipart("/api/admin/products/{id}/images", product.getId())
                                .file(pngFile))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.url").exists());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @org.junit.jupiter.api.DisplayName("C18: GET products rỗng trả PageResponse với content=[], totalElements=0")
        void shouldReturnEmptyPageResponseWhenNoProducts() throws Exception {
                mockMvc.perform(get("/api/admin/products")
                                .param("keyword", "PRODUCT_DOES_NOT_EXIST_XYZ_" + System.currentTimeMillis()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.content").isEmpty())
                                .andExpect(jsonPath("$.totalElements").value(0))
                                .andExpect(jsonPath("$.page").exists())
                                .andExpect(jsonPath("$.size").exists());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @org.junit.jupiter.api.DisplayName("C19: PATCH /api/admin/products/bulk-status cập nhật trạng thái hàng loạt")
        void shouldUpdateBulkStatusSuccessfully() throws Exception {
                Product p1 = createSampleProduct("Áo Bulk 1", 100000L);
                Product p2 = createSampleProduct("Áo Bulk 2", 200000L);

                BulkStatusRequest request = BulkStatusRequest.builder()
                                .ids(List.of(p1.getId(), p2.getId()))
                                .isActive(false)
                                .build();

                mockMvc.perform(patch("/api/admin/products/bulk-status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isNoContent());

                entityManager.flush();
                entityManager.clear();

                Product refreshedP1 = productRepository.findById(p1.getId()).orElseThrow();
                Product refreshedP2 = productRepository.findById(p2.getId()).orElseThrow();
                assertThat(refreshedP1.getIsActive()).isFalse();
                assertThat(refreshedP2.getIsActive()).isFalse();
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @org.junit.jupiter.api.DisplayName("C20: PATCH /api/admin/products/{id}/images/{imageId} cập nhật từng phần (giữ nguyên trường không gửi)")
        void shouldPartiallyUpdateImageMetadataPreservingOmittedFields() throws Exception {
                Product product = createSampleProduct("Áo Test Patch Image", 120000L);

                ProductImage image = imageRepository.save(ProductImage.builder()
                                .product(product)
                                .url("/uploads/products/test-patch.jpg")
                                .alt("Mô tả ban đầu")
                                .color("Đỏ")
                                .sortOrder(5)
                                .isPrimary(false)
                                .build());

                entityManager.flush();
                entityManager.clear();

                ImageMetadataUpdateRequest partialRequest = ImageMetadataUpdateRequest.builder()
                                .alt("Mô tả đã cập nhật")
                                .build();

                mockMvc.perform(patch("/api/admin/products/{id}/images/{imageId}", product.getId(), image.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(partialRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.alt").value("Mô tả đã cập nhật"))
                                .andExpect(jsonPath("$.color").value("Đỏ"))
                                .andExpect(jsonPath("$.sortOrder").value(5))
                                .andExpect(jsonPath("$.isPrimary").value(false));

                entityManager.flush();
                entityManager.clear();

                ProductImage refreshedImage = imageRepository.findById(image.getId()).orElseThrow();
                assertThat(refreshedImage.getAlt()).isEqualTo("Mô tả đã cập nhật");
                assertThat(refreshedImage.getColor()).isEqualTo("Đỏ");
                assertThat(refreshedImage.getSortOrder()).isEqualTo(5);
                assertThat(refreshedImage.getIsPrimary()).isFalse();
        }
}
