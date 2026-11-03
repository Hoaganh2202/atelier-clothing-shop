package com.shop.backend.einvoice.provider;

import com.shop.backend.einvoice.provider.dto.ProviderCancelRequest;
import com.shop.backend.einvoice.provider.dto.ProviderCancelResult;
import com.shop.backend.einvoice.provider.dto.ProviderDownloadRequest;
import com.shop.backend.einvoice.provider.dto.ProviderDownloadResult;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryRequest;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryResult;
import com.shop.backend.einvoice.provider.dto.ProviderIssueRequest;
import com.shop.backend.einvoice.provider.dto.ProviderIssueResult;
import com.shop.backend.einvoice.provider.misa.MisaInvoiceProvider;
import com.shop.backend.einvoice.provider.mock.MockInvoiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component("routingInvoiceProvider")
@Primary
public class RoutingInvoiceProvider implements InvoiceProvider {

    private static final Logger log = LoggerFactory.getLogger(RoutingInvoiceProvider.class);

    private final MockInvoiceProvider mockInvoiceProvider;
    private final MisaInvoiceProvider misaInvoiceProvider;
    private final InvoiceProviderProperties properties;

    @Autowired
    public RoutingInvoiceProvider(
            MockInvoiceProvider mockInvoiceProvider,
            MisaInvoiceProvider misaInvoiceProvider,
            InvoiceProviderProperties properties) {
        this.mockInvoiceProvider = mockInvoiceProvider;
        this.misaInvoiceProvider = misaInvoiceProvider;
        this.properties = properties != null ? properties : new InvoiceProviderProperties();
    }

    public InvoiceProvider getDelegate() {
        String configuredType = properties != null ? properties.getType() : null;
        if (configuredType == null || configuredType.isBlank() || "mock".equalsIgnoreCase(configuredType)) {
            return mockInvoiceProvider;
        }
        if ("misa".equalsIgnoreCase(configuredType)) {
            return misaInvoiceProvider;
        }

        throw new IllegalStateException(String.format(
                "Invalid einvoice provider type '%s'. Supported values are 'mock' and 'misa'",
                configuredType));
    }

    @Override
    public ProviderIssueResult issueInvoice(ProviderIssueRequest request) {
        return getDelegate().issueInvoice(request);
    }

    @Override
    public ProviderInquiryResult inquiryInvoiceStatus(ProviderInquiryRequest request) {
        return getDelegate().inquiryInvoiceStatus(request);
    }

    @Override
    public ProviderDownloadResult downloadInvoiceFiles(ProviderDownloadRequest request) {
        return getDelegate().downloadInvoiceFiles(request);
    }

    @Override
    public ProviderCancelResult cancelInvoice(ProviderCancelRequest request) {
        return getDelegate().cancelInvoice(request);
    }

    @Override
    public String getProviderName() {
        return getDelegate().getProviderName();
    }
}
