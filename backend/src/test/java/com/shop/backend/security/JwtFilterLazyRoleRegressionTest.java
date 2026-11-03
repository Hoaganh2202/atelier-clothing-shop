package com.shop.backend.security;

import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.user.Role;
import com.shop.backend.user.RoleRepository;
import com.shop.backend.user.User;
import com.shop.backend.user.UserRepository;
import com.shop.backend.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class JwtFilterLazyRoleRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long createdAdminId;

    @AfterEach
    void tearDown() {
        if (createdAdminId != null) {
            userRepository.deleteById(createdAdminId);
            createdAdminId = null;
        }
    }

    @Test
    @DisplayName("Should successfully authenticate admin via JwtAuthenticationFilter outside transaction")
    void shouldAuthenticateAdminWithoutLazyInitializationExceptionOutsideTransaction() throws Exception {
        Role adminRole = roleRepository.findByName("admin")
                .orElseThrow(() -> new IllegalStateException("Role 'admin' not found in test database"));

        User admin = User.builder()
                .name("Regression Admin")
                .email("regression_admin@shop.com")
                .passwordHash(passwordEncoder.encode("TestPassword123!"))
                .role(adminRole)
                .status(UserStatus.ACTIVE)
                .dataSource(DataSourceType.WEBSITE)
                .build();

        User savedAdmin = userRepository.save(admin);
        createdAdminId = savedAdmin.getId();

        String token = jwtService.generateToken(savedAdmin);

        mockMvc.perform(get("/api/admin/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
