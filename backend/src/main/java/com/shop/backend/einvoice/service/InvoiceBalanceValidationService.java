package com.shop.backend.einvoice.service;

import com.shop.backend.einvoice.dto.AdjustItemCommand;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceBalanceAdjustment;
import com.shop.backend.einvoice.model.InvoiceItem;
import java.util.List;

public interface InvoiceBalanceValidationService {
    void initializeBalance(Invoice originalInvoice, List<InvoiceItem> items);
    void reserveBalance(Long originalInvoiceId, List<AdjustItemCommand> itemsToAdjust);
    void releaseAdjustmentBalance(Long adjustmentInvoiceId);
    void releaseBalance(Long originalInvoiceId, List<AdjustItemCommand> itemsToRelease);
    void validateAndDebitBalance(Long originalInvoiceId, List<AdjustItemCommand> itemsToAdjust);
    List<InvoiceBalanceAdjustment> getBalances(Long originalInvoiceId);
}
