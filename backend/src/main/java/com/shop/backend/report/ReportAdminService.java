package com.shop.backend.report;

import com.shop.backend.report.dto.CategorySalesReportResponse;
import com.shop.backend.report.dto.OrderStatusDistributionResponse;
import com.shop.backend.report.dto.ReportSummaryResponse;
import com.shop.backend.report.dto.RevenueReportPointResponse;
import com.shop.backend.report.dto.TopProductReportResponse;
import java.time.LocalDate;
import java.util.List;

public interface ReportAdminService {

    ReportSummaryResponse getSummary(LocalDate startDate, LocalDate endDate);

    List<RevenueReportPointResponse> getRevenueTrend(LocalDate startDate, LocalDate endDate, String granularity);

    List<TopProductReportResponse> getTopProducts(LocalDate startDate, LocalDate endDate, Integer limit);

    List<CategorySalesReportResponse> getCategorySales(LocalDate startDate, LocalDate endDate);

    List<OrderStatusDistributionResponse> getOrderStatusDistribution(LocalDate startDate, LocalDate endDate);
}
