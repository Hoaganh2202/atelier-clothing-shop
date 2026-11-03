package com.shop.backend.einvoice.service;

import com.shop.backend.einvoice.model.PricingBasis;
import com.shop.backend.einvoice.model.TaxConfiguration;
import java.time.LocalDateTime;
import java.util.List;

public interface TaxConfigurationService {
    TaxConfiguration getEffectiveConfiguration(String taxCategory, PricingBasis pricingBasis, LocalDateTime taxPoint);
    TaxConfiguration saveConfiguration(TaxConfiguration configuration);
    List<TaxConfiguration> getAllConfigurations();
}
