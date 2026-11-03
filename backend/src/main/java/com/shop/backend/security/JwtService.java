package com.shop.backend.security;

import com.shop.backend.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.util.Date;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final int MIN_HEX_LENGTH = 128; // 64 bytes = 512 bits for HS512

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms:1800000}")
    private long expirationMs;

    private SecretKey signingKey;

    @PostConstruct
    public void validateAndInitializeKey() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Cấu hình app.jwt.secret không được để trống");
        }
        String trimmedSecret = secret.trim();
        if (!trimmedSecret.matches("^[0-9a-fA-F]+$")) {
            throw new IllegalStateException("Cấu hình app.jwt.secret phải là chuỗi Hex hợp lệ (0-9, a-f, A-F)");
        }
        if (trimmedSecret.length() % 2 != 0) {
            throw new IllegalStateException("Cấu hình app.jwt.secret phải có số lượng ký tự hex chẵn");
        }
        byte[] keyBytes = HexFormat.of().parseHex(trimmedSecret);
        if (keyBytes.length < 64) {
            throw new IllegalStateException("Cấu hình app.jwt.secret cho thuật toán HS512 yêu cầu tối thiểu 64 bytes (128 ký tự hex), hiện tại: " + trimmedSecret.length() + " ký tự");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // TODO: Refactor to accept userId and roleName instead of User entity
    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        String roleName = user.getRole().getName();

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("userId", user.getId())
                .claim("role", roleName)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long extractUserId(String token) {
        Claims claims = extractClaims(token);
        Object userIdClaim = claims.get("userId");
        if (userIdClaim instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(claims.getSubject());
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}

