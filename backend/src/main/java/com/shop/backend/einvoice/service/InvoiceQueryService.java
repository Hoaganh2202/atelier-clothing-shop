package com.shop.backend.einvoice.service;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.einvoice.dto.response.InvoiceDetailResponse;
import com.shop.backend.einvoice.dto.response.InvoiceRetryResponse;
import com.shop.backend.einvoice.dto.response.InvoiceSummaryResponse;
import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.InvoiceType;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;

public interface InvoiceQueryService {

    PageResponse<InvoiceSummaryResponse> getAdminInvoices(
            String keyword,
            Long orderId,
            DocumentStatus status,
            InvoiceType type,
            Boolean isDemo,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable
    );

    InvoiceDetailResponse getAdminInvoiceDetail(Long invoiceId);

    InvoiceRetryResponse retryInvoiceForOrder(Long orderId);

    InvoiceDetailResponse getCustomerInvoiceByOrderId(Long customerUserId, Long orderId);

    InvoiceDetailResponse getCustomerInvoiceDetail(Long customerUserId, Long invoiceId);

    PageResponse<InvoiceSummaryResponse> getCustomerInvoices(Long customerUserId, int page, int size);
}
