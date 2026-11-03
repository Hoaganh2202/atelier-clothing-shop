package com.shop.backend.einvoice.worker;

import com.shop.backend.einvoice.exception.OutboxConcurrencyException;
import com.shop.backend.einvoice.model.AttemptOutcome;
import com.shop.backend.einvoice.model.InvoiceOutbox;
import com.shop.backend.einvoice.provider.InvoiceProvider;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryRequest;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryResult;
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
public class InvoiceReconciliationWorker {

    private static final Logger log = LoggerFactory.getLogger(InvoiceReconciliationWorker.class);
    private static final String EVENT_RECONCILE_STATUS = "RECONCILE_STATUS";

    private final InvoiceOutboxService invoiceOutboxService;
    private final InvoiceProvider invoiceProvider;
    private final InvoiceOutboxProperties properties;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${app.einvoice.outbox.reconciliation-poll-interval-ms:15000}")
    public void scheduledReconcile() {
        if (!properties.isWorkerEnabled() || shuttingDown.get()) {
            return;
        }
        processReconciliationBatch(properties.getWorkerId());
    }

    public int processReconciliationBatch() {
        return processReconciliationBatch(properties.getWorkerId());
    }

    /**
     * Ensures all ISSUANCE_UNKNOWN invoices have a RECONCILE_STATUS outbox event,
     * claims due RECONCILE_STATUS events, and executes provider status inquiry outside of any DB transaction.
     *
     * @param workerId unique identifier of the claiming worker instance
     * @return number of reconciliation events processed
     */
    public int processReconciliationBatch(String workerId) {
        if (shuttingDown.get()) {
            log.info("Reconciliation worker [{}] is shutting down; skipping batch claim", workerId);
            return 0;
        }

        invoiceOutboxService.enqueueMissingReconcileEvents();

        int maxItems = properties.getBatchSize();
        int processedCount = 0;

        for (int i = 0; i < maxItems; i++) {
            if (shuttingDown.get()) {
                log.info("Reconciliation worker [{}] shutting down mid-batch; stopping further claims", workerId);
                break;
            }

            // Claim single item right before processing to eliminate lease aging while waiting in batch queue
            List<InvoiceOutbox> claimedEvents = invoiceOutboxService.claimEventsByType(
                    EVENT_RECONCILE_STATUS,
                    workerId,
                    properties.getLeaseDuration(),
                    1);

            if (claimedEvents.isEmpty()) {
                break;
            }

            InvoiceOutbox claimed = claimedEvents.get(0);
            if (processClaimedReconcileEvent(claimed, workerId)) {
                processedCount++;
            }
        }
        return processedCount;
    }

    public boolean processClaimedReconcileEvent(InvoiceOutbox claimed, String workerId) {
        Long outboxId = claimed.getId();
        String leaseToken = claimed.getLeaseToken();
        String correlationKey = claimed.getEventKey();

        ProviderInquiryRequest inquiryRequest;
        try {
            inquiryRequest = invoiceOutboxService.prepareReconcileDispatch(outboxId, leaseToken);
        } catch (OutboxConcurrencyException e) {
            log.warn("Stale lease detected before reconcile inquiry for outboxId={}, correlationKey={}, workerId={}: {}",
                    outboxId, correlationKey, workerId, e.getMessage());
            return false;
        }

        if (inquiryRequest == null) {
            log.info("Skipped provider reconcile inquiry for outboxId={}, correlationKey={} (invoice already ISSUED)",
                    outboxId, correlationKey);
            return true;
        }

        String refId = inquiryRequest.getRefId();
        log.info("Inquiring invoice status on provider [{}] outside DB transaction: outboxId={}, correlationKey={}, invoiceId={}, refId={}, attempt={}",
                invoiceProvider.getProviderName(), outboxId, correlationKey, inquiryRequest.getInvoiceId(), refId, claimed.getAttemptCount());

        ProviderInquiryResult inquiryResult = invokeInquirySafely(inquiryRequest, outboxId, correlationKey, refId);

        Duration retryDelay = properties.calculateBackoffDelay(claimed.getAttemptCount());
        try {
            invoiceOutboxService.finalizeReconcileOutcome(
                    outboxId,
                    leaseToken,
                    invoiceProvider.getProviderName(),
                    inquiryRequest,
                    inquiryResult,
                    properties.getMaxReconcileAttempts(),
                    retryDelay);

            log.info("Finalized RECONCILE_STATUS outcome for outboxId={}, correlationKey={}, invoiceId={}, refId={}, found={}, isIssued={}, outcome={}",
                    outboxId, correlationKey, inquiryRequest.getInvoiceId(), refId,
                    inquiryResult.isFound(), inquiryResult.isIssued(), inquiryResult.getAttemptOutcome());
            return true;
        } catch (OutboxConcurrencyException e) {
            log.warn("Stale lease rejected during finalizeReconcileOutcome for outboxId={}, correlationKey={}, refId={}, workerId={}: {}",
                    outboxId, correlationKey, refId, workerId, e.getMessage());
            return false;
        }
    }

    private ProviderInquiryResult invokeInquirySafely(
            ProviderInquiryRequest inquiryRequest,
            Long outboxId,
            String correlationKey,
            String refId) {
        try {
            ProviderInquiryResult result = invoiceProvider.inquiryInvoiceStatus(inquiryRequest);
            if (result != null) {
                return result;
            }
            return ProviderInquiryResult.builder()
                    .found(false)
                    .refId(refId)
                    .attemptOutcome(AttemptOutcome.FAILED)
                    .rawResponse("Provider returned null inquiry result")
                    .isIssued(false)
                    .isRejected(false)
                    .build();
        } catch (InvoiceProviderTimeoutException e) {
            log.warn("Provider timeout exception during inquiryInvoiceStatus for outboxId={}, correlationKey={}, refId={}: {}",
                    outboxId, correlationKey, refId, e.getMessage());
            return ProviderInquiryResult.builder()
                    .found(false)
                    .refId(refId)
                    .attemptOutcome(AttemptOutcome.TIMEOUT_UNKNOWN)
                    .rawResponse(e.getMessage())
                    .isIssued(false)
                    .isRejected(false)
                    .build();
        } catch (InvoiceProviderException e) {
            log.warn("Provider exception during inquiryInvoiceStatus for outboxId={}, correlationKey={}, refId={}: {}",
                    outboxId, correlationKey, refId, e.getMessage());
            return ProviderInquiryResult.builder()
                    .found(false)
                    .refId(refId)
                    .attemptOutcome(e.getAttemptOutcome() != null ? e.getAttemptOutcome() : AttemptOutcome.FAILED)
                    .rawResponse(e.getMessage())
                    .isIssued(false)
                    .isRejected(false)
                    .build();
        } catch (Exception e) {
            log.error("Unexpected runtime error during inquiryInvoiceStatus for outboxId={}, correlationKey={}, refId={}: {}",
                    outboxId, correlationKey, refId, e.getMessage(), e);
            return ProviderInquiryResult.builder()
                    .found(false)
                    .refId(refId)
                    .attemptOutcome(AttemptOutcome.FAILED)
                    .rawResponse(e.getMessage())
                    .isIssued(false)
                    .isRejected(false)
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
            log.info("InvoiceReconciliationWorker [{}] marked as shutting down; no new batches will be claimed", properties.getWorkerId());
        }
    }

    public void resetShutdownState() {
        shuttingDown.set(false);
    }

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }
}
