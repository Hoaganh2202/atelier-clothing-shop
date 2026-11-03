package com.shop.backend.einvoice.provider.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProviderDownloadResult {
    boolean success;
    byte[] xmlBytes;
    byte[] pdfBytes;
    String fileName;
    String errorMessage;
}
