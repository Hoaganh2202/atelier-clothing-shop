package com.shop.backend.einvoice.provider.misa.dto;

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
public class MisaAuthResponse {

    @JsonProperty("Success")
    private boolean success;

    @JsonProperty("Data")
    private String data; // JWT Bearer token

    @JsonProperty("ErrorCode")
    private String errorCode;

    @JsonProperty("Errors")
    private List<String> errors;

    @JsonProperty("CustomData")
    private Object customData;
}
