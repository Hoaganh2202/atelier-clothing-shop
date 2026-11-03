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
public class MisaCancelInvoiceItem {

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

    @JsonProperty("DeletedReason")
    private String deletedReason;

    @JsonProperty("DeletedDate")
    private String deletedDate;
}
