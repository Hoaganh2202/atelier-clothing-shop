package com.shop.backend.promotion;

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

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PromotionAdminJwtIntegrationTest {

    private String testName;
    private String testEmail;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private PromotionProductRepository promotionProductRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long createdAdminId;
    private Long createdPromotionId;

    @BeforeEach
    void setUp() {
        testName = "PROMO_JWT_" + UUID.randomUUID();
        testEmail = "promo_jwt_" + UUID.randomUUID() + "@shop.com";
        userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().startsWith("promo_jwt_"))
                .forEach(userRepository::delete);
        userRepository.findAll().stream()
                .filter(u -> "promotion_jwt_admin@shop.com".equals(u.getEmail()))
                .forEach(userRepository::delete);
    }

    @AfterEach
    void tearDown() {
        if (createdPromotionId != null) {
            promotionProductRepository.deleteByPromotionId(createdPromotionId);
            promotionRepository.deleteById(createdPromotionId);
            createdPromotionId = null;
        }
        promotionRepository.findAll().stream()
                .filter(p -> p.getName() != null && p.getName().startsWith("PROMO_JWT_"))
                .forEach(p -> {
                    promotionProductRepository.deleteByPromotionId(p.getId());
                    promotionRepository.delete(p);
                });
        if (createdAdminId != null) {
            userRepository.deleteById(createdAdminId);
            createdAdminId = null;
        }
    }

    @Test
    @DisplayName("Admin JWT ngoài transaction: tạo và đọc promotion qua filter bảo mật thật")
    void shouldCreateAndReadPromotionWithRealJwtOutsideTransaction() throws Exception {
        Role adminRole = roleRepository.findByName("admin")
                .orElseThrow(() -> new IllegalStateException("Role 'admin' not found in test database"));

        User admin = userRepository.save(User.builder()
                .name("Promotion JWT Admin")
                .email(testEmail)
                .passwordHash(passwordEncoder.encode("TestPassword123!"))
                .role(adminRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build());
        createdAdminId = admin.getId();
        String bearer = "Bearer " + jwtService.generateToken(admin);

        Map<String, Object> body = Map.of(
                "name", testName,
                "discountType", "PERCENT",
                "discountValue", 15,
                "startAt", LocalDateTime.now().minusDays(1).withNano(0).toString(),
                "endAt", LocalDateTime.now().plusDays(5).withNano(0).toString(),
                "isActive", true);

        String responseJson = mockMvc.perform(post("/api/admin/promotions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(testName))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> responseMap = objectMapper.readValue(responseJson, Map.class);
        if (responseMap.get("id") != null) {
            createdPromotionId = Long.valueOf(responseMap.get("id").toString());
        }

        mockMvc.perform(get("/api/admin/promotions")
                        .header("Authorization", bearer)
                        .param("keyword", testName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name == '%s')]", testName).exists());
    }
}
