package com.shop.backend.einvoice.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.provider.dto.ProviderInvoiceItemDto;
import com.shop.backend.einvoice.provider.dto.ProviderIssueRequest;
import com.shop.backend.einvoice.provider.misa.MisaPayloadMapper;
import com.shop.backend.einvoice.provider.misa.MisaProperties;
import com.shop.backend.einvoice.provider.misa.dto.MisaInvoiceDetailDto;
import com.shop.backend.einvoice.provider.misa.dto.MisaPublishInvoiceRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MisaPayloadMapperTest {

    private MisaPayloadMapper mapper;
    private MisaProperties properties;

    @BeforeEach
    void setUp() {
        mapper = new MisaPayloadMapper();
        properties = new MisaProperties();
        properties.setTemplateCode("1");
        properties.setInvoiceSeries("1C26TYY");
    }

    @Test
    @DisplayName("Should map ORIGINAL invoice request correctly to MISA publish request")
    void shouldMapOriginalInvoiceCorrectly() {
        ProviderInvoiceItemDto item1 = ProviderInvoiceItemDto.builder()
                .lineNumber(1)
                .itemCode("AO-01")
                .itemName("Áo Sơ Mi")
                .unitName("Cái")
                .quantity(new BigDecimal("2.00"))
                .unitPrice(new BigDecimal("200000.00"))
                .netAmount(new BigDecimal("370370.00"))
                .vatRate(new BigDecimal("0.08"))
                .vatAmount(new BigDecimal("29630.00"))
                .grossAmount(new BigDecimal("400000.00"))
                .discountAmount(BigDecimal.ZERO)
                .build();

        ProviderIssueRequest request = ProviderIssueRequest.builder()
                .refId("550e8400-e29b-41d4-a716-446655440000")
                .invoiceType(InvoiceType.ORIGINAL)
                .buyerLegalName("Nguyen Van A")
                .buyerTaxCode("0101243150")
                .buyerEmail("a@example.com")
                .totalGrossAmount(new BigDecimal("400000.00"))
                .totalNetAmount(new BigDecimal("370370.00"))
                .totalVatAmount(new BigDecimal("29630.00"))
                .totalDiscountAmount(BigDecimal.ZERO)
                .items(List.of(item1))
                .build();

        MisaPublishInvoiceRequest misaReq = mapper.toMisaPublishRequest(request, properties);

        assertThat(misaReq.getRefId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(misaReq.getReferenceType()).isEqualTo(0); // 0: Original
        assertThat(misaReq.getOrgInvoiceType()).isNull();
        assertThat(misaReq.getOrgInvNo()).isNull();
        assertThat(misaReq.getInvTemplateNo()).isEqualTo("1");
        assertThat(misaReq.getInvSeries()).isEqualTo("1C26TYY");
        assertThat(misaReq.getBuyerLegalName()).isEqualTo("Nguyen Van A");
        assertThat(misaReq.getInvoiceDetails()).hasSize(1);

        MisaInvoiceDetailDto detail = misaReq.getInvoiceDetails().get(0);
        assertThat(detail.getItemCode()).isEqualTo("AO-01");
        assertThat(detail.getVatRateName()).isEqualTo("8%");
        assertThat(detail.getIsPromotion()).isFalse();
    }

    @Test
    @DisplayName("Should map ADJUSTMENT invoice request with original reference fields")
    void shouldMapAdjustmentInvoiceWithReferences() {
        LocalDateTime origDate = LocalDateTime.of(2026, 10, 15, 10, 0, 0);

        ProviderIssueRequest request = ProviderIssueRequest.builder()
                .refId("660e8400-e29b-41d4-a716-446655440001")
                .invoiceType(InvoiceType.ADJUSTMENT)
                .originalInvoiceNo("00000123")
                .originalInvoiceSeries("1C26TYY")
                .originalTemplateCode("1")
                .originalInvoiceDate(origDate)
                .adjustmentReason("Điều chỉnh giảm do đổi trả hàng")
                .items(List.of())
                .build();

        MisaPublishInvoiceRequest misaReq = mapper.toMisaPublishRequest(request, properties);

        assertThat(misaReq.getReferenceType()).isEqualTo(2); // 2: Adjustment
        assertThat(misaReq.getOrgInvoiceType()).isEqualTo(1); // 1: Decree 123/254
        assertThat(misaReq.getOrgInvNo()).isEqualTo("00000123");
        assertThat(misaReq.getOrgInvSeries()).isEqualTo("1C26TYY");
        assertThat(misaReq.getOrgInvTemplateNo()).isEqualTo("1");
        assertThat(misaReq.getOrgInvDate()).isEqualTo("2026-10-15T10:00:00");
    }

    @Test
    @DisplayName("Should map REPLACEMENT invoice request correctly")
    void shouldMapReplacementInvoiceCorrectly() {
        ProviderIssueRequest request = ProviderIssueRequest.builder()
                .refId("770e8400-e29b-41d4-a716-446655440002")
                .invoiceType(InvoiceType.REPLACEMENT)
                .originalInvoiceNo("00000099")
                .originalInvoiceSeries("1C26TYY")
                .originalTemplateCode("1")
                .items(List.of())
                .build();

        MisaPublishInvoiceRequest misaReq = mapper.toMisaPublishRequest(request, properties);

        assertThat(misaReq.getReferenceType()).isEqualTo(1); // 1: Replacement
        assertThat(misaReq.getOrgInvoiceType()).isEqualTo(1);
        assertThat(misaReq.getOrgInvNo()).isEqualTo("00000099");
    }

    @Test
    @DisplayName("mapVatRateString must correctly format VAT rates")
    void shouldMapVatRatesAccurately() {
        assertThat(mapper.mapVatRateString(new BigDecimal("0.08"))).isEqualTo("8%");
        assertThat(mapper.mapVatRateString(new BigDecimal("8"))).isEqualTo("8%");
        assertThat(mapper.mapVatRateString(new BigDecimal("0.10"))).isEqualTo("10%");
        assertThat(mapper.mapVatRateString(new BigDecimal("10"))).isEqualTo("10%");
        assertThat(mapper.mapVatRateString(new BigDecimal("0.05"))).isEqualTo("5%");
        assertThat(mapper.mapVatRateString(BigDecimal.ZERO)).isEqualTo("0%");
        assertThat(mapper.mapVatRateString(null)).isEqualTo("8%");
    }

    @Test
    @DisplayName("toDeterministicRefId: when input is already an RFC 4122 UUID, it must be normalized to lowercase")
    void toDeterministicRefIdWithValidUuid() {
        String inputUuid = "550E8400-E29B-41D4-A716-446655440000";
        String mapped = MisaPayloadMapper.toDeterministicRefId(inputUuid);

        assertThat(mapped).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    @DisplayName("toDeterministicRefId: when input is an arbitrary business string, it must deterministically produce a valid RFC 4122 UUID")
    void toDeterministicRefIdWithArbitraryString() {
        String businessKey = "ORDER_CHECKOUT_INV_851492";

        String uuid1 = MisaPayloadMapper.toDeterministicRefId(businessKey);
        String uuid2 = MisaPayloadMapper.toDeterministicRefId(businessKey);

        assertThat(uuid1).isEqualTo(uuid2); // Strictly deterministic
        assertThat(java.util.UUID.fromString(uuid1)).isNotNull(); // Valid RFC 4122 UUID

        // Different keys produce distinct UUIDs
        String differentKeyUuid = MisaPayloadMapper.toDeterministicRefId("ORDER_CHECKOUT_INV_851493");
        assertThat(uuid1).isNotEqualTo(differentKeyUuid);
    }

    @Test
    @DisplayName("toDeterministicRefId: same invoice key produces exact same RefID across publish request and inquiry")
    void refIdStabilityAcrossIssueAndInquiry() {
        String rawKey = "EINV_ORIG_KEY_TEST_2026";

        ProviderIssueRequest issueRequest = ProviderIssueRequest.builder()
                .refId(rawKey)
                .invoiceType(InvoiceType.ORIGINAL)
                .items(List.of())
                .build();

        MisaPublishInvoiceRequest publishReq = mapper.toMisaPublishRequest(issueRequest, properties);
        String inquiryQueryRefId = MisaPayloadMapper.toDeterministicRefId(rawKey);

        assertThat(publishReq.getRefId()).isEqualTo(inquiryQueryRefId);
        assertThat(java.util.UUID.fromString(publishReq.getRefId())).isNotNull();
    }

    @Test
    @DisplayName("Golden Vector: Canonical UUID input must be preserved and normalized to lowercase")
    void goldenVectorCanonicalUuid() {
        String canonical = "e4b52b21-4235-430c-87d9-d8905b63e144";
        assertThat(MisaPayloadMapper.toDeterministicRefId(canonical)).isEqualTo("e4b52b21-4235-430c-87d9-d8905b63e144");

        String uppercase = "E4B52B21-4235-430C-87D9-D8905B63E144";
        assertThat(MisaPayloadMapper.toDeterministicRefId(uppercase)).isEqualTo("e4b52b21-4235-430c-87d9-d8905b63e144");
    }

    @Test
    @DisplayName("Golden Vector: Non-canonical UUID representations (braces and compact 32-hex) must format correctly")
    void goldenVectorNonCanonicalUuid() {
        String withBraces = "{e4b52b21-4235-430c-87d9-d8905b63e144}";
        assertThat(MisaPayloadMapper.toDeterministicRefId(withBraces)).isEqualTo("e4b52b21-4235-430c-87d9-d8905b63e144");

        String compact32Hex = "e4b52b214235430c87d9d8905b63e144";
        assertThat(MisaPayloadMapper.toDeterministicRefId(compact32Hex)).isEqualTo("e4b52b21-4235-430c-87d9-d8905b63e144");

        String compact32HexUppercase = "E4B52B214235430C87D9D8905B63E144";
        assertThat(MisaPayloadMapper.toDeterministicRefId(compact32HexUppercase)).isEqualTo("e4b52b21-4235-430c-87d9-d8905b63e144");
    }

    @Test
    @DisplayName("Golden Vector: Whitespace in input must be trimmed and produce identical literal RefID")
    void goldenVectorWhitespaceHandling() {
        String paddedUuid = "  \t e4b52b21-4235-430c-87d9-d8905b63e144 \r\n ";
        assertThat(MisaPayloadMapper.toDeterministicRefId(paddedUuid)).isEqualTo("e4b52b21-4235-430c-87d9-d8905b63e144");

        // GV-7 Literal assertion: Padded string must yield exact same literal as GV-6
        String paddedKey = "   \n\t ORDER_FULFILLMENT_RECEIPT_20261031_001   \r ";
        assertThat(MisaPayloadMapper.toDeterministicRefId(paddedKey)).isEqualTo("4a88f414-4e67-3f1c-ba76-485c18530140");
    }

    @Test
    @DisplayName("Golden Vector: Null, empty, and blank inputs must throw IllegalArgumentException")
    void goldenVectorNullAndBlankHandling() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> MisaPayloadMapper.toDeterministicRefId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or blank");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> MisaPayloadMapper.toDeterministicRefId(""))
                .isInstanceOf(IllegalArgumentException.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> MisaPayloadMapper.toDeterministicRefId("   \t\n "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Golden Vector [GV-6 & Independent]: Arbitrary business strings produce fixed literal UUIDv3 across calls and restarts")
    void goldenVectorArbitraryStringDeterminism() {
        // GV-6 Literal: Fixed literal output independent of implementation
        String businessKey1 = "ORDER_FULFILLMENT_RECEIPT_20261031_001";
        String literalExpected1 = "4a88f414-4e67-3f1c-ba76-485c18530140";

        String refId1 = MisaPayloadMapper.toDeterministicRefId(businessKey1);
        String refId2 = MisaPayloadMapper.toDeterministicRefId(businessKey1);

        assertThat(refId1).isEqualTo(literalExpected1);
        assertThat(refId2).isEqualTo(literalExpected1);

        // Independent second golden vector literal
        String businessKey2 = "ORDER_CHECKOUT_INV_851492";
        String literalExpected2 = "af8194ff-972b-3ce6-bdc7-3085691fd309";

        String refId3 = MisaPayloadMapper.toDeterministicRefId(businessKey2);
        assertThat(refId3).isEqualTo(literalExpected2);
    }

    @Test
    @DisplayName("toMisaCancelRequest: serializes RemoveInvoiceParam with nested JSON string data matching official schema")
    void shouldMapCancelRequestMatchingOfficialContract() throws Exception {
        String cancelRefId = "4a88f414-4e67-3f1c-ba76-485c18530140";
        String transactionId = "0f8fad5b-d9cb-469f-a165-70867728950e";
        String invNo = "00000001";
        String invSeries = "1C26TAA";
        String invTemplateNo = "1";
        String reason = "Khách hàng hủy đơn";
        String dateStr = "2026-10-31T14:30:00";

        var cancelRequest = mapper.toMisaCancelRequest(
                cancelRefId, transactionId, invNo, invSeries, invTemplateNo, reason, dateStr
        );

        assertThat(cancelRequest.getIsSendEmail()).isFalse();
        assertThat(cancelRequest.getEntityState()).isEqualTo(0);
        assertThat(cancelRequest.getFileContent()).isNull();
        assertThat(cancelRequest.getFileName()).isNull();
        assertThat(cancelRequest.getData()).isNotNull();

        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        var jsonTree = om.readTree(cancelRequest.getData());
        assertThat(jsonTree.isArray()).isTrue();
        assertThat(jsonTree.size()).isEqualTo(1);

        var firstItem = jsonTree.get(0);
        assertThat(firstItem.get("RefID").asText()).isEqualTo(cancelRefId);
        assertThat(firstItem.get("TransactionID").asText()).isEqualTo(transactionId);
        assertThat(firstItem.get("InvNo").asText()).isEqualTo(invNo);
        assertThat(firstItem.get("InvSeries").asText()).isEqualTo(invSeries);
        assertThat(firstItem.get("InvTemplateNo").asText()).isEqualTo(invTemplateNo);
        assertThat(firstItem.get("DeletedReason").asText()).isEqualTo(reason);
        assertThat(firstItem.get("DeletedDate").asText()).isEqualTo(dateStr);
    }
}
