package com.shop.backend.order;

import com.shop.backend.common.dto.ErrorResponse;
import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.einvoice.dto.response.InvoiceDetailResponse;
import com.shop.backend.einvoice.service.InvoiceQueryService;
import com.shop.backend.order.dto.CheckoutRequest;
import com.shop.backend.order.dto.CheckoutResponse;
import com.shop.backend.order.dto.CustomerOrderDetailResponse;
import com.shop.backend.order.dto.CustomerOrderListItemResponse;
import com.shop.backend.security.UserPrincipal;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
@Tag(name = "Customer Order", description = "API quản lý và thao tác đơn hàng dành cho khách hàng")
public class OrderCustomerController {

    private final OrderCheckoutService orderCheckoutService;
    private final OrderHistoryService orderHistoryService;
    private final InvoiceQueryService invoiceQueryService;

    @PostMapping("/checkout")
    @Operation(
            summary = "Khách hàng tạo đơn hàng mới",
            description = "Tạo đơn hàng từ giỏ hàng hoặc mua ngay, áp dụng mã voucher, trừ tồn kho và tạo bản ghi thanh toán"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đặt hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ hoặc giỏ hàng trống", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền thực hiện", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy địa chỉ hoặc biến thể sản phẩm", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Xung đột tồn kho hoặc voucher không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CheckoutResponse> checkout(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CheckoutRequest request
    ) {
        CheckoutResponse response = orderCheckoutService.checkout(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(
            summary = "Lấy danh sách đơn hàng của khách hàng đăng nhập",
            description = "Trả về danh sách đơn hàng có phân trang, hỗ trợ lọc theo trạng thái, sắp xếp mặc định theo thời gian tạo mới nhất"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách đơn hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số yêu cầu không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền thực hiện", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<CustomerOrderListItemResponse>> getOrders(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<CustomerOrderListItemResponse> response = orderHistoryService.getOrders(principal.getId(), status, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Lấy chi tiết đơn hàng của khách hàng đăng nhập",
            description = "Trả về thông tin chi tiết đơn hàng, snapshot địa chỉ và danh sách sản phẩm. Chống IDOR: đơn của người khác trả về 404"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy chi tiết đơn hàng thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền thực hiện", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CustomerOrderDetailResponse> getOrderDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        CustomerOrderDetailResponse response = orderHistoryService.getOrderDetail(principal.getId(), id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/invoice")
    @Operation(
            summary = "Khách hàng xem hóa đơn điện tử của đơn hàng",
            description = "Chống IDOR: Kiểm tra đơn hàng thuộc quyền sở hữu của khách hàng, trả về hóa đơn điện tử hiện hành hợp lệ. Nếu chưa có hoặc sai chủ sở hữu sẽ trả về 404"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy hóa đơn điện tử thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền thực hiện", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng hoặc đơn hàng chưa có hóa đơn", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<InvoiceDetailResponse> getOrderInvoice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        InvoiceDetailResponse response = invoiceQueryService.getCustomerInvoiceByOrderId(principal.getId(), id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    @Operation(
            summary = "Khách hàng hủy đơn hàng",
            description = "Hủy đơn hàng ở trạng thái PENDING và chưa thanh toán, hoàn trả tồn kho nguyên tử. Trả về 409 nếu trạng thái không hợp lệ hoặc đã thanh toán"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hủy đơn hàng thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền thực hiện", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy đơn hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Không thể hủy đơn hàng ở trạng thái hiện tại hoặc đã thanh toán", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CustomerOrderDetailResponse> cancelOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id
    ) {
        CustomerOrderDetailResponse response = orderHistoryService.cancelOrder(principal.getId(), id);
        return ResponseEntity.ok(response);
    }
}
