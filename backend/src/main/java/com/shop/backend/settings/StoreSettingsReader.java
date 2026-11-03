package com.shop.backend.settings;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StoreSettingsReader {

    public static final int FALLBACK_LOW_STOCK_THRESHOLD = 10;
    public static final BigDecimal FALLBACK_DEFAULT_SHIPPING_FEE = BigDecimal.valueOf(30000);
    public static final BigDecimal FALLBACK_FREE_SHIPPING_THRESHOLD = BigDecimal.valueOf(500000);

    private static final Long SETTINGS_ID = 1L;

    private final StoreSettingsRepository storeSettingsRepository;

    @Transactional(readOnly = true)
    public int getLowStockThreshold() {
        return storeSettingsRepository.findById(SETTINGS_ID)
                .map(StoreSettings::getLowStockThreshold)
                .filter(val -> val != null && val >= 0)
                .orElse(FALLBACK_LOW_STOCK_THRESHOLD);
    }

    @Transactional(readOnly = true)
    public BigDecimal getDefaultShippingFee() {
        return storeSettingsRepository.findById(SETTINGS_ID)
                .map(StoreSettings::getDefaultShippingFee)
                .filter(val -> val != null && val.compareTo(BigDecimal.ZERO) >= 0)
                .orElse(FALLBACK_DEFAULT_SHIPPING_FEE);
    }

    @Transactional(readOnly = true)
    public BigDecimal getFreeShippingThreshold() {
        return storeSettingsRepository.findById(SETTINGS_ID)
                .map(StoreSettings::getFreeShippingThreshold)
                .filter(val -> val != null && val.compareTo(BigDecimal.ZERO) >= 0)
                .orElse(FALLBACK_FREE_SHIPPING_THRESHOLD);
    }
}
