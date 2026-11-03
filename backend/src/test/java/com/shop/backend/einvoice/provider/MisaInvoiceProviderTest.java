package com.shop.backend.einvoice.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.einvoice.model.AttemptOutcome;
import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.provider.dto.ProviderCancelRequest;
import com.shop.backend.einvoice.provider.dto.ProviderCancelResult;
import com.shop.backend.einvoice.provider.dto.ProviderDownloadRequest;
import com.shop.backend.einvoice.provider.dto.ProviderDownloadResult;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryRequest;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryResult;
import com.shop.backend.einvoice.provider.dto.ProviderIssueRequest;
import com.shop.backend.einvoice.provider.dto.ProviderIssueResult;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderAuthException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderFatalException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderTimeoutException;
import com.shop.backend.einvoice.provider.misa.MisaApiClient;
import com.shop.backend.einvoice.provider.misa.MisaInvoiceProvider;
import com.shop.backend.einvoice.provider.misa.MisaPayloadMapper;
import com.shop.backend.einvoice.provider.misa.MisaProperties;
import com.shop.backend.einvoice.provider.misa.MisaTokenClient;
import com.shop.backend.einvoice.provider.misa.dto.MisaAuthResponse;
import com.shop.backend.einvoice.provider.misa.dto.MisaCancelInvoiceResponse;
import com.shop.backend.einvoice.provider.misa.dto.MisaInvoiceStatusItemDto;
import com.shop.backend.einvoice.provider.misa.dto.MisaPublishResponse;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MisaInvoiceProviderTest {

    private MisaApiClient apiClient;
    private MisaTokenClient tokenClient;
    private MisaProperties properties;
    private MisaPayloadMapper payloadMapper;
    private ObjectMapper objectMapper;
    private MisaInvoiceProvider provider;

    @BeforeEach
    void setUp() {
        apiClient = mock(MisaApiClient.class);
        properties = new MisaProperties();
        properties.setTemplateCode("1");
        properties.setInvoiceSeries("1C26TYY");
        properties.setLookupPortalUrl("https://meinvoice.vn/tra-cuu");

        objectMapper = new ObjectMapper();
        tokenClient = new MisaTokenClient(properties, objectMapper);
        payloadMapper = new MisaPayloadMapper();

        provider = new MisaInvoiceProvider(apiClient, tokenClient, properties, payloadMapper, objectMapper);

        // Default mock for authentication
        when(apiClient.authenticate(any())).thenReturn(
                MisaAuthResponse.builder().success(true).data("valid_bearer_token").build()
        );
    }

    @Test
    @DisplayName("issueInvoice: successfully issues invoice and maps data fields")
    void issueInvoiceSuccess() {
        ProviderIssueRequest request = ProviderIssueRequest.builder()
                .invoiceId(101L)
                .refId("ref-uuid-101")
                .invoiceType(InvoiceType.ORIGINAL)
                .buyerLegalName("Nguyen Van B")
                .totalGrossAmount(new BigDecimal("108000.00"))
                .totalNetAmount(new BigDecimal("100000.00"))
                .totalVatAmount(new BigDecimal("8000.00"))
                .totalDiscountAmount(BigDecimal.ZERO)
                .items(List.of())
                .build();

        String dataPayload = "{\"TransactionID\":\"tx-101\",\"InvNo\":\"00000010\",\"InvSeries\":\"1C26TYY\",\"TaxAuthorityCode\":\"CQT-01\",\"LookupCode\":\"LK-01\"}";
        when(apiClient.publishInvoice(eq("valid_bearer_token"), any())).thenReturn(
                MisaPublishResponse.builder().success(true).data(dataPayload).build()
        );

        ProviderIssueResult result = provider.issueInvoice(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
        assertThat(result.getTransactionId()).isEqualTo("tx-101");
        assertThat(result.getInvoiceNo()).isEqualTo("00000010");
        assertThat(result.getInvoiceSeries()).isEqualTo("1C26TYY");
        assertThat(result.getTaxAuthorityCode()).isEqualTo("CQT-01");
        assertThat(result.getLookupCode()).isEqualTo("LK-01");
        assertThat(result.getLookupUrl()).isEqualTo("https://meinvoice.vn/tra-cuu?code=LK-01");
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("issueInvoice: maps timeout to TIMEOUT_UNKNOWN outcome with retryable=true")
    void issueInvoiceTimeoutMapsToUnknownOutcome() {
        ProviderIssueRequest request = ProviderIssueRequest.builder()
                .invoiceId(102L)
                .refId("ref-uuid-102")
                .invoiceType(InvoiceType.ORIGINAL)
                .items(List.of())
                .build();

        when(apiClient.publishInvoice(eq("valid_bearer_token"), any())).thenThrow(
                new InvoiceProviderTimeoutException("MISA", "Socket timeout", null)
        );

        ProviderIssueResult result = provider.issueInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.TIMEOUT_UNKNOWN);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo("HTTP_TIMEOUT");
        verify(apiClient, times(1)).publishInvoice(any(), any());
    }

    @Test
    @DisplayName("issueInvoice: when auth times out before business request, maps to FAILED (retryable) and never calls publishInvoice")
    void issueInvoice_whenAuthTimesOut_mapsToFailedRetryable_andNeverCallsPublishInvoice() {
        ProviderIssueRequest request = ProviderIssueRequest.builder()
                .invoiceId(1021L)
                .refId("ref-uuid-1021")
                .invoiceType(InvoiceType.ORIGINAL)
                .items(List.of())
                .build();

        when(apiClient.authenticate(any())).thenThrow(
                new InvoiceProviderTimeoutException("MISA", "Auth request timed out", null)
        );

        ProviderIssueResult result = provider.issueInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.FAILED);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getErrorCode()).isEqualTo("AUTH_TIMEOUT");
        verify(apiClient, never()).publishInvoice(any(), any());
    }

    @Test
    @DisplayName("issueInvoice: when response body is cut off or connection reset after sending, maps to TIMEOUT_UNKNOWN with exactly 1 publish call")
    void issueInvoice_whenPublishBodyConnectionReset_mapsToTimeoutUnknown_withExactlyOnePublishCall() {
        ProviderIssueRequest request = ProviderIssueRequest.builder()
                .invoiceId(1022L)
                .refId("ref-uuid-1022")
                .invoiceType(InvoiceType.ORIGINAL)
                .items(List.of())
                .build();

        when(apiClient.publishInvoice(eq("valid_bearer_token"), any())).thenThrow(
                new InvoiceProviderTimeoutException("MISA", "Connection reset by peer during response body reception", null)
        );

        ProviderIssueResult result = provider.issueInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.TIMEOUT_UNKNOWN);
        assertThat(result.isRetryable()).isTrue();
        verify(apiClient, times(1)).publishInvoice(any(), any());
    }

    @Test
    @DisplayName("issueInvoice: fatal client error maps to FAILED outcome with retryable=false")
    void issueInvoiceFatalErrorMapsToFailedOutcome() {
        ProviderIssueRequest request = ProviderIssueRequest.builder()
                .invoiceId(103L)
                .refId("ref-uuid-103")
                .invoiceType(InvoiceType.ORIGINAL)
                .items(List.of())
                .build();

        when(apiClient.publishInvoice(eq("valid_bearer_token"), any())).thenThrow(
                new InvoiceProviderFatalException("MISA", "INVALID_DATA", "Buyer tax code format is invalid", null)
        );

        ProviderIssueResult result = provider.issueInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.FAILED);
        assertThat(result.isRetryable()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INVALID_DATA");
    }

    @Test
    @DisplayName("issueInvoice: automatically evicts token and retries once on 401 Unauthorized")
    void issueInvoiceRetriesOnceOn401() {
        ProviderIssueRequest request = ProviderIssueRequest.builder()
                .invoiceId(104L)
                .refId("ref-uuid-104")
                .invoiceType(InvoiceType.ORIGINAL)
                .items(List.of())
                .build();

        // First attempt throws 401, second attempt succeeds
        when(apiClient.publishInvoice(eq("valid_bearer_token"), any()))
                .thenThrow(new InvoiceProviderAuthException("MISA", "HTTP_401", "Token expired", null))
                .thenReturn(MisaPublishResponse.builder()
                        .success(true)
                        .data("{\"TransactionID\":\"tx-retry\",\"InvNo\":\"00000015\"}")
                        .build());

        ProviderIssueResult result = provider.issueInvoice(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTransactionId()).isEqualTo("tx-retry");
        verify(apiClient, times(2)).publishInvoice(any(), any());
    }

    @Test
    @DisplayName("issueInvoice: when retried attempt also returns 401, terminates immediately without looping")
    void issueInvoice_whenSecondAttemptAlso401_doesNotLoopAndThrowsAuthException() {
        ProviderIssueRequest request = ProviderIssueRequest.builder()
                .invoiceId(105L)
                .refId("ref-uuid-105")
                .invoiceType(InvoiceType.ORIGINAL)
                .items(List.of())
                .build();

        // Both attempts throw 401
        when(apiClient.publishInvoice(any(), any()))
                .thenThrow(new InvoiceProviderAuthException("MISA", "HTTP_401", "Token expired", null));

        ProviderIssueResult result = provider.issueInvoice(request);

        // Result maps the fatal auth error and does NOT retry infinitely
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.FAILED);
        assertThat(result.isRetryable()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("HTTP_401");
        // Verify exactly 2 publish attempts occurred (initial + 1 retry)
        verify(apiClient, times(2)).publishInvoice(any(), any());
    }

    @Test
    @DisplayName("inquiryInvoiceStatus: parses found invoice status accurately")
    void inquiryInvoiceStatusFound() {
        String rawRefId = "ref-reconcile-1";
        String deterministicRefId = MisaPayloadMapper.toDeterministicRefId(rawRefId);

        ProviderInquiryRequest request = ProviderInquiryRequest.builder()
                .refId(rawRefId)
                .build();

        MisaInvoiceStatusItemDto item = MisaInvoiceStatusItemDto.builder()
                .refId(deterministicRefId)
                .transactionId("tx-recon-1")
                .invNo("00000088")
                .invSeries("1C26TYY")
                .publishStatus(1)
                .eInvoiceStatus(1)
                .invoiceStatus(2)
                .taxAuthorityCode("CQT-999")
                .lookupCode("LK-999")
                .build();

        when(apiClient.getInvoiceStatusByRefId(eq("valid_bearer_token"), eq(List.of(deterministicRefId))))
                .thenReturn(List.of(item));

        ProviderInquiryResult result = provider.inquiryInvoiceStatus(request);

        assertThat(result.isFound()).isTrue();
        assertThat(result.isIssued()).isTrue();
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getInvoiceNo()).isEqualTo("00000088");
        assertThat(result.getTaxAuthorityCode()).isEqualTo("CQT-999");
    }

    @Test
    @DisplayName("inquiryInvoiceStatus: returns found=false when provider reports empty results")
    void inquiryInvoiceStatusNotFound() {
        ProviderInquiryRequest request = ProviderInquiryRequest.builder()
                .refId("ref-absent")
                .build();

        when(apiClient.getInvoiceStatusByRefId(eq("valid_bearer_token"), any()))
                .thenReturn(Collections.emptyList());

        ProviderInquiryResult result = provider.inquiryInvoiceStatus(request);

        assertThat(result.isFound()).isFalse();
        assertThat(result.isIssued()).isFalse();
    }

    @Test
    @DisplayName("downloadInvoiceFiles: downloads xml and pdf files")
    void downloadInvoiceFilesSuccess() {
        ProviderDownloadRequest request = ProviderDownloadRequest.builder()
                .transactionId("tx-down-1")
                .invoiceNo("00000020")
                .invoiceSeries("1C26TYY")
                .dataType(ProviderDownloadRequest.DownloadDataType.BOTH)
                .build();

        byte[] fakeXml = "<HDon>xml_content</HDon>".getBytes();
        byte[] fakePdf = "%PDF-1.4_content".getBytes();

        when(apiClient.downloadInvoice("valid_bearer_token", "tx-down-1", "xml")).thenReturn(fakeXml);
        when(apiClient.downloadInvoice("valid_bearer_token", "tx-down-1", "pdf")).thenReturn(fakePdf);

        ProviderDownloadResult result = provider.downloadInvoiceFiles(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getXmlBytes()).isEqualTo(fakeXml);
        assertThat(result.getPdfBytes()).isEqualTo(fakePdf);
        assertThat(result.getFileName()).isEqualTo("1C26TYY_00000020");
    }

    @Test
    @DisplayName("cancelInvoice [Direct Success]: Cancellation response success directly returns SUCCESS")
    void cancelInvoiceSuccess() {
        ProviderCancelRequest request = ProviderCancelRequest.builder()
                .refId("ref-cancel-1")
                .transactionId("tx-cancel-1")
                .reason("Khách hàng hủy đơn hoàn hàng")
                .build();

        when(apiClient.cancelInvoice(eq("valid_bearer_token"), any(), any())).thenReturn(
                MisaCancelInvoiceResponse.builder().success(true).build()
        );

        ProviderCancelResult result = provider.cancelInvoice(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
        assertThat(result.getMessage()).isEqualTo("Cancelled successfully");
    }

    @Test
    @DisplayName("cancelInvoice [Timeout]: Timeout after sending cancel must map to TIMEOUT_UNKNOWN with retryable=true")
    void cancelInvoiceTimeoutAfterSending() {
        ProviderCancelRequest request = ProviderCancelRequest.builder()
                .refId("ref-cancel-timeout")
                .transactionId("tx-timeout")
                .reason("Khách hủy đơn")
                .build();

        when(apiClient.cancelInvoice(eq("valid_bearer_token"), any(), any())).thenThrow(
                new InvoiceProviderTimeoutException("MISA", "Socket timeout during cancel", null)
        );

        ProviderCancelResult result = provider.cancelInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.TIMEOUT_UNKNOWN);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getMessage()).contains("timed out");
    }

    @Test
    @DisplayName("cancelInvoice [Inquiry Case 1]: EInvoiceStatus=2, IsDelete=true converges to SUCCESS")
    void cancelInvoiceInquiryConfirmedCancelled_Case1() {
        String rawRefId = "ref-cancel-case1";
        String deterministicRefId = MisaPayloadMapper.toDeterministicRefId(rawRefId);

        ProviderCancelRequest request = ProviderCancelRequest.builder()
                .refId(rawRefId)
                .transactionId("tx-case1")
                .reason("Hủy đơn hoàn tiền")
                .build();

        when(apiClient.cancelInvoice(eq("valid_bearer_token"), any(), any())).thenReturn(
                MisaCancelInvoiceResponse.builder()
                        .success(false)
                        .errorCode("InvoiceAlreadyCancelled")
                        .errors(List.of("Hóa đơn đã bị xóa bỏ từ trước"))
                        .build()
        );

        MisaInvoiceStatusItemDto statusItem = MisaInvoiceStatusItemDto.builder()
                .refId(deterministicRefId)
                .transactionId("tx-case1")
                .eInvoiceStatus(2)
                .isDelete(true)
                .build();

        when(apiClient.getInvoiceStatusByRefId(eq("valid_bearer_token"), eq(Collections.singletonList(deterministicRefId))))
                .thenReturn(List.of(statusItem));

        ProviderCancelResult result = provider.cancelInvoice(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
        assertThat(result.getMessage()).contains("Cancellation verified via provider inquiry (EInvoiceStatus=2 / isDelete=true)");
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("cancelInvoice [Inquiry Case 2]: EInvoiceStatus=1, IsDelete=false holds FAILED and retryable")
    void cancelInvoiceInquiryActive_Case2() {
        String rawRefId = "ref-cancel-case2";
        String deterministicRefId = MisaPayloadMapper.toDeterministicRefId(rawRefId);

        ProviderCancelRequest request = ProviderCancelRequest.builder()
                .refId(rawRefId)
                .transactionId("tx-case2")
                .reason("Hủy đơn hàng")
                .build();

        when(apiClient.cancelInvoice(eq("valid_bearer_token"), any(), any())).thenReturn(
                MisaCancelInvoiceResponse.builder()
                        .success(false)
                        .errorCode("GeneralError")
                        .errors(List.of("Không thể hủy"))
                        .build()
        );

        MisaInvoiceStatusItemDto statusItem = MisaInvoiceStatusItemDto.builder()
                .refId(deterministicRefId)
                .transactionId("tx-case2")
                .eInvoiceStatus(1) // 1: Active
                .isDelete(false)
                .build();

        when(apiClient.getInvoiceStatusByRefId(eq("valid_bearer_token"), eq(Collections.singletonList(deterministicRefId))))
                .thenReturn(List.of(statusItem));

        ProviderCancelResult result = provider.cancelInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.FAILED);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getMessage()).contains("status is not cancelled");
    }

    @Test
    @DisplayName("cancelInvoice [Inquiry Case 3]: EInvoiceStatus=3 (replacement) holds FAILED and retryable")
    void cancelInvoiceInquiryReplacement_Case3() {
        String rawRefId = "ref-cancel-case3";
        String deterministicRefId = MisaPayloadMapper.toDeterministicRefId(rawRefId);

        ProviderCancelRequest request = ProviderCancelRequest.builder()
                .refId(rawRefId)
                .transactionId("tx-case3")
                .reason("Hủy đơn hàng")
                .build();

        when(apiClient.cancelInvoice(eq("valid_bearer_token"), any(), any())).thenReturn(
                MisaCancelInvoiceResponse.builder()
                        .success(false)
                        .errorCode("Error")
                        .build()
        );

        MisaInvoiceStatusItemDto statusItem = MisaInvoiceStatusItemDto.builder()
                .refId(deterministicRefId)
                .transactionId("tx-case3")
                .eInvoiceStatus(3) // 3: Replaced
                .isDelete(false)
                .build();

        when(apiClient.getInvoiceStatusByRefId(eq("valid_bearer_token"), eq(Collections.singletonList(deterministicRefId))))
                .thenReturn(List.of(statusItem));

        ProviderCancelResult result = provider.cancelInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.FAILED);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getMessage()).contains("status is not cancelled");
    }

    @Test
    @DisplayName("cancelInvoice [Inquiry Case 4]: EInvoiceStatus=5 (adjustment) holds FAILED and retryable")
    void cancelInvoiceInquiryAdjustment_Case4() {
        String rawRefId = "ref-cancel-case4";
        String deterministicRefId = MisaPayloadMapper.toDeterministicRefId(rawRefId);

        ProviderCancelRequest request = ProviderCancelRequest.builder()
                .refId(rawRefId)
                .transactionId("tx-case4")
                .reason("Hủy đơn hàng")
                .build();

        when(apiClient.cancelInvoice(eq("valid_bearer_token"), any(), any())).thenReturn(
                MisaCancelInvoiceResponse.builder()
                        .success(false)
                        .errorCode("Error")
                        .build()
        );

        MisaInvoiceStatusItemDto statusItem = MisaInvoiceStatusItemDto.builder()
                .refId(deterministicRefId)
                .transactionId("tx-case4")
                .eInvoiceStatus(5) // 5: Adjusted
                .isDelete(false)
                .build();

        when(apiClient.getInvoiceStatusByRefId(eq("valid_bearer_token"), eq(Collections.singletonList(deterministicRefId))))
                .thenReturn(List.of(statusItem));

        ProviderCancelResult result = provider.cancelInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.FAILED);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getMessage()).contains("status is not cancelled");
    }

    @Test
    @DisplayName("cancelInvoice [Inquiry Case 5]: EInvoiceStatus=1, IsDelete=true (conflicting) holds FAILED and retryable")
    void cancelInvoiceInquiryConflictingStatus_Case5() {
        String rawRefId = "ref-cancel-case5";
        String deterministicRefId = MisaPayloadMapper.toDeterministicRefId(rawRefId);

        ProviderCancelRequest request = ProviderCancelRequest.builder()
                .refId(rawRefId)
                .transactionId("tx-case5")
                .reason("Hủy đơn hàng")
                .build();

        when(apiClient.cancelInvoice(eq("valid_bearer_token"), any(), any())).thenReturn(
                MisaCancelInvoiceResponse.builder()
                        .success(false)
                        .errorCode("Conflict")
                        .build()
        );

        // Conflicting: EInvoiceStatus=1 (Active) but isDelete=true (Deleted)!
        MisaInvoiceStatusItemDto statusItem = MisaInvoiceStatusItemDto.builder()
                .refId(deterministicRefId)
                .transactionId("tx-case5")
                .eInvoiceStatus(1)
                .isDelete(true)
                .build();

        when(apiClient.getInvoiceStatusByRefId(eq("valid_bearer_token"), eq(Collections.singletonList(deterministicRefId))))
                .thenReturn(List.of(statusItem));

        ProviderCancelResult result = provider.cancelInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.FAILED);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getMessage()).contains("conflicting status");
    }

    @Test
    @DisplayName("cancelInvoice [Inquiry Case 5b]: EInvoiceStatus=2, IsDelete=false (conflicting) holds FAILED and retryable")
    void cancelInvoiceInquiryConflictingStatus2False_Case5b() {
        String rawRefId = "ref-cancel-case5b";
        String deterministicRefId = MisaPayloadMapper.toDeterministicRefId(rawRefId);

        ProviderCancelRequest request = ProviderCancelRequest.builder()
                .refId(rawRefId)
                .transactionId("tx-case5b")
                .reason("Hủy đơn hàng")
                .build();

        when(apiClient.cancelInvoice(eq("valid_bearer_token"), any(), any())).thenReturn(
                MisaCancelInvoiceResponse.builder()
                        .success(false)
                        .errorCode("Conflict")
                        .build()
        );

        MisaInvoiceStatusItemDto statusItem = MisaInvoiceStatusItemDto.builder()
                .refId(deterministicRefId)
                .transactionId("tx-case5b")
                .eInvoiceStatus(2)
                .isDelete(false)
                .build();

        when(apiClient.getInvoiceStatusByRefId(eq("valid_bearer_token"), eq(Collections.singletonList(deterministicRefId))))
                .thenReturn(List.of(statusItem));

        ProviderCancelResult result = provider.cancelInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.FAILED);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getMessage()).contains("conflicting status");
    }

    @Test
    @DisplayName("cancelInvoice [Inquiry Case 5c]: EInvoiceStatus=null, IsDelete=true holds FAILED and retryable")
    void cancelInvoiceInquiryNullStatusTrueDelete_Case5c() {
        String rawRefId = "ref-cancel-case5c";
        String deterministicRefId = MisaPayloadMapper.toDeterministicRefId(rawRefId);

        ProviderCancelRequest request = ProviderCancelRequest.builder()
                .refId(rawRefId)
                .transactionId("tx-case5c")
                .reason("Hủy đơn hàng")
                .build();

        when(apiClient.cancelInvoice(eq("valid_bearer_token"), any(), any())).thenReturn(
                MisaCancelInvoiceResponse.builder()
                        .success(false)
                        .errorCode("IncompleteStatus")
                        .build()
        );

        MisaInvoiceStatusItemDto statusItem = MisaInvoiceStatusItemDto.builder()
                .refId(deterministicRefId)
                .transactionId("tx-case5c")
                .eInvoiceStatus(null)
                .isDelete(true)
                .build();

        when(apiClient.getInvoiceStatusByRefId(eq("valid_bearer_token"), eq(Collections.singletonList(deterministicRefId))))
                .thenReturn(List.of(statusItem));

        ProviderCancelResult result = provider.cancelInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.FAILED);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getMessage()).contains("conflicting status");
    }

    @Test
    @DisplayName("cancelInvoice [Inquiry Case 6]: Missing both status fields (null/null) holds FAILED and retryable")
    void cancelInvoiceInquiryMissingBothStatusFields_Case6() {
        String rawRefId = "ref-cancel-case6";
        String deterministicRefId = MisaPayloadMapper.toDeterministicRefId(rawRefId);

        ProviderCancelRequest request = ProviderCancelRequest.builder()
                .refId(rawRefId)
                .transactionId("tx-case6")
                .reason("Hủy đơn hàng")
                .build();

        when(apiClient.cancelInvoice(eq("valid_bearer_token"), any(), any())).thenReturn(
                MisaCancelInvoiceResponse.builder()
                        .success(false)
                        .errorCode("Unconfirmed")
                        .build()
        );

        // Missing both status fields
        MisaInvoiceStatusItemDto statusItem = MisaInvoiceStatusItemDto.builder()
                .refId(deterministicRefId)
                .transactionId("tx-case6")
                .eInvoiceStatus(null)
                .isDelete(null)
                .build();

        when(apiClient.getInvoiceStatusByRefId(eq("valid_bearer_token"), eq(Collections.singletonList(deterministicRefId))))
                .thenReturn(List.of(statusItem));

        ProviderCancelResult result = provider.cancelInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.FAILED);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getMessage()).contains("missing status fields");
    }

    @Test
    @DisplayName("cancelInvoice [Inquiry Case 7a]: RefID mismatch holds FAILED and retryable")
    void cancelInvoiceInquiryRefIdMismatch_Case7a() {
        String rawRefId = "ref-cancel-mismatch";
        String deterministicRefId = MisaPayloadMapper.toDeterministicRefId(rawRefId);

        ProviderCancelRequest request = ProviderCancelRequest.builder()
                .refId(rawRefId)
                .transactionId("tx-expected")
                .reason("Hủy đơn hàng")
                .build();

        when(apiClient.cancelInvoice(eq("valid_bearer_token"), any(), any())).thenReturn(
                MisaCancelInvoiceResponse.builder()
                        .success(false)
                        .errorCode("GeneralError")
                        .build()
        );

        MisaInvoiceStatusItemDto mismatchedItem = MisaInvoiceStatusItemDto.builder()
                .refId("different-uuid-99999")
                .transactionId("tx-expected")
                .eInvoiceStatus(2)
                .isDelete(true)
                .build();

        when(apiClient.getInvoiceStatusByRefId(eq("valid_bearer_token"), eq(Collections.singletonList(deterministicRefId))))
                .thenReturn(List.of(mismatchedItem));

        ProviderCancelResult result = provider.cancelInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.FAILED);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getMessage()).contains("mismatched invoice RefID");
    }

    @Test
    @DisplayName("cancelInvoice [Inquiry Case 7b]: TransactionID mismatch holds FAILED and retryable")
    void cancelInvoiceInquiryTransactionIdMismatch_Case7b() {
        String rawRefId = "ref-cancel-tx-mismatch";
        String deterministicRefId = MisaPayloadMapper.toDeterministicRefId(rawRefId);

        ProviderCancelRequest request = ProviderCancelRequest.builder()
                .refId(rawRefId)
                .transactionId("tx-expected-100")
                .reason("Hủy đơn hàng")
                .build();

        when(apiClient.cancelInvoice(eq("valid_bearer_token"), any(), any())).thenReturn(
                MisaCancelInvoiceResponse.builder()
                        .success(false)
                        .errorCode("GeneralError")
                        .build()
        );

        MisaInvoiceStatusItemDto mismatchedItem = MisaInvoiceStatusItemDto.builder()
                .refId(deterministicRefId)
                .transactionId("tx-DIFFERENT-200")
                .eInvoiceStatus(2)
                .isDelete(true)
                .build();

        when(apiClient.getInvoiceStatusByRefId(eq("valid_bearer_token"), eq(Collections.singletonList(deterministicRefId))))
                .thenReturn(List.of(mismatchedItem));

        ProviderCancelResult result = provider.cancelInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.FAILED);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getMessage()).contains("mismatched invoice TransactionID");
    }

    @Test
    @DisplayName("cancelInvoice [Inquiry Timeout]: Verification inquiry timeout maps to TIMEOUT_UNKNOWN with retryable=true")
    void cancelInvoiceInquiryTimeout() {
        String rawRefId = "ref-cancel-inquiry-timeout";
        String deterministicRefId = MisaPayloadMapper.toDeterministicRefId(rawRefId);

        ProviderCancelRequest request = ProviderCancelRequest.builder()
                .refId(rawRefId)
                .transactionId("tx-timeout-test")
                .reason("Hủy đơn do sai thông tin")
                .build();

        when(apiClient.cancelInvoice(eq("valid_bearer_token"), any(), any())).thenReturn(
                MisaCancelInvoiceResponse.builder()
                        .success(false)
                        .errorCode("UnknownError")
                        .build()
        );

        when(apiClient.getInvoiceStatusByRefId(eq("valid_bearer_token"), eq(Collections.singletonList(deterministicRefId))))
                .thenThrow(new InvoiceProviderTimeoutException("MISA", "Read timed out during verification inquiry", null));

        ProviderCancelResult result = provider.cancelInvoice(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttemptOutcome()).isEqualTo(AttemptOutcome.TIMEOUT_UNKNOWN);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getMessage()).contains("verification inquiry timed out");
    }

    @Test
    @DisplayName("cancelInvoice: throws IllegalArgumentException if both refId and transactionId are missing or reason is blank")
    void cancelInvoiceValidationFailure() {
        ProviderCancelRequest requestNoIds = ProviderCancelRequest.builder()
                .reason("Hợp lệ")
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                provider.cancelInvoice(requestNoIds)
        );

        ProviderCancelRequest requestNoReason = ProviderCancelRequest.builder()
                .refId("ref-1")
                .reason("   ")
                .build();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                provider.cancelInvoice(requestNoReason)
        );
    }
}
