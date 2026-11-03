package com.shop.backend.shipping;

import com.shop.backend.common.dto.ErrorResponse;
import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.order.OrderStatus;
import com.shop.backend.shipping.dto.ShippingDetailResponse;
import com.shop.backend.shipping.dto.ShippingListItemResponse;
import com.shop.backend.shipping.dto.ShippingStatusUpdateRequest;
import com.shop.backend.shipping.dto.ShippingSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import com.shop.backend.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/shipping")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@Tag(name = "Admin Shipping Management", description = "Quản lý và điều phối vận chuyển đơn hàng dành cho Admin/Staff")
public class ShippingAdminController {

    private final ShippingAdminService shippingAdminService;

    @GetMapping("/summary")
    @Operation(
            summary = "Lấy dữ liệu tổng quan thống kê vận chuyển",
            description = "Trả về số lượng đơn chờ giao (CONFIRMED), đang giao (SHIPPING) và đã giao thành công trong ngày hôm nay (COMPLETED tính theo múi giờ Việt Nam)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy dữ liệu tổng quan thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ShippingSummaryResponse> getSummary() {
        return ResponseEntity.ok(shippingAdminService.getShippingSummary());
    }

    @GetMapping
    @Operation(
            summary = "Lấy danh sách đơn hàng vận chuyển phân trang và tìm kiếm theo bộ lọc",
            description = "Hỗ trợ lọc theo trạng thái vận chuyển (CONFIRMED, SHIPPING, COMPLETED, CANCELLED), từ khóa (mã đơn ORD-xxx, tên, SĐT), tỉnh/thành phố, khoảng thời gian tạo"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách đơn hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số yêu cầu không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<ShippingListItemResponse>> getShippingOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(shippingAdminService.getShippingOrders(status, keyword, province, startDate, endDate, pageable));
    }

    @GetMapping("/{orderId}")
    @Operation(
            summary = "Lấy chi tiết đơn hàng vận chuyển theo ID",
            description = "Trả về thông tin giao nhận, địa chỉ đầy đủ, danh sách sản phẩm kiện hàng, tiền thu hộ COD và ghi chú của khách hàng"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy chi tiết đơn hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Đơn hàng không thuộc phạm vi vận chuyển", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ShippingDetailResponse> getShippingOrderDetail(@PathVariable Long orderId) {
        return ResponseEntity.ok(shippingAdminService.getShippingOrderDetail(orderId));
    }

    @PatchMapping("/{orderId}/status")
    @Operation(
            summary = "Cập nhật trạng thái vận chuyển của đơn hàng",
            description = "Chỉ cho phép chuyển trạng thái vận chuyển hợp lệ (CONFIRMED -> SHIPPING/CANCELLED, SHIPPING -> COMPLETED/CANCELLED) dưới khóa bi quan an toàn"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật trạng thái thành công"),
            @ApiResponse(responseCode = "400", description = "Trạng thái hoặc quy tắc chuyển đổi không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ShippingDetailResponse> updateShippingStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody ShippingStatusUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long actorUserId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(shippingAdminService.updateShippingStatus(orderId, request, actorUserId));
    }
}
