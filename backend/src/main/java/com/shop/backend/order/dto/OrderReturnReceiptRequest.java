package com.shop.backend.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderReturnReceiptRequest(
        @NotBlank(message = "Mã biên bản tiếp nhận không được để trống")
        @Size(max = 40, message = "Mã biên bản tiếp nhận tối đa 40 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Mã biên bản tiếp nhận chỉ chấp nhận chữ cái, số, dấu '-' hoặc '_'")
        String receiptNumber,

        @Size(max = 500, message = "Ghi chú tiếp nhận tối đa 500 ký tự")
        String note,

        @NotEmpty(message = "Danh sách sản phẩm tiếp nhận không được để trống")
        @Valid
        List<OrderReturnReceiptItemRequest> items
) {}
