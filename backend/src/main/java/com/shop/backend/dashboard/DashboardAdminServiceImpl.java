package com.shop.backend.dashboard;

import com.shop.backend.common.exception.BadRequestException;
import com.shop.backend.dashboard.dto.CustomerSegmentResponse;
import com.shop.backend.dashboard.dto.DashboardSummaryResponse;
import com.shop.backend.dashboard.dto.RevenueTrendPointResponse;
import com.shop.backend.order.OrderAdminService;
import com.shop.backend.order.dto.CustomerSegmentProjection;
import com.shop.backend.order.dto.DailyRevenueProjection;
import com.shop.backend.order.dto.MonthlyRevenueProjection;
import com.shop.backend.product.ProductAdminService;
import com.shop.backend.user.UserAdminService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardAdminServiceImpl implements DashboardAdminService {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");

    private final ProductAdminService productService;
    private final UserAdminService userAdminService;
    private final OrderAdminService orderAdminService;
    private final Clock clock;

    @Autowired
    public DashboardAdminServiceImpl(
            ProductAdminService productService,
            UserAdminService userAdminService,
            OrderAdminService orderAdminService
    ) {
        this(productService, userAdminService, orderAdminService, Clock.system(VIETNAM_ZONE));
    }

    public DashboardAdminServiceImpl(
            ProductAdminService productService,
            UserAdminService userAdminService
    ) {
        this(productService, userAdminService, null, Clock.system(VIETNAM_ZONE));
    }

    public DashboardAdminServiceImpl(
            ProductAdminService productService,
            UserAdminService userAdminService,
            Clock clock
    ) {
        this(productService, userAdminService, null, clock);
    }

    public DashboardAdminServiceImpl(
            ProductAdminService productService,
            UserAdminService userAdminService,
            OrderAdminService orderAdminService,
            Clock clock
    ) {
        this.productService = productService;
        this.userAdminService = userAdminService;
        this.orderAdminService = orderAdminService;
        this.clock = clock;
    }

    @Override
    public DashboardSummaryResponse getSummary(String period) {
        String normalizedPeriod = (period != null && !period.isBlank())
                ? period.trim().toLowerCase()
                : "today";

        long lowStockCount = productService.countLowStockVariants(null);
        long totalCustomersCount = userAdminService.countTotalCustomers();

        DateRange dateRange = resolvePeriodDateRange(normalizedPeriod);
        long newCustomersCount = userAdminService.countNewCustomersBetween(
                dateRange.currentStart(),
                dateRange.currentEnd()
        );
        long previousNewCustomersCount = userAdminService.countNewCustomersBetween(
                dateRange.previousStart(),
                dateRange.previousEnd()
        );
        double customersChangeRate = calculateChangeRate(newCustomersCount, previousNewCustomersCount);

        long revenueTotal = 0L;
        long previousRevenue = 0L;
        long newOrdersCount = 0L;
        long previousOrders = 0L;

        if (orderAdminService != null) {
            revenueTotal = orderAdminService.sumCompletedRevenueBetween(
                    dateRange.currentStart(),
                    dateRange.currentEnd()
            );
            previousRevenue = orderAdminService.sumCompletedRevenueBetween(
                    dateRange.previousStart(),
                    dateRange.previousEnd()
            );

            newOrdersCount = orderAdminService.countOrdersBetween(
                    dateRange.currentStart(),
                    dateRange.currentEnd()
            );
            previousOrders = orderAdminService.countOrdersBetween(
                    dateRange.previousStart(),
                    dateRange.previousEnd()
            );
        }

        double revenueChangeRate = calculateChangeRate(revenueTotal, previousRevenue);
        double ordersChangeRate = calculateChangeRate(newOrdersCount, previousOrders);

        return DashboardSummaryResponse.builder()
                .lowStockCount(lowStockCount)
                .newCustomersCount(newCustomersCount)
                .newCustomersChangeRate(customersChangeRate)
                .totalCustomersCount(totalCustomersCount)
                .revenueTotal(revenueTotal)
                .revenueChangeRate(revenueChangeRate)
                .newOrdersCount(newOrdersCount)
                .newOrdersChangeRate(ordersChangeRate)
                .build();
    }

    @Override
    public List<RevenueTrendPointResponse> getRevenueTrend(String period) {
        String normalized = (period != null && !period.isBlank())
                ? period.trim().toLowerCase()
                : "30days";

        LocalDate today = LocalDate.now(clock.withZone(VIETNAM_ZONE));

        if ("year".equals(normalized)) {
            int currentYear = today.getYear();
            LocalDateTime start = LocalDate.of(currentYear, 1, 1).atStartOfDay();
            LocalDateTime end = LocalDate.of(currentYear + 1, 1, 1).atStartOfDay();

            List<MonthlyRevenueProjection> projections = (orderAdminService != null)
                    ? orderAdminService.getMonthlyRevenueTrend(start, end)
                    : List.of();

            Map<String, MonthlyRevenueProjection> dataMap = new HashMap<>();
            for (MonthlyRevenueProjection item : projections) {
                if (item.getTrendMonth() != null) {
                    dataMap.put(item.getTrendMonth(), item);
                }
            }

            List<RevenueTrendPointResponse> results = new ArrayList<>(12);
            for (int month = 1; month <= 12; month++) {
                String monthKey = String.format("%04d-%02d", currentYear, month);
                String label = String.format("Thg %02d", month);
                MonthlyRevenueProjection data = dataMap.get(monthKey);

                long revenue = (data != null && data.getTotalRevenue() != null)
                        ? data.getTotalRevenue().longValue()
                        : 0L;
                long orders = (data != null && data.getTotalOrders() != null)
                        ? data.getTotalOrders()
                        : 0L;

                results.add(RevenueTrendPointResponse.builder()
                        .date(label)
                        .revenue(revenue)
                        .orders(orders)
                        .build());
            }
            return results;
        }

        int daysCount;
        if ("7days".equals(normalized)) {
            daysCount = 7;
        } else if ("30days".equals(normalized)) {
            daysCount = 30;
        } else {
            throw new BadRequestException(
                    "Kỳ biểu đồ không hợp lệ: '" + period + "'. Chỉ chấp nhận: 7days, 30days, year");
        }

        LocalDate startDate = today.minusDays(daysCount - 1);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        List<DailyRevenueProjection> projections = (orderAdminService != null)
                ? orderAdminService.getDailyRevenueTrend(start, end)
                : List.of();

        Map<LocalDate, DailyRevenueProjection> dataMap = new HashMap<>();
        for (DailyRevenueProjection item : projections) {
            if (item.getTrendDate() != null) {
                dataMap.put(item.getTrendDate().toLocalDate(), item);
            }
        }

        List<RevenueTrendPointResponse> results = new ArrayList<>(daysCount);
        for (int i = 0; i < daysCount; i++) {
            LocalDate day = startDate.plusDays(i);
            String label = day.format(DATE_FORMATTER);
            DailyRevenueProjection data = dataMap.get(day);

            long revenue = (data != null && data.getTotalRevenue() != null)
                    ? data.getTotalRevenue().longValue()
                    : 0L;
            long orders = (data != null && data.getTotalOrders() != null)
                    ? data.getTotalOrders()
                    : 0L;

            results.add(RevenueTrendPointResponse.builder()
                    .date(label)
                    .revenue(revenue)
                    .orders(orders)
                    .build());
        }
        return results;
    }

    @Override
    public List<CustomerSegmentResponse> getCustomerSegments() {
        CustomerSegmentProjection proj = (orderAdminService != null)
                ? orderAdminService.countCustomerSegments()
                : null;

        long vip = (proj != null && proj.getVipCount() != null) ? proj.getVipCount() : 0L;
        long loyal = (proj != null && proj.getLoyalCount() != null) ? proj.getLoyalCount() : 0L;
        long newCount = (proj != null && proj.getNewCount() != null) ? proj.getNewCount() : 0L;
        long potential = (proj != null && proj.getPotentialCount() != null) ? proj.getPotentialCount() : 0L;

        long total = vip + loyal + newCount + potential;

        double vipPct = total > 0 ? Math.round(((double) vip / (double) total) * 1000.0) / 10.0 : 0.0;
        double loyalPct = total > 0 ? Math.round(((double) loyal / (double) total) * 1000.0) / 10.0 : 0.0;
        double newPct = total > 0 ? Math.round(((double) newCount / (double) total) * 1000.0) / 10.0 : 0.0;
        double potentialPct = total > 0 ? Math.round(((double) potential / (double) total) * 1000.0) / 10.0 : 0.0;

        List<CustomerSegmentResponse> segments = new ArrayList<>(4);
        segments.add(CustomerSegmentResponse.builder()
                .id("vip")
                .name("Khách hàng VIP")
                .count(vip)
                .percentage(vipPct)
                .color("#18181B")
                .description("Chi tiêu từ 5.000.000 ₫ trở lên")
                .build());

        segments.add(CustomerSegmentResponse.builder()
                .id("loyal")
                .name("Khách hàng thân thiết")
                .count(loyal)
                .percentage(loyalPct)
                .color("#059669")
                .description("Đã hoàn tất từ 3 đơn hàng trở lên")
                .build());

        segments.add(CustomerSegmentResponse.builder()
                .id("new")
                .name("Khách hàng mới")
                .count(newCount)
                .percentage(newPct)
                .color("#2563EB")
                .description("Đã hoàn tất 1 - 2 đơn hàng")
                .build());

        segments.add(CustomerSegmentResponse.builder()
                .id("potential")
                .name("Khách hàng tiềm năng")
                .count(potential)
                .percentage(potentialPct)
                .color("#D97706")
                .description("Đã đăng ký tài khoản, chưa phát sinh đơn")
                .build());

        return segments;
    }

    private double calculateChangeRate(long current, long previous) {
        if (previous == 0) {
            return (current > 0) ? 100.0 : 0.0;
        }
        double rate = ((double) (current - previous) / (double) previous) * 100.0;
        return Math.round(rate * 10.0) / 10.0;
    }

    record DateRange(
            LocalDateTime currentStart,
            LocalDateTime currentEnd,
            LocalDateTime previousStart,
            LocalDateTime previousEnd
    ) {}

    private DateRange resolvePeriodDateRange(String period) {
        LocalDate today = LocalDate.now(clock.withZone(VIETNAM_ZONE));

        switch (period) {
            case "today": {
                LocalDateTime currentStart = today.atStartOfDay();
                LocalDateTime currentEnd = today.plusDays(1).atStartOfDay();
                LocalDateTime previousStart = today.minusDays(1).atStartOfDay();
                LocalDateTime previousEnd = today.atStartOfDay();
                return new DateRange(currentStart, currentEnd, previousStart, previousEnd);
            }
            case "7days": {
                LocalDateTime currentStart = today.minusDays(6).atStartOfDay();
                LocalDateTime currentEnd = today.plusDays(1).atStartOfDay();
                LocalDateTime previousStart = today.minusDays(13).atStartOfDay();
                LocalDateTime previousEnd = today.minusDays(6).atStartOfDay();
                return new DateRange(currentStart, currentEnd, previousStart, previousEnd);
            }
            case "30days": {
                LocalDateTime currentStart = today.minusDays(29).atStartOfDay();
                LocalDateTime currentEnd = today.plusDays(1).atStartOfDay();
                LocalDateTime previousStart = today.minusDays(59).atStartOfDay();
                LocalDateTime previousEnd = today.minusDays(29).atStartOfDay();
                return new DateRange(currentStart, currentEnd, previousStart, previousEnd);
            }
            case "quarter": {
                int currentMonth = today.getMonthValue();
                int firstMonthOfQuarter = ((currentMonth - 1) / 3) * 3 + 1;
                LocalDate startOfCurrentQuarter = LocalDate.of(today.getYear(), firstMonthOfQuarter, 1);
                LocalDate startOfNextQuarter = startOfCurrentQuarter.plusMonths(3);
                LocalDate startOfPreviousQuarter = startOfCurrentQuarter.minusMonths(3);

                LocalDateTime currentStart = startOfCurrentQuarter.atStartOfDay();
                LocalDateTime currentEnd = startOfNextQuarter.atStartOfDay();
                LocalDateTime previousStart = startOfPreviousQuarter.atStartOfDay();
                LocalDateTime previousEnd = startOfCurrentQuarter.atStartOfDay();
                return new DateRange(currentStart, currentEnd, previousStart, previousEnd);
            }
            default:
                throw new BadRequestException(
                        "Kỳ báo cáo không hợp lệ: '" + period + "'. Chỉ chấp nhận: today, 7days, 30days, quarter");
        }
    }
}
