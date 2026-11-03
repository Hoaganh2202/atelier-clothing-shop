package com.shop.backend.einvoice.service.impl;

import com.shop.backend.einvoice.exception.InvoiceNotFoundException;
import com.shop.backend.einvoice.exception.OutboxConcurrencyException;
import com.shop.backend.einvoice.model.AttemptOutcome;
import com.shop.backend.einvoice.model.BuyerInvoiceProfile;
import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceAttempt;
import com.shop.backend.einvoice.model.InvoiceItem;
import com.shop.backend.einvoice.model.InvoiceItemType;
import com.shop.backend.einvoice.model.InvoiceOutbox;
import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.model.OutboxStatus;
import com.shop.backend.einvoice.model.SellerInvoiceProfile;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryRequest;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryResult;
import com.shop.backend.einvoice.provider.dto.ProviderInvoiceItemDto;
import com.shop.backend.einvoice.provider.dto.ProviderIssueRequest;
import com.shop.backend.einvoice.provider.dto.ProviderIssueResult;
import com.shop.backend.einvoice.provider.misa.MisaPayloadMapper;
import com.shop.backend.einvoice.repository.InvoiceAttemptRepository;
import com.shop.backend.einvoice.repository.InvoiceItemRepository;
import com.shop.backend.einvoice.repository.InvoiceOutboxRepository;
import com.shop.backend.einvoice.repository.InvoiceRepository;
import com.shop.backend.einvoice.service.InvoiceOutboxService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.shop.backend.order.OrderStatus;
import java.sql.SQLException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InvoiceOutboxServiceImpl implements InvoiceOutboxService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceOutboxServiceImpl.class);
    private static final String EVENT_ISSUE_INVOICE = "ISSUE_INVOICE";
    private static final String EVENT_RECONCILE_STATUS = "RECONCILE_STATUS";
    private static final int MAX_ATTEMPT_ERROR_LENGTH = 500;

    private final InvoiceOutboxRepository invoiceOutboxRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final InvoiceAttemptRepository invoiceAttemptRepository;
    private final TransactionTemplate transactionTemplate;
    private final com.shop.backend.einvoice.service.InvoiceBalanceValidationService invoiceBalanceValidationService;

    @org.springframework.beans.factory.annotation.Autowired
    public InvoiceOutboxServiceImpl(
            InvoiceOutboxRepository invoiceOutboxRepository,
            InvoiceRepository invoiceRepository,
            InvoiceItemRepository invoiceItemRepository,
            InvoiceAttemptRepository invoiceAttemptRepository,
            PlatformTransactionManager transactionManager,
            com.shop.backend.einvoice.service.InvoiceBalanceValidationService invoiceBalanceValidationService) {
        this.invoiceOutboxRepository = invoiceOutboxRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.invoiceAttemptRepository = invoiceAttemptRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.invoiceBalanceValidationService = invoiceBalanceValidationService;
    }

    private LocalDateTime getDatabaseTime() {
        return invoiceOutboxRepository.getCurrentDatabaseTime();
    }

    @Override
    @Transactional
    public InvoiceOutbox createEvent(String eventType, String eventKey, Long invoiceId, String payload) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be null or blank");
        }
        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalArgumentException("eventKey must not be null or blank");
        }
        if (invoiceId == null) {
            throw new IllegalArgumentException("invoiceId must not be null");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId));

        InvoiceOutbox event = InvoiceOutbox.builder()
                .eventType(eventType)
                .eventKey(eventKey)
                .invoice(invoice)
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .attemptCount(0)
                .availableAt(getDatabaseTime().minusSeconds(5))
                .build();

        return invoiceOutboxRepository.save(event);
    }

    @Override
    @Transactional
    public List<InvoiceOutbox> claimEvents(String workerId, Duration leaseDuration, int batchSize) {
        validateClaimArguments(workerId, leaseDuration, batchSize);

        LocalDateTime now = getDatabaseTime();
        List<InvoiceOutbox> availableEvents = invoiceOutboxRepository.claimAvailableEvents(now, batchSize);
        return leaseClaimedEvents(availableEvents, workerId, now, leaseDuration);
    }

    @Override
    @Transactional
    public List<InvoiceOutbox> claimEventsByType(String eventType, String workerId, Duration leaseDuration, int batchSize) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be null or blank");
        }
        validateClaimArguments(workerId, leaseDuration, batchSize);

        LocalDateTime now = getDatabaseTime();
        List<InvoiceOutbox> availableEvents = invoiceOutboxRepository.claimAvailableEventsByType(eventType, now, batchSize);
        return leaseClaimedEvents(availableEvents, workerId, now, leaseDuration);
    }

    @Override
    @Transactional
    public void completeEvent(Long outboxId, String leaseToken) {
        InvoiceOutbox event = getEvent(outboxId);
        LocalDateTime now = getDatabaseTime();
        validateLeaseOwnership(event, leaseToken, now, "complete");

        event.setStatus(OutboxStatus.PROCESSED);
        event.setProcessedAt(now);
        event.setLockedUntil(null);
        event.setLockedBy(null);
        event.setLeaseToken(null);
        invoiceOutboxRepository.save(event);
    }

    @Override
    @Transactional
    public void failEvent(Long outboxId, String leaseToken, String errorMessage, boolean canRetry, Duration retryDelay) {
        InvoiceOutbox event = getEvent(outboxId);
        LocalDateTime now = getDatabaseTime();
        validateLeaseOwnership(event, leaseToken, now, "fail");

        event.setLastError(errorMessage);
        if (canRetry && retryDelay != null && !retryDelay.isNegative()) {
            event.setStatus(OutboxStatus.PENDING);
            event.setAvailableAt(now.plus(retryDelay));
            event.setLockedUntil(null);
            event.setLockedBy(null);
            event.setLeaseToken(null);
        } else {
            event.setStatus(OutboxStatus.FAILED);
            event.setLockedUntil(null);
            event.setLockedBy(null);
            event.setLeaseToken(null);
        }
        invoiceOutboxRepository.save(event);
    }

    @Override
    @Transactional
    public void releaseUnprocessedLease(Long outboxId, String leaseToken) {
        InvoiceOutbox event = getEvent(outboxId);
        if (event.getStatus() != OutboxStatus.PROCESSING) {
            return;
        }
        if (event.getLeaseToken() == null || !event.getLeaseToken().equals(leaseToken)) {
            return;
        }

        event.setStatus(OutboxStatus.PENDING);
        event.setAttemptCount(Math.max(0, event.getAttemptCount() - 1));
        event.setLockedUntil(null);
        event.setLockedBy(null);
        event.setLeaseToken(null);
        invoiceOutboxRepository.save(event);
    }

    @Override
    @Transactional
    public ProviderIssueRequest prepareIssueDispatch(Long outboxId, String leaseToken) {
        InvoiceOutbox event = getEvent(outboxId);
        LocalDateTime now = getDatabaseTime();
        validateLeaseOwnership(event, leaseToken, now, "prepare issue dispatch");

        Invoice invoice = invoiceRepository.findById(event.getInvoice().getId())
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + event.getInvoice().getId()));

        if (invoice.getDocumentStatus() == DocumentStatus.ISSUED) {
            event.setStatus(OutboxStatus.PROCESSED);
            event.setProcessedAt(now);
            event.setLockedUntil(null);
            event.setLockedBy(null);
            event.setLeaseToken(null);
            invoiceOutboxRepository.save(event);
            return null;
        }

        if (invoice.getDocumentStatus() == DocumentStatus.ISSUANCE_UNKNOWN
                || invoice.getDocumentStatus() == DocumentStatus.RECONCILING) {
            event.setStatus(OutboxStatus.FAILED);
            event.setLastError("Invoice is in " + invoice.getDocumentStatus()
                    + "; skipping re-publish and routing to reconciliation");
            event.setLockedUntil(null);
            event.setLockedBy(null);
            event.setLeaseToken(null);
            invoiceOutboxRepository.save(event);
            ensureReconcileEventExists(invoice, now, "UNKNOWN_STATE_GUARD");
            return null;
        }

        if (invoice.getOrder() != null && invoice.getOrder().getStatus() == OrderStatus.CANCELLED) {
            event.setStatus(OutboxStatus.FAILED);
            event.setLastError("Order is CANCELLED; skipping invoice issuance");
            event.setLockedUntil(null);
            event.setLockedBy(null);
            event.setLeaseToken(null);
            invoiceOutboxRepository.save(event);
            Invoice lockedInvoice = invoiceRepository.findByIdForUpdate(invoice.getId())
                    .orElse(invoice);
            lockedInvoice.setDocumentStatus(DocumentStatus.ISSUE_FAILED);
            invoiceRepository.save(lockedInvoice);
            return null;
        }

        if (invoice.getInvoiceType() != InvoiceType.ORIGINAL) {
            log.warn("Invoice {} has unsupported type {} for provider dispatch in current milestone; skipping dispatch",
                    invoice.getId(), invoice.getInvoiceType());
            event.setStatus(OutboxStatus.FAILED);
            event.setLastError("Unsupported invoice type for provider dispatch: " + invoice.getInvoiceType());
            event.setLockedUntil(null);
            event.setLockedBy(null);
            event.setLeaseToken(null);
            invoiceOutboxRepository.save(event);

            if (invoice.getInvoiceType() == InvoiceType.ADJUSTMENT) {
                if (invoiceBalanceValidationService == null) {
                    throw new IllegalStateException("InvoiceBalanceValidationService is required to process and release balance for ADJUSTMENT invoice: " + invoice.getId());
                }
                if (invoice.getDocumentStatus() == DocumentStatus.DRAFT
                        || invoice.getDocumentStatus() == DocumentStatus.PENDING_ISSUE) {
                    invoiceBalanceValidationService.releaseAdjustmentBalance(invoice.getId());
                } else if (invoice.getDocumentStatus() != DocumentStatus.ISSUE_FAILED) {
                    Invoice lockedAdj = invoiceRepository.findByIdForUpdate(invoice.getId()).orElse(invoice);
                    lockedAdj.setDocumentStatus(DocumentStatus.ISSUE_FAILED);
                    invoiceRepository.save(lockedAdj);
                }
            } else {
                Invoice lockedRep = invoiceRepository.findByIdForUpdate(invoice.getId()).orElse(invoice);
                lockedRep.setDocumentStatus(DocumentStatus.ISSUE_FAILED);
                invoiceRepository.save(lockedRep);
            }
            return null;
        }

        Invoice lockedInvoice = invoiceRepository.findByIdForUpdate(invoice.getId())
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoice.getId()));
        lockedInvoice.setDocumentStatus(DocumentStatus.ISSUING);
        invoiceRepository.save(lockedInvoice);

        List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(lockedInvoice.getId());
        return buildProviderIssueRequest(lockedInvoice, items);
    }

    @Override
    @Transactional
    public void finalizeIssueOutcome(
            Long outboxId,
            String leaseToken,
            String providerName,
            ProviderIssueRequest request,
            ProviderIssueResult result,
            int maxAttempts,
            Duration retryDelay) {

        InvoiceOutbox event = getEvent(outboxId);
        LocalDateTime now = getDatabaseTime();
        validateLeaseOwnership(event, leaseToken, now, "finalize issue outcome");

        Invoice invoice = invoiceRepository.findByIdForUpdate(event.getInvoice().getId())
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + event.getInvoice().getId()));

        AttemptOutcome outcome = result.getAttemptOutcome() != null
                ? result.getAttemptOutcome()
                : (result.isSuccess() ? AttemptOutcome.SUCCESS : AttemptOutcome.FAILED);

        String sanitizedReq = String.format("{\"invoiceId\":%d,\"refId\":\"%s\",\"invoiceType\":\"%s\"}",
                invoice.getId(), request.getRefId(), request.getInvoiceType());

        recordAttempt(
                invoice,
                providerName != null ? providerName : "MISA",
                "/api/v3/itg/invoice/publish",
                EVENT_ISSUE_INVOICE,
                sanitizedReq,
                result.getRawResponse(),
                result.isSuccess() ? 200 : null,
                outcome,
                result.getErrorMessage());

        if (result.isSuccess() && outcome == AttemptOutcome.SUCCESS) {
            invoice.setDocumentStatus(DocumentStatus.ISSUED);
            invoice.setProviderTransactionId(result.getTransactionId());
            invoice.setInvoiceNumber(result.getInvoiceNo());
            invoice.setInvoiceSeries(result.getInvoiceSeries());
            invoice.setTaxAuthorityCode(result.getTaxAuthorityCode());
            invoice.setOfficiallyIssuedAt(result.getIssuedAt() != null ? result.getIssuedAt() : now);
            if ("MOCK".equalsIgnoreCase(providerName)) {
                invoice.setStorageProvider("MOCK");
            }
            invoiceRepository.save(invoice);

            event.setStatus(OutboxStatus.PROCESSED);
            event.setProcessedAt(now);
            event.setLockedUntil(null);
            event.setLockedBy(null);
            event.setLeaseToken(null);
            event.setLastError(null);
            invoiceOutboxRepository.save(event);
            return;
        }

        if (outcome == AttemptOutcome.TIMEOUT_UNKNOWN) {
            invoice.setDocumentStatus(DocumentStatus.ISSUANCE_UNKNOWN);
            invoiceRepository.save(invoice);

            event.setStatus(OutboxStatus.FAILED);
            event.setLastError("Provider publish timed out (TIMEOUT_UNKNOWN); transitioned invoice to ISSUANCE_UNKNOWN for reconciliation");
            event.setLockedUntil(null);
            event.setLockedBy(null);
            event.setLeaseToken(null);
            invoiceOutboxRepository.save(event);

            ensureReconcileEventExists(invoice, now, "PUBLISH_TIMEOUT_UNKNOWN");
            return;
        }

        if (result.isRetryable() && event.getAttemptCount() < maxAttempts) {
            Duration effectiveDelay = (retryDelay != null && !retryDelay.isNegative())
                    ? retryDelay
                    : Duration.ofSeconds(10);
            invoice.setDocumentStatus(DocumentStatus.PENDING_ISSUE);
            invoiceRepository.save(invoice);

            event.setStatus(OutboxStatus.PENDING);
            event.setAvailableAt(now.plus(effectiveDelay));
            event.setLockedUntil(null);
            event.setLockedBy(null);
            event.setLeaseToken(null);
            event.setLastError(result.getErrorMessage());
            invoiceOutboxRepository.save(event);
            return;
        }

        invoice.setDocumentStatus(DocumentStatus.ISSUE_FAILED);
        invoiceRepository.save(invoice);

        event.setStatus(OutboxStatus.DEAD_LETTER);
        event.setLockedUntil(null);
        event.setLockedBy(null);
        event.setLeaseToken(null);
        event.setLastError(result.getErrorMessage());
        invoiceOutboxRepository.save(event);
    }

    @Override
    @Transactional
    public ProviderInquiryRequest prepareReconcileDispatch(Long outboxId, String leaseToken) {
        InvoiceOutbox event = getEvent(outboxId);
        LocalDateTime now = getDatabaseTime();
        validateLeaseOwnership(event, leaseToken, now, "prepare reconcile dispatch");

        Invoice invoice = invoiceRepository.findByIdForUpdate(event.getInvoice().getId())
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + event.getInvoice().getId()));

        if (invoice.getDocumentStatus() == DocumentStatus.ISSUED) {
            event.setStatus(OutboxStatus.PROCESSED);
            event.setProcessedAt(now);
            event.setLockedUntil(null);
            event.setLockedBy(null);
            event.setLeaseToken(null);
            invoiceOutboxRepository.save(event);
            return null;
        }

        if (invoice.getDocumentStatus() != DocumentStatus.RECONCILING) {
            invoice.setDocumentStatus(DocumentStatus.RECONCILING);
            invoiceRepository.save(invoice);
        }

        String deterministicRefId = MisaPayloadMapper.toDeterministicRefId(invoice.getIdempotencyKey());
        return ProviderInquiryRequest.builder()
                .invoiceId(invoice.getId())
                .refId(deterministicRefId)
                .transactionId(invoice.getProviderTransactionId())
                .invoiceNo(invoice.getInvoiceNumber())
                .invoiceSeries(invoice.getInvoiceSeries())
                .build();
    }

    @Override
    @Transactional
    public void finalizeReconcileOutcome(
            Long outboxId,
            String leaseToken,
            String providerName,
            ProviderInquiryRequest request,
            ProviderInquiryResult result,
            int maxReconcileAttempts,
            Duration retryDelay) {

        InvoiceOutbox event = getEvent(outboxId);
        LocalDateTime now = getDatabaseTime();
        validateLeaseOwnership(event, leaseToken, now, "finalize reconcile outcome");

        Invoice invoice = invoiceRepository.findByIdForUpdate(event.getInvoice().getId())
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + event.getInvoice().getId()));

        String expectedRefId = MisaPayloadMapper.toDeterministicRefId(invoice.getIdempotencyKey());
        boolean identityVerified = verifyReconciliationIdentity(invoice, expectedRefId, result);

        AttemptOutcome attemptOutcome;
        String attemptError = null;
        if (result.isFound() && !identityVerified) {
            attemptOutcome = AttemptOutcome.FAILED;
            attemptError = "Reconciliation identity mismatch for refId/transactionId";
        } else if (result.getAttemptOutcome() != null) {
            attemptOutcome = result.getAttemptOutcome();
        } else {
            attemptOutcome = AttemptOutcome.FAILED;
        }

        String sanitizedReq = String.format("{\"invoiceId\":%d,\"refId\":\"%s\"}", invoice.getId(), expectedRefId);
        recordAttempt(
                invoice,
                providerName != null ? providerName : "MISA",
                "/api/v3/itg/invoice/status",
                EVENT_RECONCILE_STATUS,
                sanitizedReq,
                result.getRawResponse(),
                attemptOutcome == AttemptOutcome.SUCCESS ? 200 : null,
                attemptOutcome,
                attemptError);

        if (attemptOutcome == AttemptOutcome.SUCCESS && result.isFound() && identityVerified && result.isIssued()) {
            invoice.setDocumentStatus(DocumentStatus.ISSUED);
            if (result.getTransactionId() != null && !result.getTransactionId().isBlank()) {
                invoice.setProviderTransactionId(result.getTransactionId());
            }
            if (result.getInvoiceNo() != null && !result.getInvoiceNo().isBlank()) {
                invoice.setInvoiceNumber(result.getInvoiceNo());
            }
            if (result.getInvoiceSeries() != null && !result.getInvoiceSeries().isBlank()) {
                invoice.setInvoiceSeries(result.getInvoiceSeries());
            }
            if (result.getTaxAuthorityCode() != null && !result.getTaxAuthorityCode().isBlank()) {
                invoice.setTaxAuthorityCode(result.getTaxAuthorityCode());
            }
            invoice.setOfficiallyIssuedAt(result.getInvoiceDate() != null ? result.getInvoiceDate() : now);
            if ("MOCK".equalsIgnoreCase(providerName)) {
                invoice.setStorageProvider("MOCK");
            }
            invoiceRepository.save(invoice);

            event.setStatus(OutboxStatus.PROCESSED);
            event.setProcessedAt(now);
            event.setLockedUntil(null);
            event.setLockedBy(null);
            event.setLeaseToken(null);
            event.setLastError(null);
            invoiceOutboxRepository.save(event);
            return;
        }

        if (result.isFound() && identityVerified && result.isRejected()) {
            invoice.setDocumentStatus(DocumentStatus.ISSUE_FAILED);
            invoiceRepository.save(invoice);

            event.setStatus(OutboxStatus.DEAD_LETTER);
            event.setLockedUntil(null);
            event.setLockedBy(null);
            event.setLeaseToken(null);
            event.setLastError("Provider confirmed invoice issuance rejected: " + result.getRawResponse());
            invoiceOutboxRepository.save(event);
            return;
        }

        // Invoice remains in ISSUANCE_UNKNOWN across timeouts, unconfirmed status, and attempt exhaustion.
        // Exhausting Inquiry attempts does NOT prove the invoice was not issued.
        invoice.setDocumentStatus(DocumentStatus.ISSUANCE_UNKNOWN);
        invoiceRepository.save(invoice);

        String statusReason;
        if (attemptOutcome == AttemptOutcome.TIMEOUT_UNKNOWN) {
            statusReason = "Reconciliation inquiry timed out; invoice remains ISSUANCE_UNKNOWN";
        } else if (result.isFound() && !identityVerified) {
            statusReason = "Reconciliation inquiry returned mismatched identity; invoice remains ISSUANCE_UNKNOWN";
        } else if (!result.isFound()) {
            statusReason = "Invoice not found on provider yet for refId " + expectedRefId + "; invoice remains ISSUANCE_UNKNOWN";
        } else {
            statusReason = "Invoice status not yet confirmed issued on provider; invoice remains ISSUANCE_UNKNOWN";
        }

        if (event.getAttemptCount() < maxReconcileAttempts) {
            Duration effectiveDelay = (retryDelay != null && !retryDelay.isNegative())
                    ? retryDelay
                    : Duration.ofSeconds(15);
            event.setStatus(OutboxStatus.PENDING);
            event.setAvailableAt(now.plus(effectiveDelay));
            event.setLockedUntil(null);
            event.setLockedBy(null);
            event.setLeaseToken(null);
            event.setLastError(statusReason);
            invoiceOutboxRepository.save(event);
        } else {
            event.setStatus(OutboxStatus.DEAD_LETTER);
            event.setLockedUntil(null);
            event.setLockedBy(null);
            event.setLeaseToken(null);
            event.setLastError("Max reconciliation attempts exhausted; invoice remains ISSUANCE_UNKNOWN awaiting operator intervention: " + statusReason);
            invoiceOutboxRepository.save(event);
        }
    }

    @Override
    @Transactional
    public int enqueueMissingReconcileEvents() {
        LocalDateTime now = getDatabaseTime();
        List<Invoice> unknownInvoices = invoiceRepository.findByDocumentStatus(DocumentStatus.ISSUANCE_UNKNOWN);
        int createdCount = 0;
        for (Invoice unknown : unknownInvoices) {
            Invoice locked = invoiceRepository.findByIdForUpdate(unknown.getId()).orElse(null);
            if (locked == null || locked.getDocumentStatus() != DocumentStatus.ISSUANCE_UNKNOWN) {
                continue;
            }
            if (ensureReconcileEventExists(locked, now, "UNKNOWN_INVOICE_SWEEP")) {
                createdCount++;
            }
        }
        return createdCount;
    }

    @Override
    @Transactional
    public int reapExpiredLeases() {
        LocalDateTime now = getDatabaseTime();
        List<InvoiceOutbox> expiredList = invoiceOutboxRepository.findExpiredLeasesForUpdate(now);

        int reapedCount = 0;
        for (InvoiceOutbox event : expiredList) {
            Invoice invoice = invoiceRepository.findByIdForUpdate(event.getInvoice().getId())
                    .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + event.getInvoice().getId()));

            if (invoice.getDocumentStatus() == DocumentStatus.ISSUED) {
                event.setStatus(OutboxStatus.PROCESSED);
                event.setProcessedAt(now);
                event.setLockedUntil(null);
                event.setLockedBy(null);
                event.setLeaseToken(null);
                invoiceOutboxRepository.save(event);
                reapedCount++;
                continue;
            }

            if (EVENT_ISSUE_INVOICE.equalsIgnoreCase(event.getEventType())) {
                if (invoice.getDocumentStatus() == DocumentStatus.DRAFT
                        || invoice.getDocumentStatus() == DocumentStatus.PENDING_ISSUE) {
                    // Lease expired before TX2 committed (before sending HTTP request).
                    // Reclaim to PENDING so another worker can claim it. NO reconciliation event!
                    event.setStatus(OutboxStatus.PENDING);
                    event.setAvailableAt(now.minusSeconds(1));
                    event.setLastError("Lease expired before provider invocation in "
                            + invoice.getDocumentStatus() + "; reclaimed to PENDING");
                    event.setLockedUntil(null);
                    event.setLockedBy(null);
                    event.setLeaseToken(null);
                    invoiceOutboxRepository.save(event);
                } else if (invoice.getDocumentStatus() == DocumentStatus.ISSUING) {
                    // TX2 committed; HTTP request was or might have been sent over the wire.
                    // Must treat outcome as UNKNOWN; transition to ISSUANCE_UNKNOWN and enqueue reconciliation.
                    invoice.setDocumentStatus(DocumentStatus.ISSUANCE_UNKNOWN);
                    invoiceRepository.save(invoice);

                    event.setStatus(OutboxStatus.FAILED);
                    event.setLastError("Lease expired while in ISSUING; transitioned invoice to ISSUANCE_UNKNOWN for safe reconciliation");
                    event.setLockedUntil(null);
                    event.setLockedBy(null);
                    event.setLeaseToken(null);
                    invoiceOutboxRepository.save(event);

                    ensureReconcileEventExists(invoice, now, "LEASE_EXPIRED_IN_FLIGHT_ISSUING");
                } else {
                    event.setStatus(OutboxStatus.FAILED);
                    event.setLastError("Lease expired while in " + invoice.getDocumentStatus());
                    event.setLockedUntil(null);
                    event.setLockedBy(null);
                    event.setLeaseToken(null);
                    invoiceOutboxRepository.save(event);
                }
            } else if (EVENT_RECONCILE_STATUS.equalsIgnoreCase(event.getEventType())) {
                if (invoice.getDocumentStatus() == DocumentStatus.RECONCILING) {
                    invoice.setDocumentStatus(DocumentStatus.ISSUANCE_UNKNOWN);
                    invoiceRepository.save(invoice);
                }
                event.setStatus(OutboxStatus.PENDING);
                event.setAvailableAt(now.minusSeconds(1));
                event.setLastError("Lease expired during reconciliation inquiry; reclaimed to PENDING");
                event.setLockedUntil(null);
                event.setLockedBy(null);
                event.setLeaseToken(null);
                invoiceOutboxRepository.save(event);
            } else {
                event.setStatus(OutboxStatus.FAILED);
                event.setLastError("Lease expired without completion");
                event.setLockedUntil(null);
                event.setLockedBy(null);
                event.setLeaseToken(null);
                invoiceOutboxRepository.save(event);
            }
            reapedCount++;
        }

        return reapedCount;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceOutbox getEvent(Long outboxId) {
        return invoiceOutboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox event not found: " + outboxId));
    }

    private void validateClaimArguments(String workerId, Duration leaseDuration, int batchSize) {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be null or blank");
        }
        if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }
    }

    private List<InvoiceOutbox> leaseClaimedEvents(
            List<InvoiceOutbox> availableEvents,
            String workerId,
            LocalDateTime now,
            Duration leaseDuration) {
        List<InvoiceOutbox> claimed = new ArrayList<>(availableEvents.size());
        for (InvoiceOutbox event : availableEvents) {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setLockedBy(workerId);
            event.setLockedUntil(now.plus(leaseDuration));
            event.setLeaseToken(UUID.randomUUID().toString());
            event.setAttemptCount(event.getAttemptCount() + 1);
            claimed.add(event);
        }
        return invoiceOutboxRepository.saveAll(claimed);
    }

    private void validateLeaseOwnership(InvoiceOutbox event, String leaseToken, LocalDateTime now, String action) {
        if (event.getStatus() != OutboxStatus.PROCESSING) {
            throw new OutboxConcurrencyException(
                    String.format("Cannot %s event %d because status is %s, expected PROCESSING",
                            action, event.getId(), event.getStatus()));
        }
        if (event.getLeaseToken() == null || !event.getLeaseToken().equals(leaseToken)) {
            throw new OutboxConcurrencyException(
                    String.format("Lease token mismatch for event %d. Fencing token check failed.", event.getId()));
        }
        if (event.getLockedUntil() != null && event.getLockedUntil().isBefore(now)) {
            throw new OutboxConcurrencyException(
                    String.format("Lease expired at %s for event %d. Stale worker rejected.",
                            event.getLockedUntil(), event.getId()));
        }
    }

    @Override
    public InvoiceOutbox reactivateReconciliation(Long invoiceId) {
        if (invoiceId == null) {
            throw new IllegalArgumentException("invoiceId must not be null");
        }

        int maxRetries = 3;
        long backoffMs = 50L;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return transactionTemplate.execute(status -> doReactivateReconciliation(invoiceId));
            } catch (Exception ex) {
                if (isDuplicateEventKeyViolation(ex)) {
                    log.info("Duplicate event key detected on reconciliation reactivation for invoice {}. Recovering existing event idempotently.",
                            invoiceId);
                    return transactionTemplate.execute(status -> recoverExistingReconcileEvent(invoiceId));
                }

                if (isTransientLockFailure(ex)) {
                    if (attempt == maxRetries) {
                        log.error("Exhausted retries ({}) due to transient lock failure on reconciliation reactivation for invoice {}",
                                maxRetries, invoiceId, ex);
                        throw ex;
                    }
                    log.warn("Transient lock failure on reconciliation reactivation for invoice {}: {}. Retrying attempt {}/{}...",
                            invoiceId, ex.getMessage(), attempt, maxRetries);
                    try {
                        Thread.sleep(backoffMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting to retry reconciliation reactivation", ie);
                    }
                    continue;
                }

                // Real constraint violations or non-transient errors must NOT be retried
                throw ex;
            }
        }
        return null;
    }

    public static boolean isTransientLockFailure(Throwable ex) {
        if (ex instanceof CannotAcquireLockException
                || ex instanceof PessimisticLockingFailureException
                || ex instanceof DeadlockLoserDataAccessException) {
            return true;
        }
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SQLException sqlEx) {
                int errorCode = sqlEx.getErrorCode();
                String sqlState = sqlEx.getSQLState();
                if (errorCode == 1213 || errorCode == 1205 || "40001".equals(sqlState)) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    public static boolean isDuplicateEventKeyViolation(Throwable ex) {
        if (!(ex instanceof DataIntegrityViolationException)) {
            return false;
        }
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SQLException sqlEx) {
                int errorCode = sqlEx.getErrorCode();
                String sqlState = sqlEx.getSQLState();
                if (errorCode == 1062 || "23000".equals(sqlState)) {
                    String msg = sqlEx.getMessage();
                    if (msg != null && msg.contains("uk_invoice_outbox_event_key")) {
                        return true;
                    }
                }
            }
            cause = cause.getCause();
        }
        String msg = ex.getMessage();
        return msg != null && msg.contains("uk_invoice_outbox_event_key");
    }

    private InvoiceOutbox recoverExistingReconcileEvent(Long invoiceId) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId));

        if (invoice.getDocumentStatus() == DocumentStatus.ISSUED
                || invoice.getDocumentStatus() == DocumentStatus.ISSUE_FAILED
                || invoice.getDocumentStatus() != DocumentStatus.ISSUANCE_UNKNOWN) {
            return null;
        }

        String reconcileKey = "RECONCILE_INVOICE_" + invoice.getId();
        Optional<InvoiceOutbox> existingOpt = invoiceOutboxRepository.findByEventKey(reconcileKey);
        if (existingOpt.isPresent()) {
            InvoiceOutbox existing = existingOpt.get();
            LocalDateTime dbNow = getDatabaseTime();
            if (existing.getStatus() == OutboxStatus.PROCESSING) {
                if (existing.getLockedUntil() != null && !existing.getLockedUntil().isBefore(dbNow)) {
                    return existing;
                }
            }
            if (existing.getStatus() == OutboxStatus.PENDING) {
                return existing;
            }

            existing.setStatus(OutboxStatus.PENDING);
            existing.setAvailableAt(dbNow.minusSeconds(1));
            existing.setLockedUntil(null);
            existing.setLockedBy(null);
            existing.setLeaseToken(null);
            existing.setAttemptCount(0);
            existing.setLastError(null);
            return invoiceOutboxRepository.save(existing);
        }
        return null;
    }

    private InvoiceOutbox doReactivateReconciliation(Long invoiceId) {
        Invoice invoice = invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId));

        // Invoice Status Guard: Only ISSUANCE_UNKNOWN invoices are eligible for recovery reconciliation
        if (invoice.getDocumentStatus() == DocumentStatus.ISSUED) {
            return null;
        }
        if (invoice.getDocumentStatus() == DocumentStatus.ISSUE_FAILED) {
            return null;
        }
        if (invoice.getDocumentStatus() != DocumentStatus.ISSUANCE_UNKNOWN) {
            return null;
        }

        LocalDateTime dbNow = getDatabaseTime();
        String reconcileKey = "RECONCILE_INVOICE_" + invoice.getId();
        Optional<InvoiceOutbox> existingOpt = invoiceOutboxRepository.findByEventKey(reconcileKey);

        if (existingOpt.isPresent()) {
            InvoiceOutbox existing = existingOpt.get();
            if (existing.getStatus() == OutboxStatus.PROCESSING) {
                if (existing.getLockedUntil() != null && !existing.getLockedUntil().isBefore(dbNow)) {
                    return existing;
                }
            }
            if (existing.getStatus() == OutboxStatus.PENDING) {
                return existing;
            }

            existing.setStatus(OutboxStatus.PENDING);
            existing.setAvailableAt(dbNow.minusSeconds(1));
            existing.setLockedUntil(null);
            existing.setLockedBy(null);
            existing.setLeaseToken(null);
            existing.setAttemptCount(0);
            existing.setLastError(null);
            return invoiceOutboxRepository.save(existing);
        }

        String reconcilePayload = String.format("{\"invoiceId\":%d,\"idempotencyKey\":\"%s\",\"reason\":\"OPERATOR_REACTIVATION\"}",
                invoice.getId(), invoice.getIdempotencyKey());
        InvoiceOutbox reconcileEvent = InvoiceOutbox.builder()
                .eventType(EVENT_RECONCILE_STATUS)
                .eventKey(reconcileKey)
                .invoice(invoice)
                .payload(reconcilePayload)
                .status(OutboxStatus.PENDING)
                .attemptCount(0)
                .availableAt(dbNow.minusSeconds(1))
                .build();
        return invoiceOutboxRepository.save(reconcileEvent);
    }

    private boolean ensureReconcileEventExists(Invoice invoice, LocalDateTime now, String reason) {
        String reconcileKey = "RECONCILE_INVOICE_" + invoice.getId();
        Optional<InvoiceOutbox> existingOpt = invoiceOutboxRepository.findByEventKey(reconcileKey);
        if (existingOpt.isPresent()) {
            InvoiceOutbox existing = existingOpt.get();
            if (existing.getStatus() == OutboxStatus.FAILED || existing.getStatus() == OutboxStatus.DEAD_LETTER) {
                existing.setStatus(OutboxStatus.PENDING);
                existing.setAvailableAt(now.minusSeconds(1));
                existing.setLockedUntil(null);
                existing.setLockedBy(null);
                existing.setLeaseToken(null);
                invoiceOutboxRepository.save(existing);
                return true;
            }
            return false;
        }

        String reconcilePayload = String.format("{\"invoiceId\":%d,\"idempotencyKey\":\"%s\",\"reason\":\"%s\"}",
                invoice.getId(), invoice.getIdempotencyKey(), reason);
        InvoiceOutbox reconcileEvent = InvoiceOutbox.builder()
                .eventType(EVENT_RECONCILE_STATUS)
                .eventKey(reconcileKey)
                .invoice(invoice)
                .payload(reconcilePayload)
                .status(OutboxStatus.PENDING)
                .attemptCount(0)
                .availableAt(now.minusSeconds(1))
                .build();
        invoiceOutboxRepository.save(reconcileEvent);
        return true;
    }

    private boolean verifyReconciliationIdentity(Invoice invoice, String expectedRefId, ProviderInquiryResult result) {
        if (!result.isFound()) {
            return false;
        }
        if (result.getRefId() == null || result.getRefId().isBlank()) {
            return false;
        }
        String returnedNormalizedRefId = MisaPayloadMapper.toDeterministicRefId(result.getRefId());
        if (!expectedRefId.equalsIgnoreCase(returnedNormalizedRefId)
                && !expectedRefId.equalsIgnoreCase(result.getRefId().trim())) {
            return false;
        }
        if (invoice.getProviderTransactionId() != null && !invoice.getProviderTransactionId().isBlank()
                && result.getTransactionId() != null && !result.getTransactionId().isBlank()
                && !invoice.getProviderTransactionId().trim().equalsIgnoreCase(result.getTransactionId().trim())) {
            return false;
        }
        return true;
    }

    private ProviderIssueRequest buildProviderIssueRequest(Invoice invoice, List<InvoiceItem> items) {
        BuyerInvoiceProfile buyer = invoice.getBuyerProfile();
        SellerInvoiceProfile seller = invoice.getSellerProfile();

        String buyerLegalName = (buyer.getCompanyName() != null && !buyer.getCompanyName().isBlank())
                ? buyer.getCompanyName()
                : buyer.getBuyerName();

        List<ProviderInvoiceItemDto> itemDtos = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            InvoiceItem item = items.get(i);
            String itemCode = item.getItemType() == InvoiceItemType.SHIPPING_FEE
                    ? "SHIPPING_FEE"
                    : "ITEM_" + (item.getOrderDetail() != null ? item.getOrderDetail().getId() : item.getId());

            itemDtos.add(ProviderInvoiceItemDto.builder()
                    .invoiceItemId(item.getId())
                    .lineNumber(i + 1)
                    .itemCode(itemCode)
                    .itemName(item.getItemName())
                    .unitName(item.getUnit())
                    .quantity(BigDecimal.valueOf(item.getQuantity()))
                    .unitPrice(item.getUnitPriceNet())
                    .discountAmount(item.getDiscountAmount())
                    .netAmount(item.getTaxableAmount())
                    .vatRate(item.getTaxRate())
                    .vatAmount(item.getTaxAmount())
                    .grossAmount(item.getLineTotalGross())
                    .isNegative(item.getQuantity() != null && item.getQuantity() < 0)
                    .build());
        }

        BigDecimal totalDiscount = (invoice.getTotalMerchandiseDiscount() != null ? invoice.getTotalMerchandiseDiscount() : BigDecimal.ZERO)
                .add(invoice.getTotalShippingDiscount() != null ? invoice.getTotalShippingDiscount() : BigDecimal.ZERO);

        String orgInvNo = null;
        String orgInvSeries = null;
        LocalDateTime orgInvDate = null;
        if (invoice.getInvoiceType() == InvoiceType.ADJUSTMENT && invoice.getOriginalInvoice() != null) {
            Invoice orig = invoice.getOriginalInvoice();
            orgInvNo = orig.getInvoiceNumber();
            orgInvSeries = orig.getInvoiceSeries();
            orgInvDate = orig.getOfficiallyIssuedAt() != null ? orig.getOfficiallyIssuedAt() : orig.getTaxPointDate();
        } else if (invoice.getInvoiceType() == InvoiceType.REPLACEMENT && invoice.getReplacingInvoice() != null) {
            Invoice rep = invoice.getReplacingInvoice();
            orgInvNo = rep.getInvoiceNumber();
            orgInvSeries = rep.getInvoiceSeries();
            orgInvDate = rep.getOfficiallyIssuedAt() != null ? rep.getOfficiallyIssuedAt() : rep.getTaxPointDate();
        }

        return ProviderIssueRequest.builder()
                .invoiceId(invoice.getId())
                .refId(MisaPayloadMapper.toDeterministicRefId(invoice.getIdempotencyKey()))
                .invoiceType(invoice.getInvoiceType())
                .invoiceSeries(invoice.getInvoiceSeries())
                .invoiceDate(invoice.getTaxPointDate())
                .paymentMethod("Tiền mặt / Chuyển khoản")
                .sellerTaxCode(seller.getSellerTaxCode())
                .sellerLegalName(seller.getSellerLegalName())
                .sellerAddress(seller.getSellerAddress())
                .buyerLegalName(buyerLegalName)
                .buyerTaxCode(buyer.getTaxCode())
                .buyerAddress(buyer.getAddress())
                .buyerEmail(buyer.getEmail())
                .buyerPhone(buyer.getPhoneNumber())
                .totalGrossAmount(invoice.getTotalAfterTax())
                .totalNetAmount(invoice.getTotalTaxableAmount())
                .totalVatAmount(invoice.getTotalTaxAmount())
                .totalDiscountAmount(totalDiscount)
                .items(itemDtos)
                .originalInvoiceNo(orgInvNo)
                .originalInvoiceSeries(orgInvSeries)
                .originalInvoiceDate(orgInvDate)
                .build();
    }

    private void recordAttempt(
            Invoice invoice,
            String providerName,
            String endpoint,
            String requestType,
            String sanitizedRequestPayload,
            String responsePayload,
            Integer httpStatusCode,
            AttemptOutcome outcome,
            String errorMessage) {

        String truncatedError = errorMessage != null && errorMessage.length() > MAX_ATTEMPT_ERROR_LENGTH
                ? errorMessage.substring(0, MAX_ATTEMPT_ERROR_LENGTH)
                : errorMessage;

        InvoiceAttempt attempt = InvoiceAttempt.builder()
                .invoice(invoice)
                .providerName(providerName)
                .endpoint(endpoint)
                .requestType(requestType)
                .sanitizedRequestPayload(sanitizedRequestPayload)
                .responsePayload(responsePayload)
                .httpStatusCode(httpStatusCode)
                .outcome(outcome)
                .errorMessage(truncatedError)
                .build();
        invoiceAttemptRepository.save(attempt);
    }
}
