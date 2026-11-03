package com.shop.backend.einvoice.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.einvoice.model.AttemptOutcome;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderAuthException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderFatalException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderRetryableException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderTimeoutException;
import com.shop.backend.einvoice.provider.misa.MisaApiClient;
import com.shop.backend.einvoice.provider.misa.MisaProperties;
import com.shop.backend.einvoice.provider.misa.dto.MisaAuthRequest;
import com.shop.backend.einvoice.provider.misa.dto.MisaAuthResponse;
import com.shop.backend.einvoice.provider.misa.dto.MisaCancelInvoiceRequest;
import com.shop.backend.einvoice.provider.misa.dto.MisaCancelInvoiceResponse;
import com.shop.backend.einvoice.provider.misa.dto.MisaInvoiceStatusItemDto;
import com.shop.backend.einvoice.provider.misa.dto.MisaPublishInvoiceRequest;
import com.shop.backend.einvoice.provider.misa.dto.MisaPublishResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MisaApiClientTest {

    private ObjectMapper objectMapper;
    private MisaProperties properties;
    private MisaApiClient apiClient;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new MisaProperties();
        properties.setBaseUrl("https://testapi.meinvoice.vn");
        properties.setTaxCode("0101243150");

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        apiClient = new MisaApiClient(properties, objectMapper, restClient);
    }

    @Test
    @DisplayName("authenticate: successfully receives Bearer token on HTTP 200")
    void authenticateSuccess() {
        String responseJson = """
                {
                    "Success": true,
                    "Data": "dummy_misa_jwt_token_12345",
                    "ErrorCode": null,
                    "Errors": []
                }
                """;

        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/integration/auth/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        MisaAuthRequest req = MisaAuthRequest.builder()
                .appId("test_app_id")
                .taxCode("0101243150")
                .username("test_user")
                .password("secret")
                .build();

        MisaAuthResponse response = apiClient.authenticate(req);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("dummy_misa_jwt_token_12345");
        mockServer.verify();
    }

    @Test
    @DisplayName("authenticate: throws InvoiceProviderAuthException on HTTP 401 Unauthorized")
    void authenticateUnauthorizedThrowsAuthException() {
        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/integration/auth/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withUnauthorizedRequest());

        MisaAuthRequest req = MisaAuthRequest.builder().taxCode("0101243150").build();

        assertThatThrownBy(() -> apiClient.authenticate(req))
                .isInstanceOf(InvoiceProviderAuthException.class)
                .hasMessageContaining("Invalid credentials");
        mockServer.verify();
    }

    @Test
    @DisplayName("publishInvoice: successfully parses publish response")
    void publishInvoiceSuccess() {
        String responseJson = """
                {
                    "Success": true,
                    "Data": "{\\"TransactionID\\":\\"tx_123\\",\\"InvNo\\":\\"00000001\\",\\"InvSeries\\":\\"1C26TYY\\",\\"TaxAuthorityCode\\":\\"TCT_CODE_123\\",\\"LookupCode\\":\\"LK987\\"}",
                    "ErrorCode": null,
                    "Errors": []
                }
                """;

        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/v3/itg/invoicepublished/publish"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer valid_token"))
                .andExpect(header("CompanyTaxCode", "0101243150"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        MisaPublishInvoiceRequest req = MisaPublishInvoiceRequest.builder()
                .refId("ref-uuid-1")
                .build();

        MisaPublishResponse response = apiClient.publishInvoice("valid_token", req);

        assertThat(response.isSuccess()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("publishInvoice: throws InvoiceProviderAuthException when MISA returns TokenExpiredCode")
    void publishInvoiceTokenExpiredThrowsAuthException() {
        String responseJson = """
                {
                    "Success": false,
                    "ErrorCode": "TokenExpiredCode",
                    "DescriptionErrorCode": "Token has expired"
                }
                """;

        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/v3/itg/invoicepublished/publish"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        MisaPublishInvoiceRequest req = MisaPublishInvoiceRequest.builder().refId("ref-1").build();

        assertThatThrownBy(() -> apiClient.publishInvoice("token", req))
                .isInstanceOf(InvoiceProviderAuthException.class)
                .hasMessageContaining("Token has expired");
        mockServer.verify();
    }

    @Test
    @DisplayName("publishInvoice: throws InvoiceProviderFatalException on HTTP 400 Bad Request (non-retryable)")
    void publishInvoiceHttp400ThrowsFatalException() {
        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/v3/itg/invoicepublished/publish"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest().body("{\"ErrorCode\":\"InvalidTaxCode\"}"));

        MisaPublishInvoiceRequest req = MisaPublishInvoiceRequest.builder().refId("ref-1").build();

        assertThatThrownBy(() -> apiClient.publishInvoice("token", req))
                .isInstanceOfSatisfying(InvoiceProviderFatalException.class, e -> {
                    assertThat(e.isRetryable()).isFalse();
                });
        mockServer.verify();
    }

    @Test
    @DisplayName("publishInvoice: throws InvoiceProviderRetryableException on HTTP 429 Rate Limit")
    void publishInvoiceHttp429ThrowsRetryableException() {
        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/v3/itg/invoicepublished/publish"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("Rate limit exceeded"));

        MisaPublishInvoiceRequest req = MisaPublishInvoiceRequest.builder().refId("ref-1").build();

        assertThatThrownBy(() -> apiClient.publishInvoice("token", req))
                .isInstanceOfSatisfying(InvoiceProviderRetryableException.class, e -> {
                    assertThat(e.isRetryable()).isTrue();
                });
        mockServer.verify();
    }

    @Test
    @DisplayName("publishInvoice: throws InvoiceProviderRetryableException on HTTP 500 Server Error")
    void publishInvoiceHttp500ThrowsRetryableException() {
        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/v3/itg/invoicepublished/publish"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body("MISA internal failure"));

        MisaPublishInvoiceRequest req = MisaPublishInvoiceRequest.builder().refId("ref-1").build();

        assertThatThrownBy(() -> apiClient.publishInvoice("token", req))
                .isInstanceOfSatisfying(InvoiceProviderRetryableException.class, e -> {
                    assertThat(e.isRetryable()).isTrue();
                });
        mockServer.verify();
    }

    @Test
    @DisplayName("publishInvoice: network timeout should throw InvoiceProviderTimeoutException mapped to TIMEOUT_UNKNOWN")
    void publishInvoiceTimeoutThrowsTimeoutException() {
        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/v3/itg/invoicepublished/publish"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new org.springframework.web.client.ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out"));
                });

        MisaPublishInvoiceRequest req = MisaPublishInvoiceRequest.builder().refId("ref-1").build();

        assertThatThrownBy(() -> apiClient.publishInvoice("token", req))
                .isInstanceOfSatisfying(InvoiceProviderTimeoutException.class, e -> {
                    assertThat(e.getAttemptOutcome()).isEqualTo(AttemptOutcome.TIMEOUT_UNKNOWN);
                    assertThat(e.isRetryable()).isTrue();
                });
        mockServer.verify();
    }

    @Test
    @DisplayName("getInvoiceStatusByRefId: parses status items list on success")
    void getInvoiceStatusByRefIdSuccess() {
        String responseJson = """
                {
                    "Success": true,
                    "Data": "[{\\"RefID\\":\\"ref-123\\",\\"TransactionID\\":\\"tx-1\\",\\"InvNo\\":\\"00000005\\",\\"InvSeries\\":\\"1C26TYY\\",\\"InvoiceStatus\\":2,\\"TaxAuthorityCode\\":\\"CQT-888\\",\\"LookupCode\\":\\"LK123\\"}]",
                    "ErrorCode": null,
                    "Errors": []
                }
                """;

        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/v3/itg/invoicepublished/invoice-status/refid"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        List<MisaInvoiceStatusItemDto> items = apiClient.getInvoiceStatusByRefId("token", List.of("ref-123"));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getRefId()).isEqualTo("ref-123");
        assertThat(items.get(0).getInvNo()).isEqualTo("00000005");
        assertThat(items.get(0).getInvoiceStatus()).isEqualTo(2);
        mockServer.verify();
    }

    @Test
    @DisplayName("cancelInvoice: successfully sends RemoveInvoiceParam and receives success response")
    void cancelInvoiceSuccess() {
        String responseJson = """
                {
                    "Success": true,
                    "ErrorCode": null,
                    "Errors": []
                }
                """;

        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/v3/v3invoice/cancel/multi"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token"))
                .andExpect(header("CompanyTaxCode", "0101243150"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.IsSendEmail").value(false))
                .andExpect(jsonPath("$.EntityState").value(0))
                .andExpect(jsonPath("$.data").isString())
                .andExpect(jsonPath("$.data").value(containsString("\"RefID\":\"ref-1\"")))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        MisaCancelInvoiceRequest req = MisaCancelInvoiceRequest.builder()
                .data("[{\"RefID\":\"ref-1\",\"DeletedReason\":\"Sai sót thông tin\"}]")
                .isSendEmail(false)
                .entityState(0)
                .build();

        MisaCancelInvoiceResponse res = apiClient.cancelInvoice("token", "ref-1", req);

        assertThat(res.isSuccess()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("cancelInvoice: validates exact JSON serialization of RemoveInvoiceParam request body")
    void cancelInvoiceRequestFixtureMatchingOfficialContract() {
        String responseJson = """
                {
                    "Success": true,
                    "ErrorCode": null,
                    "Errors": null
                }
                """;

        String expectedItemData = "[{\"RefID\":\"4a88f414-4e67-3f1c-ba76-485c18530140\",\"TransactionID\":\"tx-101\",\"InvNo\":\"00000001\",\"InvSeries\":\"1C26TAA\",\"InvTemplateNo\":\"1\",\"DeletedReason\":\"Customer return\",\"DeletedDate\":\"2026-10-31T14:30:00\"}]";

        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/v3/v3invoice/cancel/multi"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer valid_token"))
                .andExpect(header("CompanyTaxCode", "0101243150"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data").value(expectedItemData))
                .andExpect(jsonPath("$.fileContent").doesNotExist())
                .andExpect(jsonPath("$.fileName").doesNotExist())
                .andExpect(jsonPath("$.IsSendEmail").value(false))
                .andExpect(jsonPath("$.ReceiverEmail").doesNotExist())
                .andExpect(jsonPath("$.ReceiverName").doesNotExist())
                .andExpect(jsonPath("$.EntityState").value(0))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        MisaCancelInvoiceRequest req = MisaCancelInvoiceRequest.builder()
                .data(expectedItemData)
                .isSendEmail(false)
                .entityState(0)
                .build();

        MisaCancelInvoiceResponse res = apiClient.cancelInvoice("valid_token", "4a88f414-4e67-3f1c-ba76-485c18530140", req);

        assertThat(res.isSuccess()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("downloadInvoice: POST request with TransactionID list body returns Base64 decoded PDF bytes")
    void downloadInvoicePdfViaPost() {
        String base64PdfContent = java.util.Base64.getEncoder().encodeToString("%PDF-1.4_SAMPLE".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String responseJson = String.format("""
                {
                    "success": true,
                    "errorCode": null,
                    "errors": [],
                    "data": [
                        {
                            "TransactionID": "tx-101",
                            "data": "%s"
                        }
                    ]
                }
                """, base64PdfContent);

        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/v3/itg/invoicepublished/downloadinvoice?downloadDataType=pdf"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer valid_token"))
                .andExpect(content().string("[\"tx-101\"]"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        byte[] pdfBytes = apiClient.downloadInvoice("valid_token", "tx-101", "pdf");

        assertThat(pdfBytes).isEqualTo("%PDF-1.4_SAMPLE".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockServer.verify();
    }

    @Test
    @DisplayName("downloadInvoice: POST request with escaped JSON string in data returns XML bytes")
    void downloadInvoiceXmlWithEscapedJsonData() {
        String responseJson = """
                {
                    "success": true,
                    "errorCode": null,
                    "errors": [],
                    "data": "[{\\"TransactionID\\":\\"tx-102\\",\\"data\\":\\"<HDon>xml_data</HDon>\\"}]"
                }
                """;

        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/v3/itg/invoicepublished/downloadinvoice?downloadDataType=xml"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string("[\"tx-102\"]"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        byte[] xmlBytes = apiClient.downloadInvoice("valid_token", "tx-102", "xml");

        assertThat(new String(xmlBytes, java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("<HDon>xml_data</HDon>");
        mockServer.verify();
    }

    @Test
    @DisplayName("downloadInvoiceBatch: exceeding 50 TransactionIDs throws BATCH_LIMIT_EXCEEDED fatal exception")
    void downloadInvoiceBatchLimitExceededThrowsException() {
        List<String> tooManyIds = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(i -> "tx-" + i)
                .toList();

        assertThatThrownBy(() -> apiClient.downloadInvoiceBatch("valid_token", tooManyIds, "tx-1", "pdf"))
                .isInstanceOf(InvoiceProviderFatalException.class)
                .hasMessageContaining("maximum of 50 TransactionIDs per request")
                .satisfies(ex -> assertThat(((InvoiceProviderFatalException) ex).getErrorCode()).isEqualTo("BATCH_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("downloadInvoice: throws TRANSACTION_NOT_FOUND when target TransactionID is not in response")
    void downloadInvoiceTransactionNotFoundThrowsException() {
        String responseJson = """
                {
                    "success": true,
                    "errorCode": null,
                    "errors": [],
                    "data": [
                        {
                            "TransactionID": "tx-other",
                            "data": "some_data"
                        }
                    ]
                }
                """;

        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/v3/itg/invoicepublished/downloadinvoice?downloadDataType=pdf"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> apiClient.downloadInvoice("valid_token", "tx-101", "pdf"))
                .isInstanceOf(InvoiceProviderFatalException.class)
                .hasMessageContaining("TransactionID tx-101 not found in download response")
                .satisfies(ex -> assertThat(((InvoiceProviderFatalException) ex).getErrorCode()).isEqualTo("TRANSACTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("downloadInvoice: throws INVALID_FILE_PAYLOAD when PDF content is neither Base64 nor raw PDF")
    void downloadInvoiceInvalidPdfPayloadThrowsException() {
        String responseJson = """
                {
                    "success": true,
                    "errorCode": null,
                    "errors": [],
                    "data": [
                        {
                            "TransactionID": "tx-101",
                            "data": "invalid!@#not_base64_nor_pdf"
                        }
                    ]
                }
                """;

        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/v3/itg/invoicepublished/downloadinvoice?downloadDataType=pdf"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> apiClient.downloadInvoice("valid_token", "tx-101", "pdf"))
                .isInstanceOf(InvoiceProviderFatalException.class)
                .satisfies(ex -> assertThat(((InvoiceProviderFatalException) ex).getErrorCode()).isEqualTo("INVALID_FILE_PAYLOAD"));
    }

    @Test
    @DisplayName("authenticate: maps SocketTimeoutException or network failure to InvoiceProviderTimeoutException")
    void authenticateTimeoutThrowsTimeoutException() {
        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/integration/auth/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new org.springframework.web.client.ResourceAccessException("Read timed out", new java.net.SocketTimeoutException("Read timed out"));
                });

        MisaAuthRequest req = MisaAuthRequest.builder()
                .appId("test_app_id")
                .taxCode("0101243150")
                .username("test_user")
                .password("secret")
                .build();

        assertThatThrownBy(() -> apiClient.authenticate(req))
                .isInstanceOf(InvoiceProviderTimeoutException.class)
                .hasMessageContaining("Timeout calling MISA /api/integration/auth/token");
    }

    @Test
    @DisplayName("publishInvoice: maps network or deadline timeout to InvoiceProviderTimeoutException")
    void publishInvoiceDeadlineTimeoutThrowsTimeoutException() {
        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/v3/itg/invoicepublished/publish"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new org.springframework.web.client.ResourceAccessException("Execution timed out", new java.net.http.HttpTimeoutException("request timed out"));
                });

        MisaPublishInvoiceRequest req = MisaPublishInvoiceRequest.builder().build();

        assertThatThrownBy(() -> apiClient.publishInvoice("token", req))
                .isInstanceOf(InvoiceProviderTimeoutException.class)
                .hasMessageContaining("Timeout calling MISA /api/v3/itg/invoicepublished/publish");
    }

    @Test
    @DisplayName("getInvoiceStatusByRefId: maps network or deadline timeout to InvoiceProviderTimeoutException")
    void getInvoiceStatusByRefIdTimeoutThrowsTimeoutException() {
        mockServer.expect(requestTo("https://testapi.meinvoice.vn/api/v3/itg/invoicepublished/invoice-status/refid"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new org.springframework.web.client.ResourceAccessException("Read timed out", new java.net.SocketTimeoutException("Read timed out"));
                });

        assertThatThrownBy(() -> apiClient.getInvoiceStatusByRefId("token", List.of("ref-123")))
                .isInstanceOf(InvoiceProviderTimeoutException.class)
                .hasMessageContaining("Timeout calling MISA /api/v3/itg/invoicepublished/invoice-status/refid");
    }
}
