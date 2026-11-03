package com.shop.backend.security;

import com.shop.backend.user.Role;
import com.shop.backend.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private String generateRandomHexSecret(int byteLength) {
        byte[] bytes = new byte[byteLength];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    @Test
    @DisplayName("Khởi tạo thất bại khi app.jwt.secret bị null")
    void shouldFailWhenSecretIsNull() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateAndInitializeKey);
        assertTrue(ex.getMessage().contains("không được để trống"));
    }

    @Test
    @DisplayName("Khởi tạo thất bại khi app.jwt.secret là chuỗi rỗng")
    void shouldFailWhenSecretIsBlank() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "   ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateAndInitializeKey);
        assertTrue(ex.getMessage().contains("không được để trống"));
    }

    @Test
    @DisplayName("Khởi tạo thất bại khi app.jwt.secret chứa ký tự không phải hex")
    void shouldFailWhenSecretIsNotHex() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "this_is_not_hex_secret_and_has_special_chars_1234567890abcdef_zzzzzzzzzzzz");

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateAndInitializeKey);
        assertTrue(ex.getMessage().contains("chuỗi Hex hợp lệ"));
    }

    @Test
    @DisplayName("Khởi tạo thất bại khi app.jwt.secret ngắn hơn 128 ký tự hex (dưới 64 bytes)")
    void shouldFailWhenSecretIsShorterThan128HexChars() {
        JwtService service = new JwtService();
        String shortSecret = generateRandomHexSecret(32); // 32 bytes = 64 hex chars < 64 bytes
        ReflectionTestUtils.setField(service, "secret", shortSecret);

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateAndInitializeKey);
        assertTrue(ex.getMessage().contains("yêu cầu tối thiểu 64 bytes (128 ký tự hex)"));
    }

    @Test
    @DisplayName("Khởi tạo thành công với chuỗi hex 128 ký tự và tạo/xác thực token đúng")
    void shouldInitializeAndSignTokensSuccessfullyWith128HexChars() {
        JwtService service = new JwtService();
        String validSecret = generateRandomHexSecret(64); // 64 bytes = 128 hex chars
        ReflectionTestUtils.setField(service, "secret", validSecret);
        ReflectionTestUtils.setField(service, "expirationMs", 1800000L);
        service.validateAndInitializeKey();

        Role role = Role.builder().id(1L).name("customer").build();
        User user = User.builder().id(99L).role(role).build();

        String token = service.generateToken(user);
        assertNotNull(token);
        assertTrue(service.validateToken(token));
        assertEquals(99L, service.extractUserId(token));
        assertFalse(service.validateToken(token + "tampered"));
    }
}
