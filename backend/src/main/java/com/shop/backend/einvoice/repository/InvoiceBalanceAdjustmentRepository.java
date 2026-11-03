package com.shop.backend.einvoice.repository;

import com.shop.backend.einvoice.model.InvoiceBalanceAdjustment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceBalanceAdjustmentRepository extends JpaRepository<InvoiceBalanceAdjustment, Long> {

    List<InvoiceBalanceAdjustment> findByOriginalInvoiceId(Long originalInvoiceId);

    Optional<InvoiceBalanceAdjustment> findByOriginalInvoiceIdAndInvoiceItemId(Long originalInvoiceId, Long invoiceItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT iba FROM InvoiceBalanceAdjustment iba WHERE iba.originalInvoice.id = :originalInvoiceId ORDER BY iba.invoiceItem.id ASC")
    List<InvoiceBalanceAdjustment> findByOriginalInvoiceIdForUpdate(@Param("originalInvoiceId") Long originalInvoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT iba FROM InvoiceBalanceAdjustment iba WHERE iba.originalInvoice.id = :originalInvoiceId AND iba.invoiceItem.id = :invoiceItemId")
    Optional<InvoiceBalanceAdjustment> findByOriginalInvoiceIdAndInvoiceItemIdForUpdate(
            @Param("originalInvoiceId") Long originalInvoiceId,
            @Param("invoiceItemId") Long invoiceItemId);
}
