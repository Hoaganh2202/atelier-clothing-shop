package com.shop.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.auth.dto.ChangePasswordRequest;
import com.shop.backend.auth.dto.LoginRequest;
import com.shop.backend.auth.dto.RegisterRequest;
import com.shop.backend.auth.dto.UpdateProfileRequest;
import com.shop.backend.security.JwtService;
import com.shop.backend.user.Gender;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
@Import(AuthIntegrationTest.RoleMappingTestController.class)
class AuthIntegrationTest {

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

    @RestController
    @RequestMapping("/api/test/roles")
    static class RoleMappingTestController {

        @GetMapping("/admin")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<String> adminOnly() {
            return ResponseEntity.ok("admin-granted");
        }

        @GetMapping("/staff")
        @PreAuthorize("hasRole('STAFF')")
        public ResponseEntity<String> staffOnly() {
            return ResponseEntity.ok("staff-granted");
        }

        @GetMapping("/customer")
        @PreAuthorize("hasRole('CUSTOMER')")
        public ResponseEntity<String> customerOnly() {
            return ResponseEntity.ok("customer-granted");
        }

        @GetMapping("/simulate-duplicate-key")
        public void simulateDuplicateKey() {
            java.sql.SQLException sqlEx = new java.sql.SQLException("Duplicate entry 'test@test.com' for key 'users.uk_email'", "23000", 1062);
            throw new org.springframework.dao.DataIntegrityViolationException("Unique constraint violation", sqlEx);
        }

        @GetMapping("/simulate-not-null-violation")
        public void simulateNotNullViolation() {
            java.sql.SQLException sqlEx = new java.sql.SQLException("Column 'name' cannot be null", "23000", 1048);
            throw new org.springframework.dao.DataIntegrityViolationException("Not null violation", sqlEx);
        }
    }

