package com.shop.backend.einvoice.dto.response;

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
public class InvoiceSellerResponse {
    private String sellerTaxCode;
    private String sellerLegalName;
    private String sellerBrandName;
    private String sellerAddress;
    private String sellerPhone;
    private String sellerEmail;
    private String sellerBankAccount;
    private String sellerBankName;
}
