package com.shop.backend.einvoice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceRetryResponse {
    private InvoiceDetailResponse invoice;

    @JsonProperty("invoiceCreated")
    private Boolean invoiceCreated;

    private String message;
}
