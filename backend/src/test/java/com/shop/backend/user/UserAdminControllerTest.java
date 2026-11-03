package com.shop.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.security.JwtService;
import com.shop.backend.security.UserPrincipal;
import com.shop.backend.user.dto.UpdateCustomerStatusRequest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class UserAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private Role customerRole;
    private Role adminRole;
    private Role staffRole;

    @BeforeEach
    void setUp() {
        customerRole = roleRepository.findByName("customer")
                .orElseThrow(() -> new IllegalStateException("Vai trò 'customer' chưa tồn tại trong DB test"));
        adminRole = roleRepository.findByName("admin")
                .orElseThrow(() -> new IllegalStateException("Vai trò 'admin' chưa tồn tại trong DB test"));
        staffRole = roleRepository.findByName("staff")
                .orElseThrow(() -> new IllegalStateException("Vai trò 'staff' chưa tồn tại trong DB test"));
    }

    @Test
    @DisplayName("1. Không có token truy cập các endpoint quản lý khách hàng trả về 401 Unauthorized")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        UpdateCustomerStatusRequest statusReq = UpdateCustomerStatusRequest.builder()
                .status(UserStatus.BLOCKED)
                .build();

        mockMvc.perform(get("/api/admin/customers"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/customers/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/admin/customers/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("2. Token có vai trò CUSTOMER truy cập các endpoint quản lý khách hàng trả về 403 Forbidden")
    void shouldReturn403WhenAuthenticatedAsCustomer() throws Exception {
        long suffix = System.currentTimeMillis();
        User customer = createAndSaveUser("Khách Hàng " + suffix, "cust_403_" + suffix + "@test.com", "0901112233", customerRole, UserStatus.ACTIVE);
        String customerToken = jwtService.generateToken(customer);

        UpdateCustomerStatusRequest statusReq = UpdateCustomerStatusRequest.builder()
                .status(UserStatus.BLOCKED)
                .build();

        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/customers/" + customer.getId())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/customers/" + customer.getId() + "/status")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. Token có vai trò STAFF truy cập các endpoint quản lý khách hàng trả về 403 Forbidden")
    void shouldReturn403WhenAuthenticatedAsStaff() throws Exception {
        long suffix = System.currentTimeMillis();
        User staff = createAndSaveUser("Nhân Viên " + suffix, "staff_403_" + suffix + "@test.com", "0908887766", staffRole, UserStatus.ACTIVE);
        String staffToken = jwtService.generateToken(staff);

        UpdateCustomerStatusRequest statusReq = UpdateCustomerStatusRequest.builder()
                .status(UserStatus.BLOCKED)
                .build();

        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/customers/" + staff.getId())
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/admin/customers/" + staff.getId() + "/status")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("4. GET /api/admin/customers trả về danh sách có phân trang đúng cấu trúc PageResponse")
    void shouldReturnPaginatedCustomerList() throws Exception {
        long suffix = System.currentTimeMillis();
        createAndSaveUser("Khách Phân Trang 1 " + suffix, "page1_" + suffix + "@test.com", "0911000001", customerRole, UserStatus.ACTIVE);
        createAndSaveUser("Khách Phân Trang 2 " + suffix, "page2_" + suffix + "@test.com", "0911000002", customerRole, UserStatus.ACTIVE);
        createAndSaveUser("Khách Phân Trang 3 " + suffix, "page3_" + suffix + "@test.com", "0911000003", customerRole, UserStatus.ACTIVE);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/customers")
                        .param("keyword", String.valueOf(suffix))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("5. GET /api/admin/customers chỉ trả tài khoản khách hàng, loại bỏ tài khoản admin và staff")
    void shouldExcludeAdminAndStaffFromCustomerList() throws Exception {
        long suffix = System.currentTimeMillis();
        User customer = createAndSaveUser("Khách Thường " + suffix, "cust_only_" + suffix + "@test.com", "0912000001", customerRole, UserStatus.ACTIVE);
        User adminUser = createAndSaveUser("Quản Trị Viên " + suffix, "admin_excl_" + suffix + "@test.com", "0912999999", adminRole, UserStatus.ACTIVE);
        User staffUser = createAndSaveUser("Nhân Viên Kho " + suffix, "staff_excl_" + suffix + "@test.com", "0912888888", staffRole, UserStatus.ACTIVE);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/customers")
                        .param("keyword", String.valueOf(suffix))
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].id", hasItem(customer.getId().intValue())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(adminUser.getId().intValue()))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(staffUser.getId().intValue()))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("6. GET /api/admin/customers lọc theo keyword (họ tên, email, số điện thoại, không phân biệt hoa thường)")
    void shouldFilterCustomersByKeywordCaseInsensitive() throws Exception {
        long suffix = System.currentTimeMillis();
        User targetByName = createAndSaveUser("Lê Hoàng Đặc Biệt " + suffix, "normal1_" + suffix + "@test.com", "0922111001", customerRole, UserStatus.ACTIVE);
        User targetByEmail = createAndSaveUser("Phạm Bình Thường " + suffix, "special.vip." + suffix + "@shop.vn", "0922111002", customerRole, UserStatus.ACTIVE);
        User targetByPhone = createAndSaveUser("Võ Minh Tuấn " + suffix, "normal2_" + suffix + "@test.com", "0988777666", customerRole, UserStatus.ACTIVE);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/customers")
                        .param("keyword", "  ĐẶC BIỆT " + suffix + "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(targetByName.getId()));

        mockMvc.perform(get("/api/admin/customers")
                        .param("keyword", "SPECIAL.VIP." + suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(targetByEmail.getId()));

        mockMvc.perform(get("/api/admin/customers")
                        .param("keyword", "0988777666"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(targetByPhone.getId()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("7. GET /api/admin/customers escape ký tự đặc biệt của LIKE (%, _, \\) không trả về toàn bộ bảng")
    void shouldEscapeLikeWildcardsInKeywordSearch() throws Exception {
        long suffix = System.currentTimeMillis();
        createAndSaveUser("Nguyễn Văn Bình Thường " + suffix, "normal_like_" + suffix + "@test.com", "0923000111", customerRole, UserStatus.ACTIVE);
        User literalPercentUser = createAndSaveUser("Khách 100% Thật " + suffix, "percent_" + suffix + "@test.com", "0923000222", customerRole, UserStatus.ACTIVE);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/customers")
                        .param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(literalPercentUser.getId()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("8. GET /api/admin/customers lọc theo trạng thái tài khoản (status = ACTIVE / BLOCKED)")
    void shouldFilterCustomersByStatus() throws Exception {
        long suffix = System.currentTimeMillis();
        User activeUser = createAndSaveUser("Khách Hoạt Động " + suffix, "active_" + suffix + "@test.com", "0924000111", customerRole, UserStatus.ACTIVE);
        User blockedUser = createAndSaveUser("Khách Đã Khóa " + suffix, "blocked_" + suffix + "@test.com", "0924000222", customerRole, UserStatus.BLOCKED);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/customers")
                        .param("keyword", String.valueOf(suffix))
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(activeUser.getId()))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));

        mockMvc.perform(get("/api/admin/customers")
                        .param("keyword", String.valueOf(suffix))
                        .param("status", "BLOCKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(blockedUser.getId()))
                .andExpect(jsonPath("$.content[0].status").value("BLOCKED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("9. GET /api/admin/customers/{id} trả về chi tiết hồ sơ khách hàng thành công")
    void shouldGetCustomerDetailSuccessfully() throws Exception {
        long suffix = System.currentTimeMillis();
        User customer = User.builder()
                .role(customerRole)
                .name("Đặng Thu Thảo " + suffix)
                .email("thuthao_" + suffix + "@test.com")
                .phone("0933445566")
                .passwordHash("secret_hash_value")
                .gender(Gender.FEMALE)
                .birthDate(LocalDate.of(1998, 5, 20))
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build();
        customer = userRepository.save(customer);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/customers/" + customer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.getId()))
                .andExpect(jsonPath("$.name").value("Đặng Thu Thảo " + suffix))
                .andExpect(jsonPath("$.email").value("thuthao_" + suffix + "@test.com"))
                .andExpect(jsonPath("$.phone").value("0933445566"))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.birthDate").value("1998-05-20"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.dataSource").value("WEBSITE"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("10. GET /api/admin/customers/{id} trả về 404 khi ID không tồn tại hoặc tài khoản không phải khách hàng")
    void shouldReturn404WhenCustomerNotFoundOrTargetIsAdmin() throws Exception {
        long suffix = System.currentTimeMillis();
        User adminUser = createAndSaveUser("Admin Hidden " + suffix, "admin_hidden_" + suffix + "@test.com", "0933999888", adminRole, UserStatus.ACTIVE);

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/admin/customers/99999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Không tìm thấy khách hàng")));

        mockMvc.perform(get("/api/admin/customers/" + adminUser.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Không tìm thấy khách hàng")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("11. Danh sách và chi tiết khách hàng tuyệt đối không trả về passwordHash hoặc trường nhạy cảm")
    void shouldNeverExposePasswordHashInListOrDetail() throws Exception {
        long suffix = System.currentTimeMillis();
        User customer = createAndSaveUser("Khách Bảo Mật " + suffix, "sec_cust_" + suffix + "@test.com", "0935000111", customerRole, UserStatus.ACTIVE);

        entityManager.flush();
        entityManager.clear();

        String listJson = mockMvc.perform(get("/api/admin/customers")
                        .param("keyword", String.valueOf(suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(listJson).doesNotContain("passwordHash", "password_hash", "secret_hash_value");

        String detailJson = mockMvc.perform(get("/api/admin/customers/" + customer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(detailJson).doesNotContain("passwordHash", "password_hash", "secret_hash_value");
    }

    @Test
    @DisplayName("12. PATCH /api/admin/customers/{id}/status đổi trạng thái khách hàng thành công (ACTIVE <-> BLOCKED)")
    void shouldUpdateCustomerStatusSuccessfully() throws Exception {
        long suffix = System.currentTimeMillis();
        User admin = createAndSaveUser("Admin Action " + suffix, "admin_act_" + suffix + "@test.com", "0944000111", adminRole, UserStatus.ACTIVE);
        User customer = createAndSaveUser("Khách Cần Khóa " + suffix, "lock_cust_" + suffix + "@test.com", "0944000333", customerRole, UserStatus.ACTIVE);

        entityManager.flush();
        entityManager.clear();

        UserPrincipal adminPrincipal = new UserPrincipal(userRepository.findWithRoleById(admin.getId()).orElseThrow());

        UpdateCustomerStatusRequest blockRequest = UpdateCustomerStatusRequest.builder()
                .status(UserStatus.BLOCKED)
                .build();

        mockMvc.perform(patch("/api/admin/customers/" + customer.getId() + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blockRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.getId()))
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        entityManager.flush();
        entityManager.clear();
        assertThat(userRepository.findById(customer.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.BLOCKED);

        UpdateCustomerStatusRequest activateRequest = UpdateCustomerStatusRequest.builder()
                .status(UserStatus.ACTIVE)
                .build();

        mockMvc.perform(patch("/api/admin/customers/" + customer.getId() + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("13. PATCH /api/admin/customers/{id}/status chặn admin tự khóa chính tài khoản đang đăng nhập (400 Bad Request)")
    void shouldPreventAdminFromLockingOwnAccount() throws Exception {
        long suffix = System.currentTimeMillis();
        User admin = createAndSaveUser("Admin Tự Khóa " + suffix, "self_lock_" + suffix + "@test.com", "0945000111", adminRole, UserStatus.ACTIVE);

        entityManager.flush();
        entityManager.clear();

        UserPrincipal adminPrincipal = new UserPrincipal(userRepository.findWithRoleById(admin.getId()).orElseThrow());
        UpdateCustomerStatusRequest blockRequest = UpdateCustomerStatusRequest.builder()
                .status(UserStatus.BLOCKED)
                .build();

        mockMvc.perform(patch("/api/admin/customers/" + admin.getId() + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blockRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("tài khoản đang đăng nhập")));
    }

    @Test
    @DisplayName("14. PATCH /api/admin/customers/{id}/status chặn thao tác lên tài khoản admin/staff khác (400 Bad Request)")
    void shouldPreventModifyingNonCustomerAccount() throws Exception {
        long suffix = System.currentTimeMillis();
        User currentAdmin = createAndSaveUser("Current Admin " + suffix, "curr_adm_" + suffix + "@test.com", "0946000111", adminRole, UserStatus.ACTIVE);
        User otherAdmin = createAndSaveUser("Other Admin " + suffix, "other_adm_" + suffix + "@test.com", "0946000222", adminRole, UserStatus.ACTIVE);

        entityManager.flush();
        entityManager.clear();

        UserPrincipal adminPrincipal = new UserPrincipal(userRepository.findWithRoleById(currentAdmin.getId()).orElseThrow());
        UpdateCustomerStatusRequest blockRequest = UpdateCustomerStatusRequest.builder()
                .status(UserStatus.BLOCKED)
                .build();

        mockMvc.perform(patch("/api/admin/customers/" + otherAdmin.getId() + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blockRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("tài khoản khách hàng")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("15. GET /api/admin/customers trả về 400 Bad Request khi trường sort không hợp lệ")
    void shouldReturn400WhenSortFieldIsInvalid() throws Exception {
        mockMvc.perform(get("/api/admin/customers")
                        .param("sort", "passwordHash,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Trường sắp xếp không hợp lệ")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("16. Trả về 400 Bad Request khi truyền giá trị status không hợp lệ ở query hoặc body")
    void shouldReturn400WhenStatusValueIsInvalidOrMissing() throws Exception {
        long suffix = System.currentTimeMillis();
        User customer = createAndSaveUser("Khách Validate " + suffix, "val_cust_" + suffix + "@test.com", "0947000111", customerRole, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/admin/customers")
                        .param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/admin/customers/" + customer.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"INVALID_STATUS\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/admin/customers/" + customer.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").isNotEmpty());
    }

    @Test
    @DisplayName("17. Khóa tài khoản vô hiệu hóa ngay JWT đang còn hạn trên /api/auth/me (401) và chặn đăng nhập mới (403)")
    void shouldInvalidateActiveJwtOnAuthMeAndRejectLoginWhenCustomerIsLocked() throws Exception {
        long suffix = System.currentTimeMillis();
        String rawPassword = "Password@123";
        String customerEmail = "active_to_locked_" + suffix + "@test.com";

        User admin = createAndSaveUser("Admin Lock Enforcer " + suffix, "admin_enforcer_" + suffix + "@test.com", "0948000111", adminRole, UserStatus.ACTIVE);
        User customer = User.builder()
                .role(customerRole)
                .name("Khách Kiểm Tra Token " + suffix)
                .email(customerEmail)
                .phone("0948000222")
                .passwordHash(passwordEncoder.encode(rawPassword))
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build();
        customer = userRepository.save(customer);

        entityManager.flush();
        entityManager.clear();

        String customerToken = jwtService.generateToken(customer);
        String adminToken = jwtService.generateToken(admin);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.getId()))
                .andExpect(jsonPath("$.email").value(customerEmail));

        UpdateCustomerStatusRequest blockRequest = UpdateCustomerStatusRequest.builder()
                .status(UserStatus.BLOCKED)
                .build();

        mockMvc.perform(patch("/api/admin/customers/" + customer.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blockRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        String loginPayload = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", customerEmail, rawPassword);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message", containsString("khóa")));
    }

    private User createAndSaveUser(String name, String email, String phone, Role role, UserStatus status) {
        User user = User.builder()
                .role(role)
                .name(name)
                .email(email)
                .phone(phone)
                .passwordHash("secret_hash_value")
                .status(status)
                .dataSource(DataSourceType.WEBSITE)
                .build();
        return userRepository.save(user);
    }
}
