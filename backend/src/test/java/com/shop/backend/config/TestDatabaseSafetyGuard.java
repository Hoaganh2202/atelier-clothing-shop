package com.shop.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Slf4j
@Component
@Profile("test")
public class TestDatabaseSafetyGuard {

    private final DataSource dataSource;
    private final Environment environment;

    public TestDatabaseSafetyGuard(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @PostConstruct
    public void validateTestDatabase() {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.toLowerCase().contains("_test")) {
            throw new IllegalStateException(
                "CRITICAL SAFETY VIOLATION: Test profile is active but datasource URL does not contain '_test': " + url
            );
        }
        try (Connection conn = dataSource.getConnection()) {
            String catalog = conn.getCatalog();
            if (catalog != null && !catalog.toLowerCase().contains("_test")) {
                throw new IllegalStateException(
                    "CRITICAL SAFETY VIOLATION: Test database catalog does not contain '_test': " + catalog
                );
            }
            log.info("TestDatabaseSafetyGuard passed: verified test database '{}' (url: {})", catalog, url);
        } catch (SQLException e) {
            log.error("Failed to connect and verify test database catalog", e);
            throw new IllegalStateException("Failed to verify test database catalog", e);
        }
    }
}
