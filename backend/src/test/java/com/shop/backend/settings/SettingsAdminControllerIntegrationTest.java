package com.shop.backend.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.auth.dto.LoginRequest;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.security.JwtService;
import com.shop.backend.settings.dto.ChangePasswordRequest;
import com.shop.backend.settings.dto.StoreSettingsRequest;
import com.shop.backend.settings.dto.UpdateAdminAccountRequest;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SettingsAdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StoreSettingsRepository storeSettingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User adminUser;
    private User staffUser;
    private User customerUser;
    private String adminToken;
    private String staffToken;
    private String customerToken;

    private final List<Path> filesToCleanUp = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName("admin")
                .orElseGet(() -> roleRepository.save(new Role(null, "admin")));
        Role staffRole = roleRepository.findByName("staff")
                .orElseGet(() -> roleRepository.save(new Role(null, "staff")));
        Role customerRole = roleRepository.findByName("customer")
                .orElseGet(() -> roleRepository.save(new Role(null, "customer")));

        String runId = UUID.randomUUID().toString().substring(0, 8);
        adminUser = userRepository.save(User.builder()
                .name("Admin Settings Test")
                .email("admin_settings_" + runId + "@shop.com")
                .phone("090" + runId.replaceAll("[^0-9]", "1").substring(0, 7))
                .passwordHash(passwordEncoder.encode("OldPassword123!"))
                .role(adminRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        staffUser = userRepository.save(User.builder()
                .name("Staff Settings Test")
                .email("staff_settings_" + runId + "@shop.com")
                .phone("092" + runId.replaceAll("[^0-9]", "3").substring(0, 7))
                .passwordHash(passwordEncoder.encode("StaffPassword123!"))
                .role(staffRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        customerUser = userRepository.save(User.builder()
                .name("Customer Settings Test")
                .email("customer_settings_" + runId + "@shop.com")
                .phone("091" + runId.replaceAll("[^0-9]", "2").substring(0, 7))
                .passwordHash(passwordEncoder.encode("Customer123!"))
                .role(customerRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());

        adminToken = "Bearer " + jwtService.generateToken(adminUser);
        staffToken = "Bearer " + jwtService.generateToken(staffUser);
        customerToken = "Bearer " + jwtService.generateToken(customerUser);
    }

    @AfterEach
    void tearDown() {
        if (customerUser != null && customerUser.getId() != null) {
            userRepository.deleteById(customerUser.getId());
        }
        if (staffUser != null && staffUser.getId() != null) {
            userRepository.deleteById(staffUser.getId());
        }
        if (adminUser != null && adminUser.getId() != null) {
            userRepository.deleteById(adminUser.getId());
        }

        // Restore default settings to prevent polluting other integration tests
        storeSettingsRepository.findById(1L).ifPresent(s -> {
            s.setStoreName("Closet Shop");
            s.setLegalName("Công ty TNHH Thời trang Closet");
            s.setTaxCode("0101234567");
            s.setAddress("123 Đường Cầu Giấy, Phường Quan Hoa, Quận Cầu Giấy, Hà Nội");
            s.setPhone("0987654321");
            s.setEmail("contact@closetshop.com");
            s.setLogoUrl("/uploads/products/logo.png");
            s.setLowStockThreshold(10);
            s.setDefaultShippingFee(new BigDecimal("30000.00"));
            s.setFreeShippingThreshold(new BigDecimal("500000.00"));
            s.setUpdatedBy("system");
            storeSettingsRepository.save(s);
        });

        // Clean up created test files
        for (Path path : filesToCleanUp) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("GET /api/admin/settings/store trả đúng cấu hình mặc định (id = 1)")
    void getStoreSettings_shouldReturnDefaultSettings() throws Exception {
        mockMvc.perform(get("/api/admin/settings/store")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeName").isNotEmpty())
                .andExpect(jsonPath("$.lowStockThreshold").isNumber())
                .andExpect(jsonPath("$.defaultShippingFee").isNumber())
                .andExpect(jsonPath("$.freeShippingThreshold").isNumber());
    }

    @Test
    @DisplayName("PUT /api/admin/settings/store cập nhật thành công và dữ liệu được lưu")
    void updateStoreSettings_shouldUpdateAndPersist() throws Exception {
        StoreSettingsRequest request = StoreSettingsRequest.builder()
                .storeName("Closet Boutique Hà Nội")
                .legalName("Công ty Cổ phần Thời trang Closet Việt Nam")
                .taxCode("0101234567")
                .address("456 Phố Cầu Giấy, Hà Nội")
                .phone("0912345678")
                .email("support@closetboutique.vn")
                .logoUrl("/uploads/store/logo-new.png")
                .lowStockThreshold(15)
                .defaultShippingFee(35000L)
                .freeShippingThreshold(600000L)
                .build();

        mockMvc.perform(put("/api/admin/settings/store")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeName").value("Closet Boutique Hà Nội"))
                .andExpect(jsonPath("$.legalName").value("Công ty Cổ phần Thời trang Closet Việt Nam"))
                .andExpect(jsonPath("$.taxCode").value("0101234567"))
                .andExpect(jsonPath("$.lowStockThreshold").value(15))
                .andExpect(jsonPath("$.defaultShippingFee").value(35000))
                .andExpect(jsonPath("$.freeShippingThreshold").value(600000))
                .andExpect(jsonPath("$.updatedBy").value(adminUser.getEmail()));

        StoreSettings persisted = storeSettingsRepository.findById(1L).orElseThrow();
        assertThat(persisted.getStoreName()).isEqualTo("Closet Boutique Hà Nội");
        assertThat(persisted.getLowStockThreshold()).isEqualTo(15);
        assertThat(persisted.getDefaultShippingFee()).isEqualByComparingTo(new BigDecimal("35000.00"));
        assertThat(persisted.getFreeShippingThreshold()).isEqualByComparingTo(new BigDecimal("600000.00"));
    }

    @Test
    @DisplayName("Validation: MST sai, email sai, ngưỡng âm trả 400 kèm thông báo tiếng Việt")
    void updateStoreSettings_validationErrors_shouldReturn400() throws Exception {
        StoreSettingsRequest invalidRequest = StoreSettingsRequest.builder()
                .storeName("  ")
                .taxCode("INVALID_TAX")
                .email("not-an-email")
                .lowStockThreshold(-5)
                .defaultShippingFee(-1000L)
                .freeShippingThreshold(-5000L)
                .build();

        mockMvc.perform(put("/api/admin/settings/store")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("Phân quyền A2: STAFF dùng được /account nhưng bị 403 ở /store và /logo")
    void security_staffPermissions() throws Exception {
        // STAFF xem tài khoản của chính mình -> 200
        mockMvc.perform(get("/api/admin/settings/account")
                        .header("Authorization", staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(staffUser.getEmail()))
                .andExpect(jsonPath("$.role").value("staff"));

        // STAFF cập nhật tài khoản của mình -> 200
        UpdateAdminAccountRequest updateReq = UpdateAdminAccountRequest.builder()
                .name("Nhân Viên Cập Nhật")
                .phone("0929998877")
                .build();
        mockMvc.perform(put("/api/admin/settings/account")
                        .header("Authorization", staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nhân Viên Cập Nhật"));

        // STAFF đổi mật khẩu của mình -> 200
        ChangePasswordRequest pwReq = ChangePasswordRequest.builder()
                .currentPassword("StaffPassword123!")
                .newPassword("NewStaffPass456!")
                .confirmPassword("NewStaffPass456!")
                .build();
        mockMvc.perform(put("/api/admin/settings/account/password")
                        .header("Authorization", staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pwReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đổi mật khẩu thành công"));

        // STAFF cố tình truy cập /store -> 403
        mockMvc.perform(get("/api/admin/settings/store")
                        .header("Authorization", staffToken))
                .andExpect(status().isForbidden());

        // STAFF cố tình cập nhật /store -> 403
        StoreSettingsRequest req = StoreSettingsRequest.builder()
                .storeName("Staff Try Edit")
                .lowStockThreshold(10)
                .defaultShippingFee(30000L)
                .freeShippingThreshold(500000L)
                .build();
        mockMvc.perform(put("/api/admin/settings/store")
                        .header("Authorization", staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // STAFF cố tình upload /logo -> 403
        byte[] validPng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", validPng);
        mockMvc.perform(multipart("/api/admin/settings/logo")
                        .file(file)
                        .header("Authorization", staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Phân quyền A2: CUSTOMER bị 403 tất cả, không token bị 401 trên mọi endpoint settings")
    void security_customerAndUnauthenticated_shouldDeny() throws Exception {
        byte[] validPng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", validPng);

        // 1. Không token -> 401 Unauthorized
        mockMvc.perform(get("/api/admin/settings/store"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/settings/store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/settings/logo").file(file))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/settings/account"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/settings/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/settings/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // 2. CUSTOMER token -> 403 Forbidden
        mockMvc.perform(get("/api/admin/settings/store")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/settings/store")
                        .header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/settings/logo")
                        .file(file)
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/settings/account")
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/settings/account")
                        .header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/settings/account/password")
                        .header("Authorization", customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("P6.6: Public endpoint /api/settings/shipping chỉ cho phép GET, từ chối POST/PUT/DELETE")
    void publicShippingPolicy_shouldOnlyAllowGet() throws Exception {
        // Gọi không có auth với các method khác GET -> bị Spring Security từ chối (401 Unauthorized)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/settings/shipping"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/settings/shipping"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/settings/shipping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A3 Upload Logo: PNG hợp lệ -> 200, lưu vào uploads/store/, file tồn tại trên đĩa")
    void uploadLogo_validPng_shouldSucceed() throws Exception {
        byte[] validPng = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
        };
        MockMultipartFile file = new MockMultipartFile("file", "test-logo.png", "image/png", validPng);

        String responseBody = mockMvc.perform(multipart("/api/admin/settings/logo")
                        .file(file)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String logoUrl = objectMapper.readTree(responseBody).get("logoUrl").asText();
        assertThat(logoUrl).startsWith("/uploads/store/");

        Path filePath = Paths.get("." + logoUrl.replace("/uploads", "/uploads")).toAbsolutePath().normalize();
        // Since logoUrl is /uploads/store/..., the relative path from project root is uploads/store/...
        Path relativePath = Paths.get(logoUrl.substring(1)); // "uploads/store/..."
        filesToCleanUp.add(relativePath);
        assertThat(Files.exists(relativePath)).isTrue();
    }

    @Test
    @DisplayName("A3 Upload Logo: File văn bản đổi đuôi .png -> 400 kèm thông báo tiếng Việt")
    void uploadLogo_invalidMagicBytes_shouldReturn400() throws Exception {
        byte[] fakeImage = "This is a text file not an image!".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png", fakeImage);

        mockMvc.perform(multipart("/api/admin/settings/logo")
                        .file(file)
                        .header("Authorization", adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chỉ chấp nhận các định dạng ảnh hợp lệ: JPG, PNG, WEBP, GIF"));
    }

    @Test
    @DisplayName("A3 Upload Logo: File vượt quá 2MB -> 400 kèm thông báo tiếng Việt")
    void uploadLogo_exceedMaxSize_shouldReturn4xx() throws Exception {
        // Create dummy content slightly above 2MB (2MB + 1 byte)
        byte[] oversizedBytes = new byte[2 * 1024 * 1024 + 1];
        // Valid PNG magic bytes in header
        oversizedBytes[0] = (byte) 0x89;
        oversizedBytes[1] = 0x50;
        oversizedBytes[2] = 0x4E;
        oversizedBytes[3] = 0x47;
        oversizedBytes[4] = 0x0D;
        oversizedBytes[5] = 0x0A;
        oversizedBytes[6] = 0x1A;
        oversizedBytes[7] = 0x0A;

        MockMultipartFile file = new MockMultipartFile("file", "huge.png", "image/png", oversizedBytes);

        mockMvc.perform(multipart("/api/admin/settings/logo")
                        .file(file)
                        .header("Authorization", adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dung lượng ảnh logo không được vượt quá 2MB"));
    }

    @Test
    @DisplayName("GET & PUT /account: xem và cập nhật họ tên, số điện thoại admin")
    void accountEndpoints_shouldViewAndUpdateAdminProfile() throws Exception {
        mockMvc.perform(get("/api/admin/settings/account")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(adminUser.getId()))
                .andExpect(jsonPath("$.email").value(adminUser.getEmail()))
                .andExpect(jsonPath("$.role").value("admin"));

        UpdateAdminAccountRequest updateReq = UpdateAdminAccountRequest.builder()
                .name("Admin Nguyễn Cập Nhật")
                .phone("0988776655")
                .build();

        mockMvc.perform(put("/api/admin/settings/account")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Admin Nguyễn Cập Nhật"))
                .andExpect(jsonPath("$.phone").value("0988776655"));

        User refreshed = userRepository.findById(adminUser.getId()).orElseThrow();
        assertThat(refreshed.getName()).isEqualTo("Admin Nguyễn Cập Nhật");
        assertThat(refreshed.getPhone()).isEqualTo("0988776655");
    }

    @Test
    @DisplayName("Đổi mật khẩu: đúng mật khẩu cũ -> thành công; sai mật khẩu cũ -> lỗi; không khớp xác nhận -> lỗi")
    void changePassword_scenarios() throws Exception {
        // 1. Mismatch confirmation
        ChangePasswordRequest mismatchReq = ChangePasswordRequest.builder()
                .currentPassword("OldPassword123!")
                .newPassword("NewSecretPass123!")
                .confirmPassword("DifferentConfirm123!")
                .build();

        mockMvc.perform(put("/api/admin/settings/account/password")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mismatchReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Xác nhận mật khẩu mới không khớp"));

        // 2. Wrong current password
        ChangePasswordRequest wrongCurrentReq = ChangePasswordRequest.builder()
                .currentPassword("WrongPass999!")
                .newPassword("NewSecretPass123!")
                .confirmPassword("NewSecretPass123!")
                .build();

        mockMvc.perform(put("/api/admin/settings/account/password")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongCurrentReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không chính xác"));

        // 3. Valid change password
        ChangePasswordRequest validReq = ChangePasswordRequest.builder()
                .currentPassword("OldPassword123!")
                .newPassword("BrandNewPass456!")
                .confirmPassword("BrandNewPass456!")
                .build();

        mockMvc.perform(put("/api/admin/settings/account/password")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đổi mật khẩu thành công"));

        // Verify login with new password succeeds
        LoginRequest loginRequest = new LoginRequest(adminUser.getEmail(), "BrandNewPass456!");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("Không thể tạo dòng thứ hai trong store_settings (ràng buộc CHECK id = 1)")
    void storeSettings_shouldRejectSecondRow() {
        StoreSettings second = StoreSettings.builder()
                .id(2L)
                .storeName("Illegal Second Store")
                .lowStockThreshold(10)
                .defaultShippingFee(new BigDecimal("30000.00"))
                .freeShippingThreshold(new BigDecimal("500000.00"))
                .updatedAt(LocalDateTime.now())
                .updatedBy("hacker")
                .build();

        assertThatThrownBy(() -> {
            storeSettingsRepository.saveAndFlush(second);
        }).satisfies(ex -> {
            assertThat(ex.getMessage()).contains("chk_store_settings_single_row");
        });
    }
}
