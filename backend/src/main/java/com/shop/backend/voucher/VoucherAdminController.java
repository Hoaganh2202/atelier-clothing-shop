package com.shop.backend.voucher;

import com.shop.backend.common.dto.ErrorResponse;
import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.model.DiscountType;
import com.shop.backend.voucher.dto.VoucherCreateRequest;
import com.shop.backend.voucher.dto.VoucherResponse;
import com.shop.backend.voucher.dto.VoucherStatusRequest;
import com.shop.backend.voucher.dto.VoucherUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/vouchers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@Tag(name = "Admin Voucher Management", description = "Quản lý mã giảm giá dành cho Admin/Staff")
public class VoucherAdminController {

    private final VoucherAdminService voucherService;

    @GetMapping
    @Operation(summary = "Danh sách voucher có phân trang (size tối đa 100, vượt thì được giới hạn về 100) và lọc theo mã, loại giảm, bật/tắt, trạng thái thời gian")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách voucher thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số lọc không đúng định dạng",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<VoucherResponse>> getVouchers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DiscountType discountType,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) VoucherTimeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                voucherService.getVouchers(keyword, discountType, isActive, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết voucher theo ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy chi tiết voucher thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy voucher",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VoucherResponse> getVoucherById(@PathVariable Long id) {
        return ResponseEntity.ok(voucherService.getVoucherById(id));
    }

    @PostMapping
    @Operation(summary = "Tạo voucher mới (mã được chuẩn hóa in hoa, không đổi được sau khi tạo)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tạo voucher thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Mã voucher đã tồn tại",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VoucherResponse> createVoucher(@Valid @RequestBody VoucherCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voucherService.createVoucher(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật voucher (không đổi được mã)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật voucher thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy voucher",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VoucherResponse> updateVoucher(
            @PathVariable Long id, @Valid @RequestBody VoucherUpdateRequest request) {
        return ResponseEntity.ok(voucherService.updateVoucher(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Bật/tắt voucher")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật trạng thái thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy voucher",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VoucherResponse> updateVoucherStatus(
            @PathVariable Long id, @Valid @RequestBody VoucherStatusRequest request) {
        return ResponseEntity.ok(voucherService.updateVoucherStatus(id, request));
    }
}
