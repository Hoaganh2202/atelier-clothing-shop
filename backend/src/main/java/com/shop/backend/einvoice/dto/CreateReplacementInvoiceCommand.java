package com.shop.backend.einvoice.dto;

import com.shop.backend.einvoice.model.BuyerInvoiceProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReplacementInvoiceCommand {
    private Long targetInvoiceId;
    private String idempotencyKey;
    private String replacementReason;
    private BuyerInvoiceProfile buyerProfile;
    private InvoiceCalculationCommand calculationCommand;
}
