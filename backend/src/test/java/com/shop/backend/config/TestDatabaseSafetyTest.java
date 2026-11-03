package com.shop.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class TestDatabaseSafetyTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Đảm bảo test đang kết nối đúng schema clothing_shop_test")
    void shouldConnectToClothingShopTestDatabase() {
        String currentDb = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        assertThat(currentDb).isEqualTo("clothing_shop_test");
    }

    @Test
    @DisplayName("Đảm bảo TestDatabaseSafetyGuard ném lỗi nếu URL không chứa _test")
    void shouldThrowExceptionWhenUrlDoesNotContainTest() {
        Environment env = mock(Environment.class);
        when(env.getProperty("spring.datasource.url", "")).thenReturn("jdbc:mysql://localhost:3306/clothing_shop_db");
        DataSource ds = mock(DataSource.class);
        TestDatabaseSafetyGuard guard = new TestDatabaseSafetyGuard(ds, env);

        assertThatThrownBy(guard::validateTestDatabase)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not contain '_test'");
    }
}
