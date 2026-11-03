package com.shop.backend.einvoice.repository;

import com.shop.backend.einvoice.model.InvoiceItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    List<InvoiceItem> findByInvoiceId(Long invoiceId);

    List<InvoiceItem> findByOriginalInvoiceItemId(Long originalInvoiceItemId);
}
