package com.shop.backend.einvoice.dto.response;

import com.shop.backend.einvoice.model.CustomerType;
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
public class InvoiceBuyerResponse {
    private CustomerType customerType;
    private String buyerName;
    private String companyName;
    private String taxCode;
    private String email;
    private String address;
    private String phoneNumber;
}
