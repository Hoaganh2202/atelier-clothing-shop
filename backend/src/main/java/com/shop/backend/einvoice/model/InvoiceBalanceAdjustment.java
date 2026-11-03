package com.shop.backend.einvoice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "invoice_balance_adjustments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceBalanceAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_invoice_id", nullable = false)
    private Invoice originalInvoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_item_id", nullable = false)
    private InvoiceItem invoiceItem;

    @Column(name = "original_quantity", nullable = false)
    private Integer originalQuantity;

    @Builder.Default
    @Column(name = "total_adjusted_quantity", nullable = false)
    private Integer totalAdjustedQuantity = 0;

    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    @Column(name = "original_taxable_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal originalTaxableAmount;

    @Builder.Default
    @Column(name = "total_adjusted_taxable_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAdjustedTaxableAmount = BigDecimal.ZERO;

    @Column(name = "remaining_taxable_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal remainingTaxableAmount;

    @Column(name = "original_tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal originalTaxAmount;

    @Builder.Default
    @Column(name = "total_adjusted_tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAdjustedTaxAmount = BigDecimal.ZERO;

    @Column(name = "remaining_tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal remainingTaxAmount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
