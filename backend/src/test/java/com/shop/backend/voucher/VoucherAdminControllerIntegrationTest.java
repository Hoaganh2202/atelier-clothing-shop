package com.shop.backend.voucher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.shop.backend.common.converter.DiscountTypeConverter;
import com.shop.backend.common.model.DiscountType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
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
class VoucherAdminControllerIntegrationTest {

    private static final String BASE_URL = "/api/admin/vouchers";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private EntityManager entityManager;

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN");
    }

    private static RequestPostProcessor staff() {
        return SecurityMockMvcRequestPostProcessors.user("staff").roles("STAFF");
    }

    private static RequestPostProcessor customer() {
        return SecurityMockMvcRequestPostProcessors.user("customer").roles("CUSTOMER");
    }

    private static String uniqueCode() {
        return "TST" + ThreadLocalRandom.current().nextInt(100_000, 999_999);
    }

    private Map<String, Object> validPayload(String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("discountType", "PERCENT");
        body.put("discountValue", 10);
        body.put("minOrder", 200000);
        body.put("maxDiscount", 50000);
        body.put("usageLimit", 100);
        body.put("perUserLimit", 2);
        body.put("startAt", LocalDateTime.now().minusDays(1).withNano(0).toString());
        body.put("endAt", LocalDateTime.now().plusDays(30).withNano(0).toString());
        return body;
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Voucher saveVoucher(String code, LocalDateTime startAt, LocalDateTime endAt, boolean isActive, DiscountType type) {
        return voucherRepository.saveAndFlush(Voucher.builder()
                .code(code)
                .discountType(type)
                .discountValue(BigDecimal.valueOf(type == DiscountType.PERCENT ? 10 : 30000))
                .startAt(startAt)
                .endAt(endAt)
                .perUserLimit(1)
                .isActive(isActive)
                .build());
    }

    private void expectBadRequest(Map<String, Object> body, String messagePart) throws Exception {
        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(messagePart)));
    }

    @Test
    @DisplayName("Phân quyền: 401 khi chưa đăng nhập, 403 với CUSTOMER, ADMIN/STAFF truy cập được")
    void shouldEnforceRoleSecurity() throws Exception {
        Voucher voucher = saveVoucher(uniqueCode(), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                true, DiscountType.PERCENT);
        String itemUrl = BASE_URL + "/" + voucher.getId();
        String statusBody = json(Map.of("isActive", false));

        mockMvc.perform(get(BASE_URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(itemUrl)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json(validPayload(uniqueCode()))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(BASE_URL).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get(itemUrl).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE_URL).with(customer()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(validPayload(uniqueCode()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(itemUrl).with(customer()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(validPayload(uniqueCode()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(itemUrl + "/status").with(customer()).contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(BASE_URL).with(admin())).andExpect(status().isOk());
        mockMvc.perform(get(itemUrl).with(staff())).andExpect(status().isOk());
        mockMvc.perform(patch(itemUrl + "/status").with(staff()).contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Không có endpoint DELETE voucher")
    void shouldNotExposeDeleteEndpoint() throws Exception {
        Voucher voucher = saveVoucher(uniqueCode(), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                true, DiscountType.AMOUNT);
        mockMvc.perform(delete(BASE_URL + "/" + voucher.getId()).with(admin()))
                .andExpect(status().is4xxClientError());
        assertThat(voucherRepository.existsById(voucher.getId())).isTrue();
    }

    @Test
    @DisplayName("Tạo voucher: chuẩn hóa mã in hoa, mặc định bật, trả đúng DTO")
    void shouldCreateVoucherWithNormalizedCode() throws Exception {
        String code = uniqueCode();
        Map<String, Object> body = validPayload("  " + code.toLowerCase() + " ");

        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.discountType").value("PERCENT"))
                .andExpect(jsonPath("$.discountValue").value(10))
                .andExpect(jsonPath("$.minOrder").value(200000))
                .andExpect(jsonPath("$.maxDiscount").value(50000))
                .andExpect(jsonPath("$.usageLimit").value(100))
                .andExpect(jsonPath("$.perUserLimit").value(2))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.usedCount").doesNotExist());
    }

    @Test
    @DisplayName("Tạo voucher: perUserLimit mặc định 1, các giới hạn tùy chọn để null")
    void shouldDefaultPerUserLimitAndAllowNullOptionalLimits() throws Exception {
        Map<String, Object> body = validPayload(uniqueCode());
        body.put("discountType", "AMOUNT");
        body.put("discountValue", 30000);
        body.remove("maxDiscount");
        body.remove("minOrder");
        body.remove("usageLimit");
        body.remove("perUserLimit");

        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perUserLimit").value(1))
                .andExpect(jsonPath("$.usageLimit").doesNotExist())
                .andExpect(jsonPath("$.minOrder").doesNotExist())
                .andExpect(jsonPath("$.maxDiscount").doesNotExist());
    }

    @Test
    @DisplayName("Tạo voucher: start_at trong quá khứ được chấp nhận")
    void shouldAcceptPastStartAt() throws Exception {
        Map<String, Object> body = validPayload(uniqueCode());
        body.put("startAt", LocalDateTime.now().minusYears(1).withNano(0).toString());

        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Tạo voucher: mã trùng (kể cả khác hoa/thường) trả 409")
    void shouldRejectDuplicateCodeWith409() throws Exception {
        String code = uniqueCode();
        saveVoucher(code, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), true, DiscountType.PERCENT);

        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(validPayload(code.toLowerCase()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(code)));
    }

    @Test
    @DisplayName("Tạo voucher: mã sai định dạng hoặc ngoài độ dài 3-50 trả 400")
    void shouldRejectInvalidCodeFormat() throws Exception {
        expectBadRequest(validPayload("AB"), "3-50");
        expectBadRequest(validPayload("SALE 10"), "chữ in hoa");
        expectBadRequest(validPayload("SALE@10"), "chữ in hoa");

        Map<String, Object> tooLong = validPayload("A".repeat(51));
        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(json(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").exists());

        Map<String, Object> blank = validPayload("   ");
        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(json(blank)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").exists());
    }

    @Test
    @DisplayName("Tạo voucher: ngày bắt đầu phải trước ngày kết thúc")
    void shouldRejectStartAtNotBeforeEndAt() throws Exception {
        Map<String, Object> body = validPayload(uniqueCode());
        body.put("startAt", LocalDateTime.now().plusDays(10).withNano(0).toString());
        body.put("endAt", LocalDateTime.now().plusDays(5).withNano(0).toString());
        expectBadRequest(body, "trước thời gian kết thúc");

        String same = LocalDateTime.now().plusDays(5).withNano(0).toString();
        body.put("startAt", same);
        body.put("endAt", same);
        expectBadRequest(body, "trước thời gian kết thúc");
    }

    @Test
    @DisplayName("Tạo voucher: end_at trong quá khứ trả 400")
    void shouldRejectPastEndAtOnCreate() throws Exception {
        Map<String, Object> body = validPayload(uniqueCode());
        body.put("startAt", LocalDateTime.now().minusDays(10).withNano(0).toString());
        body.put("endAt", LocalDateTime.now().minusDays(1).withNano(0).toString());
        expectBadRequest(body, "ở tương lai");
    }

    @Test
    @DisplayName("Giá trị giảm: percent trong (0,100], amount > 0")
    void shouldValidateDiscountValue() throws Exception {
        Map<String, Object> over = validPayload(uniqueCode());
        over.put("discountValue", 101);
        expectBadRequest(over, "không quá 100");

        Map<String, Object> zero = validPayload(uniqueCode());
        zero.put("discountValue", 0);
        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(json(zero)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.discountValue").exists());

        Map<String, Object> negative = validPayload(uniqueCode());
        negative.put("discountType", "AMOUNT");
        negative.remove("maxDiscount");
        negative.put("discountValue", -5);
        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(json(negative)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.discountValue").exists());

        Map<String, Object> hundred = validPayload(uniqueCode());
        hundred.put("discountValue", 100);
        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(json(hundred)))
                .andExpect(status().isCreated());

        Map<String, Object> bigAmount = validPayload(uniqueCode());
        bigAmount.put("discountType", "AMOUNT");
        bigAmount.remove("maxDiscount");
        bigAmount.put("discountValue", 500000);
        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(json(bigAmount)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("max_discount chỉ hợp lệ khi giảm theo phần trăm: amount kèm max_discount trả 400")
    void shouldRejectMaxDiscountForAmountType() throws Exception {
        Map<String, Object> body = validPayload(uniqueCode());
        body.put("discountType", "AMOUNT");
        body.put("discountValue", 30000);
        body.put("maxDiscount", 10000);
        expectBadRequest(body, "phần trăm");
    }

    @Test
    @DisplayName("Các trường số: không âm, usage_limit và per_user_limit >= 1, max_discount > 0")
    void shouldValidateNumericLimits() throws Exception {
        Map<String, Object> body = validPayload(uniqueCode());
        body.put("minOrder", -1);
        body.put("maxDiscount", 0);
        body.put("usageLimit", 0);
        body.put("perUserLimit", 0);

        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.minOrder").exists())
                .andExpect(jsonPath("$.errors.maxDiscount").exists())
                .andExpect(jsonPath("$.errors.usageLimit").exists())
                .andExpect(jsonPath("$.errors.perUserLimit").exists());
    }

    @Test
    @DisplayName("Thiếu trường bắt buộc và loại giảm giá lạ trả 400")
    void shouldRejectMissingRequiredFieldsAndUnknownDiscountType() throws Exception {
        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").exists())
                .andExpect(jsonPath("$.errors.discountType").exists())
                .andExpect(jsonPath("$.errors.discountValue").exists())
                .andExpect(jsonPath("$.errors.startAt").exists())
                .andExpect(jsonPath("$.errors.endAt").exists());

        Map<String, Object> body = validPayload(uniqueCode());
        body.put("discountType", "BOGUS");
        mockMvc.perform(post(BASE_URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Sửa voucher: không đổi mã, cho phép sửa voucher đã hết hạn, giữ isActive khi không gửi")
    void shouldUpdateVoucherKeepingCodeAndAllowExpired() throws Exception {
        String code = uniqueCode();
        Voucher expired = saveVoucher(code, LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(1),
                false, DiscountType.PERCENT);

        Map<String, Object> body = validPayload("IGNORED-CODE");
        body.put("discountValue", 25);
        body.put("startAt", LocalDateTime.now().minusDays(30).withNano(0).toString());
        body.put("endAt", LocalDateTime.now().minusHours(1).withNano(0).toString());

        mockMvc.perform(put(BASE_URL + "/" + expired.getId()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.discountValue").value(25))
                .andExpect(jsonPath("$.isActive").value(false))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    @DisplayName("Sửa voucher: vẫn kiểm tra ngày, giá trị giảm, max_discount; 404 khi không tồn tại")
    void shouldValidateOnUpdateAndReturn404() throws Exception {
        Voucher voucher = saveVoucher(uniqueCode(), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                true, DiscountType.PERCENT);
        String url = BASE_URL + "/" + voucher.getId();

        Map<String, Object> badDates = validPayload("X");
        badDates.put("startAt", LocalDateTime.now().plusDays(2).withNano(0).toString());
        badDates.put("endAt", LocalDateTime.now().plusDays(1).withNano(0).toString());
        mockMvc.perform(put(url).with(admin()).contentType(MediaType.APPLICATION_JSON).content(json(badDates)))
                .andExpect(status().isBadRequest());

        Map<String, Object> amountWithMax = validPayload("X");
        amountWithMax.put("discountType", "AMOUNT");
        mockMvc.perform(put(url).with(admin()).contentType(MediaType.APPLICATION_JSON).content(json(amountWithMax)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(BASE_URL + "/999999999").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(validPayload("X"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get(BASE_URL + "/999999999").with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(patch(BASE_URL + "/999999999/status").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("isActive", true))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Bật/tắt voucher qua PATCH status; thiếu isActive trả 400")
    void shouldToggleStatus() throws Exception {
        Voucher voucher = saveVoucher(uniqueCode(), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                true, DiscountType.PERCENT);
        String url = BASE_URL + "/" + voucher.getId() + "/status";

        mockMvc.perform(patch(url).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("isActive", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
        mockMvc.perform(patch(url).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("isActive", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
        mockMvc.perform(patch(url).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.isActive").exists());
    }

    @Test
    @DisplayName("Danh sách: phân trang phẳng, size bị giới hạn về 1-100, status lạ trả 400")
    void shouldPaginateAndLimitSize() throws Exception {
        for (int i = 0; i < 3; i++) {
            saveVoucher(uniqueCode() + i, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), true,
                    DiscountType.PERCENT);
        }

        mockMvc.perform(get(BASE_URL).with(admin()).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.pageable").doesNotExist());

        mockMvc.perform(get(BASE_URL).with(admin()).param("size", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        mockMvc.perform(get(BASE_URL).with(admin()).param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1));
        mockMvc.perform(get(BASE_URL).with(admin()).param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));
        mockMvc.perform(get(BASE_URL).with(admin()).param("status", "BOGUS")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Danh sách: lọc theo keyword, discountType, isActive")
    void shouldFilterByKeywordTypeAndActive() throws Exception {
        String marker = "FLT" + ThreadLocalRandom.current().nextInt(1000, 9999);
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        saveVoucher(marker + "-PCT-ON", start, end, true, DiscountType.PERCENT);
        saveVoucher(marker + "-PCT-OFF", start, end, false, DiscountType.PERCENT);
        saveVoucher(marker + "-AMT-ON", start, end, true, DiscountType.AMOUNT);

        mockMvc.perform(get(BASE_URL).with(admin()).param("keyword", marker.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get(BASE_URL).with(admin()).param("keyword", marker).param("discountType", "AMOUNT"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value(marker + "-AMT-ON"));
        mockMvc.perform(get(BASE_URL).with(admin()).param("keyword", marker).param("isActive", "false"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value(marker + "-PCT-OFF"));
        mockMvc.perform(get(BASE_URL).with(admin()).param("keyword", marker).param("isActive", "true")
                        .param("discountType", "PERCENT"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(BASE_URL).with(admin()).param("keyword", marker + "%"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Danh sách: lọc status theo thời gian, độc lập với isActive")
    void shouldFilterByTimeStatusIndependentOfIsActive() throws Exception {
        String marker = "STS" + ThreadLocalRandom.current().nextInt(1000, 9999);
        LocalDateTime now = LocalDateTime.now();
        saveVoucher(marker + "-SCH", now.plusDays(1), now.plusDays(5), true, DiscountType.PERCENT);
        saveVoucher(marker + "-RUN", now.minusDays(1), now.plusDays(1), false, DiscountType.PERCENT);
        saveVoucher(marker + "-EXP", now.minusDays(5), now.minusDays(1), true, DiscountType.PERCENT);

        mockMvc.perform(get(BASE_URL).with(admin()).param("keyword", marker).param("status", "SCHEDULED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value(marker + "-SCH"))
                .andExpect(jsonPath("$.content[0].status").value("SCHEDULED"));
        mockMvc.perform(get(BASE_URL).with(admin()).param("keyword", marker).param("status", "RUNNING"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value(marker + "-RUN"))
                .andExpect(jsonPath("$.content[0].isActive").value(false));
        mockMvc.perform(get(BASE_URL).with(admin()).param("keyword", marker).param("status", "EXPIRED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value(marker + "-EXP"))
                .andExpect(jsonPath("$.content[*].code", not(hasItem(marker + "-RUN"))));
    }

    @Test
    @DisplayName("Enum DiscountType: ghi, clear(), đọc lại đúng; giá trị lạ ném IllegalArgumentException")
    void shouldPersistDiscountTypeAsLowercaseAndRejectUnknownValue() {
        Voucher saved = saveVoucher(uniqueCode(), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                true, DiscountType.AMOUNT);
        entityManager.clear();

        Voucher reloaded = voucherRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getDiscountType()).isEqualTo(DiscountType.AMOUNT);

        Object rawValue = entityManager
                .createNativeQuery("SELECT discount_type FROM vouchers WHERE id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();
        assertThat(rawValue).isEqualTo("amount");

        assertThatThrownBy(() -> new DiscountTypeConverter().convertToEntityAttribute("bogus"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
