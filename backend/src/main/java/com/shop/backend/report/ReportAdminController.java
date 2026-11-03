package com.shop.backend.report;

import com.shop.backend.common.dto.ErrorResponse;
import com.shop.backend.report.dto.CategorySalesReportResponse;
import com.shop.backend.report.dto.OrderStatusDistributionResponse;
import com.shop.backend.report.dto.ReportSummaryResponse;
import com.shop.backend.report.dto.RevenueReportPointResponse;
import com.shop.backend.report.dto.TopProductReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@Tag(name = "Admin Reports", description = "Các API báo cáo & thống kê chuyên sâu dành cho Admin/Staff")
public class ReportAdminController {

    private final ReportAdminService reportAdminService;

    @GetMapping("/summary")
    @Operation(
            summary = "Lấy dữ liệu tổng quan tài chính và đơn hàng trong khoảng thời gian tùy chỉnh",
            description = "Trả về doanh thu gộp (Gross Merchandise), doanh thu thuần (Net Revenue), tổng giảm giá, phí ship, số đơn hoàn thành và giá trị đơn trung bình (AOV). Chỉ tính các đơn có trạng thái COMPLETED dựa trên timestamp completed_at theo múi giờ Asia/Ho_Chi_Minh."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy dữ liệu tổng quan báo cáo thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số ngày không hợp lệ hoặc khoảng ngày vượt quá 366 ngày", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ReportSummaryResponse> getSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(reportAdminService.getSummary(startDate, endDate));
    }

    @GetMapping("/revenue")
    @Operation(
            summary = "Lấy dữ liệu xu hướng doanh thu và số đơn theo ngày, tuần hoặc tháng trong khoảng thời gian",
            description = "Hỗ trợ nhóm dữ liệu theo ngày (day), tuần ISO-8601 (week: YYYY-Www) hoặc tháng (month: YYYY-MM). Tự động điền giá trị 0 cho các mốc thời gian không phát sinh đơn. Chỉ tính đơn COMPLETED theo completed_at."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy dữ liệu xu hướng doanh thu thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số ngày hoặc độ mịn (granularity) không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<RevenueReportPointResponse>> getRevenueTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "day") String granularity
    ) {
        return ResponseEntity.ok(reportAdminService.getRevenueTrend(startDate, endDate, granularity));
    }

    @GetMapping("/top-products")
    @Operation(
            summary = "Lấy danh sách top sản phẩm bán chạy nhất trong khoảng thời gian",
            description = "Sắp xếp theo số lượng bán giảm dần, tiêu chí phụ là doanh thu. Dữ liệu tên sản phẩm và đơn giá được trích xuất từ snapshot order_details tại thời điểm mua hàng. Chỉ tính đơn COMPLETED."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy top sản phẩm bán chạy thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số ngày hoặc limit (1-100) không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<TopProductReportResponse>> getTopProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        return ResponseEntity.ok(reportAdminService.getTopProducts(startDate, endDate, limit));
    }

    @GetMapping("/category-sales")
    @Operation(
            summary = "Lấy báo cáo doanh thu và sản lượng bán theo danh mục hàng hóa",
            description = "Tổng hợp doanh thu và số lượng theo từng danh mục sản phẩm, tính toán tỷ trọng phần trăm doanh thu. Sử dụng snapshot giá từ order_details của các đơn COMPLETED."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy báo cáo doanh thu theo danh mục thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số ngày không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<CategorySalesReportResponse>> getCategorySales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(reportAdminService.getCategorySales(startDate, endDate));
    }

    @GetMapping("/order-status-distribution")
    @Operation(
            summary = "Lấy tỷ lệ phân bố và số lượng của các trạng thái đơn hàng được tạo trong kỳ",
            description = "Thống kê tất cả trạng thái đơn hàng (PENDING, CONFIRMED, SHIPPING, COMPLETED, CANCELLED, REFUNDED) dựa trên thời điểm tạo created_at trong khoảng thời gian đã chọn."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy phân bố trạng thái đơn hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số ngày không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực danh tính", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<OrderStatusDistributionResponse>> getOrderStatusDistribution(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(reportAdminService.getOrderStatusDistribution(startDate, endDate));
    }
}
