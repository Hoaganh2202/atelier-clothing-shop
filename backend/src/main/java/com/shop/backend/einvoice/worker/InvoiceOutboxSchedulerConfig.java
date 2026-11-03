package com.shop.backend.einvoice.worker;

import com.shop.backend.einvoice.provider.misa.MisaProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class InvoiceOutboxSchedulerConfig {

    public static final int WORST_CASE_HTTP_CALL_COUNT = 3;
    public static final long MIN_TX3_SAFETY_MARGIN_SECONDS = 15L;

    private final InvoiceOutboxProperties outboxProperties;
    private final MisaProperties misaProperties;

    public InvoiceOutboxSchedulerConfig(
            InvoiceOutboxProperties outboxProperties,
            @Autowired(required = false) MisaProperties misaProperties) {
        this.outboxProperties = outboxProperties;
        this.misaProperties = misaProperties;
    }

    /**
     * Calculates the minimum required lease duration based on sequential HTTP requests in the worst-case HTTP 401 retry flow:
     * 1. Initial Business call with cached token returning HTTP 401 (connectTimeout + readTimeout)
     * 2. Authentication call to acquire fresh JWT token (connectTimeout + readTimeout)
     * 3. Retried Business call with fresh token (connectTimeout + readTimeout)
     * Plus safety margin for Phase 3 DB transaction finalization.
     */
    public static long calculateMinimumLeaseDuration(int connectTimeoutSeconds, int readTimeoutSeconds, long safetyMarginSeconds) {
        long perRequestTimeout = (long) connectTimeoutSeconds + readTimeoutSeconds;
        long totalHttpChainTimeout = WORST_CASE_HTTP_CALL_COUNT * perRequestTimeout;
        return totalHttpChainTimeout + safetyMarginSeconds;
    }

    @PostConstruct
    public void validateLeaseConfiguration() {
        if (misaProperties != null && misaProperties.isEnabled()) {
            int connectTimeout = misaProperties.getConnectTimeoutSeconds();
            int readTimeout = misaProperties.getReadTimeoutSeconds();
            long singleHttpTimeout = (long) connectTimeout + readTimeout;
            long minRequiredLeaseDuration = calculateMinimumLeaseDuration(
                    connectTimeout,
                    readTimeout,
                    MIN_TX3_SAFETY_MARGIN_SECONDS);

            if (outboxProperties.getLeaseDurationSeconds() < minRequiredLeaseDuration) {
                throw new IllegalStateException(String.format(
                        "Invalid E-Invoice configuration: app.einvoice.outbox.lease-duration-seconds (%d) "
                                + "must be at least %d seconds to cover worst-case 3-request sequential MISA HTTP chain "
                                + "(Business 401: %ds + Auth: %ds + Retry: %ds = %ds) plus %ds TX3 finalize margin",
                        outboxProperties.getLeaseDurationSeconds(),
                        minRequiredLeaseDuration,
                        singleHttpTimeout,
                        singleHttpTimeout,
                        singleHttpTimeout,
                        singleHttpTimeout * WORST_CASE_HTTP_CALL_COUNT,
                        MIN_TX3_SAFETY_MARGIN_SECONDS));
            }
        }
    }
}
