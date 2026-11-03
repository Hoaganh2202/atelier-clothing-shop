package com.shop.backend.einvoice.provider.misa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderAuthException;
import com.shop.backend.einvoice.provider.misa.dto.MisaAuthRequest;
import com.shop.backend.einvoice.provider.misa.dto.MisaAuthResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MisaTokenClient {

    private static final Logger log = LoggerFactory.getLogger(MisaTokenClient.class);
    public static final long DEFAULT_FALLBACK_TTL_SECONDS = 1800L; // 30 minutes conservative default

    private final MisaProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>();
    private final ReentrantLock refreshLock = new ReentrantLock();

    public MisaTokenClient(MisaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public record CachedToken(String token, Instant expiresAt, boolean isFallback) {
        public boolean isExpired(long bufferSeconds) {
            long effectiveBuffer = isFallback ? Math.min(bufferSeconds, 300L) : bufferSeconds;
            return Instant.now().plusSeconds(effectiveBuffer).isAfter(expiresAt);
        }
    }

    public record ExpirationResult(Instant expiresAt, boolean isFallback) {}

    /**
     * Obtains a valid Bearer token, utilizing cache when available or acquiring a lock
     * to refresh the token if it has expired or falls within the safety buffer window.
     *
     * @param authSupplier supplier that performs the actual HTTP authentication call
     * @return valid JWT bearer token
     */
    public String getValidToken(Supplier<MisaAuthResponse> authSupplier) {
        CachedToken current = tokenCache.get();
        long buffer = properties.getTokenExpirationBufferSeconds();

        if (current != null && !current.isExpired(buffer)) {
            return current.token();
        }

        refreshLock.lock();
        try {
            // Double-checked locking to avoid concurrent refresh
            current = tokenCache.get();
            if (current != null && !current.isExpired(buffer)) {
                return current.token();
            }

            log.info("MISA access token is absent or expiring within safety buffer ({}s). Refreshing token...", buffer);
            MisaAuthResponse response = authSupplier.get();

            if (response == null || !response.isSuccess() || response.getData() == null || response.getData().isBlank()) {
                String errorMsg = (response != null && response.getErrorCode() != null)
                        ? "MISA Auth error: " + response.getErrorCode()
                        : "MISA Authentication failed or returned empty token";
                throw new InvoiceProviderAuthException("MISA", "AUTH_FAILED", errorMsg, response != null ? response.toString() : null);
            }

            String rawToken = response.getData().trim();
            ExpirationResult expResult = extractExpiration(rawToken);
            CachedToken newToken = new CachedToken(rawToken, expResult.expiresAt(), expResult.isFallback());
            tokenCache.set(newToken);

            log.info("MISA access token refreshed successfully. Valid until: {} (fallback: {})", expResult.expiresAt(), expResult.isFallback());
            return rawToken;
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * Evicts the cached token ONLY IF the current cached token matches the failed token.
     * This prevents race conditions where worker A refreshes to T2, but worker B's late 401
     * response from T1 would otherwise evict T2 and trigger an unnecessary refresh.
     *
     * @param failedToken the token that encountered an authorization error
     * @return true if the token was evicted, false if current token already differs
     */
    public boolean evictTokenIfMatches(String failedToken) {
        if (failedToken == null || failedToken.isBlank()) {
            return false;
        }
        refreshLock.lock();
        try {
            CachedToken current = tokenCache.get();
            if (current != null && failedToken.equals(current.token())) {
                tokenCache.set(null);
                log.warn("MISA token cache evicted because it matched the failed token.");
                return true;
            } else {
                log.info("MISA token cache was NOT evicted because the current token already differs from the failed token.");
                return false;
            }
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * Explicitly invalidates the cached token unconditionally.
     */
    public void evictToken() {
        refreshLock.lock();
        try {
            CachedToken old = tokenCache.getAndSet(null);
            if (old != null) {
                log.warn("MISA token cache evicted unconditionally due to invalidation request.");
            }
        } finally {
            refreshLock.unlock();
        }
    }

    public CachedToken getCachedTokenSnapshot() {
        return tokenCache.get();
    }

    public MisaAuthRequest buildAuthRequest() {
        return MisaAuthRequest.builder()
                .appId(properties.getAppId())
                .taxCode(properties.getTaxCode())
                .username(properties.getUsername())
                .password(properties.getPassword())
                .build();
    }

    public long getEffectiveFallbackTtlSeconds() {
        long configured = properties.getTokenFallbackTtlSeconds();
        if (configured <= 0) {
            log.warn("MISA token fallback TTL is configured with an invalid or zero value ({}s). Clamping to conservative default of {}s. Note: actual token TTL awaits MISA official confirmation.",
                    configured, DEFAULT_FALLBACK_TTL_SECONDS);
            return DEFAULT_FALLBACK_TTL_SECONDS;
        }
        return configured;
    }

    private ExpirationResult extractExpiration(String jwtToken) {
        long fallbackSeconds = getEffectiveFallbackTtlSeconds();
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length >= 2) {
                byte[] decodedPayload = Base64.getUrlDecoder().decode(parts[1]);
                JsonNode payloadNode = objectMapper.readTree(new String(decodedPayload, StandardCharsets.UTF_8));
                if (payloadNode.has("exp") && payloadNode.get("exp").isNumber()) {
                    long expSeconds = payloadNode.get("exp").asLong();
                    return new ExpirationResult(Instant.ofEpochSecond(expSeconds), false);
                } else {
                    log.warn("MISA token payload does not contain a numeric 'exp' claim. Applying fallback TTL of {}s. Note: actual token TTL awaits MISA official confirmation.", fallbackSeconds);
                }
            } else {
                log.warn("MISA token is not a standard 3-part JWT. Applying fallback TTL of {}s. Note: actual token TTL awaits MISA official confirmation.", fallbackSeconds);
            }
        } catch (Exception e) {
            log.warn("Unable to parse JWT payload from MISA token. Applying fallback TTL of {}s. Error: {}. Note: actual token TTL awaits MISA official confirmation.", fallbackSeconds, e.getMessage());
        }
        return new ExpirationResult(Instant.now().plusSeconds(fallbackSeconds), true);
    }
}
