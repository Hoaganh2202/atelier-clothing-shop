package com.shop.backend.einvoice.repository;

import com.shop.backend.einvoice.model.BuyerInvoiceProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BuyerInvoiceProfileRepository extends JpaRepository<BuyerInvoiceProfile, Long> {
}
