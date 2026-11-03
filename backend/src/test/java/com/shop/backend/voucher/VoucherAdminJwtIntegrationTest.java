package com.shop.backend.voucher;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.security.JwtService;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class VoucherAdminJwtIntegrationTest {

    private static final String TEST_CODE = "JWTVOUCHERTEST";
    private static final String TEST_EMAIL = "voucher_jwt_admin@shop.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long createdAdminId;

    @AfterEach
    void tearDown() {
        voucherRepository.findAll().stream()
                .filter(voucher -> TEST_CODE.equals(voucher.getCode()))
                .forEach(voucherRepository::delete);
        if (createdAdminId != null) {
            userRepository.deleteById(createdAdminId);
            createdAdminId = null;
        }
    }

    @Test
    @DisplayName("Admin JWT ngoài transaction: tạo và đọc voucher qua filter bảo mật thật")
    void shouldCreateAndReadVoucherWithRealJwtOutsideTransaction() throws Exception {
        Role adminRole = roleRepository.findByName("admin")
                .orElseThrow(() -> new IllegalStateException("Role 'admin' not found in test database"));
        User admin = userRepository.save(User.builder()
                .name("Voucher JWT Admin")
                .email(TEST_EMAIL)
                .passwordHash(passwordEncoder.encode("TestPassword123!"))
                .role(adminRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());
        createdAdminId = admin.getId();
        String bearer = "Bearer " + jwtService.generateToken(admin);

        Map<String, Object> body = Map.of(
                "code", TEST_CODE,
                "discountType", "AMOUNT",
                "discountValue", 50000,
                "startAt", LocalDateTime.now().minusDays(1).withNano(0).toString(),
                "endAt", LocalDateTime.now().plusDays(7).withNano(0).toString());

        mockMvc.perform(post("/api/admin/vouchers").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(TEST_CODE));

        mockMvc.perform(get("/api/admin/vouchers").header("Authorization", bearer).param("keyword", TEST_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].discountType").value("AMOUNT"));
    }
}
