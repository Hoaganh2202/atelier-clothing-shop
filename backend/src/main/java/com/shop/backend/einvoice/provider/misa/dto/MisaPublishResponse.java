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
public class MisaPublishResponse {

    @JsonProperty("Success")
    private boolean success;

    @JsonProperty("Data")
    private Object data;

    @JsonProperty("ErrorCode")
    private String errorCode;

    @JsonProperty("DescriptionErrorCode")
    private String descriptionErrorCode;

    @JsonProperty("Errors")
    private List<String> errors;

    @JsonProperty("CustomData")
    private Object customData;
}
