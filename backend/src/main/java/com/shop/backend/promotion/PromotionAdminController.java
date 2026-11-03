package com.shop.backend.promotion;

import com.shop.backend.common.dto.ErrorResponse;
import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.common.model.DiscountType;
import com.shop.backend.promotion.dto.PromotionCreateRequest;
import com.shop.backend.promotion.dto.PromotionListItemResponse;
import com.shop.backend.promotion.dto.PromotionResponse;
import com.shop.backend.promotion.dto.PromotionStatusRequest;
import com.shop.backend.promotion.dto.PromotionUpdateRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/admin/promotions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@Tag(name = "Admin Promotion Management", description = "Quản lý chương trình khuyến mãi sản phẩm dành cho Admin/Staff")
public class PromotionAdminController {

    private final PromotionAdminService promotionAdminService;

    @GetMapping
    @Operation(summary = "Lấy danh sách khuyến mãi có phân trang và lọc theo tên, loại giảm, bật/tắt, trạng thái thời gian, sản phẩm")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách khuyến mãi thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số lọc không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<PromotionListItemResponse>> getPromotions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DiscountType discountType,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) PromotionTimeStatus status,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                promotionAdminService.getPromotions(keyword, discountType, isActive, status, productId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết chương trình khuyến mãi kèm danh sách sản phẩm áp dụng")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy chi tiết khuyến mãi thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chương trình khuyến mãi",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PromotionResponse> getPromotionById(@PathVariable Long id) {
        return ResponseEntity.ok(promotionAdminService.getPromotionById(id));
    }

    @PostMapping
    @Operation(summary = "Tạo mới chương trình khuyến mãi và gán sản phẩm áp dụng")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tạo khuyến mãi thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc có sản phẩm không tồn tại",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PromotionResponse> createPromotion(
            @Valid @RequestBody PromotionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(promotionAdminService.createPromotion(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật chương trình khuyến mãi và thay thế danh sách sản phẩm áp dụng")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật khuyến mãi thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc có sản phẩm không tồn tại",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chương trình khuyến mãi",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PromotionResponse> updatePromotion(
            @PathVariable Long id,
            @Valid @RequestBody PromotionUpdateRequest request) {
        return ResponseEntity.ok(promotionAdminService.updatePromotion(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Bật/tắt trạng thái kích hoạt khuyến mãi")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật trạng thái thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chương trình khuyến mãi",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PromotionResponse> updatePromotionStatus(
            @PathVariable Long id,
            @Valid @RequestBody PromotionStatusRequest request) {
        return ResponseEntity.ok(promotionAdminService.updatePromotionStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa chương trình khuyến mãi và các liên kết sản phẩm")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Xóa khuyến mãi thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chương trình khuyến mãi",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deletePromotion(@PathVariable Long id) {
        promotionAdminService.deletePromotion(id);
        return ResponseEntity.noContent().build();
    }
}
