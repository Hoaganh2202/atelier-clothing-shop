package com.shop.backend.einvoice.repository;

import com.shop.backend.einvoice.model.SellerInvoiceProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SellerInvoiceProfileRepository extends JpaRepository<SellerInvoiceProfile, Long> {

    Optional<SellerInvoiceProfile> findFirstByOrderByIdDesc();
}
