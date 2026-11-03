package com.shop.backend.einvoice.worker;

import com.shop.backend.einvoice.provider.misa.MisaProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceOutboxSchedulerConfigTest {

    @Test
    @DisplayName("Worst-case HTTP duration accounts for 3-call sequential chain (Business 401 + Auth + Retried Business)")
    void leaseDuration_accountsForWorstCase3CallHttpChain() {
        int connectTimeout = 5;
        int readTimeout = 25;
        long margin = 15L;

        long minDuration = InvoiceOutboxSchedulerConfig.calculateMinimumLeaseDuration(connectTimeout, readTimeout, margin);

        // Per-request timeout = 5 + 25 = 30s
        // Worst-case 3 HTTP calls = Business 401 (30s) + Auth (30s) + Retried Business (30s) = 90s
        // Minimum lease duration = 90s + 15s margin = 105s
        assertThat(minDuration).isEqualTo(105L);
    }

    @Test
    @DisplayName("Validation fails when lease duration is below minimum effective duration for 3-call chain")
    void leaseDuration_belowMinimum_failsValidation() {
        MisaProperties misaProps = new MisaProperties();
        misaProps.setEnabled(true);
        misaProps.setConnectTimeoutSeconds(5);
        misaProps.setReadTimeoutSeconds(25);

        InvoiceOutboxProperties outboxProps = new InvoiceOutboxProperties();
        outboxProps.setLeaseDurationSeconds(100L); // 100s < 105s required

        InvoiceOutboxSchedulerConfig config = new InvoiceOutboxSchedulerConfig(outboxProps, misaProps);

        assertThatThrownBy(config::validateLeaseConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be at least 105 seconds to cover worst-case 3-request sequential MISA HTTP chain");
    }

    @Test
    @DisplayName("Validation passes when lease duration exactly matches minimum effective duration (105s)")
    void leaseDuration_exactMinimum_passesValidation() {
        MisaProperties misaProps = new MisaProperties();
        misaProps.setEnabled(true);
        misaProps.setConnectTimeoutSeconds(5);
        misaProps.setReadTimeoutSeconds(25);

        InvoiceOutboxProperties outboxProps = new InvoiceOutboxProperties();
        outboxProps.setLeaseDurationSeconds(105L); // exactly 105s

        InvoiceOutboxSchedulerConfig config = new InvoiceOutboxSchedulerConfig(outboxProps, misaProps);

        assertThatCode(config::validateLeaseConfiguration).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Validation passes with default 120s lease duration providing comfortable margin")
    void leaseDuration_default120s_passesValidation() {
        MisaProperties misaProps = new MisaProperties();
        misaProps.setEnabled(true);
        misaProps.setConnectTimeoutSeconds(5);
        misaProps.setReadTimeoutSeconds(25);

        InvoiceOutboxProperties outboxProps = new InvoiceOutboxProperties();
        // Default is 120s, which provides 120 - 105 = 15s buffer over worst-case 3-call HTTP chain
        assertThat(outboxProps.getLeaseDurationSeconds()).isEqualTo(120L);

        InvoiceOutboxSchedulerConfig config = new InvoiceOutboxSchedulerConfig(outboxProps, misaProps);

        assertThatCode(config::validateLeaseConfiguration).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Validation is skipped when MISA provider is not enabled")
    void leaseDuration_misaDisabled_skipsValidation() {
        MisaProperties misaProps = new MisaProperties();
        misaProps.setEnabled(false);

        InvoiceOutboxProperties outboxProps = new InvoiceOutboxProperties();
        outboxProps.setLeaseDurationSeconds(10L); // small value would otherwise fail

        InvoiceOutboxSchedulerConfig config = new InvoiceOutboxSchedulerConfig(outboxProps, misaProps);

        assertThatCode(config::validateLeaseConfiguration).doesNotThrowAnyException();
    }
}
