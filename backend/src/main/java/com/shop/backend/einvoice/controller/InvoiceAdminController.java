package com.shop.backend.einvoice.controller;

import com.shop.backend.common.dto.ErrorResponse;
import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.einvoice.dto.response.InvoiceDetailResponse;
import com.shop.backend.einvoice.dto.response.InvoiceRetryResponse;
import com.shop.backend.einvoice.dto.response.InvoiceSummaryResponse;
import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.service.InvoiceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@Tag(name = "Admin Electronic Invoices", description = "Quản lý và tra cứu hóa đơn điện tử cho Admin/Staff")
public class InvoiceAdminController {

    private final InvoiceQueryService invoiceQueryService;

    @GetMapping
    @Operation(
            summary = "Lấy danh sách hóa đơn điện tử phân trang và tìm kiếm theo bộ lọc",
            description = "Hỗ trợ lọc theo trạng thái chứng từ (documentStatus), loại hóa đơn (invoiceType), cờ demo (isDemo), từ khóa tìm kiếm (số hóa đơn, ký hiệu, mã CQT, mã đơn hàng, tên khách), khoảng thời gian và orderId"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách hóa đơn thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số yêu cầu không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<InvoiceSummaryResponse>> getInvoices(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) DocumentStatus documentStatus,
            @RequestParam(required = false) InvoiceType invoiceType,
            @RequestParam(required = false) Boolean isDemo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<InvoiceSummaryResponse> response = invoiceQueryService.getAdminInvoices(
                keyword, orderId, documentStatus, invoiceType, isDemo, startDate, endDate, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Xem chi tiết một hóa đơn điện tử theo ID",
            description = "Trả về thông tin chi tiết hóa đơn, snapshot bên mua/bên bán, danh sách dòng hàng và tổng tiền thuế"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy chi tiết hóa đơn thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy hóa đơn", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InvoiceDetailResponse> getInvoiceById(@PathVariable Long id) {
        InvoiceDetailResponse response = invoiceQueryService.getAdminInvoiceDetail(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/orders/{orderId}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Phát hành lại hóa đơn gốc cho đơn hàng đã thanh toán thành công",
            description = "Dành riêng cho ADMIN xử lý các trường hợp đơn hàng thanh toán thành công nhưng chưa phát hành hóa đơn hoặc bị lỗi. Idempotent và chống race-condition."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xử lý phát hành hóa đơn thành công"),
            @ApiResponse(responseCode = "400", description = "Đơn hàng chưa thanh toán hoặc đã hủy", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Chỉ Quản trị viên (ADMIN) mới có quyền thực hiện thao tác này", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InvoiceRetryResponse> retryInvoiceForOrder(@PathVariable Long orderId) {
        InvoiceRetryResponse response = invoiceQueryService.retryInvoiceForOrder(orderId);
        return ResponseEntity.ok(response);
    }
}
