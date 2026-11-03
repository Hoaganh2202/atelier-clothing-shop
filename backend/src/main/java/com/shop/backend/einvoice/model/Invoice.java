package com.shop.backend.einvoice.model;

import com.shop.backend.inventory.OrderReturnReceipt;
import com.shop.backend.order.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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
@Table(name = "invoices")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Transient
    @Builder.Default
    private boolean newlyCreated = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fulfillment_event_id")
    private FulfillmentEvent fulfillmentEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_invoice_id")
    private Invoice originalInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_invoice_id")
    private Invoice replacedByInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacing_invoice_id")
    private Invoice replacingInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_receipt_id")
    private OrderReturnReceipt returnReceipt;

    @Column(name = "financial_settlement_reference", length = 100)
    private String financialSettlementReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_profile_id", nullable = false)
    private BuyerInvoiceProfile buyerProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_profile_id", nullable = false)
    private SellerInvoiceProfile sellerProfile;

    @Column(name = "invoice_type", nullable = false, length = 30)
    private InvoiceType invoiceType;

    @Column(name = "adjustment_type", length = 30)
    private AdjustmentType adjustmentType;

    @Column(name = "document_status", nullable = false, length = 30)
    private DocumentStatus documentStatus;

    @Builder.Default
    @Column(name = "is_replaced", nullable = false)
    private Boolean isReplaced = false;

    @Column(name = "invoice_series", length = 20)
    private String invoiceSeries;

    @Column(name = "invoice_number", length = 30)
    private String invoiceNumber;

    @Column(name = "tax_authority_code", length = 100)
    private String taxAuthorityCode;

    @Column(name = "tax_point_date", nullable = false)
    private LocalDateTime taxPointDate;

    @Column(name = "officially_issued_at")
    private LocalDateTime officiallyIssuedAt;

    @Column(name = "pricing_basis", nullable = false, length = 30)
    private PricingBasis pricingBasis;

    @Builder.Default
    @Column(name = "total_merchandise_before_tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalMerchandiseBeforeTax = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_merchandise_discount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalMerchandiseDiscount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_shipping_before_tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalShippingBeforeTax = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_shipping_discount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalShippingDiscount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_taxable_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalTaxableAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalTaxAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_after_tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAfterTax = BigDecimal.ZERO;

    @Column(name = "xml_storage_key", length = 255)
    private String xmlStorageKey;

    @Column(name = "pdf_storage_key", length = 255)
    private String pdfStorageKey;

    @Column(name = "xml_sha256", length = 64)
    private String xmlSha256;

    @Column(name = "pdf_sha256", length = 64)
    private String pdfSha256;

    @Column(name = "storage_provider", length = 50)
    private String storageProvider;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "retention_policy_ref", length = 100)
    private String retentionPolicyRef;

    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Determines whether this invoice was issued via a simulated mock provider (demo mode).
     *
     * @return true if the invoice was issued in demo/mock mode, false if issued via real vendor
     */
    public boolean isDemoIssued() {
        if ("MOCK".equalsIgnoreCase(storageProvider)) {
            return true;
        }
        if (providerTransactionId != null && providerTransactionId.startsWith("DEMO-")) {
            return true;
        }
        if (invoiceSeries != null && invoiceSeries.startsWith("DEMO")) {
            return true;
        }
        return invoiceNumber != null && invoiceNumber.startsWith("DEMO-");
    }
}
