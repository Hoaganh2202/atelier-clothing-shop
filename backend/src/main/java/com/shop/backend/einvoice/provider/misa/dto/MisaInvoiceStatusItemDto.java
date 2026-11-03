package com.shop.backend.einvoice.provider.misa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MisaInvoiceStatusItemDto {

    @JsonProperty("RefID")
    private String refId;

    @JsonProperty("TransactionID")
    private String transactionId;

    @JsonProperty("InvNo")
    private String invNo;

    @JsonProperty("InvSeries")
    private String invSeries;

    @JsonProperty("InvTemplateNo")
    private String invTemplateNo;

    @JsonProperty("InvDate")
    private String invDate;

    @JsonProperty("EInvoiceStatus")
    private Integer eInvoiceStatus; // 1: Original, 2: Deleted/Cancelled, 3: Replaced, 5: Adjusted

    @JsonProperty("InvoiceStatus")
    private Integer invoiceStatus; // Legacy/alias: 1: Pending, 2: Published/Cancelled in some variants

    @JsonProperty("PublishStatus")
    private Integer publishStatus; // 0: Draft, 1: Published, 2: Error

    @JsonProperty("IsDelete")
    private Boolean isDelete;

    @JsonProperty("DeletedDate")
    private String deletedDate;

    @JsonProperty("DeletedReason")
    private String deletedReason;

    @JsonProperty("TaxAuthorityCode")
    private String taxAuthorityCode;

    @JsonProperty("LookupCode")
    private String lookupCode;

    @JsonProperty("ErrorMessage")
    private String errorMessage;
}
