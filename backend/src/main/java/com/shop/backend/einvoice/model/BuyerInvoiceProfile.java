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
@Table(name = "buyer_invoice_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuyerInvoiceProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_type", nullable = false, length = 30)
    private CustomerType customerType;

    @Column(name = "tax_code", length = 30)
    private String taxCode;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "buyer_name", nullable = false, length = 150)
    private String buyerName;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "address", nullable = false, length = 300)
    private String address;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Builder.Default
    @Column(name = "is_immutable", nullable = false)
    private Boolean isImmutable = false;

    @CreationTimestamp
    @Column(name = "snapshotted_at", nullable = false, updatable = false)
    private LocalDateTime snapshottedAt;
}
