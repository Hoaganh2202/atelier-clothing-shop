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
public class MisaAuthRequest {

    @JsonProperty("appid")
    private String appId;

    @JsonProperty("taxcode")
    private String taxCode;

    @JsonProperty("username")
    private String username;

    @JsonProperty("password")
    private String password;
}
