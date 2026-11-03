package com.shop.backend.einvoice.provider.misa.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MisaCancelInvoiceResponse {

    @JsonProperty("Success")
    @JsonAlias("success")
    private boolean success;

    @JsonProperty("ErrorCode")
    @JsonAlias("errorCode")
    private String errorCode;

    @JsonProperty("Errors")
    @JsonAlias("errors")
    private List<String> errors;

    @JsonProperty("Data")
    @JsonAlias("data")
    private Object data;
}
