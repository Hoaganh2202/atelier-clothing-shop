package com.shop.backend.user;

import com.shop.backend.common.dto.ErrorResponse;
import com.shop.backend.common.dto.PageResponse;
import com.shop.backend.security.UserPrincipal;
import com.shop.backend.user.dto.CustomerDetailResponse;
import com.shop.backend.user.dto.CustomerListItemResponse;
import com.shop.backend.user.dto.UpdateCustomerStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Customer Management", description = "Quản lý tài khoản khách hàng dành cho Admin")
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    @Operation(
            summary = "Lấy danh sách khách hàng phân trang và lọc theo từ khóa, trạng thái",
            description = "Hỗ trợ tìm kiếm theo họ tên, email, số điện thoại (không phân biệt hoa thường), lọc theo trạng thái tài khoản, phân trang tối đa 100 bản ghi/trang"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách khách hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số phân trang hoặc sắp xếp không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<CustomerListItemResponse>> getCustomers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(userAdminService.getCustomers(keyword, status, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin chi tiết hồ sơ khách hàng theo ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy thông tin chi tiết khách hàng thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy khách hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CustomerDetailResponse> getCustomerById(@PathVariable Long id) {
        return ResponseEntity.ok(userAdminService.getCustomerById(id));
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Khóa hoặc mở khóa tài khoản khách hàng",
            description = "Cập nhật trạng thái tài khoản khách hàng (ACTIVE/BLOCKED). Chặn tự khóa tài khoản đang đăng nhập hoặc thao tác trên tài khoản không phải khách hàng."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật trạng thái khách hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ, tự khóa tài khoản đang đăng nhập hoặc tài khoản đích không phải khách hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy khách hàng", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CustomerDetailResponse> updateCustomerStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCustomerStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long currentAdminId = principal != null ? principal.getId() : null;
        return ResponseEntity.ok(userAdminService.updateCustomerStatus(id, request, currentAdminId));
    }
}
