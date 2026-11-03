package com.shop.backend.einvoice.provider.misa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.shop.backend.einvoice.provider.exception.InvoiceProviderAuthException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderRetryableException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderTimeoutException;
import com.shop.backend.einvoice.provider.misa.dto.MisaCancelInvoiceRequest;
import com.shop.backend.einvoice.provider.misa.dto.MisaCancelInvoiceResponse;
import com.shop.backend.einvoice.provider.misa.dto.MisaInvoiceStatusItemDto;
import com.shop.backend.einvoice.provider.misa.dto.MisaPublishInvoiceRequest;
import com.shop.backend.einvoice.provider.misa.dto.MisaPublishResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MisaInvoiceProvider implements InvoiceProvider {

    private static final Logger log = LoggerFactory.getLogger(MisaInvoiceProvider.class);
    private static final String PROVIDER_NAME = "MISA";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final MisaApiClient apiClient;
    private final MisaTokenClient tokenClient;
    private final MisaProperties properties;
    private final MisaPayloadMapper payloadMapper;
    private final ObjectMapper objectMapper;

    public MisaInvoiceProvider(
            MisaApiClient apiClient,
            MisaTokenClient tokenClient,
            MisaProperties properties,
            MisaPayloadMapper payloadMapper,
            ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.tokenClient = tokenClient;
        this.properties = properties;
        this.payloadMapper = payloadMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public ProviderIssueResult issueInvoice(ProviderIssueRequest request) {
        if (request.getInvoiceType() != null && request.getInvoiceType() != InvoiceType.ORIGINAL) {
            throw new UnsupportedOperationException(
                    "Invoice issuance for type " + request.getInvoiceType() + " is not supported in MisaInvoiceProvider yet (scheduled for EINV-2.3.2)");
        }

        log.info("Processing issue invoice via MISA for refId: {}, invoiceId: {}", request.getRefId(), request.getInvoiceId());

        try {
            return executeWithTokenRetry(token -> {
                MisaPublishInvoiceRequest publishReq = payloadMapper.toMisaPublishRequest(request, properties);
                MisaPublishResponse response = apiClient.publishInvoice(token, publishReq);

                return parsePublishResult(request, response);
            });
        } catch (InvoiceProviderTimeoutException e) {
            log.error("Timeout occurred while issuing invoice via MISA for refId: {}. Marking outcome TIMEOUT_UNKNOWN", request.getRefId(), e);
            return ProviderIssueResult.builder()
                    .success(false)
                    .attemptOutcome(AttemptOutcome.TIMEOUT_UNKNOWN)
                    .errorCode(e.getErrorCode())
                    .errorMessage(e.getMessage())
                    .rawResponse(e.getRawResponse())
                    .retryable(true)
                    .build();
        } catch (InvoiceProviderException e) {
            log.error("Provider error issuing invoice via MISA for refId: {}: {}", request.getRefId(), e.getMessage());
            return ProviderIssueResult.builder()
                    .success(false)
                    .attemptOutcome(e.getAttemptOutcome())
                    .errorCode(e.getErrorCode())
                    .errorMessage(e.getMessage())
                    .rawResponse(e.getRawResponse())
                    .retryable(e.isRetryable())
                    .build();
        }
    }

    @Override
    public ProviderInquiryResult inquiryInvoiceStatus(ProviderInquiryRequest request) {
        String queryRefId = request.getRefId() != null
                ? MisaPayloadMapper.toDeterministicRefId(request.getRefId())
                : null;
        log.info("Inquiring invoice status via MISA for rawRefId: {}, queryRefId: {}", request.getRefId(), queryRefId);

        try {
            return executeWithTokenRetry(token -> {
                List<MisaInvoiceStatusItemDto> items = apiClient.getInvoiceStatusByRefId(token, Collections.singletonList(queryRefId));

                if (items == null || items.isEmpty()) {
                    return ProviderInquiryResult.builder()
                            .found(false)
                            .refId(request.getRefId())
                            .attemptOutcome(AttemptOutcome.SUCCESS)
                            .isIssued(false)
                            .isRejected(false)
                            .build();
                }

                MisaInvoiceStatusItemDto item = items.get(0);
                boolean isIssued = (item.getPublishStatus() != null && item.getPublishStatus() == 1)
                        || (item.getEInvoiceStatus() != null && item.getEInvoiceStatus() == 1);
                boolean isRejected = (item.getPublishStatus() != null && item.getPublishStatus() == 2);

                return ProviderInquiryResult.builder()
                        .found(true)
                        .refId(item.getRefId())
                        .transactionId(item.getTransactionId())
                        .invoiceNo(item.getInvNo())
                        .invoiceSeries(item.getInvSeries())
                        .templateCode(item.getInvTemplateNo())
                        .status(item.getInvoiceStatus() != null ? String.valueOf(item.getInvoiceStatus()) : null)
                        .taxAuthorityCode(item.getTaxAuthorityCode())
                        .lookupCode(item.getLookupCode())
                        .attemptOutcome(AttemptOutcome.SUCCESS)
                        .isIssued(isIssued)
                        .isRejected(isRejected)
                        .build();
            });
        } catch (InvoiceProviderTimeoutException e) {
            log.error("Timeout inquiring invoice status for refId: {}", request.getRefId(), e);
            return ProviderInquiryResult.builder()
                    .found(false)
                    .refId(request.getRefId())
                    .attemptOutcome(AttemptOutcome.TIMEOUT_UNKNOWN)
                    .rawResponse(e.getMessage())
                    .isIssued(false)
                    .isRejected(false)
                    .build();
        } catch (InvoiceProviderException e) {
            log.error("Error inquiring invoice status for refId: {}: {}", request.getRefId(), e.getMessage());
            return ProviderInquiryResult.builder()
                    .found(false)
                    .refId(request.getRefId())
                    .attemptOutcome(e.getAttemptOutcome())
                    .rawResponse(e.getMessage())
                    .isIssued(false)
                    .isRejected(false)
                    .build();
        }
    }

    @Override
    public ProviderDownloadResult downloadInvoiceFiles(ProviderDownloadRequest request) {
        log.info("Downloading invoice files via MISA for transactionId: {}, type: {}", request.getTransactionId(), request.getDataType());

        try {
            return executeWithTokenRetry(token -> {
                byte[] xmlBytes = null;
                byte[] pdfBytes = null;

                if (request.getDataType() == ProviderDownloadRequest.DownloadDataType.XML
                        || request.getDataType() == ProviderDownloadRequest.DownloadDataType.BOTH) {
                    xmlBytes = apiClient.downloadInvoice(token, request.getTransactionId(), "xml");
                }
                if (request.getDataType() == ProviderDownloadRequest.DownloadDataType.PDF
                        || request.getDataType() == ProviderDownloadRequest.DownloadDataType.BOTH) {
                    pdfBytes = apiClient.downloadInvoice(token, request.getTransactionId(), "pdf");
                }

                String fileName = (request.getInvoiceSeries() != null && request.getInvoiceNo() != null)
                        ? request.getInvoiceSeries() + "_" + request.getInvoiceNo()
                        : "invoice_" + request.getRefId();

                return ProviderDownloadResult.builder()
                        .success(true)
                        .xmlBytes(xmlBytes)
                        .pdfBytes(pdfBytes)
                        .fileName(fileName)
                        .build();
            });
        } catch (Exception e) {
            log.error("Error downloading invoice files via MISA for transactionId: {}: {}", request.getTransactionId(), e.getMessage());
            return ProviderDownloadResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public ProviderCancelResult cancelInvoice(ProviderCancelRequest request) {
        if ((request.getRefId() == null || request.getRefId().isBlank())
                && (request.getTransactionId() == null || request.getTransactionId().isBlank())) {
            throw new IllegalArgumentException("Either refId or transactionId must be provided for cancellation");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new IllegalArgumentException("Cancellation reason must not be null or blank");
        }

        String cancelRefId = request.getRefId() != null
                ? MisaPayloadMapper.toDeterministicRefId(request.getRefId())
                : null;
        log.info("Cancelling invoice via MISA for rawRefId: {}, cancelRefId: {}", request.getRefId(), cancelRefId);

        try {
            return executeWithTokenRetry(token -> {
                String dateStr = request.getCancelledAt() != null
                        ? request.getCancelledAt().format(ISO_FORMATTER)
                        : LocalDateTime.now().format(ISO_FORMATTER);

                MisaCancelInvoiceRequest cancelReq = payloadMapper.toMisaCancelRequest(
                        cancelRefId,
                        request.getTransactionId(),
                        request.getInvoiceNo(),
                        request.getInvoiceSeries(),
                        null,
                        request.getReason(),
                        dateStr
                );

                MisaCancelInvoiceResponse response;
                try {
                    response = apiClient.cancelInvoice(token, cancelRefId, cancelReq);
                } catch (InvoiceProviderTimeoutException e) {
                    throw e;
                } catch (InvoiceProviderException e) {
                    return verifyCancellationOrHoldUnknown(token, cancelRefId, request.getTransactionId(), e.getErrorCode(), e.getMessage(), e.getRawResponse());
                }

                if (response != null && response.isSuccess()) {
                    return ProviderCancelResult.builder()
                            .success(true)
                            .message("Cancelled successfully")
                            .attemptOutcome(AttemptOutcome.SUCCESS)
                            .retryable(false)
                            .build();
                }

                String errCode = response != null ? response.getErrorCode() : "UNKNOWN_ERROR";
                String errMsg = (response != null && response.getErrors() != null)
                        ? String.join("; ", response.getErrors())
                        : (response != null ? response.getErrorCode() : "Empty response");
                return verifyCancellationOrHoldUnknown(token, cancelRefId, request.getTransactionId(), errCode, errMsg, response != null ? response.toString() : null);
            });
        } catch (InvoiceProviderTimeoutException e) {
            log.error("Timeout occurred while sending cancelInvoice via MISA for refId: {}. Marking outcome TIMEOUT_UNKNOWN", cancelRefId, e);
            return ProviderCancelResult.builder()
                    .success(false)
                    .message("Cancellation request timed out; status unknown on provider")
                    .attemptOutcome(AttemptOutcome.TIMEOUT_UNKNOWN)
                    .retryable(true)
                    .rawResponse(e.getMessage())
                    .build();
        } catch (InvoiceProviderException e) {
            log.error("Provider error executing cancellation workflow for refId: {}: {}", cancelRefId, e.getMessage());
            return ProviderCancelResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .attemptOutcome(e.getAttemptOutcome())
                    .retryable(e.isRetryable())
                    .rawResponse(e.getRawResponse())
                    .build();
        }
    }

    private ProviderCancelResult verifyCancellationOrHoldUnknown(
            String token,
            String cancelRefId,
            String transactionId,
            String originalErrorCode,
            String originalErrorMessage,
            String originalRawResponse) {

        log.warn("Cancellation request returned non-success response (code: {}, msg: {}). Initiating verification inquiry for refId: {}, txId: {}",
                originalErrorCode, originalErrorMessage, cancelRefId, transactionId);

        if (cancelRefId == null || cancelRefId.isBlank()) {
            log.warn("Cannot perform verification inquiry without RefID. Holding cancellation as FAILED (retryable for reconciliation).");
            return ProviderCancelResult.builder()
                    .success(false)
                    .message("Cancellation failed and verification inquiry cannot run without RefID: " + originalErrorMessage)
                    .attemptOutcome(AttemptOutcome.FAILED)
                    .retryable(true)
                    .rawResponse(originalRawResponse)
                    .build();
        }

        List<MisaInvoiceStatusItemDto> items;
        try {
            items = apiClient.getInvoiceStatusByRefId(token, Collections.singletonList(cancelRefId));
        } catch (InvoiceProviderTimeoutException e) {
            log.warn("Verification inquiry timed out for refId: {}. Holding cancellation outcome as TIMEOUT_UNKNOWN.", cancelRefId, e);
            return ProviderCancelResult.builder()
                    .success(false)
                    .message("Cancellation returned error/duplicate and verification inquiry timed out; transferring to reconciliation")
                    .attemptOutcome(AttemptOutcome.TIMEOUT_UNKNOWN)
                    .retryable(true)
                    .rawResponse(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.warn("Verification inquiry failed for refId: {}: {}. Holding outcome as FAILED (retryable).", cancelRefId, e.getMessage());
            return ProviderCancelResult.builder()
                    .success(false)
                    .message("Cancellation returned error and verification inquiry failed: " + e.getMessage())
                    .attemptOutcome(AttemptOutcome.FAILED)
                    .retryable(true)
                    .rawResponse(e.getMessage())
                    .build();
        }

        if (items == null || items.isEmpty()) {
            log.warn("Verification inquiry returned no items for refId: {}. Status unconfirmed; holding as FAILED (retryable).", cancelRefId);
            return ProviderCancelResult.builder()
                    .success(false)
                    .message("Verification inquiry found no invoice on provider for refId: " + cancelRefId)
                    .attemptOutcome(AttemptOutcome.FAILED)
                    .retryable(true)
                    .rawResponse(originalRawResponse)
                    .build();
        }

        MisaInvoiceStatusItemDto item = items.get(0);

        if (item.getRefId() == null || !cancelRefId.equalsIgnoreCase(item.getRefId())) {
            log.warn("Verification inquiry returned missing or mismatched RefID (expected: {}, got: {})", cancelRefId, item.getRefId());
            return ProviderCancelResult.builder()
                    .success(false)
                    .message("Verification inquiry returned mismatched invoice RefID (expected " + cancelRefId + ", got " + item.getRefId() + ")")
                    .attemptOutcome(AttemptOutcome.FAILED)
                    .retryable(true)
                    .rawResponse(item.toString())
                    .build();
        }

        if (transactionId != null && !transactionId.isBlank()
                && item.getTransactionId() != null && !item.getTransactionId().isBlank()
                && !transactionId.equalsIgnoreCase(item.getTransactionId())) {
            log.warn("Verification inquiry returned mismatched TransactionID (expected: {}, got: {})", transactionId, item.getTransactionId());
            return ProviderCancelResult.builder()
                    .success(false)
                    .message("Verification inquiry returned mismatched invoice TransactionID (expected " + transactionId + ", got " + item.getTransactionId() + ")")
                    .attemptOutcome(AttemptOutcome.FAILED)
                    .retryable(true)
                    .rawResponse(item.toString())
                    .build();
        }

        Integer eStatus = item.getEInvoiceStatus();
        Boolean isDel = item.getIsDelete();

        boolean isCancellationConfirmed = Integer.valueOf(2).equals(eStatus) && !Boolean.FALSE.equals(isDel);

        if (isCancellationConfirmed) {
            log.info("Verification inquiry confirmed invoice is CANCELLED on provider for refId: {}. Converging cancellation outcome to SUCCESS.", cancelRefId);
            return ProviderCancelResult.builder()
                    .success(true)
                    .message("Cancellation verified via provider inquiry (EInvoiceStatus=2 / isDelete=true)")
                    .attemptOutcome(AttemptOutcome.SUCCESS)
                    .retryable(false)
                    .rawResponse(item.toString())
                    .build();
        }

        if (eStatus == null && isDel == null) {
            log.warn("Verification inquiry returned invoice {} but both EInvoiceStatus and IsDelete fields are missing. Holding outcome as FAILED (retryable).", cancelRefId);
            return ProviderCancelResult.builder()
                    .success(false)
                    .message("Verification inquiry returned missing status fields (both EInvoiceStatus and IsDelete are null)")
                    .attemptOutcome(AttemptOutcome.FAILED)
                    .retryable(true)
                    .rawResponse(item.toString())
                    .build();
        }

        boolean isConflicting = (eStatus == null && Boolean.TRUE.equals(isDel))
                || (eStatus != null && eStatus != 2 && Boolean.TRUE.equals(isDel))
                || (Integer.valueOf(2).equals(eStatus) && Boolean.FALSE.equals(isDel));

        if (isConflicting) {
            log.warn("Verification inquiry returned conflicting or incomplete status for refId {}: EInvoiceStatus={}, isDelete={}. Holding outcome as FAILED (retryable).",
                    cancelRefId, eStatus, isDel);
            return ProviderCancelResult.builder()
                    .success(false)
                    .message("Verification inquiry returned conflicting status (EInvoiceStatus=" + eStatus + ", isDelete=" + isDel + ")")
                    .attemptOutcome(AttemptOutcome.FAILED)
                    .retryable(true)
                    .rawResponse(item.toString())
                    .build();
        }

        log.warn("Verification inquiry found invoice {} but its status is NOT cancelled (EInvoiceStatus={}, isDelete={}). Holding outcome as FAILED (retryable).",
                cancelRefId, eStatus, isDel);
        return ProviderCancelResult.builder()
                .success(false)
                .message("Invoice exists on provider but status is not cancelled (EInvoiceStatus=" + eStatus + ", isDelete=" + isDel + ")")
                .attemptOutcome(AttemptOutcome.FAILED)
                .retryable(true)
                .rawResponse(item.toString())
                .build();
    }

    /**
     * Executes an operation with automatic token retrieval, caching, and conditional 401 retry handling.
     * Uses evictTokenIfMatches to ensure a late 401 response does not evict a token already refreshed by another thread.
     */
    <T> T executeWithTokenRetry(Function<String, T> action) {
        String token;
        try {
            token = tokenClient.getValidToken(() -> apiClient.authenticate(tokenClient.buildAuthRequest()));
        } catch (InvoiceProviderTimeoutException e) {
            log.warn("MISA authentication timed out before business request could be dispatched: {}", e.getMessage());
            throw new InvoiceProviderRetryableException("MISA", "AUTH_TIMEOUT",
                    "MISA authentication timed out before business request: " + e.getMessage(), null, e);
        } catch (InvoiceProviderAuthException e) {
            throw e;
        } catch (InvoiceProviderException e) {
            log.warn("MISA authentication failed before business request could be dispatched: {}", e.getMessage());
            throw new InvoiceProviderRetryableException("MISA", "AUTH_FAILED",
                    "MISA authentication failed before business request: " + e.getMessage(), null, e);
        }

        try {
            return action.apply(token);
        } catch (InvoiceProviderAuthException e) {
            log.warn("MISA reported authentication/authorization error for token. Evicting token if matching and retrying once...", e);
            tokenClient.evictTokenIfMatches(token);
            String freshToken;
            try {
                freshToken = tokenClient.getValidToken(() -> apiClient.authenticate(tokenClient.buildAuthRequest()));
            } catch (InvoiceProviderTimeoutException te) {
                log.warn("MISA authentication timed out during token refresh: {}", te.getMessage());
                throw new InvoiceProviderRetryableException("MISA", "AUTH_TIMEOUT",
                        "MISA authentication timed out during token refresh: " + te.getMessage(), null, te);
            }
            return action.apply(freshToken);
        }
    }

    private ProviderIssueResult parsePublishResult(ProviderIssueRequest request, MisaPublishResponse response) {
        String transactionId = null;
        String invNo = null;
        String invSeries = properties.getInvoiceSeries();
        String taxAuthCode = null;
        String lookupCode = null;

        if (response.getData() != null) {
            try {
                JsonNode dataNode = (response.getData() instanceof String strData)
                        ? objectMapper.readTree(strData)
                        : objectMapper.valueToTree(response.getData());

                if (dataNode.isObject()) {
                    transactionId = dataNode.path("TransactionID").asText(null);
                    invNo = dataNode.path("InvNo").asText(null);
                    invSeries = dataNode.path("InvSeries").asText(invSeries);
                    taxAuthCode = dataNode.path("TaxAuthorityCode").asText(null);
                    lookupCode = dataNode.path("LookupCode").asText(null);
                }
            } catch (Exception e) {
                log.debug("Unable to parse MISA publish response data object: {}", response.getData());
            }
        }

        String lookupUrl = (lookupCode != null && properties.getLookupPortalUrl() != null)
                ? properties.getLookupPortalUrl() + "?code=" + lookupCode
                : null;

        return ProviderIssueResult.builder()
                .success(true)
                .transactionId(transactionId)
                .invoiceNo(invNo)
                .invoiceSeries(invSeries)
                .templateCode(request.getTemplateCode() != null ? request.getTemplateCode() : properties.getTemplateCode())
                .issuedAt(LocalDateTime.now())
                .taxAuthorityCode(taxAuthCode)
                .lookupCode(lookupCode)
                .lookupUrl(lookupUrl)
                .attemptOutcome(AttemptOutcome.SUCCESS)
                .retryable(false)
                .build();
    }
}
