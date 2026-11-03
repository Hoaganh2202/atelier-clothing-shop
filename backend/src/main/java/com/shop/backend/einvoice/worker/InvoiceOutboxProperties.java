package com.shop.backend.einvoice.worker;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.einvoice.outbox")
public class InvoiceOutboxProperties {

    private boolean workerEnabled = false;
    private String workerId = "einv-worker-" + UUID.randomUUID().toString().substring(0, 8);
    private int batchSize = 10;
    private long pollIntervalMs = 5000L;
    private long reconciliationPollIntervalMs = 15000L;
    private long leaseReaperIntervalMs = 30000L;
    private long leaseDurationSeconds = 120L;
    private int maxAttempts = 5;
    private int maxReconcileAttempts = 10;
    private long initialBackoffSeconds = 10L;
    private double backoffMultiplier = 2.0;
    private long maxBackoffSeconds = 900L;

    public Duration getLeaseDuration() {
        return Duration.ofSeconds(Math.max(1L, leaseDurationSeconds));
    }

    /**
     * Computes exponential backoff delay for the given 1-based attempt number, bounded by maxBackoffSeconds.
     */
    public Duration calculateBackoffDelay(int attemptCount) {
        int exponent = Math.max(0, attemptCount - 1);
        double rawSeconds = initialBackoffSeconds * Math.pow(Math.max(1.0, backoffMultiplier), exponent);
        long boundedSeconds = Math.min(maxBackoffSeconds, Math.max(1L, Math.round(rawSeconds)));
        return Duration.ofSeconds(boundedSeconds);
    }

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getReconciliationPollIntervalMs() {
        return reconciliationPollIntervalMs;
    }

    public void setReconciliationPollIntervalMs(long reconciliationPollIntervalMs) {
        this.reconciliationPollIntervalMs = reconciliationPollIntervalMs;
    }

    public long getLeaseReaperIntervalMs() {
        return leaseReaperIntervalMs;
    }

    public void setLeaseReaperIntervalMs(long leaseReaperIntervalMs) {
        this.leaseReaperIntervalMs = leaseReaperIntervalMs;
    }

    public long getLeaseDurationSeconds() {
        return leaseDurationSeconds;
    }

    public void setLeaseDurationSeconds(long leaseDurationSeconds) {
        this.leaseDurationSeconds = leaseDurationSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getMaxReconcileAttempts() {
        return maxReconcileAttempts;
    }

    public void setMaxReconcileAttempts(int maxReconcileAttempts) {
        this.maxReconcileAttempts = maxReconcileAttempts;
    }

    public long getInitialBackoffSeconds() {
        return initialBackoffSeconds;
    }

    public void setInitialBackoffSeconds(long initialBackoffSeconds) {
        this.initialBackoffSeconds = initialBackoffSeconds;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public void setBackoffMultiplier(double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }

    public long getMaxBackoffSeconds() {
        return maxBackoffSeconds;
    }

    public void setMaxBackoffSeconds(long maxBackoffSeconds) {
        this.maxBackoffSeconds = maxBackoffSeconds;
    }

    @Override
    public String toString() {
        return "InvoiceOutboxProperties{" +
                "workerEnabled=" + workerEnabled +
                ", workerId='" + workerId + '\'' +
                ", batchSize=" + batchSize +
                ", pollIntervalMs=" + pollIntervalMs +
                ", reconciliationPollIntervalMs=" + reconciliationPollIntervalMs +
                ", leaseReaperIntervalMs=" + leaseReaperIntervalMs +
                ", leaseDurationSeconds=" + leaseDurationSeconds +
                ", maxAttempts=" + maxAttempts +
                ", maxReconcileAttempts=" + maxReconcileAttempts +
                ", initialBackoffSeconds=" + initialBackoffSeconds +
                ", backoffMultiplier=" + backoffMultiplier +
                ", maxBackoffSeconds=" + maxBackoffSeconds +
                '}';
    }
}
