package com.shop.backend.einvoice.repository;

import com.shop.backend.einvoice.model.InvoiceAttempt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceAttemptRepository extends JpaRepository<InvoiceAttempt, Long> {

    List<InvoiceAttempt> findByInvoiceIdOrderByAttemptedAtDesc(Long invoiceId);
}
