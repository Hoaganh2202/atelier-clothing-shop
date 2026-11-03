package com.shop.backend.einvoice.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.einvoice.provider")
public class InvoiceProviderProperties {

    /**
     * Active invoice provider type: "mock" (default for demo/local) or "misa" (for live integration).
     */
    private String type = "mock";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type != null ? type.trim().toLowerCase() : "mock";
    }
}
