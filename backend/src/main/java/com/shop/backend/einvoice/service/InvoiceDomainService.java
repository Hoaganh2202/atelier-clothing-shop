package com.shop.backend.einvoice.service;

import com.shop.backend.einvoice.dto.CreateAdjustmentInvoiceCommand;
import com.shop.backend.einvoice.dto.CreateReplacementInvoiceCommand;
import com.shop.backend.einvoice.dto.InvoiceCalculationCommand;
import com.shop.backend.einvoice.model.BuyerInvoiceProfile;
import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceItem;
import com.shop.backend.einvoice.model.SellerInvoiceProfile;
import java.util.List;

public interface InvoiceDomainService {
    Invoice createOriginalInvoice(
            Long fulfillmentEventId,
            String idempotencyKey,
            BuyerInvoiceProfile buyerProfile,
            SellerInvoiceProfile sellerProfile,
            InvoiceCalculationCommand calculationCommand);

    Invoice createOriginalInvoiceForOrder(Long orderId, String idempotencyKey);

    Invoice createAdjustmentInvoice(CreateAdjustmentInvoiceCommand command);

    Invoice approveAdjustmentInvoice(Long adjustmentInvoiceId);

    Invoice rejectAdjustmentInvoice(Long adjustmentInvoiceId, String rejectionReason);

    Invoice createAdjustmentDraftFromReturnReceipt(Long returnReceiptId);

    Invoice createReplacementInvoice(CreateReplacementInvoiceCommand command);

    Invoice transitionStatus(Long invoiceId, DocumentStatus targetStatus);

    Invoice getInvoice(Long invoiceId);

    List<InvoiceItem> getInvoiceItems(Long invoiceId);
}
