package com.shop.backend.einvoice.provider.misa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.provider.dto.ProviderInvoiceItemDto;
import com.shop.backend.einvoice.provider.dto.ProviderIssueRequest;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderFatalException;
import com.shop.backend.einvoice.provider.misa.dto.MisaCancelInvoiceItem;
import com.shop.backend.einvoice.provider.misa.dto.MisaCancelInvoiceRequest;
import com.shop.backend.einvoice.provider.misa.dto.MisaInvoiceDetailDto;
import com.shop.backend.einvoice.provider.misa.dto.MisaPublishInvoiceRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MisaPayloadMapper {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ObjectMapper objectMapper;

    public MisaPayloadMapper() {
        this(new ObjectMapper());
    }

    @Autowired
    public MisaPayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public MisaPublishInvoiceRequest toMisaPublishRequest(ProviderIssueRequest request, MisaProperties properties) {
        String templateCode = request.getTemplateCode() != null ? request.getTemplateCode() : properties.getTemplateCode();
        String series = request.getInvoiceSeries() != null ? request.getInvoiceSeries() : properties.getInvoiceSeries();
        String dateStr = request.getInvoiceDate() != null
                ? request.getInvoiceDate().format(ISO_FORMATTER)
                : java.time.LocalDateTime.now().format(ISO_FORMATTER);

        Integer refType = mapReferenceType(request.getInvoiceType());
        Integer orgInvType = (request.getInvoiceType() == InvoiceType.ADJUSTMENT || request.getInvoiceType() == InvoiceType.REPLACEMENT) ? 1 : null;

        String orgInvDateStr = request.getOriginalInvoiceDate() != null
                ? request.getOriginalInvoiceDate().format(ISO_FORMATTER)
                : null;

        List<MisaInvoiceDetailDto> details = request.getItems() != null
                ? request.getItems().stream().map(this::toMisaItemDetail).collect(Collectors.toList())
                : Collections.emptyList();

        String deterministicRefId = toDeterministicRefId(request.getRefId());

        return MisaPublishInvoiceRequest.builder()
                .refId(deterministicRefId)
                .invTemplateNo(templateCode)
                .invSeries(series)
                .invDate(dateStr)
                .currencyCode("VND")
                .exchangeRate(BigDecimal.ONE)
                .paymentMethodName(request.getPaymentMethod() != null ? request.getPaymentMethod() : "Tiền mặt / Chuyển khoản")
                .buyerLegalName(request.getBuyerLegalName())
                .buyerTaxCode(request.getBuyerTaxCode())
                .buyerAddress(request.getBuyerAddress())
                .buyerEmail(request.getBuyerEmail())
                .buyerPhoneNumber(request.getBuyerPhone())
                .totalAmountWithoutVATOC(request.getTotalNetAmount())
                .totalVATAmountOC(request.getTotalVatAmount())
                .totalDiscountAmountOC(request.getTotalDiscountAmount())
                .totalAmountOC(request.getTotalGrossAmount())
                .referenceType(refType)
                .orgInvoiceType(orgInvType)
                .orgInvTemplateNo(request.getOriginalTemplateCode())
                .orgInvSeries(request.getOriginalInvoiceSeries())
                .orgInvNo(request.getOriginalInvoiceNo())
                .orgInvDate(orgInvDateStr)
                .invoiceDetails(details)
                .build();
    }

    public MisaCancelInvoiceRequest toMisaCancelRequest(
            String cancelRefId,
            String transactionId,
            String invNo,
            String invSeries,
            String invTemplateNo,
            String deletedReason,
            String deletedDate) {

        MisaCancelInvoiceItem item = MisaCancelInvoiceItem.builder()
                .refId(cancelRefId)
                .transactionId(transactionId)
                .invNo(invNo)
                .invSeries(invSeries)
                .invTemplateNo(invTemplateNo)
                .deletedReason(deletedReason)
                .deletedDate(deletedDate)
                .build();

        String serializedData;
        try {
            serializedData = objectMapper.writeValueAsString(Collections.singletonList(item));
        } catch (JsonProcessingException e) {
            throw new InvoiceProviderFatalException("MISA", "CANCEL_SERIALIZATION_FAILED",
                    "Failed to serialize cancel invoice items to JSON string: " + e.getMessage(), null, e);
        }

        return MisaCancelInvoiceRequest.builder()
                .data(serializedData)
                .fileContent(null)
                .fileName(null)
                .isSendEmail(Boolean.FALSE)
                .receiverEmail(null)
                .receiverName(null)
                .entityState(0)
                .build();
    }

    public MisaInvoiceDetailDto toMisaItemDetail(ProviderInvoiceItemDto item) {
        String vatRateStr = mapVatRateString(item.getVatRate());

        return MisaInvoiceDetailDto.builder()
                .lineNumber(item.getLineNumber())
                .itemCode(item.getItemCode())
                .itemName(item.getItemName())
                .unitName(item.getUnitName() != null ? item.getUnitName() : "Cái")
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .amountOC(item.getNetAmount())
                .discountAmountOC(item.getDiscountAmount())
                .vatRateName(vatRateStr)
                .vatAmountOC(item.getVatAmount())
                .isPromotion(BigDecimal.ZERO.compareTo(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO) == 0)
                .build();
    }

    public Integer mapReferenceType(InvoiceType invoiceType) {
        if (invoiceType == null) {
            return 0;
        }
        return switch (invoiceType) {
            case ORIGINAL -> 0;
            case REPLACEMENT -> 1;
            case ADJUSTMENT -> 2;
        };
    }

    public String mapVatRateString(BigDecimal rate) {
        if (rate == null) {
            return "8%";
        }
        // Check standard percentages: 0, 0.05, 0.08, 0.10
        if (rate.compareTo(new BigDecimal("0.08")) == 0 || rate.compareTo(new BigDecimal("8")) == 0) {
            return "8%";
        }
        if (rate.compareTo(new BigDecimal("0.10")) == 0 || rate.compareTo(new BigDecimal("10")) == 0) {
            return "10%";
        }
        if (rate.compareTo(new BigDecimal("0.05")) == 0 || rate.compareTo(new BigDecimal("5")) == 0) {
            return "5%";
        }
        if (rate.compareTo(BigDecimal.ZERO) == 0) {
            return "0%";
        }
        return rate.toPlainString() + "%";
    }

    /**
     * Converts a domain idempotency key into a strictly compliant RFC 4122 UUID RefID.
     * <p>
     * - If the input is canonical UUID, uppercase, or enclosed in braces '{...}', it is normalized to lowercase canonical UUID.
     * - If the input is a 32-character compact hexadecimal string without hyphens, it is formatted to standard 8-4-4-4-12 UUID.
     * - If the input is an arbitrary business string, a deterministic UUIDv3 is derived using MD5 namespace hashing
     *   with the prefix "CLOSET_SHOP_EINV:".
     * <p>
     * IMPORTANT ARCHITECTURAL CONTRACT:
     * This derivation algorithm forms a persistent distributed contract with MISA and the Tax Authority.
     * Any change to this algorithm will alter the RefID of historical invoices during reconciliation or audit,
     * which constitutes a severe BREAKING CHANGE. Do not alter this mapping without a coordinated database migration.
     *
     * @param rawIdempotencyKey raw idempotency key or identifier
     * @return RFC 4122 compliant lowercase canonical UUID string
     */
    public static String toDeterministicRefId(String rawIdempotencyKey) {
        if (rawIdempotencyKey == null || rawIdempotencyKey.isBlank()) {
            throw new IllegalArgumentException("rawIdempotencyKey must not be null or blank");
        }
        String candidate = rawIdempotencyKey.trim();

        if (candidate.startsWith("{") && candidate.endsWith("}") && candidate.length() == 38) {
            candidate = candidate.substring(1, candidate.length() - 1).trim();
        }

        if (candidate.length() == 32 && candidate.matches("^[0-9a-fA-F]{32}$")) {
            candidate = candidate.substring(0, 8) + "-"
                    + candidate.substring(8, 12) + "-"
                    + candidate.substring(12, 16) + "-"
                    + candidate.substring(16, 20) + "-"
                    + candidate.substring(20, 32);
        }

        if (isValidUuid(candidate)) {
            return UUID.fromString(candidate).toString().toLowerCase();
        }

        return UUID.nameUUIDFromBytes(("CLOSET_SHOP_EINV:" + candidate).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static boolean isValidUuid(String str) {
        try {
            UUID parsed = UUID.fromString(str);
            return parsed.toString().equalsIgnoreCase(str);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
