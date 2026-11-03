package com.shop.backend.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.address.dto.AddressRequest;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.security.UserPrincipal;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAddressRepository userAddressRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    private Role customerRole;

    @BeforeEach
    void setUp() {
        customerRole = roleRepository.findByName("customer")
                .orElseThrow(() -> new IllegalStateException("Vai trò 'customer' chưa tồn tại trong DB test"));
    }

    @Test
    @DisplayName("1. Chưa đăng nhập gọi các endpoint /api/addresses trả về 401 Unauthorized")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        AddressRequest validRequest = buildValidRequest("Nguyễn Văn A", "0901234567", false);
        String body = objectMapper.writeValueAsString(validRequest);

        mockMvc.perform(get("/api/addresses"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/addresses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/addresses/1/default"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/addresses/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("2. GET /api/addresses trả về danh sách rỗng [] khi khách hàng chưa có địa chỉ nào")
    void shouldReturnEmptyListWhenUserHasNoAddresses() throws Exception {
        User user = createCustomer("empty");
        UserPrincipal principal = toPrincipal(user);

        mockMvc.perform(get("/api/addresses")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("3. POST /api/addresses tạo địa chỉ đầu tiên luôn tự động đặt isDefault = true và chuẩn hóa chuỗi")
    void shouldMakeFirstAddressDefaultRegardlessOfRequestFlag() throws Exception {
        User user = createCustomer("first_default");
        UserPrincipal principal = toPrincipal(user);

        AddressRequest request = AddressRequest.builder()
                .receiverName("  Trần Thị Bích  ")
                .phone(" 0912345678 ")
                .province("  TP. Hồ Chí Minh ")
                .district("   ")
                .ward("")
                .address("  123 Nguyễn Huệ, Quận 1  ")
                .isDefault(false)
                .build();

        mockMvc.perform(post("/api/addresses")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.receiverName").value("Trần Thị Bích"))
                .andExpect(jsonPath("$.phone").value("0912345678"))
                .andExpect(jsonPath("$.province").value("TP. Hồ Chí Minh"))
                .andExpect(jsonPath("$.district").value(nullValue()))
                .andExpect(jsonPath("$.ward").value(nullValue()))
                .andExpect(jsonPath("$.address").value("123 Nguyễn Huệ, Quận 1"))
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    @DisplayName("4. POST /api/addresses thêm địa chỉ thứ 2 với isDefault = false giữ nguyên địa chỉ đầu là mặc định")
    void shouldKeepExistingDefaultWhenAddingSecondAddressWithDefaultFalse() throws Exception {
        User user = createCustomer("second_false");
        UserAddress first = saveAddressDirectly(user, "Người Nhận 1", "0901111111", true);

        entityManager.flush();
        entityManager.clear();

        UserPrincipal principal = toPrincipal(user);
        AddressRequest secondReq = buildValidRequest("Người Nhận 2", "+84912345678", false);

        mockMvc.perform(post("/api/addresses")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiverName").value("Người Nhận 2"))
                .andExpect(jsonPath("$.isDefault").value(false));

        entityManager.flush();
        entityManager.clear();

        UserAddress reloadedFirst = userAddressRepository.findById(first.getId()).orElseThrow();
        assertThat(reloadedFirst.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("5. POST /api/addresses thêm địa chỉ thứ 2 với isDefault = true chuyển mặc định sang địa chỉ mới và chỉ còn 1 mặc định")
    void shouldSwitchDefaultWhenAddingSecondAddressWithDefaultTrue() throws Exception {
        User user = createCustomer("second_true");
        UserAddress first = saveAddressDirectly(user, "Người Nhận 1", "0901111111", true);

        entityManager.flush();
        entityManager.clear();

        UserPrincipal principal = toPrincipal(user);
        AddressRequest secondReq = buildValidRequest("Người Nhận 2", "0902222222", true);

        mockMvc.perform(post("/api/addresses")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiverName").value("Người Nhận 2"))
                .andExpect(jsonPath("$.isDefault").value(true));

        entityManager.flush();
        entityManager.clear();

        List<UserAddress> all = userAddressRepository.findAllByUserIdOrdered(user.getId());
        assertThat(all).hasSize(2);
        assertThat(all.stream().filter(UserAddress::getIsDefault).count()).isEqualTo(1);
        assertThat(userAddressRepository.findById(first.getId()).orElseThrow().getIsDefault()).isFalse();
    }

    @Test
    @DisplayName("6. GET /api/addresses chỉ trả địa chỉ của chính mình và sắp xếp ORDER BY is_default DESC, id DESC")
    void shouldReturnAddressesOrderedByDefaultDescThenIdDesc() throws Exception {
        User userA = createCustomer("order_a");
        User userB = createCustomer("order_b");

        UserAddress a1 = saveAddressDirectly(userA, "A1 - Cũ", "0901000001", false);
        UserAddress a2 = saveAddressDirectly(userA, "A2 - Mặc định", "0901000002", true);
        UserAddress a3 = saveAddressDirectly(userA, "A3 - Mới nhất không mặc định", "0901000003", false);
        saveAddressDirectly(userB, "B1 - Của người khác", "0909999999", true);

        entityManager.flush();
        entityManager.clear();

        UserPrincipal principalA = toPrincipal(userA);

        mockMvc.perform(get("/api/addresses")
                        .with(SecurityMockMvcRequestPostProcessors.user(principalA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(a2.getId()))
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[1].id").value(a3.getId()))
                .andExpect(jsonPath("$[1].isDefault").value(false))
                .andExpect(jsonPath("$[2].id").value(a1.getId()))
                .andExpect(jsonPath("$[2].isDefault").value(false));
    }

    @Test
    @DisplayName("7. POST /api/addresses trả về 400 Bad Request khi vượt quá giới hạn 10 địa chỉ/user")
    void shouldRejectWhenExceedingMax10AddressesPerUser() throws Exception {
        User user = createCustomer("limit_10");
        for (int i = 1; i <= AddressService.MAX_ADDRESSES_PER_USER; i++) {
            saveAddressDirectly(user, "Địa chỉ " + i, "090123456" + (i % 10), i == 1);
        }

        entityManager.flush();
        entityManager.clear();

        UserPrincipal principal = toPrincipal(user);
        AddressRequest eleventhRequest = buildValidRequest("Địa chỉ 11", "0908888888", false);

        mockMvc.perform(post("/api/addresses")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eleventhRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("tối đa 10 địa chỉ")));
    }

    @Test
    @DisplayName("8. POST /api/addresses trả về 400 kèm lỗi tiếng Việt khi thiếu trường bắt buộc, SĐT sai định dạng hoặc quá dài")
    void shouldReturn400WithVietnameseErrorsWhenValidationFails() throws Exception {
        User user = createCustomer("validation");
        UserPrincipal principal = toPrincipal(user);

        AddressRequest invalidRequest = AddressRequest.builder()
                .receiverName("   ")
                .phone("12345abc")
                .province("")
                .district("Q".repeat(101))
                .address("")
                .build();

        mockMvc.perform(post("/api/addresses")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.receiverName").value("Họ tên người nhận không được để trống"))
                .andExpect(jsonPath("$.errors.phone", containsString("Số điện thoại không hợp lệ")))
                .andExpect(jsonPath("$.errors.province").value("Tỉnh/Thành phố không được để trống"))
                .andExpect(jsonPath("$.errors.district").value("Quận/Huyện không được vượt quá 100 ký tự"))
                .andExpect(jsonPath("$.errors.address").value("Địa chỉ chi tiết không được để trống"));
    }

    @Test
    @DisplayName("9. PUT /api/addresses/{id} cập nhật thông tin và GIỮ NGUYÊN isDefault = true nếu địa chỉ đang mặc định mà gửi isDefault = false")
    void shouldUpdateAddressFieldsAndKeepDefaultWhenUpdatingCurrentDefaultWithFalse() throws Exception {
        User user = createCustomer("put_keep_default");
        UserAddress defaultAddr = saveAddressDirectly(user, "Tên Cũ", "0901112222", true);

        entityManager.flush();
        entityManager.clear();

        UserPrincipal principal = toPrincipal(user);
        AddressRequest updateReq = AddressRequest.builder()
                .receiverName("Tên Mới Cập Nhật")
                .phone("+84987654321")
                .province("Hà Nội")
                .district("Quận Cầu Giấy")
                .ward("Phường Dịch Vọng")
                .address("Số 10 Xuân Thủy")
                .isDefault(false)
                .build();

        mockMvc.perform(put("/api/addresses/" + defaultAddr.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(defaultAddr.getId()))
                .andExpect(jsonPath("$.receiverName").value("Tên Mới Cập Nhật"))
                .andExpect(jsonPath("$.phone").value("+84987654321"))
                .andExpect(jsonPath("$.province").value("Hà Nội"))
                .andExpect(jsonPath("$.district").value("Quận Cầu Giấy"))
                .andExpect(jsonPath("$.ward").value("Phường Dịch Vọng"))
                .andExpect(jsonPath("$.address").value("Số 10 Xuân Thủy"))
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    @DisplayName("10. PUT /api/addresses/{id} với isDefault = true chuyển mặc định sang địa chỉ đang sửa và hạ mặc định cũ")
    void shouldSwitchDefaultWhenUpdatingNonDefaultAddressWithDefaultTrue() throws Exception {
        User user = createCustomer("put_switch_default");
        UserAddress addr1 = saveAddressDirectly(user, "Địa chỉ 1", "0901111111", true);
        UserAddress addr2 = saveAddressDirectly(user, "Địa chỉ 2", "0902222222", false);

        entityManager.flush();
        entityManager.clear();

        UserPrincipal principal = toPrincipal(user);
        AddressRequest updateReq = buildValidRequest("Địa chỉ 2 Đã Sửa", "0902222222", true);

        mockMvc.perform(put("/api/addresses/" + addr2.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(addr2.getId()))
                .andExpect(jsonPath("$.isDefault").value(true));

        entityManager.flush();
        entityManager.clear();

        assertThat(userAddressRepository.findById(addr1.getId()).orElseThrow().getIsDefault()).isFalse();
        assertThat(userAddressRepository.findById(addr2.getId()).orElseThrow().getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("11. PATCH /api/addresses/{id}/default đặt mặc định thành công, đảm bảo duy nhất 1 mặc định và idempotent")
    void shouldSetDefaultAddressViaPatchAndBeIdempotent() throws Exception {
        User user = createCustomer("patch_default");
        UserAddress addr1 = saveAddressDirectly(user, "Địa chỉ 1", "0901111111", true);
        UserAddress addr2 = saveAddressDirectly(user, "Địa chỉ 2", "0902222222", false);

        entityManager.flush();
        entityManager.clear();

        UserPrincipal principal = toPrincipal(user);

        mockMvc.perform(patch("/api/addresses/" + addr2.getId() + "/default")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(addr2.getId()))
                .andExpect(jsonPath("$.isDefault").value(true));

        mockMvc.perform(patch("/api/addresses/" + addr2.getId() + "/default")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(addr2.getId()))
                .andExpect(jsonPath("$.isDefault").value(true));

        entityManager.flush();
        entityManager.clear();

        assertThat(userAddressRepository.findById(addr1.getId()).orElseThrow().getIsDefault()).isFalse();
        assertThat(userAddressRepository.findById(addr2.getId()).orElseThrow().getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("12. DELETE /api/addresses/{id} xóa địa chỉ thường trả về 204 và giữ nguyên địa chỉ mặc định hiện tại")
    void shouldDeleteNonDefaultAddressWithoutChangingCurrentDefault() throws Exception {
        User user = createCustomer("delete_normal");
        UserAddress defaultAddr = saveAddressDirectly(user, "Mặc định", "0901111111", true);
        UserAddress normalAddr = saveAddressDirectly(user, "Thường", "0902222222", false);

        entityManager.flush();
        entityManager.clear();

        UserPrincipal principal = toPrincipal(user);

        mockMvc.perform(delete("/api/addresses/" + normalAddr.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        assertThat(userAddressRepository.findById(normalAddr.getId())).isEmpty();
        assertThat(userAddressRepository.findById(defaultAddr.getId()).orElseThrow().getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("13. DELETE /api/addresses/{id} xóa địa chỉ mặc định tự động chuyển mặc định cho địa chỉ còn lại có id lớn nhất")
    void shouldPromoteNewestRemainingAddressWhenDeletingDefaultAddress() throws Exception {
        User user = createCustomer("delete_default_promote");
        UserAddress defaultAddr = saveAddressDirectly(user, "Mặc định Cũ", "0901111111", true);
        UserAddress olderRemaining = saveAddressDirectly(user, "Còn lại Cũ hơn", "0902222222", false);
        UserAddress newestRemaining = saveAddressDirectly(user, "Còn lại Mới nhất", "0903333333", false);

        entityManager.flush();
        entityManager.clear();

        UserPrincipal principal = toPrincipal(user);

        mockMvc.perform(delete("/api/addresses/" + defaultAddr.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        assertThat(userAddressRepository.findById(defaultAddr.getId())).isEmpty();
        assertThat(userAddressRepository.findById(newestRemaining.getId()).orElseThrow().getIsDefault()).isTrue();
        assertThat(userAddressRepository.findById(olderRemaining.getId()).orElseThrow().getIsDefault()).isFalse();
    }

    @Test
    @DisplayName("14. DELETE /api/addresses/{id} xóa địa chỉ cuối cùng thành công (204) và danh sách còn lại rỗng")
    void shouldDeleteLastRemainingAddressCleanly() throws Exception {
        User user = createCustomer("delete_last");
        UserAddress onlyAddr = saveAddressDirectly(user, "Địa chỉ Duy Nhất", "0901111111", true);

        entityManager.flush();
        entityManager.clear();

        UserPrincipal principal = toPrincipal(user);

        mockMvc.perform(delete("/api/addresses/" + onlyAddr.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(status().isNoContent());

        assertThat(userAddressRepository.findAllByUserIdOrdered(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("15. Chống IDOR: User B gọi PUT/PATCH/DELETE lên địa chỉ của User A đều nhận 404 và dữ liệu User A không đổi")
    void shouldPreventIdorWhenUserAccessesOrMutatesAnotherUsersAddress() throws Exception {
        User userA = createCustomer("idor_owner");
        User userB = createCustomer("idor_attacker");

        UserAddress addressOfA = saveAddressDirectly(userA, "Chủ sở hữu A", "0901111111", false);
        saveAddressDirectly(userA, "Mặc định của A", "0901111112", true);

        entityManager.flush();
        entityManager.clear();

        UserPrincipal principalB = toPrincipal(userB);
        AddressRequest maliciousUpdate = buildValidRequest("B Chiếm Quyền", "0909999999", true);

        mockMvc.perform(put("/api/addresses/" + addressOfA.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(principalB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousUpdate)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(patch("/api/addresses/" + addressOfA.getId() + "/default")
                        .with(SecurityMockMvcRequestPostProcessors.user(principalB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(delete("/api/addresses/" + addressOfA.getId())
                        .with(SecurityMockMvcRequestPostProcessors.user(principalB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        entityManager.flush();
        entityManager.clear();

        UserAddress unchanged = userAddressRepository.findById(addressOfA.getId()).orElseThrow();
        assertThat(unchanged.getReceiverName()).isEqualTo("Chủ sở hữu A");
        assertThat(unchanged.getUser().getId()).isEqualTo(userA.getId());
        assertThat(unchanged.getIsDefault()).isFalse();
    }

    @Test
    @DisplayName("16. Chống ghi đè userId từ body: Nếu client gửi kèm userId của người khác trong JSON, địa chỉ vẫn thuộc về user đang đăng nhập")
    void shouldIgnoreInjectedUserIdInRequestBody() throws Exception {
        User victim = createCustomer("victim_user");
        User loggedInUser = createCustomer("logged_in_user");

        entityManager.flush();
        entityManager.clear();

        UserPrincipal principal = toPrincipal(loggedInUser);
        String payloadWithInjectedUserId = String.format(
                "{\"userId\":%d,\"receiverName\":\"Người Nhận Thật\",\"phone\":\"0905556667\",\"province\":\"Đà Nẵng\",\"district\":\"Quận Hải Châu\",\"ward\":\"Phường Thạch Thang\",\"address\":\"24 Trần Phú\",\"isDefault\":true}",
                victim.getId()
        );

        mockMvc.perform(post("/api/addresses")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadWithInjectedUserId))
                .andExpect(status().isCreated());

        entityManager.flush();
        entityManager.clear();

        assertThat(userAddressRepository.findAllByUserIdOrdered(victim.getId())).isEmpty();
        List<UserAddress> loggedInAddresses = userAddressRepository.findAllByUserIdOrdered(loggedInUser.getId());
        assertThat(loggedInAddresses).hasSize(1);
        assertThat(loggedInAddresses.get(0).getReceiverName()).isEqualTo("Người Nhận Thật");
    }

    @Test
    @DisplayName("17. PUT, PATCH default và DELETE trả về 404 Not Found khi ID địa chỉ không tồn tại")
    void shouldReturn404WhenAddressIdDoesNotExist() throws Exception {
        User user = createCustomer("not_found_id");
        entityManager.flush();
        entityManager.clear();

        UserPrincipal principal = toPrincipal(user);
        AddressRequest updateRequest = buildValidRequest("Nguyễn Văn Không Tồn Tại", "0901234567", false);
        long nonExistentId = 99999999L;

        mockMvc.perform(put("/api/addresses/" + nonExistentId)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("Không tìm thấy địa chỉ")));

        mockMvc.perform(patch("/api/addresses/" + nonExistentId + "/default")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("Không tìm thấy địa chỉ")));

        mockMvc.perform(delete("/api/addresses/" + nonExistentId)
                        .with(SecurityMockMvcRequestPostProcessors.user(principal)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("Không tìm thấy địa chỉ")));
    }

    private User createCustomer(String tag) {
        long suffix = System.nanoTime();
        User user = User.builder()
                .role(customerRole)
                .name("Khách " + tag)
                .email("addr_" + tag + "_" + suffix + "@test.com")
                .phone("09" + String.format("%08d", Math.abs(suffix % 100_000_000L)))
                .passwordHash("hashed_password")
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build();
        return userRepository.save(user);
    }

    private UserPrincipal toPrincipal(User user) {
        User userWithRole = userRepository.findWithRoleById(user.getId()).orElseThrow();
        return new UserPrincipal(userWithRole);
    }

    private UserAddress saveAddressDirectly(User user, String receiverName, String phone, boolean isDefault) {
        UserAddress addr = UserAddress.builder()
                .user(user)
                .receiverName(receiverName)
                .phone(phone)
                .province("TP. Hồ Chí Minh")
                .district("Quận 1")
                .ward("Phường Bến Nghé")
                .address("Số 1 Lê Duẩn")
                .isDefault(isDefault)
                .build();
        return userAddressRepository.save(addr);
    }

    private AddressRequest buildValidRequest(String receiverName, String phone, Boolean isDefault) {
        return AddressRequest.builder()
                .receiverName(receiverName)
                .phone(phone)
                .province("TP. Hồ Chí Minh")
                .district("Quận 3")
                .ward("Phường Võ Thị Sáu")
                .address("200 Điện Biên Phủ")
                .isDefault(isDefault)
                .build();
    }
}
