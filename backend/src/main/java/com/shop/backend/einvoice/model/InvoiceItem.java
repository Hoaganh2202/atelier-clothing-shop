package com.shop.backend.einvoice.model;

import com.shop.backend.order.OrderDetail;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "invoice_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_invoice_item_id")
    private InvoiceItem originalInvoiceItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_detail_id")
    private OrderDetail orderDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fulfillment_event_item_id")
    private FulfillmentEventItem fulfillmentEventItem;

    @Column(name = "item_type", nullable = false, length = 30)
    private InvoiceItemType itemType;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "unit", nullable = false, length = 30)
    private String unit;

    @Builder.Default
    @Column(name = "original_quantity", nullable = false)
    private Integer originalQuantity = 0;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price_gross", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPriceGross;

    @Column(name = "unit_price_net", nullable = false, precision = 15, scale = 4)
    private BigDecimal unitPriceNet;

    @Builder.Default
    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "taxable_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "tax_category", nullable = false, length = 50)
    private String taxCategory;

    @Column(name = "tax_treatment", nullable = false, length = 30)
    private TaxTreatment taxTreatment;

    @Column(name = "tax_rate", precision = 5, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_code", nullable = false, length = 20)
    private String taxCode;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "line_total_gross", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotalGross;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