    @Test
    @DisplayName("Đăng ký thành công bằng email, trả về accessToken và thông tin user với role customer")
    void shouldRegisterSuccessfullyWithEmail() throws Exception {
        RegisterRequest request = new RegisterRequest("Nguyen Van A", "user.email@test.com", null, "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.user.name").value("Nguyen Van A"))
                .andExpect(jsonPath("$.user.email").value("user.email@test.com"))
                .andExpect(jsonPath("$.user.role").value("customer"));
    }

    @Test
    @DisplayName("Đăng ký thành công bằng fullName alias từ FE-Dk_DN")
    void shouldRegisterSuccessfullyWithFullNameAlias() throws Exception {
        String jsonPayload = """
                {
                    "fullName": "Alias User",
                    "email": "alias.user@test.com",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.name").value("Alias User"));
    }

    @Test
    @DisplayName("Đăng ký thành công bằng số điện thoại")
    void shouldRegisterSuccessfullyWithPhone() throws Exception {
        RegisterRequest request = new RegisterRequest("Tran Thi B", null, "0912345678", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.user.name").value("Tran Thi B"))
                .andExpect(jsonPath("$.user.phone").value("0912345678"))
                .andExpect(jsonPath("$.user.role").value("customer"));
    }

    @Test
    @DisplayName("Từ chối đăng ký khi email đã tồn tại (409 Conflict)")
    void shouldRejectRegistrationWhenEmailAlreadyExists() throws Exception {
        RegisterRequest first = new RegisterRequest("User One", "dup@test.com", null, "password123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk());

        RegisterRequest second = new RegisterRequest("User Two", "dup@test.com", null, "password123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Email đã được sử dụng")));
    }

    @Test
    @DisplayName("Từ chối đăng ký khi số điện thoại đã tồn tại (409 Conflict)")
    void shouldRejectRegistrationWhenPhoneAlreadyExists() throws Exception {
        RegisterRequest first = new RegisterRequest("User One", null, "0988776655", "password123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk());

        RegisterRequest second = new RegisterRequest("User Two", null, "0988776655", "password123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Số điện thoại đã được sử dụng")));
    }

    @Test
    @DisplayName("Từ chối đăng ký khi thiếu cả email và số điện thoại (400 Bad Request)")
    void shouldRejectRegistrationWhenNeitherEmailNorPhoneProvided() throws Exception {
        RegisterRequest request = new RegisterRequest("No Contact", null, null, "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Phải cung cấp ít nhất email hoặc số điện thoại")));
    }

    @Test
    @DisplayName("Từ chối đăng ký khi mật khẩu ngắn hơn 6 ký tự (400 Bad Request)")
    void shouldRejectRegistrationWhenPasswordTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest("Short Pass", "short@test.com", null, "12345");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password", containsString("từ 6 đến 72 ký tự")));
    }

    @Test
    @DisplayName("Từ chối đăng ký khi mật khẩu dài hơn 72 ký tự (400 Bad Request)")
    void shouldRejectRegistrationWhenPasswordTooLong() throws Exception {
        String longPassword = "a".repeat(73);
        RegisterRequest request = new RegisterRequest("Long Pass", "long@test.com", null, longPassword);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password", containsString("từ 6 đến 72 ký tự")));
    }

    @Test
    @DisplayName("Đăng nhập thành công với email và mật khẩu đúng")
    void shouldLoginSuccessfullyWithEmail() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Login User", "login.ok@test.com", null, "secret123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk());

        LoginRequest loginReq = new LoginRequest("login.ok@test.com", "secret123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.user.name").value("Login User"))
                .andExpect(jsonPath("$.user.email").value("login.ok@test.com"))
                .andExpect(jsonPath("$.user.role").value("customer"));
    }

    @Test
    @DisplayName("Đăng nhập thành công với số điện thoại")
    void shouldLoginSuccessfullyWithPhone() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Phone User", null, "0933445566", "secret123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk());

        LoginRequest loginReq = new LoginRequest("0933445566", "secret123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.user.name").value("Phone User"))
                .andExpect(jsonPath("$.user.phone").value("0933445566"));
    }

    @Test
    @DisplayName("Đăng nhập thất bại khi sai mật khẩu: thông báo lỗi chung, mã 401")
    void shouldFailLoginWhenPasswordIsWrong() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Wrong Pass User", "wrongpass@test.com", null, "secret123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk());

        LoginRequest loginReq = new LoginRequest("wrongpass@test.com", "incorrectPassword");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Email hoặc mật khẩu không chính xác"));
    }

    @Test
    @DisplayName("Đăng nhập thất bại khi tài khoản không tồn tại: thông báo lỗi chung giống hệt, mã 401")
    void shouldFailLoginWhenUserDoesNotExist() throws Exception {
        LoginRequest loginReq = new LoginRequest("nonexistent@test.com", "anyPassword");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Email hoặc mật khẩu không chính xác"));
    }

    @Test
    @DisplayName("Tài khoản blocked nhưng nhập sai mật khẩu -> 401 (không lộ trạng thái tài khoản)")
    void shouldReturn401WhenBlockedAccountEntersWrongPassword() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Blocked User Wrong Pass", "blocked.wrongpass@test.com", null, "secret123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail("blocked.wrongpass@test.com").orElseThrow();
        user.setStatus(UserStatus.BLOCKED);
        userRepository.save(user);

        LoginRequest loginReq = new LoginRequest("blocked.wrongpass@test.com", "wrongPassword");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Email hoặc mật khẩu không chính xác"));
    }

    @Test
    @DisplayName("Tài khoản blocked và nhập đúng mật khẩu -> 403 Forbidden")
    void shouldRejectLoginWhenAccountIsBlockedAndCredentialsCorrect() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Blocked User", "blocked@test.com", null, "secret123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail("blocked@test.com").orElseThrow();
        user.setStatus(UserStatus.BLOCKED);
        userRepository.save(user);

        LoginRequest loginReq = new LoginRequest("blocked@test.com", "secret123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message", containsString("khóa")));
    }

    @Test
    @DisplayName("GET /api/auth/me thành công khi kèm Bearer token hợp lệ")
    void shouldGetMeSuccessfullyWithBearerToken() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Me User", "me@test.com", null, "secret123");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String token = jsonNode.get("accessToken").asText();
        assertNotNull(token);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Me User"))
                .andExpect(jsonPath("$.email").value("me@test.com"))
                .andExpect(jsonPath("$.role").value("customer"));
    }

    @Test
    @DisplayName("GET /api/auth/me thất bại trả JSON 401 khi không truyền token")
    void shouldRejectGetMeWithoutTokenWithJson401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Chưa xác thực danh tính"));
    }

    // ──────────────────────────────────────────────────────────────
    // Kiểm tra ánh xạ vai trò: ROLE_ADMIN, ROLE_STAFF, ROLE_CUSTOMER
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ROLE_ADMIN: Admin truy cập endpoint admin -> 200 OK")
    void shouldAllowAdminAccessToAdminEndpoint() throws Exception {
        Role adminRole = roleRepository.findByName("admin").orElseThrow();
        User admin = User.builder()
                .name("Admin User")
                .email("admin@test.com")
                .passwordHash("hash")
                .role(adminRole)
                .status(UserStatus.ACTIVE)
                .dataSource(com.shop.backend.common.model.DataSourceType.WEBSITE)
                .build();
        userRepository.save(admin);

        String adminToken = jwtService.generateToken(admin);

        mockMvc.perform(get("/api/test/roles/admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-granted"));
    }

    @Test
    @DisplayName("ROLE_CUSTOMER: Customer truy cập endpoint admin -> 403 Forbidden với JSON lỗi chung")
    void shouldDenyCustomerAccessToAdminEndpointWithJson403() throws Exception {
        Role customerRole = roleRepository.findByName("customer").orElseThrow();
        User customer = User.builder()
                .name("Customer User")
                .email("cust@test.com")
                .passwordHash("hash")
                .role(customerRole)
                .status(UserStatus.ACTIVE)
                .dataSource(com.shop.backend.common.model.DataSourceType.WEBSITE)
                .build();
        userRepository.save(customer);

        String customerToken = jwtService.generateToken(customer);

        mockMvc.perform(get("/api/test/roles/admin")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Bạn không có quyền thực hiện thao tác này"));
    }

    @Test
    @DisplayName("ROLE_STAFF: Staff truy cập endpoint staff -> 200 OK")
    void shouldAllowStaffAccessToStaffEndpoint() throws Exception {
        Role staffRole = roleRepository.findByName("staff").orElseThrow();
        User staff = User.builder()
                .name("Staff User")
                .email("staff@test.com")
                .passwordHash("hash")
                .role(staffRole)
                .status(UserStatus.ACTIVE)
                .dataSource(com.shop.backend.common.model.DataSourceType.WEBSITE)
                .build();
        userRepository.save(staff);

        String staffToken = jwtService.generateToken(staff);

        mockMvc.perform(get("/api/test/roles/staff")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(content().string("staff-granted"));
    }

    @Test
    @DisplayName("ROLE_CUSTOMER: Customer truy cập endpoint customer -> 200 OK")
    void shouldAllowCustomerAccessToCustomerEndpoint() throws Exception {
        Role customerRole = roleRepository.findByName("customer").orElseThrow();
        User customer = User.builder()
                .name("Customer User 2")
                .email("cust2@test.com")
                .passwordHash("hash")
                .role(customerRole)
                .status(UserStatus.ACTIVE)
                .dataSource(com.shop.backend.common.model.DataSourceType.WEBSITE)
                .build();
        userRepository.save(customer);

        String customerToken = jwtService.generateToken(customer);

        mockMvc.perform(get("/api/test/roles/customer")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(content().string("customer-granted"));
    }

    @Test
    @DisplayName("Chưa xác thực truy cập endpoint admin -> 401 Unauthorized với JSON lỗi chung")
    void shouldDenyUnauthenticatedAccessWithJson401() throws Exception {
        mockMvc.perform(get("/api/test/roles/admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Chưa xác thực danh tính"));
    }

    @Test
    @DisplayName("Đăng nhập thành công với số điện thoại có khoảng trắng đầu/cuối được trim tự động")
    void shouldLoginSuccessfullyWithPhoneWithWhitespace() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Phone Trim User", null, "0911223344", "secret123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk());

        LoginRequest loginReq = new LoginRequest("   0911223344   ", "secret123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.user.name").value("Phone Trim User"))
                .andExpect(jsonPath("$.user.phone").value("0911223344"));
    }

    @Test
    @DisplayName("DataIntegrityViolationException do vi phạm unique constraint -> 409 Conflict")
    void shouldReturn409WhenDataIntegrityViolationIsUniqueConstraint() throws Exception {
        Role customerRole = roleRepository.findByName("customer").orElseThrow();
        User customer = User.builder()
                .name("Test User")
                .email("test.unique@test.com")
                .passwordHash("hash")
                .role(customerRole)
                .status(UserStatus.ACTIVE)
                .dataSource(com.shop.backend.common.model.DataSourceType.WEBSITE)
                .build();
        userRepository.save(customer);

        String token = jwtService.generateToken(customer);

        mockMvc.perform(get("/api/test/roles/simulate-duplicate-key")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Dữ liệu đã tồn tại trong hệ thống"));
    }

    @Test
    @DisplayName("DataIntegrityViolationException do NOT NULL hoặc lỗi khác -> 500 (không báo nhầm là trùng)")
    void shouldReturn500WhenDataIntegrityViolationIsNotUniqueConstraint() throws Exception {
        Role customerRole = roleRepository.findByName("customer").orElseThrow();
        User customer = User.builder()
                .name("Test User 2")
                .email("test.notnull@test.com")
                .passwordHash("hash")
                .role(customerRole)
                .status(UserStatus.ACTIVE)
                .dataSource(com.shop.backend.common.model.DataSourceType.WEBSITE)
                .build();
        userRepository.save(customer);

        String token = jwtService.generateToken(customer);

        mockMvc.perform(get("/api/test/roles/simulate-not-null-violation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Đã có lỗi hệ thống xảy ra, vui lòng thử lại sau"));
    }

    // ──────────────────────────────────────────────────────────────
    // Profile Management Tests
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/auth/me trả về đầy đủ gender và birthDate khi user đã cập nhật")
    void shouldGetMeWithGenderAndBirthDate() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Gender User", "gender@test.com", null, "secret123");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String token = jsonNode.get("accessToken").asText();

        User user = userRepository.findByEmail("gender@test.com").orElseThrow();
        user.setGender(Gender.FEMALE);
        user.setBirthDate(LocalDate.of(1998, 5, 20));
        userRepository.save(user);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gender User"))
                .andExpect(jsonPath("$.email").value("gender@test.com"))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.birthDate").value("1998-05-20"))
                .andExpect(jsonPath("$.role").value("customer"));
    }

    @Test
    @DisplayName("PUT /api/auth/profile cập nhật họ tên, sđt, giới tính, ngày sinh thành công")
    void shouldUpdateProfileSuccessfully() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Old Name", "profile.update@test.com", null, "secret123");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String token = jsonNode.get("accessToken").asText();

        UpdateProfileRequest updateReq = new UpdateProfileRequest(
                "New Name Updated",
                "0987654321",
                Gender.MALE,
                LocalDate.of(1995, 10, 15)
        );

        mockMvc.perform(put("/api/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name Updated"))
                .andExpect(jsonPath("$.phone").value("0987654321"))
                .andExpect(jsonPath("$.gender").value("MALE"))
                .andExpect(jsonPath("$.birthDate").value("1995-10-15"))
                .andExpect(jsonPath("$.email").value("profile.update@test.com"))
                .andExpect(jsonPath("$.role").value("customer"));

        User updatedUser = userRepository.findByEmail("profile.update@test.com").orElseThrow();
        assertEquals("New Name Updated", updatedUser.getName());
        assertEquals("0987654321", updatedUser.getPhone());
        assertEquals(Gender.MALE, updatedUser.getGender());
        assertEquals(LocalDate.of(1995, 10, 15), updatedUser.getBirthDate());
    }

    @Test
    @DisplayName("PUT /api/auth/profile từ chối khi họ tên để trống (400 Bad Request)")
    void shouldRejectProfileUpdateWhenNameIsBlank() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Blank Name Test", "blank.name@test.com", null, "secret123");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("accessToken").asText();

        UpdateProfileRequest updateReq = new UpdateProfileRequest("   ", "0987654321", Gender.MALE, null);

        mockMvc.perform(put("/api/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("không hợp lệ")));
    }

    @Test
    @DisplayName("PUT /api/auth/profile từ chối khi số điện thoại trùng với tài khoản khác (409 Conflict)")
    void shouldRejectProfileUpdateWhenPhoneConflictsWithAnotherUser() throws Exception {
        RegisterRequest user1 = new RegisterRequest("User One", null, "0912345678", "secret123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user1)))
                .andExpect(status().isOk());

        RegisterRequest user2 = new RegisterRequest("User Two", "user2@test.com", null, "secret123");
        MvcResult user2Result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user2)))
                .andExpect(status().isOk())
                .andReturn();

        String token2 = objectMapper.readTree(user2Result.getResponse().getContentAsString()).get("accessToken").asText();

        UpdateProfileRequest updateReq = new UpdateProfileRequest("User Two Updated", "0912345678", null, null);

        mockMvc.perform(put("/api/auth/profile")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("Số điện thoại đã được sử dụng")));
    }

    @Test
    @DisplayName("PUT /api/auth/profile cho phép cập nhật khi giữ nguyên số điện thoại của chính mình")
    void shouldAllowProfileUpdateKeepingSamePhone() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Keep Phone", "keep.phone@test.com", "0933998877", "secret123");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("accessToken").asText();

        UpdateProfileRequest updateReq = new UpdateProfileRequest("Keep Phone Renamed", "0933998877", Gender.OTHER, null);

        mockMvc.perform(put("/api/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Keep Phone Renamed"))
                .andExpect(jsonPath("$.phone").value("0933998877"));
    }

    @Test
    @DisplayName("PUT /api/auth/profile từ chối khi ngày sinh trong tương lai (400 Bad Request)")
    void shouldRejectProfileUpdateWhenBirthDateInFuture() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Future Birth", "future.birth@test.com", null, "secret123");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("accessToken").asText();

        UpdateProfileRequest updateReq = new UpdateProfileRequest(
                "Future Birth",
                null,
                Gender.MALE,
                LocalDate.now().plusDays(2)
        );

        mockMvc.perform(put("/api/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.birthDate", containsString("tương lai")));
    }

    @Test
    @DisplayName("PUT /api/auth/profile từ chối khi gender không hợp lệ (400 Bad Request)")
    void shouldRejectProfileUpdateWhenGenderIsInvalid() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Invalid Gender", "invalid.gender@test.com", null, "secret123");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("accessToken").asText();

        String payload = """
                {
                    "name": "Invalid Gender User",
                    "gender": "ALIEN"
                }
                """;

        mockMvc.perform(put("/api/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("không hợp lệ hoặc sai định dạng")));
    }

    @Test
    @DisplayName("PUT /api/auth/profile từ chối khi chưa xác thực (401 Unauthorized)")
    void shouldRejectProfileUpdateWithoutAuthentication() throws Exception {
        UpdateProfileRequest updateReq = new UpdateProfileRequest("Anonymous", null, null, null);

        mockMvc.perform(put("/api/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Chưa xác thực danh tính"));
    }

    @Test
    @DisplayName("PUT /api/auth/profile không cho phép thay đổi email, role hoặc mật khẩu qua request")
    void shouldNotAllowProfileUpdateToChangeEmailRoleOrPassword() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Secure User", "secure@test.com", null, "originalPass123");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("accessToken").asText();
        User beforeUser = userRepository.findByEmail("secure@test.com").orElseThrow();
        String originalPasswordHash = beforeUser.getPasswordHash();

        String maliciousPayload = """
                {
                    "name": "Legit Name",
                    "email": "hacked@test.com",
                    "role": "admin",
                    "password": "hackedPassword",
                    "passwordHash": "fakeHash",
                    "status": "blocked"
                }
                """;

        mockMvc.perform(put("/api/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Legit Name"))
                .andExpect(jsonPath("$.email").value("secure@test.com"))
                .andExpect(jsonPath("$.role").value("customer"));

        User afterUser = userRepository.findByEmail("secure@test.com").orElseThrow();
        assertEquals("Legit Name", afterUser.getName());
        assertEquals("secure@test.com", afterUser.getEmail());
        assertEquals("customer", afterUser.getRole().getName());
        assertEquals(originalPasswordHash, afterUser.getPasswordHash());
        assertEquals(UserStatus.ACTIVE, afterUser.getStatus());
        assertFalse(userRepository.existsByEmail("hacked@test.com"));
    }

    // ──────────────────────────────────────────────────────────────
    // Change Password Tests
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/change-password đổi mật khẩu thành công với mật khẩu hiện tại đúng")
    void shouldChangePasswordSuccessfully() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Pass User", "pass.user@test.com", null, "oldPassword123");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("accessToken").asText();

        ChangePasswordRequest changeReq = new ChangePasswordRequest("oldPassword123", "newPassword456");

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đổi mật khẩu thành công"));

        // Login with old password must fail
        LoginRequest oldLogin = new LoginRequest("pass.user@test.com", "oldPassword123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLogin)))
                .andExpect(status().isUnauthorized());

        // Login with new password must succeed
        LoginRequest newLogin = new LoginRequest("pass.user@test.com", "newPassword456");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));

        // Password hash in DB must be BCrypt encoded and not plain text
        User user = userRepository.findByEmail("pass.user@test.com").orElseThrow();
        assertTrue(user.getPasswordHash().startsWith("$2a$") || user.getPasswordHash().startsWith("$2b$"));
        assertTrue(passwordEncoder.matches("newPassword456", user.getPasswordHash()));
        assertFalse(user.getPasswordHash().contains("newPassword456"));
    }

    @Test
    @DisplayName("POST /api/auth/change-password từ chối khi mật khẩu hiện tại không chính xác (400 Bad Request)")
    void shouldRejectChangePasswordWhenCurrentPasswordIncorrect() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Pass User 2", "pass.wrong@test.com", null, "correctOld123");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("accessToken").asText();

        ChangePasswordRequest changeReq = new ChangePasswordRequest("wrongOldPassword", "newPassword456");

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không chính xác"));
    }

    @Test
    @DisplayName("POST /api/auth/change-password từ chối khi mật khẩu mới ngắn hơn 6 ký tự (400 Bad Request)")
    void shouldRejectChangePasswordWhenNewPasswordTooShort() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Pass User 3", "pass.short@test.com", null, "correctOld123");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("accessToken").asText();

        ChangePasswordRequest changeReq = new ChangePasswordRequest("correctOld123", "12345");

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.newPassword", containsString("từ 6 đến 72 ký tự")));
    }

    @Test
    @DisplayName("POST /api/auth/change-password từ chối khi chưa xác thực (401 Unauthorized)")
    void shouldRejectChangePasswordWithoutAuthentication() throws Exception {
        ChangePasswordRequest changeReq = new ChangePasswordRequest("oldPass123", "newPass456");

        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Chưa xác thực danh tính"));
    }

    @Test
    @DisplayName("POST /api/auth/register chấp nhận mật khẩu ASCII đúng 72 byte")
    void shouldAcceptRegistrationWithAscii72BytePassword() throws Exception {
        String pass72 = "A".repeat(72);
        RegisterRequest registerReq = new RegisterRequest("Pass 72 User", "pass72@test.com", null, pass72);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));

        LoginRequest loginReq = new LoginRequest("pass72@test.com", pass72);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/auth/register từ chối mật khẩu ASCII 73 byte (vượt quá 72 byte)")
    void shouldRejectRegistrationWithAscii73BytePassword() throws Exception {
        String pass73 = "A".repeat(73);
        RegisterRequest registerReq = new RegisterRequest("Pass 73 User", "pass73@test.com", null, pass73);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/auth/register từ chối mật khẩu Unicode <= 72 ký tự nhưng > 72 byte UTF-8")
    void shouldRejectRegistrationWithUnicodePasswordExceeding72Bytes() throws Exception {
        // 30 ký tự 'ế' = 30 ký tự (< 72 ký tự), nhưng 30 * 3 = 90 byte UTF-8 (> 72 byte)
        String passUnicodeOver72Bytes = "ế".repeat(30);
        RegisterRequest registerReq = new RegisterRequest("Unicode Over User", "unicode.over@test.com", null, passUnicodeOver72Bytes);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(content().string(containsString("72 byte")));
    }

    @Test
    @DisplayName("POST /api/auth/register chấp nhận mật khẩu Unicode <= 72 byte UTF-8")
    void shouldAcceptRegistrationWithUnicodePasswordUnder72Bytes() throws Exception {
        // "MậtKhẩu@123" gồm 11 ký tự, 15 byte UTF-8 (< 72 byte)
        String validUnicodePass = "MậtKhẩu@123";
        RegisterRequest registerReq = new RegisterRequest("Unicode Valid User", "unicode.valid@test.com", null, validUnicodePass);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));

        LoginRequest loginReq = new LoginRequest("unicode.valid@test.com", validUnicodePass);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/auth/change-password từ chối mật khẩu mới Unicode vượt quá 72 byte UTF-8")
    void shouldRejectChangePasswordWithUnicodePasswordExceeding72Bytes() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Change Pass User", "change.unicode@test.com", null, "validOldPass123");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("accessToken").asText();

        // 30 ký tự 'ế' = 90 byte UTF-8 (> 72 byte)
        String passUnicodeOver = "ế".repeat(30);
        ChangePasswordRequest changeReq = new ChangePasswordRequest("validOldPass123", passUnicodeOver);

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(content().string(containsString("72 byte")));
    }

    @Test
    @DisplayName("POST /api/auth/change-password chấp nhận mật khẩu mới Unicode <= 72 byte UTF-8")
    void shouldAcceptChangePasswordWithUnicodePasswordUnder72Bytes() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Change Pass Valid", "change.valid@test.com", null, "validOldPass123");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("accessToken").asText();

        String validNewUnicodePass = "MậtKhẩuMới#2026";
        ChangePasswordRequest changeReq = new ChangePasswordRequest("validOldPass123", validNewUnicodePass);

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đổi mật khẩu thành công"));

        LoginRequest loginReq = new LoginRequest("change.valid@test.com", validNewUnicodePass);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/auth/register từ chối an toàn khi password null hoặc rỗng (400 Bad Request, không NPE)")
    void shouldRejectRegistrationWhenPasswordNullOrEmpty() throws Exception {
        RegisterRequest nullReq = new RegisterRequest("Null User", "nullpass@test.com", null, null);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        RegisterRequest emptyReq = new RegisterRequest("Empty User", "emptypass@test.com", null, "");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/auth/change-password từ chối an toàn khi newPassword null hoặc rỗng (400 Bad Request, không NPE)")
    void shouldRejectChangePasswordWhenNewPasswordNullOrEmpty() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("Null Change User", "nullchange@test.com", null, "validOldPass123");
        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("accessToken").asText();

        ChangePasswordRequest nullNewPass = new ChangePasswordRequest("validOldPass123", null);
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullNewPass)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        ChangePasswordRequest emptyNewPass = new ChangePasswordRequest("validOldPass123", "");
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyNewPass)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
