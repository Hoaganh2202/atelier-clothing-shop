package com.shop.backend.einvoice.repository;

import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByIdempotencyKey(String idempotencyKey);

    Optional<Invoice> findByReturnReceiptId(Long returnReceiptId);

    Optional<Invoice> findByReturnReceiptIdAndInvoiceType(Long returnReceiptId, InvoiceType invoiceType);

    Optional<Invoice> findByFulfillmentEventIdAndInvoiceType(Long fulfillmentEventId, InvoiceType invoiceType);

    List<Invoice> findByOrderId(Long orderId);

    Optional<Invoice> findByOrderIdAndInvoiceType(Long orderId, InvoiceType invoiceType);

    List<Invoice> findByOriginalInvoiceId(Long originalInvoiceId);

    List<Invoice> findByDocumentStatus(DocumentStatus documentStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT i FROM Invoice i " +
            "JOIN FETCH i.buyerProfile " +
            "JOIN FETCH i.sellerProfile " +
            "JOIN FETCH i.order o " +
            "LEFT JOIN FETCH o.user " +
            "WHERE i.id = :id")
    Optional<Invoice> findByIdWithProfilesAndOrder(@Param("id") Long id);

    @Query("SELECT i FROM Invoice i " +
            "JOIN FETCH i.buyerProfile " +
            "JOIN FETCH i.sellerProfile " +
            "JOIN FETCH i.order o " +
            "LEFT JOIN FETCH o.user " +
            "WHERE o.id = :orderId")
    List<Invoice> findByOrderIdWithProfilesAndOrder(@Param("orderId") Long orderId);

    @Override
    @EntityGraph(attributePaths = {"buyerProfile", "sellerProfile", "order"})
    Page<Invoice> findAll(Specification<Invoice> spec, Pageable pageable);
}
