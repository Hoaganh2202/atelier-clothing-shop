package com.shop.backend.einvoice.dto;

import com.shop.backend.einvoice.model.AdjustmentType;
import java.util.List;
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
public class CreateAdjustmentInvoiceCommand {
    private Long originalInvoiceId;
    private Long returnReceiptId;
    private String financialSettlementReference;
    private AdjustmentType adjustmentType;
    private String idempotencyKey;
    private List<AdjustItemCommand> itemsToAdjust;
}
