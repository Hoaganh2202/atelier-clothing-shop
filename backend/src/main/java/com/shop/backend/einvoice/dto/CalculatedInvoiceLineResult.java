package com.shop.backend.einvoice.dto;

import com.shop.backend.einvoice.model.InvoiceItemType;
import com.shop.backend.einvoice.model.TaxTreatment;
import java.math.BigDecimal;
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
public class CalculatedInvoiceLineResult {
    private Long orderDetailId;
    private Long fulfillmentEventItemId;
    private InvoiceItemType itemType;
    private String itemName;
    private String unit;
    private Integer quantity;
    private BigDecimal unitPriceGross;
    private BigDecimal unitPriceNet;
    private BigDecimal discountAmount;
    private BigDecimal taxableAmount;
    private String taxCategory;
    private TaxTreatment taxTreatment;
    private BigDecimal taxRate;
    private String taxCode;
    private String legalReference;
    private BigDecimal taxAmount;
    private BigDecimal lineTotalGross;
}
