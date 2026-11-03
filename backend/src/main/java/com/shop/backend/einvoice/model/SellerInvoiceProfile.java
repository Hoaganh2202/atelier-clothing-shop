package com.shop.backend.einvoice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "seller_invoice_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerInvoiceProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_tax_code", nullable = false, length = 30)
    private String sellerTaxCode;

    @Column(name = "seller_legal_name", nullable = false, length = 255)
    private String sellerLegalName;

    @Column(name = "seller_brand_name", nullable = false, length = 150)
    private String sellerBrandName;

    @Column(name = "seller_address", nullable = false, length = 300)
    private String sellerAddress;

    @Column(name = "seller_phone", nullable = false, length = 30)
    private String sellerPhone;

    @Column(name = "seller_email", length = 150)
    private String sellerEmail;

    @Column(name = "seller_bank_account", length = 50)
    private String sellerBankAccount;

    @Column(name = "seller_bank_name", length = 150)
    private String sellerBankName;

    @Builder.Default
    @Column(name = "is_immutable", nullable = false)
    private Boolean isImmutable = false;

    @CreationTimestamp
    @Column(name = "snapshotted_at", nullable = false, updatable = false)
    private LocalDateTime snapshottedAt;
}
