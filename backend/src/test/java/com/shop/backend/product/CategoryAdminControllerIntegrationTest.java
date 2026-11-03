package com.shop.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.product.dto.CategoryStatusRequest;
import com.shop.backend.product.dto.CategoryUpsertRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class CategoryAdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Phân quyền: 401 khi chưa đăng nhập, 403 với CUSTOMER, 200/201/204 với ADMIN/STAFF")
    void shouldEnforceRoleSecurityOnAllEndpoints() throws Exception {
        Category category = categoryRepository.save(Category.builder()
                .name("Security Test Category " + System.currentTimeMillis())
                .slug("sec-test-" + System.currentTimeMillis())
                .isActive(true)
                .build());

        CategoryUpsertRequest upsertReq = CategoryUpsertRequest.builder()
                .name("New Category")
                .build();

        CategoryStatusRequest statusReq = CategoryStatusRequest.builder()
                .isActive(false)
                .build();

        // 401 Unauthorized
        mockMvc.perform(get("/api/admin/categories")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/categories/" + category.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upsertReq)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/categories/" + category.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upsertReq)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/admin/categories/" + category.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/categories/" + category.getId())).andExpect(status().isUnauthorized());

        // 403 Forbidden with CUSTOMER
        mockMvc.perform(get("/api/admin/categories")
                        .with(SecurityMockMvcRequestPostProcessors.user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/categories/" + category.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/categories")
                        .with(SecurityMockMvcRequestPostProcessors.user("customer").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upsertReq)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/categories/" + category.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user("customer").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upsertReq)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/categories/" + category.getId() + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.user("customer").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/categories/" + category.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());

        // 200 with STAFF
        mockMvc.perform(get("/api/admin/categories")
                        .with(SecurityMockMvcRequestPostProcessors.user("staff").roles("STAFF")))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/categories trả về toàn bộ danh mục kèm số lượng sản phẩm và quan hệ cha con")
    void shouldReturnCategoriesWithParentMappingAndProductCount() throws Exception {
        long suffix = System.currentTimeMillis();
        Category parent = categoryRepository.save(Category.builder()
                .name("Áo Nam " + suffix)
                .slug("ao-nam-" + suffix)
                .isActive(true)
                .build());

        Category child = categoryRepository.save(Category.builder()
                .parent(parent)
                .name("Áo Sơ Mi " + suffix)
                .slug("ao-so-mi-" + suffix)
                .isActive(false)
                .build());

        productRepository.save(Product.builder()
                .category(child)
                .name("Sản phẩm đếm " + suffix)
                .slug("sp-dem-" + suffix)
                .basePrice(BigDecimal.valueOf(100000))
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + parent.getId() + ")].name").value("Áo Nam " + suffix))
                .andExpect(jsonPath("$[?(@.id == " + parent.getId() + ")].parentId").value(contains(nullValue())))
                .andExpect(jsonPath("$[?(@.id == " + parent.getId() + ")].productCount").value(0))
                .andExpect(jsonPath("$[?(@.id == " + child.getId() + ")].name").value("Áo Sơ Mi " + suffix))
                .andExpect(jsonPath("$[?(@.id == " + child.getId() + ")].parentId").value(parent.getId().intValue()))
                .andExpect(jsonPath("$[?(@.id == " + child.getId() + ")].productCount").value(1))
                .andExpect(jsonPath("$[?(@.id == " + child.getId() + ")].isActive").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/categories/{id} trả về chi tiết danh mục, 404 khi không tìm thấy, 400 khi sai kiểu")
    void shouldGetCategoryById() throws Exception {
        long suffix = System.currentTimeMillis();
        Category category = categoryRepository.save(Category.builder()
                .name("Quần Jeans " + suffix)
                .slug("quan-jeans-" + suffix)
                .isActive(true)
                .build());

        mockMvc.perform(get("/api/admin/categories/" + category.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(category.getId()))
                .andExpect(jsonPath("$.name").value("Quần Jeans " + suffix))
                .andExpect(jsonPath("$.slug").value("quan-jeans-" + suffix))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.productCount").value(0));

        mockMvc.perform(get("/api/admin/categories/9999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/api/admin/categories/invalid-id"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/admin/categories tạo danh mục mới: tự sinh slug từ name, hoặc nhận slug thủ công")
    void shouldCreateCategorySuccessfully() throws Exception {
        long suffix = System.currentTimeMillis();

        // 1. Root category with auto-generated slug
        CategoryUpsertRequest autoSlugReq = CategoryUpsertRequest.builder()
                .name("Áo Thun Nữ " + suffix)
                .isActive(true)
                .build();

        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(autoSlugReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Áo Thun Nữ " + suffix))
                .andExpect(jsonPath("$.slug").value("ao-thun-nu-" + suffix))
                .andExpect(jsonPath("$.parentId").isEmpty())
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.productCount").value(0));

        // 2. Child category with explicit slug
        Category parent = categoryRepository.save(Category.builder()
                .name("Giày Dép " + suffix)
                .slug("giay-dep-" + suffix)
                .isActive(true)
                .build());

        CategoryUpsertRequest childReq = CategoryUpsertRequest.builder()
                .parentId(parent.getId())
                .name("Giày Sneaker " + suffix)
                .slug("custom-sneaker-" + suffix)
                .isActive(false)
                .build();

        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(childReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Giày Sneaker " + suffix))
                .andExpect(jsonPath("$.slug").value("custom-sneaker-" + suffix))
                .andExpect(jsonPath("$.parentId").value(parent.getId()))
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/admin/categories kiểm tra validation: tên trống, slug sai format, trùng slug, danh mục cha không tồn tại hoặc vượt 2 cấp")
    void shouldRejectInvalidCreateCategoryRequests() throws Exception {
        long suffix = System.currentTimeMillis();

        // 1. Tên để trống -> 400
        CategoryUpsertRequest emptyNameReq = CategoryUpsertRequest.builder()
                .name("")
                .build();
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyNameReq)))
                .andExpect(status().isBadRequest());

        // 2. Slug sai format (chứa chữ hoa, dấu cách hoặc ký tự đặc biệt) -> 400
        CategoryUpsertRequest invalidSlugReq = CategoryUpsertRequest.builder()
                .name("Áo Hợp Lệ " + suffix)
                .slug("ao-hop-le!")
                .build();
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidSlugReq)))
                .andExpect(status().isBadRequest());

        // 3. Trùng slug (case-insensitive) -> 409
        Category existing = categoryRepository.save(Category.builder()
                .name("Đầm Xòe " + suffix)
                .slug("dam-xoe-" + suffix)
                .isActive(true)
                .build());

        CategoryUpsertRequest dupSlugReq = CategoryUpsertRequest.builder()
                .name("Đầm Xòe Mới " + suffix)
                .slug("dam-xoe-" + suffix)
                .build();
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupSlugReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("đã tồn tại")));

        // 4. Danh mục cha không tồn tại -> 404
        CategoryUpsertRequest nonExistentParentReq = CategoryUpsertRequest.builder()
                .parentId(8888888L)
                .name("Con Mồ Côi " + suffix)
                .build();
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonExistentParentReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("không tồn tại")));

        // 5. Vượt quá 2 cấp (danh mục cha đã có cha) -> 400
        Category root = categoryRepository.save(Category.builder()
                .name("Cấp 1 " + suffix)
                .slug("cap-1-" + suffix)
                .isActive(true)
                .build());
        Category level2 = categoryRepository.save(Category.builder()
                .parent(root)
                .name("Cấp 2 " + suffix)
                .slug("cap-2-" + suffix)
                .isActive(true)
                .build());

        CategoryUpsertRequest level3Req = CategoryUpsertRequest.builder()
                .parentId(level2.getId())
                .name("Cấp 3 " + suffix)
                .build();
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(level3Req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("tối đa 2 cấp")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /api/admin/categories/{id} cập nhật tên, slug, parentId, giữ nguyên slug của chính nó không bị 409")
    void shouldUpdateCategorySuccessfully() throws Exception {
        long suffix = System.currentTimeMillis();
        Category parent1 = categoryRepository.save(Category.builder()
                .name("Parent 1 " + suffix)
                .slug("parent-1-" + suffix)
                .isActive(true)
                .build());
        Category parent2 = categoryRepository.save(Category.builder()
                .name("Parent 2 " + suffix)
                .slug("parent-2-" + suffix)
                .isActive(true)
                .build());
        Category child = categoryRepository.save(Category.builder()
                .parent(parent1)
                .name("Child " + suffix)
                .slug("child-" + suffix)
                .isActive(true)
                .build());

        CategoryUpsertRequest updateReq = CategoryUpsertRequest.builder()
                .parentId(parent2.getId())
                .name("Child Updated " + suffix)
                .slug("child-" + suffix) // Same slug, should not conflict with itself
                .isActive(false)
                .build();

        mockMvc.perform(put("/api/admin/categories/" + child.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(child.getId()))
                .andExpect(jsonPath("$.name").value("Child Updated " + suffix))
                .andExpect(jsonPath("$.slug").value("child-" + suffix))
                .andExpect(jsonPath("$.parentId").value(parent2.getId()))
                .andExpect(jsonPath("$.isActive").value(false));

        Category updated = categoryRepository.findById(child.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Child Updated " + suffix);
        assertThat(updated.getParent().getId()).isEqualTo(parent2.getId());
        assertThat(updated.getIsActive()).isFalse();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /api/admin/categories/{id} chặn: cha là chính nó, biến category có con thành con, trùng slug của category khác")
    void shouldRejectInvalidUpdateCategoryRequests() throws Exception {
        long suffix = System.currentTimeMillis();
        Category rootWithChild = categoryRepository.save(Category.builder()
                .name("Root Có Con " + suffix)
                .slug("root-co-con-" + suffix)
                .isActive(true)
                .build());
        categoryRepository.save(Category.builder()
                .parent(rootWithChild)
                .name("Con Của Root " + suffix)
                .slug("con-cua-root-" + suffix)
                .isActive(true)
                .build());

        Category anotherRoot = categoryRepository.save(Category.builder()
                .name("Root Khác " + suffix)
                .slug("root-khac-" + suffix)
                .isActive(true)
                .build());

        // 1. Tự làm cha của chính nó -> 400
        CategoryUpsertRequest selfParentReq = CategoryUpsertRequest.builder()
                .parentId(anotherRoot.getId())
                .name("Root Khác " + suffix)
                .build();
        mockMvc.perform(put("/api/admin/categories/" + anotherRoot.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CategoryUpsertRequest.builder()
                                .parentId(anotherRoot.getId())
                                .name("Self Parent")
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("chính nó")));

        // 2. Chuyển category đang có con thành danh mục con -> 400
        CategoryUpsertRequest demoteParentReq = CategoryUpsertRequest.builder()
                .parentId(anotherRoot.getId())
                .name("Cố tình làm con")
                .build();
        mockMvc.perform(put("/api/admin/categories/" + rootWithChild.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(demoteParentReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("đang có danh mục con")));

        // 3. Trùng slug với category khác -> 409
        CategoryUpsertRequest dupSlugReq = CategoryUpsertRequest.builder()
                .name("Đổi tên")
                .slug("root-co-con-" + suffix) // already taken by rootWithChild
                .build();
        mockMvc.perform(put("/api/admin/categories/" + anotherRoot.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupSlugReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("đã tồn tại")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /api/admin/categories/{id} xóa thành công khi danh mục trống; chặn 409 khi còn danh mục con hoặc sản phẩm")
    void shouldHandleCategoryDeletionProperly() throws Exception {
        long suffix = System.currentTimeMillis();

        // 1. Chặn xóa khi còn con -> 409
        Category parentWithChild = categoryRepository.save(Category.builder()
                .name("Parent Chặn Xóa " + suffix)
                .slug("parent-chan-xoa-" + suffix)
                .isActive(true)
                .build());
        Category childCat = categoryRepository.save(Category.builder()
                .parent(parentWithChild)
                .name("Child Xóa " + suffix)
                .slug("child-xoa-" + suffix)
                .isActive(true)
                .build());

        mockMvc.perform(delete("/api/admin/categories/" + parentWithChild.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("còn danh mục con")));

        // 2. Chặn xóa khi còn sản phẩm -> 409
        productRepository.save(Product.builder()
                .category(childCat)
                .name("Sản phẩm thuộc con " + suffix)
                .slug("sp-thuoc-con-" + suffix)
                .basePrice(BigDecimal.valueOf(250000))
                .isActive(true)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        mockMvc.perform(delete("/api/admin/categories/" + childCat.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("còn sản phẩm")));

        // 3. Xóa thành công danh mục sạch -> 204
        Category cleanCategory = categoryRepository.save(Category.builder()
                .name("Danh mục sạch " + suffix)
                .slug("danh-muc-sach-" + suffix)
                .isActive(true)
                .build());

        mockMvc.perform(delete("/api/admin/categories/" + cleanCategory.getId()))
                .andExpect(status().isNoContent());

        assertThat(categoryRepository.findById(cleanCategory.getId())).isEmpty();

        // 4. Xóa danh mục không tồn tại -> 404
        mockMvc.perform(delete("/api/admin/categories/9999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PATCH /api/admin/categories/{id}/status cập nhật bật/tắt hoạt động")
    void shouldToggleCategoryStatus() throws Exception {
        long suffix = System.currentTimeMillis();
        Category category = categoryRepository.save(Category.builder()
                .name("Toggle Status " + suffix)
                .slug("toggle-status-" + suffix)
                .isActive(true)
                .build());

        // Toggle to false
        mockMvc.perform(patch("/api/admin/categories/" + category.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CategoryStatusRequest.builder().isActive(false).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(category.getId()))
                .andExpect(jsonPath("$.isActive").value(false));

        Category updated1 = categoryRepository.findById(category.getId()).orElseThrow();
        assertThat(updated1.getIsActive()).isFalse();

        // Toggle back to true
        mockMvc.perform(patch("/api/admin/categories/" + category.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CategoryStatusRequest.builder().isActive(true).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(category.getId()))
                .andExpect(jsonPath("$.isActive").value(true));

        Category updated2 = categoryRepository.findById(category.getId()).orElseThrow();
        assertThat(updated2.getIsActive()).isTrue();

        // Non-existent category -> 404
        mockMvc.perform(patch("/api/admin/categories/9999999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CategoryStatusRequest.builder().isActive(true).build())))
                .andExpect(status().isNotFound());
    }
}
