package com.shop.backend.einvoice.repository;

import com.shop.backend.einvoice.model.PricingBasis;
import com.shop.backend.einvoice.model.TaxConfiguration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TaxConfigurationRepository extends JpaRepository<TaxConfiguration, Long> {

    @Query("SELECT t FROM TaxConfiguration t " +
           "WHERE t.taxCategory = :taxCategory " +
           "  AND t.pricingBasis = :pricingBasis " +
           "  AND t.isActive = true " +
           "  AND :taxPoint >= t.effectiveFrom " +
           "  AND :taxPoint <= t.effectiveTo")
    Optional<TaxConfiguration> findEffectiveConfiguration(
            @Param("taxCategory") String taxCategory,
            @Param("pricingBasis") PricingBasis pricingBasis,
            @Param("taxPoint") LocalDateTime taxPoint);
}
