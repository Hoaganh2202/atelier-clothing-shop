package com.shop.backend.einvoice.provider.mock;

import com.shop.backend.einvoice.model.AttemptOutcome;
import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.provider.InvoiceProvider;
import com.shop.backend.einvoice.provider.dto.ProviderCancelRequest;
import com.shop.backend.einvoice.provider.dto.ProviderCancelResult;
import com.shop.backend.einvoice.provider.dto.ProviderDownloadRequest;
import com.shop.backend.einvoice.provider.dto.ProviderDownloadResult;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryRequest;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryResult;
import com.shop.backend.einvoice.provider.dto.ProviderIssueRequest;
import com.shop.backend.einvoice.provider.dto.ProviderIssueResult;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("mockInvoiceProvider")
public class MockInvoiceProvider implements InvoiceProvider {

    private static final Logger log = LoggerFactory.getLogger(MockInvoiceProvider.class);
    public static final String PROVIDER_NAME = "MOCK";
    public static final String DEMO_SERIES = "DEMO26";
    public static final String DEMO_TEMPLATE = "DEMO";

    @Override
    public ProviderIssueResult issueInvoice(ProviderIssueRequest request) {
        if (request == null) {
            return ProviderIssueResult.builder()
                    .success(false)
                    .attemptOutcome(AttemptOutcome.FAILED)
                    .errorCode("EMPTY_ISSUE_REQUEST")
                    .errorMessage("ProviderIssueRequest must not be null")
                    .retryable(false)
                    .build();
        }

        InvoiceType invoiceType = request.getInvoiceType();
        if (invoiceType != InvoiceType.ORIGINAL) {
            log.warn("MockInvoiceProvider received unsupported invoice type '{}' for invoiceId={}",
                    invoiceType, request.getInvoiceId());
            return ProviderIssueResult.builder()
                    .success(false)
                    .attemptOutcome(AttemptOutcome.FAILED)
                    .errorCode("MOCK_" + (invoiceType != null ? invoiceType.name() : "NULL") + "_NOT_SUPPORTED")
                    .errorMessage("Mock provider does not support " + invoiceType + " invoice issuance in current milestone")
                    .retryable(false)
                    .build();
        }

        String refId = request.getRefId() != null ? request.getRefId() : UUID.randomUUID().toString();
        long invoiceId = request.getInvoiceId() != null ? request.getInvoiceId() : 1L;
        String demoTxId = "DEMO-MOCK-TX-" + UUID.randomUUID();
        String demoInvoiceNo = "DEMO-" + String.format("%08d", invoiceId);
        LocalDateTime now = LocalDateTime.now();

        log.info("MockInvoiceProvider simulated successful issuance: invoiceId={}, refId={}, invoiceNo={}, txId={}",
                invoiceId, refId, demoInvoiceNo, demoTxId);

        String rawResponse = String.format(
                "{\"mode\":\"DEMO_MOCK\",\"provider\":\"%s\",\"invoiceId\":%d,\"refId\":\"%s\",\"invoiceNo\":\"%s\",\"isDemo\":true}",
                PROVIDER_NAME, invoiceId, refId, demoInvoiceNo);

        return ProviderIssueResult.builder()
                .success(true)
                .attemptOutcome(AttemptOutcome.SUCCESS)
                .transactionId(demoTxId)
                .invoiceNo(demoInvoiceNo)
                .invoiceSeries(DEMO_SERIES)
                .templateCode(DEMO_TEMPLATE)
                .issuedAt(now)
                .taxAuthorityCode(null)
                .lookupCode(null)
                .lookupUrl("http://localhost:8080/demo/invoices/" + refId)
                .rawResponse(rawResponse)
                .retryable(false)
                .build();
    }

    @Override
    public ProviderInquiryResult inquiryInvoiceStatus(ProviderInquiryRequest request) {
        if (request == null) {
            return ProviderInquiryResult.builder()
                    .found(false)
                    .isIssued(false)
                    .attemptOutcome(AttemptOutcome.FAILED)
                    .status("EMPTY_REQUEST")
                    .rawResponse("{\"mode\":\"DEMO_MOCK\",\"error\":\"EMPTY_REQUEST\"}")
                    .build();
        }

        String txId = request.getTransactionId() != null ? request.getTransactionId() : ("DEMO-MOCK-TX-" + request.getRefId());
        String invoiceNo = "DEMO-" + (request.getRefId() != null ? request.getRefId().substring(0, Math.min(8, request.getRefId().length())) : "00000001");

        return ProviderInquiryResult.builder()
                .found(true)
                .isIssued(true)
                .isRejected(false)
                .refId(request.getRefId())
                .transactionId(txId)
                .invoiceNo(invoiceNo)
                .invoiceSeries(DEMO_SERIES)
                .templateCode(DEMO_TEMPLATE)
                .taxAuthorityCode(null)
                .invoiceDate(LocalDateTime.now())
                .status("ISSUED")
                .attemptOutcome(AttemptOutcome.SUCCESS)
                .rawResponse("{\"mode\":\"DEMO_MOCK\",\"inquiryStatus\":\"DEMO_ISSUED\",\"isDemo\":true}")
                .build();
    }

    @Override
    public ProviderDownloadResult downloadInvoiceFiles(ProviderDownloadRequest request) {
        String dummyPdfContent = "%PDF-1.4\n%DEMO INVOICE (SIMULATED - NOT VALID FOR TAX AUTHORITIES)\n";
        String dummyXmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><DemoInvoice isDemo=\"true\" provider=\"MOCK\"/>";

        return ProviderDownloadResult.builder()
                .success(true)
                .fileName("DEMO-INVOICE.pdf")
                .pdfBytes(dummyPdfContent.getBytes(StandardCharsets.UTF_8))
                .xmlBytes(dummyXmlContent.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    @Override
    public ProviderCancelResult cancelInvoice(ProviderCancelRequest request) {
        return ProviderCancelResult.builder()
                .success(true)
                .message("Simulated cancellation in demo mode")
                .attemptOutcome(AttemptOutcome.SUCCESS)
                .rawResponse("{\"mode\":\"DEMO_MOCK\",\"cancelled\":true}")
                .retryable(false)
                .build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
}
