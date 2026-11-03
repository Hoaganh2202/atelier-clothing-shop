package com.shop.backend.einvoice.service;

import com.shop.backend.einvoice.model.InvoiceOutbox;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryRequest;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryResult;
import com.shop.backend.einvoice.provider.dto.ProviderIssueRequest;
import com.shop.backend.einvoice.provider.dto.ProviderIssueResult;
import java.time.Duration;
import java.util.List;

public interface InvoiceOutboxService {
    InvoiceOutbox createEvent(String eventType, String eventKey, Long invoiceId, String payload);

    List<InvoiceOutbox> claimEvents(String workerId, Duration leaseDuration, int batchSize);

    List<InvoiceOutbox> claimEventsByType(String eventType, String workerId, Duration leaseDuration, int batchSize);

    void completeEvent(Long outboxId, String leaseToken);

    void failEvent(Long outboxId, String leaseToken, String errorMessage, boolean canRetry, Duration retryDelay);

    void releaseUnprocessedLease(Long outboxId, String leaseToken);

    ProviderIssueRequest prepareIssueDispatch(Long outboxId, String leaseToken);

    void finalizeIssueOutcome(
            Long outboxId,
            String leaseToken,
            String providerName,
            ProviderIssueRequest request,
            ProviderIssueResult result,
            int maxAttempts,
            Duration retryDelay);

    ProviderInquiryRequest prepareReconcileDispatch(Long outboxId, String leaseToken);

    void finalizeReconcileOutcome(
            Long outboxId,
            String leaseToken,
            String providerName,
            ProviderInquiryRequest request,
            ProviderInquiryResult result,
            int maxReconcileAttempts,
            Duration retryDelay);

    int enqueueMissingReconcileEvents();

    int reapExpiredLeases();

    InvoiceOutbox reactivateReconciliation(Long invoiceId);

    InvoiceOutbox getEvent(Long outboxId);
}
