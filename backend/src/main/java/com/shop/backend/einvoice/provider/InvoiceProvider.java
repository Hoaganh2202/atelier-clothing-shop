package com.shop.backend.einvoice.provider;

import com.shop.backend.einvoice.provider.dto.ProviderCancelRequest;
import com.shop.backend.einvoice.provider.dto.ProviderCancelResult;
import com.shop.backend.einvoice.provider.dto.ProviderDownloadRequest;
import com.shop.backend.einvoice.provider.dto.ProviderDownloadResult;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryRequest;
import com.shop.backend.einvoice.provider.dto.ProviderInquiryResult;
import com.shop.backend.einvoice.provider.dto.ProviderIssueRequest;
import com.shop.backend.einvoice.provider.dto.ProviderIssueResult;

public interface InvoiceProvider {

    /**
     * Issues an electronic invoice (Original, Adjustment, or Replacement).
     *
     * @param request the standardized provider issue request
     * @return the issue result from the provider
     */
    ProviderIssueResult issueInvoice(ProviderIssueRequest request);

    /**
     * Inquires the status of an electronic invoice using its reference identifier or transaction ID.
     * This method is critical for outbox reconciliation of invoices in ISSUANCE_UNKNOWN state.
     *
     * @param request the inquiry request
     * @return the inquiry result
     */
    ProviderInquiryResult inquiryInvoiceStatus(ProviderInquiryRequest request);

    /**
     * Downloads signed original XML and/or rendered PDF files.
     *
     * @param request the download request
     * @return the downloaded file contents
     */
    ProviderDownloadResult downloadInvoiceFiles(ProviderDownloadRequest request);

    /**
     * Requests cancellation or error notice for an issued invoice if supported by provider.
     *
     * @param request the cancel request
     * @return the cancel result
     */
    ProviderCancelResult cancelInvoice(ProviderCancelRequest request);

    /**
     * Returns the unique identifier of this provider implementation (e.g., "MISA", "VNPT").
     *
     * @return provider name
     */
    String getProviderName();
}
