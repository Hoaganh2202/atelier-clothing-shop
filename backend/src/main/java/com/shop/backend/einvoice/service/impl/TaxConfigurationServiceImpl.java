package com.shop.backend.einvoice.service.impl;

import com.shop.backend.einvoice.exception.TaxConfigurationNotFoundException;
import com.shop.backend.einvoice.model.PricingBasis;
import com.shop.backend.einvoice.model.TaxConfiguration;
import com.shop.backend.einvoice.repository.TaxConfigurationRepository;
import com.shop.backend.einvoice.service.TaxConfigurationService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaxConfigurationServiceImpl implements TaxConfigurationService {

    private final TaxConfigurationRepository taxConfigurationRepository;

    @Override
    @Transactional(readOnly = true)
    public TaxConfiguration getEffectiveConfiguration(String taxCategory, PricingBasis pricingBasis, LocalDateTime taxPoint) {
        if (taxCategory == null || taxCategory.isBlank()) {
            throw new IllegalArgumentException("taxCategory must not be null or blank");
        }
        if (pricingBasis == null) {
            throw new IllegalArgumentException("pricingBasis must not be null");
        }
        if (taxPoint == null) {
            throw new IllegalArgumentException("taxPoint must not be null");
        }

        return taxConfigurationRepository.findEffectiveConfiguration(taxCategory.toLowerCase().trim(), pricingBasis, taxPoint)
                .orElseThrow(() -> new TaxConfigurationNotFoundException(
                        String.format("No effective tax configuration found for category '%s', pricing basis '%s' at tax point '%s'",
                                taxCategory, pricingBasis, taxPoint)));
    }

    @Override
    @Transactional
    public TaxConfiguration saveConfiguration(TaxConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("configuration must not be null");
        }
        if (configuration.getTaxCategory() != null) {
            configuration.setTaxCategory(configuration.getTaxCategory().toLowerCase().trim());
        }
        return taxConfigurationRepository.save(configuration);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxConfiguration> getAllConfigurations() {
        return taxConfigurationRepository.findAll();
    }
}
