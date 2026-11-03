package com.shop.backend.einvoice.provider.mock;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.backend.einvoice.model.AttemptOutcome;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.provider.dto.ProviderCancelRequest;
import com.shop.backend.einvoice.provider.dto.ProviderCancelResult;
import com.shop.backend.einvoice.provider.dto.ProviderDownloadRequest;
import com.shop.backend.einvoice.provider.dto.ProviderDownloadResult;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryRequest;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryResult;
import com.shop.backend.einvoice.provider.dto.ProviderIssueRequest;
import com.shop.backend.einvoice.provider.dto.ProviderIssueResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockInvoiceProviderTest {

    private MockInvoiceProvider mockProvider;

    @BeforeEach
    void setUp() {
        mockProvider = new MockInvoiceProvider();
    }

    @Test
    @DisplayName("Should successfully issue ORIGINAL invoice in demo mode with distinct demo identifiers")
    void shouldSuccessfullyIssueOriginalInvoiceInDemoMode() {
        ProviderIssueRequest request = ProviderIssueRequest.builder()
                .invoiceId(101L)
                .refId("660e8400-e29b-41d4-a716-446655440000")
                .invoiceType(InvoiceType.ORIGINAL)
                .templateCode("1")
                .invoiceSeries("1C26TYY")
                .invoiceDate(LocalDateTime.now())
                .paymentMethod("COD")
                .buyerLegalName("Nguyen Van A")
                .totalGrossAmount(new BigDecimal("1000000"))
                .totalNetAmount(new BigDecimal("925925.93"))
                .totalVatAmount(new BigDecimal("74074.07"))
                .totalDiscountAmount(BigDecimal.ZERO)
                .items(Collections.emptyList())
                .build();

        ProviderIssueResult result = mockProvider.issueInvoice(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
        assertThat(result.getTransactionId()).startsWith("DEMO-MOCK-TX-");
        assertThat(result.getInvoiceNo()).isEqualTo("DEMO-00000101");
        assertThat(result.getInvoiceSeries()).isEqualTo("DEMO26");
        assertThat(result.getTemplateCode()).isEqualTo("DEMO");
        assertThat(result.getIssuedAt()).isNotNull();
        assertThat(result.getTaxAuthorityCode()).isNull();
        assertThat(result.getLookupCode()).isNull();
        assertThat(result.getLookupUrl()).contains("/demo/invoices/660e8400-e29b-41d4-a716-446655440000");
        assertThat(result.getRawResponse()).contains("\"mode\":\"DEMO_MOCK\"");
        assertThat(result.isRetryable()).isFalse();

        Invoice invoice = Invoice.builder()
                .providerTransactionId(result.getTransactionId())
                .invoiceSeries(result.getInvoiceSeries())
                .invoiceNumber(result.getInvoiceNo())
                .storageProvider("MOCK")
                .build();
        assertThat(invoice.isDemoIssued()).isTrue();
    }

    @Test
    @DisplayName("Should reject ADJUSTMENT invoice issuance with business error code")
    void shouldRejectAdjustmentInvoiceIssuance() {
        ProviderIssueRequest request = ProviderIssueRequest.builder()
                .invoiceId(102L)
                .refId("770e8400-e29b-41d4-a716-446655440001")
                .invoiceType(InvoiceType.ADJUSTMENT)
                .originalInvoiceNo("DEMO-00000101")
                .originalInvoiceSeries("DEMO26")
                .items(Collections.emptyList())
                .build();

        ProviderIssueResult result = mockProvider.issueInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.FAILED);
        assertThat(result.getErrorCode()).isEqualTo("MOCK_ADJUSTMENT_NOT_SUPPORTED");
        assertThat(result.getErrorMessage()).contains("Mock provider does not support ADJUSTMENT");
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("Should reject REPLACEMENT invoice issuance with business error code")
    void shouldRejectReplacementInvoiceIssuance() {
        ProviderIssueRequest request = ProviderIssueRequest.builder()
                .invoiceId(103L)
                .refId("880e8400-e29b-41d4-a716-446655440002")
                .invoiceType(InvoiceType.REPLACEMENT)
                .items(Collections.emptyList())
                .build();

        ProviderIssueResult result = mockProvider.issueInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.FAILED);
        assertThat(result.getErrorCode()).isEqualTo("MOCK_REPLACEMENT_NOT_SUPPORTED");
        assertThat(result.getErrorMessage()).contains("Mock provider does not support REPLACEMENT");
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("Should simulate status inquiry correctly in demo mode")
    void shouldSimulateStatusInquiry() {
        ProviderInquiryRequest request = ProviderInquiryRequest.builder()
                .refId("demo-ref-123")
                .transactionId("DEMO-MOCK-TX-123")
                .build();

        ProviderInquiryResult result = mockProvider.inquiryInvoiceStatus(request);

        assertThat(result.isFound()).isTrue();
        assertThat(result.isIssued()).isTrue();
        assertThat(result.getTransactionId()).isEqualTo("DEMO-MOCK-TX-123");
        assertThat(result.getInvoiceSeries()).isEqualTo("DEMO26");
        assertThat(result.getTaxAuthorityCode()).isNull();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
    }

    @Test
    @DisplayName("Should simulate file download containing clear demo disclaimers")
    void shouldSimulateFileDownloadWithDisclaimer() {
        ProviderDownloadRequest request = ProviderDownloadRequest.builder()
                .dataType(ProviderDownloadRequest.DownloadDataType.PDF)
                .build();

        ProviderDownloadResult result = mockProvider.downloadInvoiceFiles(request);

        assertThat(result.isSuccess()).isTrue();
        String pdfStr = new String(result.getPdfBytes(), StandardCharsets.UTF_8);
        assertThat(pdfStr).contains("DEMO INVOICE (SIMULATED - NOT VALID FOR TAX AUTHORITIES)");
        String xmlStr = new String(result.getXmlBytes(), StandardCharsets.UTF_8);
        assertThat(xmlStr).contains("provider=\"MOCK\"");
    }

    @Test
    @DisplayName("Should simulate invoice cancellation")
    void shouldSimulateCancellation() {
        ProviderCancelRequest request = ProviderCancelRequest.builder()
                .reason("Khách hàng hủy đơn")
                .build();

        ProviderCancelResult result = mockProvider.cancelInvoice(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
    }

    @Test
    @DisplayName("Provider name must be MOCK")
    void shouldReturnProviderNameMock() {
        assertThat(mockProvider.getProviderName()).isEqualTo("MOCK");
    }
}
