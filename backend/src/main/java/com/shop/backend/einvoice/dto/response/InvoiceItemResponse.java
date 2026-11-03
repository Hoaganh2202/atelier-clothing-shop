package com.shop.backend.einvoice.dto.response;

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
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceItemResponse {
    private Long id;
    private InvoiceItemType itemType;
    private String itemName;
    private String unit;
    private Integer quantity;
    private Integer originalQuantity;
    private Long unitPriceGross;
    private Long unitPriceNet;
    private Long discountAmount;
    private Long taxableAmount;
    private String taxCategory;
    private TaxTreatment taxTreatment;
    private BigDecimal taxRate;
    private String taxCode;
    private Long taxAmount;
    private Long lineTotalGross;
}
