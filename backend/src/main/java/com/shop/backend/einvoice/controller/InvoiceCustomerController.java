package com.shop.backend.einvoice.controller;

import com.shop.backend.common.dto.ErrorResponse;
import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.einvoice.dto.response.InvoiceDetailResponse;
import com.shop.backend.einvoice.dto.response.InvoiceSummaryResponse;
import com.shop.backend.einvoice.service.InvoiceQueryService;
import com.shop.backend.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Customer Electronic Invoices", description = "Tra cứu và xem hóa đơn điện tử dành cho khách hàng")
public class InvoiceCustomerController {

    private final InvoiceQueryService invoiceQueryService;

    @GetMapping
    @Operation(
            summary = "Khách hàng xem danh sách hóa đơn điện tử của mình",
            description = "Trả về danh sách hóa đơn có phân trang thuộc các đơn hàng của khách hàng đang đăng nhập"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách hóa đơn thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền thực hiện", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<InvoiceSummaryResponse>> getMyInvoices(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<InvoiceSummaryResponse> response = invoiceQueryService.getCustomerInvoices(
                principal.getId(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Khách hàng xem chi tiết hóa đơn điện tử theo ID",
            description = "Chống IDOR: Chỉ cho phép xem nếu hóa đơn thuộc đơn hàng của chính khách hàng đăng nhập, ngược lại trả về 404"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy chi tiết hóa đơn thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền thực hiện", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy hóa đơn", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InvoiceDetailResponse> getInvoiceById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        InvoiceDetailResponse response = invoiceQueryService.getCustomerInvoiceDetail(principal.getId(), id);
        return ResponseEntity.ok(response);
    }
}
