package com.shop.backend.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.common.model.DiscountType;
import com.shop.backend.product.Category;
import com.shop.backend.product.CategoryRepository;
import com.shop.backend.product.Product;
import com.shop.backend.product.ProductRepository;
import com.shop.backend.promotion.dto.PromotionCreateRequest;
import com.shop.backend.promotion.dto.PromotionStatusRequest;
import com.shop.backend.promotion.dto.PromotionUpdateRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class PromotionAdminControllerIntegrationTest {

    private static final String BASE_URL = "/api/admin/promotions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private PromotionProductRepository promotionProductRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PromotionAdminService promotionAdminService;

    private Product productA;
    private Product productB;
    private Product productC;

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.user("admin_user").roles("ADMIN");
    }

    private static RequestPostProcessor staff() {
        return SecurityMockMvcRequestPostProcessors.user("staff_user").roles("STAFF");
    }

    private static RequestPostProcessor customer() {
        return SecurityMockMvcRequestPostProcessors.user("cust_user").roles("CUSTOMER");
    }

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(Category.builder()
                .name("Thời Trang Nam " + UUID.randomUUID())
                .slug("thoi-trang-nam-" + UUID.randomUUID())
                .isActive(true)
                .build());

        productA = productRepository.save(Product.builder()
                .category(category)
                .name("Áo Polo Classic " + UUID.randomUUID())
                .slug("ao-polo-classic-" + UUID.randomUUID())
                .basePrice(BigDecimal.valueOf(250_000))
                .dataSource(DataSourceType.WEBSITE)
                .isActive(true)
                .build());

        productB = productRepository.save(Product.builder()
                .category(category)
                .name("Quần Khaki Slim " + UUID.randomUUID())
                .slug("quan-khaki-slim-" + UUID.randomUUID())
                .basePrice(BigDecimal.valueOf(350_000))
                .dataSource(DataSourceType.WEBSITE)
                .isActive(true)
                .build());

        productC = productRepository.save(Product.builder()
                .category(category)
                .name("Áo Sơ Mi Oxford " + UUID.randomUUID())
                .slug("ao-so-mi-oxford-" + UUID.randomUUID())
                .basePrice(BigDecimal.valueOf(400_000))
                .dataSource(DataSourceType.WEBSITE)
                .isActive(true)
                .build());
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @Test
    @DisplayName("Tạo khuyến mãi thành công với loại PERCENT và gán danh sách sản phẩm")
    void shouldCreatePromotionSuccessfullyWithValidPercentAndProducts() throws Exception {
        PromotionCreateRequest request = PromotionCreateRequest.builder()
                .name("Khuyến mãi Giảm Giá 20%")
                .discountType(DiscountType.PERCENT)
                .discountValue(20L)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .isActive(true)
                .productIds(List.of(productA.getId(), productB.getId()))
                .build();

        mockMvc.perform(post(BASE_URL).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Khuyến mãi Giảm Giá 20%"))
                .andExpect(jsonPath("$.discountType").value("PERCENT"))
                .andExpect(jsonPath("$.discountValue").value(20))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.productCount").value(2))
                .andExpect(jsonPath("$.products", hasSize(2)));

        List<Promotion> promotions = promotionRepository.findAll();
        assertThat(promotions).isNotEmpty();
        Promotion saved = promotions.stream()
                .filter(p -> "Khuyến mãi Giảm Giá 20%".equals(p.getName()))
                .findFirst()
                .orElseThrow();

        List<Long> linkedProductIds = promotionProductRepository.findProductIdsByPromotionId(saved.getId());
        assertThat(linkedProductIds).containsExactlyInAnyOrder(productA.getId(), productB.getId());
    }

    @Test
    @DisplayName("Tạo khuyến mãi thành công với loại AMOUNT và danh sách sản phẩm rỗng")
    void shouldCreatePromotionSuccessfullyWithAmountAndEmptyProducts() throws Exception {
        PromotionCreateRequest request = PromotionCreateRequest.builder()
                .name("Giảm 50K Toàn Bộ")
                .discountType(DiscountType.AMOUNT)
                .discountValue(50_000L)
                .startAt(LocalDateTime.now().minusDays(2))
                .endAt(LocalDateTime.now().plusDays(5))
                .productIds(List.of())
                .build();

        mockMvc.perform(post(BASE_URL).with(staff())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Giảm 50K Toàn Bộ"))
                .andExpect(jsonPath("$.discountType").value("AMOUNT"))
                .andExpect(jsonPath("$.discountValue").value(50000))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.productCount").value(0))
                .andExpect(jsonPath("$.products", hasSize(0)));
    }

    @Test
    @DisplayName("Từ chối tạo khi PERCENT > 100")
    void shouldRejectCreateWhenPercentExceeds100() throws Exception {
        PromotionCreateRequest request = PromotionCreateRequest.builder()
                .name("Giảm Quá 100%")
                .discountType(DiscountType.PERCENT)
                .discountValue(105L)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .build();

        mockMvc.perform(post(BASE_URL).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Giá trị giảm theo phần trăm phải từ 1 đến 100"));
    }

    @Test
    @DisplayName("Từ chối tạo khi PERCENT <= 0")
    void shouldRejectCreateWhenPercentIsZeroOrNegative() throws Exception {
        PromotionCreateRequest request = PromotionCreateRequest.builder()
                .name("Giảm 0%")
                .discountType(DiscountType.PERCENT)
                .discountValue(0L)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .build();

        mockMvc.perform(post(BASE_URL).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Từ chối tạo khi AMOUNT <= 0")
    void shouldRejectCreateWhenAmountIsZeroOrNegative() throws Exception {
        PromotionCreateRequest request = PromotionCreateRequest.builder()
                .name("Giảm 0 Đồng")
                .discountType(DiscountType.AMOUNT)
                .discountValue(0L)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .build();

        mockMvc.perform(post(BASE_URL).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Từ chối tạo khi endAt <= startAt")
    void shouldRejectCreateWhenEndAtIsBeforeOrEqualStartAt() throws Exception {
        LocalDateTime time = LocalDateTime.now();
        PromotionCreateRequest request = PromotionCreateRequest.builder()
                .name("Sai Thời Gian")
                .discountType(DiscountType.PERCENT)
                .discountValue(10L)
                .startAt(time)
                .endAt(time.minusHours(1))
                .build();

        mockMvc.perform(post(BASE_URL).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thời gian kết thúc phải sau thời gian bắt đầu"));
    }

    @Test
    @DisplayName("Từ chối tạo khi tên rỗng hoặc vượt quá 150 ký tự")
    void shouldRejectCreateWhenNameIsBlankOrTooLong() throws Exception {
        PromotionCreateRequest blankRequest = PromotionCreateRequest.builder()
                .name("    ")
                .discountType(DiscountType.PERCENT)
                .discountValue(10L)
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(2))
                .build();

        mockMvc.perform(post(BASE_URL).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(blankRequest)))
                .andExpect(status().isBadRequest());

        PromotionCreateRequest longRequest = PromotionCreateRequest.builder()
                .name("A".repeat(151))
                .discountType(DiscountType.PERCENT)
                .discountValue(10L)
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(2))
                .build();

        mockMvc.perform(post(BASE_URL).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(longRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Từ chối tạo khi productIds chứa ID không tồn tại và liệt kê rõ ID thiếu")
    void shouldRejectCreateWhenProductIdsContainNonExistentId() throws Exception {
        PromotionCreateRequest request = PromotionCreateRequest.builder()
                .name("Khuyến Mãi Sản Phẩm Lỗi")
                .discountType(DiscountType.PERCENT)
                .discountValue(15L)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .productIds(List.of(productA.getId(), 999998L, 999999L))
                .build();

        mockMvc.perform(post(BASE_URL).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Không tìm thấy các sản phẩm với ID: 999998, 999999"));
    }

    @Test
    @DisplayName("Từ chối tạo khi productIds vượt quá 100 sản phẩm")
    void shouldRejectCreateWhenProductIdsExceed100() throws Exception {
        List<Long> over100Ids = new ArrayList<>();
        for (long i = 1; i <= 101; i++) {
            over100Ids.add(i);
        }

        PromotionCreateRequest request = PromotionCreateRequest.builder()
                .name("Khuyến Mãi Quá Nhiều Sản Phẩm")
                .discountType(DiscountType.PERCENT)
                .discountValue(15L)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .productIds(over100Ids)
                .build();

        mockMvc.perform(post(BASE_URL).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Danh sách sản phẩm áp dụng không được vượt quá 100 sản phẩm"));
    }

    @Test
    @DisplayName("Tự động loại bỏ ID trùng lặp trong productIds mà không báo lỗi")
    void shouldDeduplicateProductIdsOnCreate() throws Exception {
        PromotionCreateRequest request = PromotionCreateRequest.builder()
                .name("Trùng ID Sản Phẩm")
                .discountType(DiscountType.AMOUNT)
                .discountValue(25_000L)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(3))
                .productIds(List.of(productA.getId(), productA.getId(), productB.getId(), productB.getId()))
                .build();

        mockMvc.perform(post(BASE_URL).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productCount").value(2))
                .andExpect(jsonPath("$.products", hasSize(2)));
    }

    @Test
    @DisplayName("Lấy chi tiết khuyến mãi theo ID kèm thông tin sản phẩm áp dụng")
    void shouldGetPromotionByIdWithProductDetails() throws Exception {
        Promotion promo = promotionRepository.save(Promotion.builder()
                .name("Chi Tiết Khuyến Mãi Test")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(30))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .isActive(true)
                .build());

        promotionProductRepository.save(new PromotionProduct(new PromotionProductId(productA.getId(), promo.getId())));

        mockMvc.perform(get(BASE_URL + "/{id}", promo.getId()).with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(promo.getId()))
                .andExpect(jsonPath("$.name").value("Chi Tiết Khuyến Mãi Test"))
                .andExpect(jsonPath("$.discountType").value("PERCENT"))
                .andExpect(jsonPath("$.discountValue").value(30))
                .andExpect(jsonPath("$.productCount").value(1))
                .andExpect(jsonPath("$.products[0].id").value(productA.getId()))
                .andExpect(jsonPath("$.products[0].name").value(productA.getName()));
    }

    @Test
    @DisplayName("Trả về 404 khi xem chi tiết ID không tồn tại")
    void shouldReturn404WhenGetPromotionByNonExistentId() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{id}", 888888L).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy khuyến mãi với ID 888888"));
    }

    @Test
    @DisplayName("Cập nhật khuyến mãi và thay thế toàn bộ tập productIds chính xác")
    void shouldUpdatePromotionAndReplaceProductIds() throws Exception {
        Promotion promo = promotionRepository.save(Promotion.builder()
                .name("Khuyến Mãi Ban Đầu")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(10))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .isActive(true)
                .build());

        promotionProductRepository.save(new PromotionProduct(new PromotionProductId(productA.getId(), promo.getId())));
        promotionProductRepository.save(new PromotionProduct(new PromotionProductId(productB.getId(), promo.getId())));

        PromotionUpdateRequest updateRequest = PromotionUpdateRequest.builder()
                .name("Khuyến Mãi Sau Cập Nhật")
                .discountType(DiscountType.AMOUNT)
                .discountValue(70_000L)
                .startAt(LocalDateTime.now().minusHours(2))
                .endAt(LocalDateTime.now().plusDays(8))
                .isActive(false)
                .productIds(List.of(productB.getId(), productC.getId())) // bỏ A, giữ B, thêm C
                .build();

        mockMvc.perform(put(BASE_URL + "/{id}", promo.getId()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Khuyến Mãi Sau Cập Nhật"))
                .andExpect(jsonPath("$.discountType").value("AMOUNT"))
                .andExpect(jsonPath("$.discountValue").value(70000))
                .andExpect(jsonPath("$.isActive").value(false))
                .andExpect(jsonPath("$.productCount").value(2));

        List<Long> linkedProductIds = promotionProductRepository.findProductIdsByPromotionId(promo.getId());
        assertThat(linkedProductIds).containsExactlyInAnyOrder(productB.getId(), productC.getId());
    }

    @Test
    @DisplayName("Cập nhật trạng thái isActive qua endpoint PATCH")
    void shouldUpdatePromotionStatus() throws Exception {
        Promotion promo = promotionRepository.save(Promotion.builder()
                .name("Bật Tắt Khuyến Mãi")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(15))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .isActive(true)
                .build());

        PromotionStatusRequest statusRequest = new PromotionStatusRequest(false);

        mockMvc.perform(patch(BASE_URL + "/{id}/status", promo.getId()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        Promotion reloaded = promotionRepository.findById(promo.getId()).orElseThrow();
        assertThat(reloaded.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("Xóa khuyến mãi thành công và xóa luôn liên kết product_promotions trong DB")
    void shouldDeletePromotionAndCascadeDeleteProductLinks() throws Exception {
        Promotion promo = promotionRepository.save(Promotion.builder()
                .name("Khuyến Mãi Cần Xóa")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(25))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .isActive(true)
                .build());

        promotionProductRepository.save(new PromotionProduct(new PromotionProductId(productA.getId(), promo.getId())));
        promotionProductRepository.save(new PromotionProduct(new PromotionProductId(productB.getId(), promo.getId())));

        mockMvc.perform(delete(BASE_URL + "/{id}", promo.getId()).with(admin()))
                .andExpect(status().isNoContent());

        assertThat(promotionRepository.findById(promo.getId())).isEmpty();
        assertThat(promotionProductRepository.findProductIdsByPromotionId(promo.getId())).isEmpty();
    }

    @Test
    @DisplayName("Lọc danh sách theo keyword, discountType và isActive")
    void shouldFilterPromotionsByKeywordAndDiscountTypeAndIsActive() throws Exception {
        Promotion p1 = promotionRepository.save(Promotion.builder()
                .name("Đại Tiệc Mùa Đông")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(20))
                .startAt(LocalDateTime.now().minusDays(2))
                .endAt(LocalDateTime.now().plusDays(5))
                .isActive(true)
                .build());

        Promotion p2 = promotionRepository.save(Promotion.builder()
                .name("Đại Tiệc Mùa Hè")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(50_000))
                .startAt(LocalDateTime.now().minusDays(2))
                .endAt(LocalDateTime.now().plusDays(5))
                .isActive(false)
                .build());

        mockMvc.perform(get(BASE_URL).with(staff())
                        .param("keyword", "Mùa Đông")
                        .param("discountType", "PERCENT")
                        .param("isActive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]", p1.getId()).exists())
                .andExpect(jsonPath("$.content[?(@.id == %d)]", p2.getId()).doesNotExist());
    }

    @Test
    @DisplayName("Lọc danh sách theo productId chính xác")
    void shouldFilterPromotionsByProductId() throws Exception {
        Promotion p1 = promotionRepository.save(Promotion.builder()
                .name("Khuyến Mãi Cho Áo Polo")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(10))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .isActive(true)
                .build());

        Promotion p2 = promotionRepository.save(Promotion.builder()
                .name("Khuyến Mãi Cho Quần Khaki")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(30_000))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .isActive(true)
                .build());

        promotionProductRepository.save(new PromotionProduct(new PromotionProductId(productA.getId(), p1.getId())));
        promotionProductRepository.save(new PromotionProduct(new PromotionProductId(productB.getId(), p2.getId())));

        mockMvc.perform(get(BASE_URL).with(staff()).param("productId", String.valueOf(productA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]", p1.getId()).exists())
                .andExpect(jsonPath("$.content[?(@.id == %d)]", p2.getId()).doesNotExist());
    }

    @Test
    @DisplayName("Phân trang chuẩn và giới hạn size tối đa 100")
    void shouldPaginateAndEnforceMaxPageSize() throws Exception {
        mockMvc.perform(get(BASE_URL).with(staff())
                        .param("page", "0")
                        .param("size", "250"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    @DisplayName("Phân quyền: Chưa đăng nhập trả về 401, Khách hàng trả về 403")
    void shouldEnforceSecurityPermissions() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(BASE_URL).with(customer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Tính toán productCount chính xác bằng GROUP BY không N+1")
    void shouldAccuratelyCalculateProductCountViaGroupBy() throws Exception {
        Promotion p = promotionRepository.save(Promotion.builder()
                .name("Khuyến Mãi Đếm Sản Phẩm")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(15))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .isActive(true)
                .build());

        promotionProductRepository.save(new PromotionProduct(new PromotionProductId(productA.getId(), p.getId())));
        promotionProductRepository.save(new PromotionProduct(new PromotionProductId(productB.getId(), p.getId())));
        promotionProductRepository.save(new PromotionProduct(new PromotionProductId(productC.getId(), p.getId())));

        mockMvc.perform(get(BASE_URL).with(staff()).param("keyword", "Đếm Sản Phẩm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productCount").value(3));
    }

    @Test
    @DisplayName("Xác thực trạng thái thời gian SCHEDULED, RUNNING, EXPIRED với Clock cố định")
    void shouldProperlyResolveTimeStatusWithFixedClock() {
        Instant fixedInstant = Instant.parse("2026-06-15T12:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
        LocalDateTime fixedNow = LocalDateTime.now(fixedClock);

        Promotion scheduled = Promotion.builder()
                .name("Chưa Diễn Ra")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(10))
                .startAt(fixedNow.plusDays(1))
                .endAt(fixedNow.plusDays(5))
                .isActive(true)
                .build();

        Promotion running = Promotion.builder()
                .name("Đang Chạy")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(10))
                .startAt(fixedNow.minusDays(1))
                .endAt(fixedNow.plusDays(2))
                .isActive(true)
                .build();

        Promotion expired = Promotion.builder()
                .name("Đã Hết Hạn")
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(10))
                .startAt(fixedNow.minusDays(10))
                .endAt(fixedNow.minusDays(1))
                .isActive(true)
                .build();

        PromotionRepository promoRepo = promotionRepository;
        promoRepo.saveAll(Arrays.asList(scheduled, running, expired));

        PromotionAdminServiceImpl serviceWithClock = new PromotionAdminServiceImpl(
                promotionRepository,
                promotionProductRepository,
                null,
                fixedClock);

        var scheduledPage = serviceWithClock.getPromotions(
                "Chưa Diễn Ra", null, null, PromotionTimeStatus.SCHEDULED, null, 0, 10);
        assertThat(scheduledPage.getContent()).hasSize(1);
        assertThat(scheduledPage.getContent().get(0).getStatus()).isEqualTo(PromotionTimeStatus.SCHEDULED);

        var runningPage = serviceWithClock.getPromotions(
                "Đang Chạy", null, null, PromotionTimeStatus.RUNNING, null, 0, 10);
        assertThat(runningPage.getContent()).hasSize(1);
        assertThat(runningPage.getContent().get(0).getStatus()).isEqualTo(PromotionTimeStatus.RUNNING);

        var expiredPage = serviceWithClock.getPromotions(
                "Đã Hết Hạn", null, null, PromotionTimeStatus.EXPIRED, null, 0, 10);
        assertThat(expiredPage.getContent()).hasSize(1);
        assertThat(expiredPage.getContent().get(0).getStatus()).isEqualTo(PromotionTimeStatus.EXPIRED);
    }
}
