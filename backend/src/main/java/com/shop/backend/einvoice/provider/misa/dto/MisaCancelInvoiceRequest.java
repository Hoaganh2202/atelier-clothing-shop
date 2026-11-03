package com.shop.backend.einvoice.provider.misa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Models MISA meInvoice RemoveInvoiceParam request payload for POST /api/v3/v3invoice/cancel/multi.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MisaCancelInvoiceRequest {

    /**
     * Serialized JSON string containing an array of invoice items to cancel (MisaCancelInvoiceItem).
     * MISA meInvoice ASP.NET Web API binds RemoveInvoiceParam.data as a string.
     */
    @JsonProperty("data")
    private String data;

    @JsonProperty("fileContent")
    private String fileContent;

    @JsonProperty("fileName")
    private String fileName;

    @JsonProperty("IsSendEmail")
    @Builder.Default
    private Boolean isSendEmail = Boolean.FALSE;

    @JsonProperty("ReceiverEmail")
    private String receiverEmail;

    @JsonProperty("ReceiverName")
    private String receiverName;

    @JsonProperty("EntityState")
    @Builder.Default
    private Integer entityState = 0;
}
