package com.shop.backend.einvoice.provider.misa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MisaPublishInvoiceRequest {

    @JsonProperty("RefID")
    private String refId;

    @JsonProperty("InvTemplateNo")
    private String invTemplateNo;

    @JsonProperty("InvSeries")
    private String invSeries;

    @JsonProperty("InvDate")
    private String invDate;

    @JsonProperty("CurrencyCode")
    private String currencyCode;

    @JsonProperty("ExchangeRate")
    private BigDecimal exchangeRate;

    @JsonProperty("PaymentMethodName")
    private String paymentMethodName;

    @JsonProperty("BuyerLegalName")
    private String buyerLegalName;

    @JsonProperty("BuyerTaxCode")
    private String buyerTaxCode;

    @JsonProperty("BuyerAddress")
    private String buyerAddress;

    @JsonProperty("BuyerEmail")
    private String buyerEmail;

    @JsonProperty("BuyerPhoneNumber")
    private String buyerPhoneNumber;

    @JsonProperty("TotalAmountWithoutVATOC")
    private BigDecimal totalAmountWithoutVATOC;

    @JsonProperty("TotalVATAmountOC")
    private BigDecimal totalVATAmountOC;

    @JsonProperty("TotalDiscountAmountOC")
    private BigDecimal totalDiscountAmountOC;

    @JsonProperty("TotalAmountOC")
    private BigDecimal totalAmountOC;

    @JsonProperty("ReferenceType")
    private Integer referenceType; // 0: Original, 1: Replacement, 2: Adjustment

    @JsonProperty("OrgInvoiceType")
    private Integer orgInvoiceType; // 1: Decree 123 / Decree 254

    @JsonProperty("OrgInvTemplateNo")
    private String orgInvTemplateNo;

    @JsonProperty("OrgInvSeries")
    private String orgInvSeries;

    @JsonProperty("OrgInvNo")
    private String orgInvNo;

    @JsonProperty("OrgInvDate")
    private String orgInvDate;

    @JsonProperty("InvoiceDetails")
    private List<MisaInvoiceDetailDto> invoiceDetails;
}
