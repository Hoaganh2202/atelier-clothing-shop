package com.shop.backend.einvoice.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.backend.einvoice.provider.misa.MisaProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MisaPropertiesSecurityTest {

    @Test
    @DisplayName("toString() must redact password and must not leak sensitive credentials")
    void toStringShouldRedactPassword() {
        MisaProperties properties = new MisaProperties();
        properties.setUsername("test_user_admin");
        properties.setPassword("super_secret_password_12345");
        properties.setAppId("app_id_test");
        properties.setTaxCode("0101243150");

        String toStringResult = properties.toString();

        assertThat(toStringResult).doesNotContain("super_secret_password_12345");
        assertThat(toStringResult).contains("[PROTECTED]");
        assertThat(toStringResult).contains("test_user_admin");
        assertThat(toStringResult).contains("0101243150");
    }
}
