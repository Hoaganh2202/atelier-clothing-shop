package com.shop.backend.einvoice.service;

import com.shop.backend.einvoice.dto.CalculatedInvoiceResult;
import com.shop.backend.einvoice.dto.InvoiceCalculationCommand;

public interface TaxCalculationService {
    CalculatedInvoiceResult calculateInvoice(InvoiceCalculationCommand command);
}
