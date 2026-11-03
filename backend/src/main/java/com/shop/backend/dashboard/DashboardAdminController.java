package com.shop.backend.dashboard;

import com.shop.backend.common.dto.ErrorResponse;
import com.shop.backend.dashboard.dto.CustomerSegmentResponse;
import com.shop.backend.dashboard.dto.DashboardSummaryResponse;
import com.shop.backend.dashboard.dto.RevenueTrendPointResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@Tag(name = "Admin Dashboard", description = "Các API thống kê, tổng quan vận hành dành cho Admin/Staff")
public class DashboardAdminController {

    private final DashboardAdminService dashboardService;

    @GetMapping("/summary")
    @Operation(
            summary = "Lấy dữ liệu tổng quan các chỉ số vận hành chính (KPI cards) cho Admin Dashboard",
            description = "Trả về tổng số biến thể sắp hết hàng (stock < 10), số khách hàng mới theo kỳ và tỷ lệ tăng trưởng so với kỳ trước, tổng số khách hàng, tổng doanh thu đơn hoàn thành và số đơn hàng mới."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy dữ liệu tổng quan thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số kỳ báo cáo không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<DashboardSummaryResponse> getSummary(
            @RequestParam(defaultValue = "today") String period
    ) {
        return ResponseEntity.ok(dashboardService.getSummary(period));
    }

    @GetMapping("/revenue-trend")
    @Operation(
            summary = "Lấy dữ liệu xu hướng doanh thu và số đơn theo ngày hoặc tháng cho biểu đồ Admin Dashboard",
            description = "Trả về mảng các điểm dữ liệu liên tục theo kỳ (7days: 7 ngày, 30days: 30 ngày, year: 12 tháng) dựa trên múi giờ Việt Nam. Chỉ tính doanh thu của đơn hoàn thành (completed)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy dữ liệu biểu đồ thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số kỳ báo cáo không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<RevenueTrendPointResponse>> getRevenueTrend(
            @RequestParam(defaultValue = "30days") String period
    ) {
        return ResponseEntity.ok(dashboardService.getRevenueTrend(period));
    }

    @GetMapping("/customer-segments")
    @Operation(
            summary = "Lấy dữ liệu phân loại khách hàng theo 4 nhóm chi tiêu và tần suất mua hàng cho Admin Dashboard",
            description = "Trả về số lượng và tỷ lệ % của 4 phân khúc khách hàng: VIP, Thân thiết, Khách mới, Tiềm năng."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy dữ liệu phân loại khách hàng thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<CustomerSegmentResponse>> getCustomerSegments() {
        return ResponseEntity.ok(dashboardService.getCustomerSegments());
    }
}
