package com.shop.backend.einvoice.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderAuthException;
import com.shop.backend.einvoice.provider.misa.MisaProperties;
import com.shop.backend.einvoice.provider.misa.MisaTokenClient;
import com.shop.backend.einvoice.provider.misa.dto.MisaAuthResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MisaTokenClientTest {

    private ObjectMapper objectMapper;
    private MisaProperties properties;
    private MisaTokenClient tokenClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new MisaProperties();
        properties.setTokenExpirationBufferSeconds(3600); // 1 hour buffer
        tokenClient = new MisaTokenClient(properties, objectMapper);
    }

    private String createDummyJwt(long expEpochSeconds) {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"sub\":\"user\",\"exp\":" + expEpochSeconds + "}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".dummy_signature";
    }

    @Test
    @DisplayName("Should retrieve new token and cache it when cache is initially empty")
    void shouldAuthenticateWhenCacheEmpty() {
        long futureExp = Instant.now().plusSeconds(7200).getEpochSecond();
        String validJwt = createDummyJwt(futureExp);

        AtomicInteger callCount = new AtomicInteger(0);
        String token = tokenClient.getValidToken(() -> {
            callCount.incrementAndGet();
            return MisaAuthResponse.builder()
                    .success(true)
                    .data(validJwt)
                    .build();
        });

        assertThat(token).isEqualTo(validJwt);
        assertThat(callCount.get()).isEqualTo(1);

        // Second call should hit cache
        String cachedToken = tokenClient.getValidToken(() -> {
            callCount.incrementAndGet();
            return MisaAuthResponse.builder().success(true).data("another_token").build();
        });

        assertThat(cachedToken).isEqualTo(validJwt);
        assertThat(callCount.get()).isEqualTo(1); // Not incremented
    }

    @Test
    @DisplayName("Should refresh token when current token is within the expiration safety buffer")
    void shouldRefreshWhenWithinSafetyBuffer() {
        // Token expires in 30 minutes, but buffer is 60 minutes
        long expIn30Mins = Instant.now().plusSeconds(1800).getEpochSecond();
        String soonExpiringJwt = createDummyJwt(expIn30Mins);

        long expIn2Hours = Instant.now().plusSeconds(7200).getEpochSecond();
        String freshJwt = createDummyJwt(expIn2Hours);

        AtomicInteger callCount = new AtomicInteger(0);

        // First call gets token expiring soon
        tokenClient.getValidToken(() -> {
            callCount.incrementAndGet();
            return MisaAuthResponse.builder().success(true).data(soonExpiringJwt).build();
        });
        assertThat(callCount.get()).isEqualTo(1);

        // Second call detects token is within 1h buffer, triggers refresh
        String refreshedToken = tokenClient.getValidToken(() -> {
            callCount.incrementAndGet();
            return MisaAuthResponse.builder().success(true).data(freshJwt).build();
        });

        assertThat(refreshedToken).isEqualTo(freshJwt);
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Concurrent requests when cache is empty must synchronize and invoke auth exactly once")
    void concurrentTokenRefreshMustSynchronize() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        long futureExp = Instant.now().plusSeconds(10000).getEpochSecond();
        String jwt = createDummyJwt(futureExp);
        AtomicInteger authInvocations = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    tokenClient.getValidToken(() -> {
                        authInvocations.incrementAndGet();
                        try {
                            Thread.sleep(50); // Simulate network latency
                        } catch (InterruptedException ignored) {}
                        return MisaAuthResponse.builder().success(true).data(jwt).build();
                    });
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Fire all threads simultaneously
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(authInvocations.get()).isEqualTo(1); // Mutex guaranteed single execution
    }

    @Test
    @DisplayName("evictToken must invalidate current cache so next call re-authenticates")
    void evictTokenShouldClearCache() {
        long futureExp = Instant.now().plusSeconds(7200).getEpochSecond();
        String jwt1 = createDummyJwt(futureExp);
        String jwt2 = createDummyJwt(futureExp + 1000);

        AtomicInteger callCount = new AtomicInteger(0);
        tokenClient.getValidToken(() -> {
            callCount.incrementAndGet();
            return MisaAuthResponse.builder().success(true).data(jwt1).build();
        });
        assertThat(callCount.get()).isEqualTo(1);

        // Evict
        tokenClient.evictToken();
        assertThat(tokenClient.getCachedTokenSnapshot()).isNull();

        // Next call must call auth supplier
        String token2 = tokenClient.getValidToken(() -> {
            callCount.incrementAndGet();
            return MisaAuthResponse.builder().success(true).data(jwt2).build();
        });

        assertThat(token2).isEqualTo(jwt2);
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("evictTokenIfMatches: should evict only when token matches current cached token")
    void evictTokenIfMatches() {
        long futureExp = Instant.now().plusSeconds(7200).getEpochSecond();
        String t1 = createDummyJwt(futureExp);
        String t2 = createDummyJwt(futureExp + 1000);

        tokenClient.getValidToken(() -> MisaAuthResponse.builder().success(true).data(t1).build());

        // Attempt evict with a different token -> must return false and keep t1
        boolean evictedWrong = tokenClient.evictTokenIfMatches("wrong_token_t0");
        assertThat(evictedWrong).isFalse();
        assertThat(tokenClient.getCachedTokenSnapshot()).isNotNull();
        assertThat(tokenClient.getCachedTokenSnapshot().token()).isEqualTo(t1);

        // Evict with matching t1 -> must return true and clear cache
        boolean evictedCorrect = tokenClient.evictTokenIfMatches(t1);
        assertThat(evictedCorrect).isTrue();
        assertThat(tokenClient.getCachedTokenSnapshot()).isNull();
    }

    @Test
    @DisplayName("Race condition test: late 401 from T1 must NOT evict T2 that was already refreshed by another thread")
    void late401MustNotEvictRefreshedToken() {
        long futureExp = Instant.now().plusSeconds(7200).getEpochSecond();
        String t1 = createDummyJwt(futureExp);
        String t2 = createDummyJwt(futureExp + 1000);

        AtomicInteger authInvocations = new AtomicInteger(0);

        // Initial token T1 is cached
        tokenClient.getValidToken(() -> {
            authInvocations.incrementAndGet();
            return MisaAuthResponse.builder().success(true).data(t1).build();
        });
        assertThat(authInvocations.get()).isEqualTo(1);

        // Thread A receives 401 with T1, evicts T1, and refreshes to T2
        boolean threadAEvicted = tokenClient.evictTokenIfMatches(t1);
        assertThat(threadAEvicted).isTrue();
        String refreshedToken = tokenClient.getValidToken(() -> {
            authInvocations.incrementAndGet();
            return MisaAuthResponse.builder().success(true).data(t2).build();
        });
        assertThat(refreshedToken).isEqualTo(t2);
        assertThat(authInvocations.get()).isEqualTo(2);

        // Thread B's late 401 arrives with T1. Attempting to evict T1 must NOT touch T2!
        boolean threadBEvicted = tokenClient.evictTokenIfMatches(t1);
        assertThat(threadBEvicted).isFalse();
        assertThat(tokenClient.getCachedTokenSnapshot()).isNotNull();
        assertThat(tokenClient.getCachedTokenSnapshot().token()).isEqualTo(t2);

        // When Thread B requests a token, it immediately gets T2 without triggering a 3rd auth call
        String tokenForThreadB = tokenClient.getValidToken(() -> {
            authInvocations.incrementAndGet();
            return MisaAuthResponse.builder().success(true).data("t3_unneeded").build();
        });
        assertThat(tokenForThreadB).isEqualTo(t2);
        assertThat(authInvocations.get()).isEqualTo(2); // No extra redundant refresh!
    }

    @Test
    @DisplayName("extractExpiration: JWT without exp claim should use configured fallback TTL")
    void tokenWithoutExpShouldUseConfiguredFallbackTtl() {
        properties.setTokenFallbackTtlSeconds(1800); // 30 minutes
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payloadNoExp = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"user_no_exp\"}".getBytes(StandardCharsets.UTF_8));
        String jwtNoExp = header + "." + payloadNoExp + ".signature";

        tokenClient.getValidToken(() -> MisaAuthResponse.builder().success(true).data(jwtNoExp).build());

        MisaTokenClient.CachedToken cached = tokenClient.getCachedTokenSnapshot();
        assertThat(cached).isNotNull();
        long expectedMinExp = Instant.now().plusSeconds(1790).getEpochSecond();
        long expectedMaxExp = Instant.now().plusSeconds(1810).getEpochSecond();
        assertThat(cached.expiresAt().getEpochSecond()).isBetween(expectedMinExp, expectedMaxExp);
    }

    @Test
    @DisplayName("extractExpiration: JWT with non-numeric exp should use configured fallback TTL without crashing")
    void tokenWithNonNumericExpShouldUseFallback() {
        properties.setTokenFallbackTtlSeconds(1200); // 20 minutes
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payloadMalformedExp = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"user\",\"exp\":\"not_a_number\"}".getBytes(StandardCharsets.UTF_8));
        String jwtMalformedExp = header + "." + payloadMalformedExp + ".signature";

        tokenClient.getValidToken(() -> MisaAuthResponse.builder().success(true).data(jwtMalformedExp).build());

        MisaTokenClient.CachedToken cached = tokenClient.getCachedTokenSnapshot();
        assertThat(cached).isNotNull();
        long expectedMinExp = Instant.now().plusSeconds(1190).getEpochSecond();
        long expectedMaxExp = Instant.now().plusSeconds(1210).getEpochSecond();
        assertThat(cached.expiresAt().getEpochSecond()).isBetween(expectedMinExp, expectedMaxExp);
    }

    @Test
    @DisplayName("extractExpiration: completely malformed or opaque token string should use configured fallback TTL without crashing")
    void opaqueTokenShouldUseFallbackWithoutCrashing() {
        properties.setTokenFallbackTtlSeconds(900); // 15 minutes
        String opaqueToken = "misa_opaque_bearer_token_string_without_dots";

        tokenClient.getValidToken(() -> MisaAuthResponse.builder().success(true).data(opaqueToken).build());

        MisaTokenClient.CachedToken cached = tokenClient.getCachedTokenSnapshot();
        assertThat(cached).isNotNull();
        assertThat(cached.token()).isEqualTo(opaqueToken);
        long expectedMinExp = Instant.now().plusSeconds(890).getEpochSecond();
        long expectedMaxExp = Instant.now().plusSeconds(910).getEpochSecond();
        assertThat(cached.expiresAt().getEpochSecond()).isBetween(expectedMinExp, expectedMaxExp);
    }

    @Test
    @DisplayName("extractExpiration: configured fallback TTL <= 0 must clamp to safe default 1800s")
    void zeroOrNegativeFallbackTtlShouldClampToDefault() {
        properties.setTokenFallbackTtlSeconds(0);
        assertThat(tokenClient.getEffectiveFallbackTtlSeconds()).isEqualTo(1800L);

        properties.setTokenFallbackTtlSeconds(-500);
        assertThat(tokenClient.getEffectiveFallbackTtlSeconds()).isEqualTo(1800L);

        String opaqueToken = "opaque_token_zero_fallback";
        tokenClient.getValidToken(() -> MisaAuthResponse.builder().success(true).data(opaqueToken).build());

        MisaTokenClient.CachedToken cached = tokenClient.getCachedTokenSnapshot();
        assertThat(cached).isNotNull();
        long expectedMinExp = Instant.now().plusSeconds(1790).getEpochSecond();
        long expectedMaxExp = Instant.now().plusSeconds(1810).getEpochSecond();
        assertThat(cached.expiresAt().getEpochSecond()).isBetween(expectedMinExp, expectedMaxExp);
    }

    @Test
    @DisplayName("CachedToken: malformed token has finite expiration and is not cached indefinitely")
    void malformedTokenMustNotBeCachedIndefinitely() {
        properties.setTokenFallbackTtlSeconds(600); // 10 minutes
        String opaqueToken = "malformed_token";

        tokenClient.getValidToken(() -> MisaAuthResponse.builder().success(true).data(opaqueToken).build());

        MisaTokenClient.CachedToken cached = tokenClient.getCachedTokenSnapshot();
        assertThat(cached).isNotNull();
        assertThat(cached.expiresAt()).isBeforeOrEqualTo(Instant.now().plusSeconds(601));
    }

    @Test
    @DisplayName("Auth failure or empty token should throw InvoiceProviderAuthException")
    void authFailureShouldThrowException() {
        assertThatThrownBy(() -> tokenClient.getValidToken(() ->
                MisaAuthResponse.builder().success(false).errorCode("InvalidCredentials").build()
        )).isInstanceOf(InvoiceProviderAuthException.class)
                .hasMessageContaining("InvalidCredentials");

        assertThatThrownBy(() -> tokenClient.getValidToken(() ->
                MisaAuthResponse.builder().success(true).data("   ").build()
        )).isInstanceOf(InvoiceProviderAuthException.class);
    }
}
