package com.shop.backend.einvoice.provider.misa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderAuthException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderFatalException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderRetryableException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderTimeoutException;
import com.shop.backend.einvoice.provider.misa.dto.MisaAuthRequest;
import com.shop.backend.einvoice.provider.misa.dto.MisaAuthResponse;
import com.shop.backend.einvoice.provider.misa.dto.MisaCancelInvoiceRequest;
import com.shop.backend.einvoice.provider.misa.dto.MisaCancelInvoiceResponse;
import com.shop.backend.einvoice.provider.misa.dto.MisaInvoiceStatusItemDto;
import com.shop.backend.einvoice.provider.misa.dto.MisaPublishInvoiceRequest;
import com.shop.backend.einvoice.provider.misa.dto.MisaPublishResponse;
import com.shop.backend.einvoice.provider.misa.dto.MisaStatusInquiryResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class MisaApiClient {

    private static final Logger log = LoggerFactory.getLogger(MisaApiClient.class);

    private final MisaProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public MisaApiClient(MisaProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
        BoundedDeadlineClientHttpRequestFactory factory = new BoundedDeadlineClientHttpRequestFactory(
                httpClient,
                Duration.ofSeconds(properties.getReadTimeoutSeconds()),
                10 * 1024 * 1024
        );

        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * Public constructor for testing with custom RestClient.
     */
    public MisaApiClient(MisaProperties properties, ObjectMapper objectMapper, RestClient customRestClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = customRestClient;
    }

    /**
     * Authenticates with MISA Open API to receive a Bearer JWT token.
     */
    public MisaAuthResponse authenticate(MisaAuthRequest request) {
        String endpoint = "/api/integration/auth/token";
        log.info("Calling MISA Auth API at {} with taxCode: {}", endpoint, request.getTaxCode());

        try {
            return restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MisaAuthResponse.class);
        } catch (ResourceAccessException e) {
            throw handleNetworkException(endpoint, e);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new InvoiceProviderAuthException("MISA", "HTTP_401", "Invalid credentials for MISA", e.getResponseBodyAsString(), e);
        } catch (HttpClientErrorException e) {
            throw handleClientErrorException(endpoint, e);
        } catch (HttpServerErrorException e) {
            throw handleServerErrorException(endpoint, e);
        }
    }

    /**
     * Publishes an invoice to MISA meInvoice v3.
     */
    public MisaPublishResponse publishInvoice(String token, MisaPublishInvoiceRequest request) {
        String endpoint = buildPublishEndpoint();
        log.info("Calling MISA Publish Invoice API at {} for refId: {}", endpoint, request.getRefId());

        try {
            MisaPublishResponse response = restClient.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("CompanyTaxCode", properties.getTaxCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MisaPublishResponse.class);

            validateBusinessResponse(endpoint, response);
            return response;
        } catch (ResourceAccessException e) {
            throw handleNetworkException(endpoint, e);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new InvoiceProviderAuthException("MISA", "HTTP_401", "MISA token expired or unauthorized during publish", e.getResponseBodyAsString(), e);
        } catch (HttpClientErrorException e) {
            throw handleClientErrorException(endpoint, e);
        } catch (HttpServerErrorException e) {
            throw handleServerErrorException(endpoint, e);
        }
    }

    /**
     * Queries invoice statuses by an array of RefIDs.
     */
    public List<MisaInvoiceStatusItemDto> getInvoiceStatusByRefId(String token, List<String> refIds) {
        String endpoint = buildStatusInquiryEndpoint();
        log.info("Calling MISA Invoice Status Inquiry API at {} for {} refIds", endpoint, refIds.size());

        try {
            MisaStatusInquiryResponse response = restClient.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("CompanyTaxCode", properties.getTaxCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(refIds)
                    .retrieve()
                    .body(MisaStatusInquiryResponse.class);

            if (response == null || !response.isSuccess()) {
                String errCode = response != null ? response.getErrorCode() : "UNKNOWN";
                log.warn("MISA status inquiry returned unsuccessful response: {}", errCode);
                return Collections.emptyList();
            }

            return parseInquiryStatusItems(response.getData());
        } catch (ResourceAccessException e) {
            throw handleNetworkException(endpoint, e);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new InvoiceProviderAuthException("MISA", "HTTP_401", "MISA token expired or unauthorized during status inquiry", e.getResponseBodyAsString(), e);
        } catch (HttpClientErrorException e) {
            throw handleClientErrorException(endpoint, e);
        } catch (HttpServerErrorException e) {
            throw handleServerErrorException(endpoint, e);
        }
    }

    /**
     * Downloads signed invoice files (XML or PDF) via POST with TransactionID list body.
     */
    public byte[] downloadInvoice(String token, String transactionId, String dataType) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId must not be null or blank");
        }
        return downloadInvoiceBatch(token, Collections.singletonList(transactionId), transactionId, dataType);
    }

    /**
     * Downloads signed invoice files for a batch of TransactionIDs (max 50 per MISA API limit).
     */
    public byte[] downloadInvoiceBatch(String token, List<String> transactionIds, String targetTransactionId, String dataType) {
        if (transactionIds == null || transactionIds.isEmpty()) {
            throw new IllegalArgumentException("transactionIds must not be null or empty");
        }
        if (transactionIds.size() > 50) {
            throw new InvoiceProviderFatalException("MISA", "BATCH_LIMIT_EXCEEDED",
                    "MISA download API supports a maximum of 50 TransactionIDs per request (requested: " + transactionIds.size() + ")", null);
        }

        String endpoint = buildDownloadEndpoint(dataType);
        log.info("Calling MISA Download Invoice API at {} via POST for {} transactionIds, target: {}, type: {}",
                endpoint, transactionIds.size(), targetTransactionId, dataType);

        try {
            String responseStr = restClient.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("CompanyTaxCode", properties.getTaxCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(transactionIds)
                    .retrieve()
                    .body(String.class);

            return parseDownloadFileBytes(responseStr, targetTransactionId, dataType);
        } catch (ResourceAccessException e) {
            throw handleNetworkException(endpoint, e);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new InvoiceProviderAuthException("MISA", "HTTP_401", "MISA token expired or unauthorized during download", e.getResponseBodyAsString(), e);
        } catch (HttpClientErrorException e) {
            throw handleClientErrorException(endpoint, e);
        } catch (HttpServerErrorException e) {
            throw handleServerErrorException(endpoint, e);
        }
    }

    private String buildDownloadEndpoint(String dataType) {
        String base = properties.isUseCodeApi()
                ? "/api/v3/code/itg/invoicepublished/downloadinvoice"
                : "/api/v3/itg/invoicepublished/downloadinvoice";
        String typeParam = (dataType != null ? dataType.toLowerCase() : "pdf");
        String query = "?downloadDataType=" + typeParam;
        if (properties.isUseCodeApi()) {
            query += "&invoiceWithCode=true";
        }
        return base + query;
    }

    byte[] parseDownloadFileBytes(String responseStr, String expectedTxId, String dataType) {
        if (responseStr == null || responseStr.isBlank()) {
            throw new InvoiceProviderFatalException("MISA", "EMPTY_DOWNLOAD_RESPONSE", "Received empty response from MISA download API", null);
        }
        String trimmed = responseStr.trim();
        if (trimmed.startsWith("%PDF") || trimmed.startsWith("<?xml") || (trimmed.startsWith("<") && trimmed.endsWith(">"))) {
            return trimmed.getBytes(StandardCharsets.UTF_8);
        }

        try {
            JsonNode root = objectMapper.readTree(trimmed);
            if (root.has("success") && !root.get("success").asBoolean()) {
                String errCode = root.has("errorCode") ? root.get("errorCode").asText() : "DOWNLOAD_ERROR";
                throw new InvoiceProviderFatalException("MISA", errCode, "MISA download API returned unsuccessful status", responseStr);
            }
            if (root.has("Success") && !root.get("Success").asBoolean()) {
                String errCode = root.has("ErrorCode") ? root.get("ErrorCode").asText() : "DOWNLOAD_ERROR";
                throw new InvoiceProviderFatalException("MISA", errCode, "MISA download API returned unsuccessful status", responseStr);
            }

            JsonNode dataNode = root.has("data") ? root.get("data") : root.get("Data");
            if (dataNode == null || dataNode.isNull()) {
                throw new InvoiceProviderFatalException("MISA", "DOWNLOAD_DATA_EMPTY", "No data payload in MISA download response", responseStr);
            }

            if (dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    JsonNode txNode = item.has("TransactionID") ? item.get("TransactionID") : item.get("transactionId");
                    if (expectedTxId == null || (txNode != null && expectedTxId.equalsIgnoreCase(txNode.asText()))) {
                        JsonNode contentNode = item.has("data") ? item.get("data") : (item.has("FileContent") ? item.get("FileContent") : item.get("Data"));
                        if (contentNode != null && !contentNode.isNull()) {
                            return decodeFileContentString(contentNode.asText(), dataType);
                        }
                    }
                }
                if (expectedTxId != null) {
                    throw new InvoiceProviderFatalException("MISA", "TRANSACTION_NOT_FOUND",
                            "TransactionID " + expectedTxId + " not found in download response", responseStr);
                }
                if (dataNode.size() > 0) {
                    JsonNode firstItem = dataNode.get(0);
                    JsonNode contentNode = firstItem.has("data") ? firstItem.get("data") : (firstItem.has("FileContent") ? firstItem.get("FileContent") : firstItem.get("Data"));
                    if (contentNode != null && !contentNode.isNull()) {
                        return decodeFileContentString(contentNode.asText(), dataType);
                    }
                }
            }

            if (dataNode.isTextual()) {
                String text = dataNode.asText().trim();
                if (text.startsWith("[") || text.startsWith("{")) {
                    JsonNode nested = objectMapper.readTree(text);
                    if (nested.isArray()) {
                        for (JsonNode item : nested) {
                            JsonNode txNode = item.has("TransactionID") ? item.get("TransactionID") : item.get("transactionId");
                            if (expectedTxId == null || (txNode != null && expectedTxId.equalsIgnoreCase(txNode.asText()))) {
                                JsonNode contentNode = item.has("data") ? item.get("data") : (item.has("FileContent") ? item.get("FileContent") : item.get("Data"));
                                if (contentNode != null && !contentNode.isNull()) {
                                    return decodeFileContentString(contentNode.asText(), dataType);
                                }
                            }
                        }
                        if (expectedTxId != null) {
                            throw new InvoiceProviderFatalException("MISA", "TRANSACTION_NOT_FOUND",
                                    "TransactionID " + expectedTxId + " not found in download response", responseStr);
                        }
                        if (nested.size() > 0) {
                            JsonNode firstItem = nested.get(0);
                            JsonNode contentNode = firstItem.has("data") ? firstItem.get("data") : (firstItem.has("FileContent") ? firstItem.get("FileContent") : firstItem.get("Data"));
                            if (contentNode != null && !contentNode.isNull()) {
                                return decodeFileContentString(contentNode.asText(), dataType);
                            }
                        }
                    }
                }
                return decodeFileContentString(text, dataType);
            }

            throw new InvoiceProviderFatalException("MISA", "INVALID_DOWNLOAD_DATA", "Unsupported data format in download response", responseStr);
        } catch (InvoiceProviderFatalException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse MISA download response for transactionId: {}", expectedTxId, e);
            throw new InvoiceProviderFatalException("MISA", "DOWNLOAD_PARSE_FAILED", "Failed to parse MISA download response: " + e.getMessage(), responseStr, e);
        }
    }

    private byte[] decodeFileContentString(String content, String dataType) {
        if (content == null || content.isBlank()) {
            throw new InvoiceProviderFatalException("MISA", "EMPTY_FILE_CONTENT", "Downloaded file content is empty", null);
        }
        String trimmed = content.trim();
        boolean isXml = "xml".equalsIgnoreCase(dataType);

        if (isXml && (trimmed.startsWith("<") || trimmed.startsWith("<?xml"))) {
            return trimmed.getBytes(StandardCharsets.UTF_8);
        }

        try {
            return Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException notBase64) {
            if ("pdf".equalsIgnoreCase(dataType) && !trimmed.startsWith("%PDF")) {
                throw new InvoiceProviderFatalException("MISA", "INVALID_FILE_PAYLOAD", "Downloaded PDF content is neither valid Base64 nor raw PDF binary", trimmed);
            }
            return trimmed.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * Cancels an invoice by RefID or TransactionID via RemoveInvoiceParam payload.
     */
    public MisaCancelInvoiceResponse cancelInvoice(String token, String refId, MisaCancelInvoiceRequest request) {
        String endpoint = "/api/v3/v3invoice/cancel/multi";
        log.info("Calling MISA Cancel Invoice API at {} for refId: {}", endpoint, refId);

        try {
            return restClient.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("CompanyTaxCode", properties.getTaxCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MisaCancelInvoiceResponse.class);
        } catch (ResourceAccessException e) {
            throw handleNetworkException(endpoint, e);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new InvoiceProviderAuthException("MISA", "HTTP_401", "MISA token expired or unauthorized during cancel", e.getResponseBodyAsString(), e);
        } catch (HttpClientErrorException e) {
            throw handleClientErrorException(endpoint, e);
        } catch (HttpServerErrorException e) {
            throw handleServerErrorException(endpoint, e);
        }
    }

    private String buildPublishEndpoint() {
        return properties.isUseCodeApi()
                ? "/api/v3/code/itg/invoicepublished/publish"
                : "/api/v3/itg/invoicepublished/publish";
    }

    private String buildStatusInquiryEndpoint() {
        return properties.isUseCodeApi()
                ? "/api/v3/code/itg/invoicepublished/invoice-status/refid"
                : "/api/v3/itg/invoicepublished/invoice-status/refid";
    }

    private void validateBusinessResponse(String endpoint, MisaPublishResponse response) {
        if (response == null) {
            throw new InvoiceProviderFatalException("MISA", "EMPTY_RESPONSE", "Received null response from MISA " + endpoint, null);
        }
        if (!response.isSuccess()) {
            String errCode = response.getErrorCode() != null ? response.getErrorCode() : "BUSINESS_ERROR";
            String desc = response.getDescriptionErrorCode() != null ? response.getDescriptionErrorCode() : "Business error from MISA";

            if ("TokenExpiredCode".equalsIgnoreCase(errCode) || "InvalidTokenCode".equalsIgnoreCase(errCode) || "UnAuthorize".equalsIgnoreCase(errCode)) {
                throw new InvoiceProviderAuthException("MISA", errCode, desc, response.toString());
            }
            if ("ServerMaintenance".equalsIgnoreCase(errCode) || "SystemBusy".equalsIgnoreCase(errCode)) {
                throw new InvoiceProviderRetryableException("MISA", errCode, desc, response.toString());
            }
            throw new InvoiceProviderFatalException("MISA", errCode, desc, response.toString());
        }
    }

    private List<MisaInvoiceStatusItemDto> parseInquiryStatusItems(Object data) {
        if (data == null) {
            return Collections.emptyList();
        }
        try {
            if (data instanceof String strData) {
                String trimmed = strData.trim();
                if (trimmed.isEmpty()) {
                    return Collections.emptyList();
                }
                if (trimmed.startsWith("[")) {
                    return objectMapper.readValue(trimmed, new TypeReference<List<MisaInvoiceStatusItemDto>>() {});
                } else if (trimmed.startsWith("{")) {
                    MisaInvoiceStatusItemDto single = objectMapper.readValue(trimmed, MisaInvoiceStatusItemDto.class);
                    return Collections.singletonList(single);
                }
            }
            if (data instanceof List) {
                return objectMapper.convertValue(data, new TypeReference<List<MisaInvoiceStatusItemDto>>() {});
            }
            if (data instanceof Map) {
                MisaInvoiceStatusItemDto single = objectMapper.convertValue(data, MisaInvoiceStatusItemDto.class);
                return Collections.singletonList(single);
            }
            return objectMapper.convertValue(data, new TypeReference<List<MisaInvoiceStatusItemDto>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse MISA status inquiry response data: {}", data, e);
            return Collections.emptyList();
        }
    }

    private InvoiceProviderException handleNetworkException(String endpoint, ResourceAccessException e) {
        log.error("Network or timeout error calling MISA endpoint: {}", endpoint, e);
        return new InvoiceProviderTimeoutException("MISA", "Timeout calling MISA " + endpoint + ": " + e.getMessage(), e);
    }

    private InvoiceProviderException handleClientErrorException(String endpoint, HttpClientErrorException e) {
        HttpStatusCode status = e.getStatusCode();
        String responseBody = e.getResponseBodyAsString();
        log.error("MISA Client error {} calling endpoint {}: {}", status, endpoint, responseBody);

        if (status.value() == 429) {
            return new InvoiceProviderRetryableException("MISA", "HTTP_429", "Rate limit exceeded on MISA", responseBody, e);
        }
        return new InvoiceProviderFatalException("MISA", "HTTP_" + status.value(), "Client error from MISA: " + status, responseBody, e);
    }

    private InvoiceProviderException handleServerErrorException(String endpoint, HttpServerErrorException e) {
        HttpStatusCode status = e.getStatusCode();
        String responseBody = e.getResponseBodyAsString();
        log.error("MISA Server error {} calling endpoint {}: {}", status, endpoint, responseBody);

        return new InvoiceProviderRetryableException("MISA", "HTTP_" + status.value(), "Server error from MISA: " + status, responseBody, e);
    }
}
