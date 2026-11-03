package com.shop.backend.einvoice.worker;

import com.shop.backend.einvoice.exception.OutboxConcurrencyException;
import com.shop.backend.einvoice.model.AttemptOutcome;
import com.shop.backend.einvoice.model.InvoiceOutbox;
import com.shop.backend.einvoice.provider.InvoiceProvider;
import com.shop.backend.einvoice.provider.dto.ProviderIssueRequest;
import com.shop.backend.einvoice.provider.dto.ProviderIssueResult;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderTimeoutException;
import com.shop.backend.einvoice.service.InvoiceOutboxService;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvoiceOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(InvoiceOutboxWorker.class);
    private static final String EVENT_ISSUE_INVOICE = "ISSUE_INVOICE";

    private final InvoiceOutboxService invoiceOutboxService;
    private final InvoiceProvider invoiceProvider;
    private final InvoiceOutboxProperties properties;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${app.einvoice.outbox.poll-interval-ms:5000}")
    public void scheduledPoll() {
        if (!properties.isWorkerEnabled() || shuttingDown.get()) {
            return;
        }
        processNextBatch(properties.getWorkerId());
    }

    public int processNextBatch() {
        return processNextBatch(properties.getWorkerId());
    }

    /**
     * Claims a batch of due ISSUE_INVOICE outbox events and dispatches each event outside of any DB transaction.
     *
     * @param workerId unique identifier of the claiming worker instance
     * @return number of events processed in this batch
     */
    public int processNextBatch(String workerId) {
        if (shuttingDown.get()) {
            log.info("Outbox worker [{}] is shutting down; skipping batch claim", workerId);
            return 0;
        }

        int maxItems = properties.getBatchSize();
        int processedCount = 0;

        for (int i = 0; i < maxItems; i++) {
            if (shuttingDown.get()) {
                log.info("Worker [{}] shutting down mid-batch; stopping further claims", workerId);
                break;
            }

            // Claim single item right before processing to eliminate lease aging while waiting in batch queue
            List<InvoiceOutbox> claimedEvents = invoiceOutboxService.claimEventsByType(
                    EVENT_ISSUE_INVOICE,
                    workerId,
                    properties.getLeaseDuration(),
                    1);

            if (claimedEvents.isEmpty()) {
                break;
            }

            InvoiceOutbox claimed = claimedEvents.get(0);
            if (processClaimedIssueEvent(claimed, workerId)) {
                processedCount++;
            }
        }
        return processedCount;
    }

    public boolean processClaimedIssueEvent(InvoiceOutbox claimed, String workerId) {
        Long outboxId = claimed.getId();
        String leaseToken = claimed.getLeaseToken();
        String correlationKey = claimed.getEventKey();

        ProviderIssueRequest issueRequest;
        try {
            issueRequest = invoiceOutboxService.prepareIssueDispatch(outboxId, leaseToken);
        } catch (OutboxConcurrencyException e) {
            log.warn("Stale lease detected before provider dispatch for outboxId={}, correlationKey={}, workerId={}: {}",
                    outboxId, correlationKey, workerId, e.getMessage());
            return false;
        }

        if (issueRequest == null) {
            log.info("Skipped provider issue dispatch for outboxId={}, correlationKey={} (already finalized or routed to reconciliation)",
                    outboxId, correlationKey);
            return true;
        }

        String refId = issueRequest.getRefId();
        log.info("Dispatching ISSUE_INVOICE to provider [{}] outside DB transaction: outboxId={}, correlationKey={}, invoiceId={}, refId={}, attempt={}",
                invoiceProvider.getProviderName(), outboxId, correlationKey, issueRequest.getInvoiceId(), refId, claimed.getAttemptCount());

        ProviderIssueResult issueResult = invokeProviderSafely(issueRequest, outboxId, correlationKey, refId);

        Duration retryDelay = properties.calculateBackoffDelay(claimed.getAttemptCount());
        try {
            invoiceOutboxService.finalizeIssueOutcome(
                    outboxId,
                    leaseToken,
                    invoiceProvider.getProviderName(),
                    issueRequest,
                    issueResult,
                    properties.getMaxAttempts(),
                    retryDelay);

            log.info("Finalized ISSUE_INVOICE outcome for outboxId={}, correlationKey={}, invoiceId={}, refId={}, outcome={}, success={}",
                    outboxId, correlationKey, issueRequest.getInvoiceId(), refId, issueResult.getAttemptOutcome(), issueResult.isSuccess());
            return true;
        } catch (OutboxConcurrencyException e) {
            log.warn("Stale lease rejected during finalizeIssueOutcome for outboxId={}, correlationKey={}, refId={}, workerId={}: {}",
                    outboxId, correlationKey, refId, workerId, e.getMessage());
            return false;
        }
    }

    private ProviderIssueResult invokeProviderSafely(
            ProviderIssueRequest issueRequest,
            Long outboxId,
            String correlationKey,
            String refId) {
        try {
            ProviderIssueResult result = invoiceProvider.issueInvoice(issueRequest);
            if (result != null) {
                return result;
            }
            return ProviderIssueResult.builder()
                    .success(false)
                    .attemptOutcome(AttemptOutcome.FAILED)
                    .errorCode("EMPTY_PROVIDER_RESULT")
                    .errorMessage("Provider returned null result")
                    .retryable(true)
                    .build();
        } catch (InvoiceProviderTimeoutException e) {
            log.warn("Provider timeout exception during issueInvoice for outboxId={}, correlationKey={}, refId={}: {}",
                    outboxId, correlationKey, refId, e.getMessage());
            return ProviderIssueResult.builder()
                    .success(false)
                    .attemptOutcome(AttemptOutcome.TIMEOUT_UNKNOWN)
                    .errorCode(e.getErrorCode())
                    .errorMessage(e.getMessage())
                    .rawResponse(e.getRawResponse())
                    .retryable(true)
                    .build();
        } catch (InvoiceProviderException e) {
            log.warn("Provider exception during issueInvoice for outboxId={}, correlationKey={}, refId={}, retryable={}: {}",
                    outboxId, correlationKey, refId, e.isRetryable(), e.getMessage());
            return ProviderIssueResult.builder()
                    .success(false)
                    .attemptOutcome(e.getAttemptOutcome() != null ? e.getAttemptOutcome() : AttemptOutcome.FAILED)
                    .errorCode(e.getErrorCode())
                    .errorMessage(e.getMessage())
                    .rawResponse(e.getRawResponse())
                    .retryable(e.isRetryable())
                    .build();
        } catch (Exception e) {
            log.error("Unexpected runtime error during issueInvoice for outboxId={}, correlationKey={}, refId={}: {}",
                    outboxId, correlationKey, refId, e.getMessage(), e);
            return ProviderIssueResult.builder()
                    .success(false)
                    .attemptOutcome(AttemptOutcome.FAILED)
                    .errorCode("UNEXPECTED_PROVIDER_ERROR")
                    .errorMessage(e.getMessage())
                    .retryable(true)
                    .build();
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        initiateShutdown();
    }

    @PreDestroy
    public void initiateShutdown() {
        if (shuttingDown.compareAndSet(false, true)) {
            log.info("InvoiceOutboxWorker [{}] marked as shutting down; no new batches will be claimed", properties.getWorkerId());
        }
    }

    public void resetShutdownState() {
        shuttingDown.set(false);
    }

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }
}
