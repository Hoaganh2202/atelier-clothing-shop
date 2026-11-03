package com.shop.backend.inventory;

import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.dto.ErrorResponse;
import com.shop.backend.inventory.dto.InventoryStockUpdateRequest;
import com.shop.backend.inventory.dto.InventorySummaryResponse;
import com.shop.backend.inventory.dto.InventoryVariantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/inventory")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@Tag(name = "Admin Inventory Management", description = "Quản lý kho hàng và tồn kho biến thể dành cho Admin/Staff")
public class InventoryAdminController {

    private final InventoryAdminService inventoryAdminService;

    public InventoryAdminController(InventoryAdminService inventoryAdminService) {
        this.inventoryAdminService = inventoryAdminService;
    }

    @GetMapping("/variants")
    @Operation(summary = "Xem danh sách tồn kho biến thể phân trang có lọc và sắp xếp")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách tồn kho thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số lọc hoặc sắp xếp không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<InventoryVariantResponse>> getVariants(
            @Parameter(description = "Từ khóa tìm kiếm theo tên sản phẩm hoặc mã SKU")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "ID danh mục sản phẩm cần lọc")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Kích cỡ cần lọc (ví dụ: S, M, L, XL)")
            @RequestParam(required = false) String size,
            @Parameter(description = "Màu sắc cần lọc (ví dụ: Đen, Trắng)")
            @RequestParam(required = false) String color,
            @Parameter(description = "Trạng thái tồn kho (ALL, OUT_OF_STOCK, LOW_STOCK, IN_STOCK)")
            @RequestParam(required = false, defaultValue = "ALL") String stockStatus,
            @Parameter(description = "Ngưỡng cảnh báo sắp hết hàng (mặc định: 10, giới hạn: 1-1000)")
            @RequestParam(required = false) Integer threshold,
            @Parameter(description = "Lọc theo trạng thái kinh doanh của sản phẩm (true: đang bán, false: ngừng bán)")
            @RequestParam(required = false) Boolean productActive,
            @Parameter(description = "Số trang (bắt đầu từ 0)")
            @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Số lượng bản ghi trên một trang (mặc định: 20, tối đa: 100)")
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @Parameter(description = "Tiêu chí sắp xếp: property,direction (stock, id, productName, sku; mặc định: stock,asc)")
            @RequestParam(required = false) String sort
    ) {
        PageResponse<InventoryVariantResponse> response = inventoryAdminService.getVariants(
                keyword,
                categoryId,
                size,
                color,
                stockStatus,
                threshold,
                productActive,
                page,
                pageSize,
                sort
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/summary")
    @Operation(summary = "Xem thống kê tổng quan tình trạng tồn kho")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy thống kê tổng quan thành công"),
            @ApiResponse(responseCode = "400", description = "Ngưỡng threshold không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InventorySummaryResponse> getSummary(
            @Parameter(description = "Ngưỡng cảnh báo sắp hết hàng (mặc định: 10, giới hạn: 1-1000)")
            @RequestParam(required = false) Integer threshold
    ) {
        InventorySummaryResponse summary = inventoryAdminService.getSummary(threshold);
        return ResponseEntity.ok(summary);
    }

    @PatchMapping("/variants/{id}/stock")
    @Operation(summary = "Cập nhật số lượng tồn kho biến thể an toàn bằng Compare-And-Swap")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật tồn kho thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ hoặc số lượng âm",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy biến thể",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Tồn kho đã bị thay đổi bởi giao dịch khác (xung đột dữ liệu)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InventoryVariantResponse> updateStock(
            @Parameter(description = "ID của biến thể cần cập nhật", required = true)
            @PathVariable Long id,
            @Valid @RequestBody InventoryStockUpdateRequest request
    ) {
        InventoryVariantResponse updated = inventoryAdminService.updateStock(id, request);
        return ResponseEntity.ok(updated);
    }
}
