package com.shop.backend.report;

import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.order.OrderAdminService;
import com.shop.backend.order.OrderStatus;
import com.shop.backend.order.dto.CategorySalesProjection;
import com.shop.backend.order.dto.OrderStatusCountProjection;
import com.shop.backend.order.dto.PeriodRevenueProjection;
import com.shop.backend.order.dto.ReportSummaryProjection;
import com.shop.backend.order.dto.TopProductProjection;
import com.shop.backend.report.dto.CategorySalesReportResponse;
import com.shop.backend.report.dto.OrderStatusDistributionResponse;
import com.shop.backend.report.dto.ReportSummaryResponse;
import com.shop.backend.report.dto.RevenueReportPointResponse;
import com.shop.backend.report.dto.TopProductReportResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportAdminServiceImpl implements ReportAdminService {

    private final OrderAdminService orderAdminService;

    @Override
    public ReportSummaryResponse getSummary(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        ReportSummaryProjection projection = orderAdminService.getReportSummary(start, end);

        long grossRevenue = (projection != null && projection.getGrossRevenue() != null)
                ? projection.getGrossRevenue().longValue()
                : 0L;
        long netRevenue = (projection != null && projection.getNetRevenue() != null)
                ? projection.getNetRevenue().longValue()
                : 0L;
        long totalDiscount = (projection != null && projection.getTotalDiscount() != null)
                ? projection.getTotalDiscount().longValue()
                : 0L;
        long totalShippingFee = (projection != null && projection.getTotalShippingFee() != null)
                ? projection.getTotalShippingFee().longValue()
                : 0L;
        long completedOrderCount = (projection != null && projection.getCompletedOrderCount() != null)
                ? projection.getCompletedOrderCount()
                : 0L;

        long averageOrderValue = completedOrderCount > 0
                ? (netRevenue / completedOrderCount)
                : 0L;

        return ReportSummaryResponse.builder()
                .grossRevenue(grossRevenue)
                .netRevenue(netRevenue)
                .totalDiscount(totalDiscount)
                .totalShippingFee(totalShippingFee)
                .completedOrderCount(completedOrderCount)
                .averageOrderValue(averageOrderValue)
                .build();
    }

    @Override
    public List<RevenueReportPointResponse> getRevenueTrend(LocalDate startDate, LocalDate endDate, String granularity) {
        validateDateRange(startDate, endDate);
        String normGranularity = (granularity != null && !granularity.isBlank())
                ? granularity.trim().toLowerCase()
                : "day";

        if (!"day".equals(normGranularity) && !"week".equals(normGranularity) && !"month".equals(normGranularity)) {
            throw new BadRequestException("Độ mịn thời gian (granularity) chỉ chấp nhận: day, week, month");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        List<PeriodRevenueProjection> dbData;
        switch (normGranularity) {
            case "week":
                dbData = orderAdminService.getWeeklyReportRevenueTrend(start, end);
                return fillWeeklyGaps(startDate, endDate, dbData);
            case "month":
                dbData = orderAdminService.getMonthlyReportRevenueTrend(start, end);
                return fillMonthlyGaps(startDate, endDate, dbData);
            case "day":
            default:
                dbData = orderAdminService.getDailyReportRevenueTrend(start, end);
                return fillDailyGaps(startDate, endDate, dbData);
        }
    }

    @Override
    public List<TopProductReportResponse> getTopProducts(LocalDate startDate, LocalDate endDate, Integer limit) {
        validateDateRange(startDate, endDate);
        int effectiveLimit = (limit != null) ? limit : 10;
        if (effectiveLimit < 1 || effectiveLimit > 100) {
            throw new BadRequestException("Giới hạn số lượng (limit) phải từ 1 đến 100");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        List<TopProductProjection> projections = orderAdminService.getTopSellingProducts(start, end, effectiveLimit);
        if (projections == null || projections.isEmpty()) {
            return Collections.emptyList();
        }

        return projections.stream()
                .map(p -> TopProductReportResponse.builder()
                        .productId(p.getProductId())
                        .productName(p.getProductName())
                        .totalQuantitySold(p.getTotalQuantitySold() != null ? p.getTotalQuantitySold() : 0L)
                        .totalRevenue(p.getTotalRevenue() != null ? p.getTotalRevenue().longValue() : 0L)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<CategorySalesReportResponse> getCategorySales(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        List<CategorySalesProjection> projections = orderAdminService.getCategorySales(start, end);
        if (projections == null || projections.isEmpty()) {
            return Collections.emptyList();
        }

        long totalRevenueSum = projections.stream()
                .mapToLong(p -> p.getTotalRevenue() != null ? p.getTotalRevenue().longValue() : 0L)
                .sum();

        return projections.stream()
                .map(p -> {
                    long revenue = p.getTotalRevenue() != null ? p.getTotalRevenue().longValue() : 0L;
                    double percentage = (totalRevenueSum > 0)
                            ? Math.round(((double) revenue / (double) totalRevenueSum) * 1000.0) / 10.0
                            : 0.0;
                    return CategorySalesReportResponse.builder()
                            .categoryId(p.getCategoryId())
                            .categoryName(p.getCategoryName())
                            .totalQuantitySold(p.getTotalQuantitySold() != null ? p.getTotalQuantitySold() : 0L)
                            .totalRevenue(revenue)
                            .percentage(percentage)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderStatusDistributionResponse> getOrderStatusDistribution(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        List<OrderStatusCountProjection> projections = orderAdminService.getOrderStatusDistribution(start, end);
        Map<String, Long> countMap = new HashMap<>();
        if (projections != null) {
            for (OrderStatusCountProjection p : projections) {
                if (p.getStatus() != null) {
                    countMap.put(p.getStatus().toLowerCase(), p.getOrderCount());
                }
            }
        }

        long totalOrders = countMap.values().stream().mapToLong(Long::longValue).sum();

        List<OrderStatusDistributionResponse> result = new ArrayList<>();
        for (OrderStatus status : OrderStatus.values()) {
            long count = countMap.getOrDefault(status.name().toLowerCase(), 0L);
            double percentage = (totalOrders > 0)
                    ? Math.round(((double) count / (double) totalOrders) * 1000.0) / 10.0
                    : 0.0;

            result.add(OrderStatusDistributionResponse.builder()
                    .status(status)
                    .statusLabel(getStatusLabel(status))
                    .orderCount(count)
                    .percentage(percentage)
                    .build());
        }
        return result;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException("Ngày bắt đầu và ngày kết thúc là bắt buộc");
        }
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("Ngày bắt đầu không được sau ngày kết thúc");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 366) {
            throw new BadRequestException("Khoảng thời gian báo cáo không được vượt quá 366 ngày");
        }
    }

    private List<RevenueReportPointResponse> fillDailyGaps(LocalDate startDate, LocalDate endDate, List<PeriodRevenueProjection> dbData) {
        Map<String, PeriodRevenueProjection> map = new HashMap<>();
        if (dbData != null) {
            for (PeriodRevenueProjection p : dbData) {
                if (p.getPeriod() != null) {
                    map.put(p.getPeriod(), p);
                }
            }
        }

        List<RevenueReportPointResponse> points = new ArrayList<>();
        LocalDate curr = startDate;
        while (!curr.isAfter(endDate)) {
            String periodKey = curr.format(DateTimeFormatter.ISO_LOCAL_DATE);
            PeriodRevenueProjection p = map.get(periodKey);
            points.add(RevenueReportPointResponse.builder()
                    .period(periodKey)
                    .netRevenue(p != null && p.getNetRevenue() != null ? p.getNetRevenue().longValue() : 0L)
                    .grossRevenue(p != null && p.getGrossRevenue() != null ? p.getGrossRevenue().longValue() : 0L)
                    .completedOrderCount(p != null && p.getCompletedOrderCount() != null ? p.getCompletedOrderCount() : 0L)
                    .build());
            curr = curr.plusDays(1);
        }
        return points;
    }

    private List<RevenueReportPointResponse> fillWeeklyGaps(LocalDate startDate, LocalDate endDate, List<PeriodRevenueProjection> dbData) {
        Map<String, PeriodRevenueProjection> map = new HashMap<>();
        if (dbData != null) {
            for (PeriodRevenueProjection p : dbData) {
                if (p.getPeriod() != null) {
                    map.put(p.getPeriod(), p);
                }
            }
        }

        List<RevenueReportPointResponse> points = new ArrayList<>();
        LocalDate curr = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endWeek = endDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        while (!curr.isAfter(endWeek)) {
            String periodKey = String.format("%04d-W%02d", curr.get(IsoFields.WEEK_BASED_YEAR), curr.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            PeriodRevenueProjection p = map.get(periodKey);
            points.add(RevenueReportPointResponse.builder()
                    .period(periodKey)
                    .netRevenue(p != null && p.getNetRevenue() != null ? p.getNetRevenue().longValue() : 0L)
                    .grossRevenue(p != null && p.getGrossRevenue() != null ? p.getGrossRevenue().longValue() : 0L)
                    .completedOrderCount(p != null && p.getCompletedOrderCount() != null ? p.getCompletedOrderCount() : 0L)
                    .build());
            curr = curr.plusWeeks(1);
        }
        return points;
    }

    private List<RevenueReportPointResponse> fillMonthlyGaps(LocalDate startDate, LocalDate endDate, List<PeriodRevenueProjection> dbData) {
        Map<String, PeriodRevenueProjection> map = new HashMap<>();
        if (dbData != null) {
            for (PeriodRevenueProjection p : dbData) {
                if (p.getPeriod() != null) {
                    map.put(p.getPeriod(), p);
                }
            }
        }

        List<RevenueReportPointResponse> points = new ArrayList<>();
        YearMonth curr = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);

        while (!curr.isAfter(end)) {
            String periodKey = curr.toString();
            PeriodRevenueProjection p = map.get(periodKey);
            points.add(RevenueReportPointResponse.builder()
                    .period(periodKey)
                    .netRevenue(p != null && p.getNetRevenue() != null ? p.getNetRevenue().longValue() : 0L)
                    .grossRevenue(p != null && p.getGrossRevenue() != null ? p.getGrossRevenue().longValue() : 0L)
                    .completedOrderCount(p != null && p.getCompletedOrderCount() != null ? p.getCompletedOrderCount() : 0L)
                    .build());
            curr = curr.plusMonths(1);
        }
        return points;
    }

    private String getStatusLabel(OrderStatus status) {
        switch (status) {
            case PENDING:
                return "Chờ xác nhận";
            case CONFIRMED:
                return "Đã xác nhận";
            case SHIPPING:
                return "Đang giao hàng";
            case COMPLETED:
                return "Đã hoàn thành";
            case CANCELLED:
                return "Đã hủy";
            case REFUNDED:
                return "Đã hoàn tiền";
            default:
                return status.name();
        }
    }
}
