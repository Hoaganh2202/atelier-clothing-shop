package com.shop.backend.order;

import com.shop.backend.common.dto.ErrorResponse;
import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.order.dto.OrderAdminDetailResponse;
import com.shop.backend.order.dto.OrderAdminListItemResponse;
import com.shop.backend.order.dto.OrderStatusUpdateRequest;
import com.shop.backend.security.UserPrincipal;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import com.shop.backend.order.dto.OrderReturnReceiptRequest;
import com.shop.backend.order.dto.OrderReturnReceiptResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@Tag(name = "Admin Order Management", description = "Quản lý và xử lý đơn hàng dành cho Admin/Staff")
public class OrderAdminController {

    private final OrderAdminService orderAdminService;

    @GetMapping
    @Operation(
            summary = "Lấy danh sách đơn hàng phân trang và tìm kiếm theo bộ lọc",
            description = "Hỗ trợ lọc theo trạng thái, từ khóa (mã đơn, tên người nhận, SĐT), khoảng thời gian tạo (startDate, endDate theo khoảng nửa mở), phân trang tối đa 100 đơn/trang"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách đơn hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số yêu cầu không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<OrderAdminListItemResponse>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(orderAdminService.getOrders(status, keyword, startDate, endDate, pageable));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Lấy thông tin chi tiết đơn hàng theo ID",
            description = "Trả về thông tin snapshot người nhận, địa chỉ giao hàng, chi tiết sản phẩm, thanh toán và các khoản tiền của đơn hàng"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy chi tiết đơn hàng thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OrderAdminDetailResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderAdminService.getOrderById(id));
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Cập nhật trạng thái đơn hàng",
            description = "Chuyển trạng thái đơn hàng theo State Machine. Tự động hoàn kho nguyên tử khi hủy đơn hoặc hoàn tiền. Không cho phép chuyển sai luồng."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật trạng thái đơn hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Trạng thái hoặc chuyển đổi không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OrderAdminDetailResponse> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrderStatusUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long actorUserId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(orderAdminService.updateOrderStatus(id, request, actorUserId));
    }

    @PostMapping("/{orderId}/returns/receipt")
    @Operation(
            summary = "Ghi nhận biên bản tiếp nhận hàng hoàn thực tế và xử lý hoàn kho",
            description = "Dành riêng cho ADMIN/STAFF tiếp nhận hàng hoàn vật lý từ đơn hàng bị hủy khi đang giao (SHIPPING -> CANCELLED) hoặc đơn đã hoàn tiền (REFUNDED)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tiếp nhận hàng hoàn thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ hoặc đơn hàng không đủ điều kiện", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Xung đột biên bản tiếp nhận trùng lặp với payload khác", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OrderReturnReceiptResponse> processReturnReceipt(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderReturnReceiptRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long actorUserId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(orderAdminService.processReturnReceipt(orderId, request, actorUserId));
    }
}
