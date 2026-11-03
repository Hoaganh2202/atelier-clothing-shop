package com.shop.backend.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class UserEntityIntegrationTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserRepository userRepository;

    // ──────────────────────────────────────────────────────────────
    // JPA persistence round-trip
    // ──────────────────────────────────────────────────────────────

    @Test
    void persistAndFindUser() {
        Role role = new Role();
        role.setName("TEST_ROLE");
        em.persistAndFlush(role);

        User user = new User();
        user.setRole(role);
        user.setName("Test User");
        user.setEmail("integration_test_user@example.com");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setGender(Gender.OTHER);
        user.setDataSource(com.shop.backend.common.model.DataSourceType.WEBSITE);
        em.persistAndFlush(user);

        em.clear();

        User found = userRepository.findByEmail("integration_test_user@example.com").orElseThrow();
        assertThat(found.getName()).isEqualTo("Test User");
        assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(found.getGender()).isEqualTo(Gender.OTHER);
    }

    // ──────────────────────────────────────────────────────────────
    // AbstractEnumConverter contract
    // ──────────────────────────────────────────────────────────────

    @Test
    void shouldThrowExceptionWhenUnknownEnumValueEncountered() {
        UserStatusConverter converter = new UserStatusConverter();
        assertThatThrownBy(() -> converter.convertToEntityAttribute("TOTALLY_UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TOTALLY_UNKNOWN");
    }

    @Test
    void converterShouldSerializeEnumToLowercaseString() {
        UserStatusConverter converter = new UserStatusConverter();
        assertThat(converter.convertToDatabaseColumn(UserStatus.ACTIVE)).isEqualTo("active");
        assertThat(converter.convertToDatabaseColumn(UserStatus.BLOCKED)).isEqualTo("blocked");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }
}