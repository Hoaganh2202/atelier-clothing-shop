package com.shop.backend.dashboard;

import com.shop.backend.dashboard.dto.CustomerSegmentResponse;
import com.shop.backend.dashboard.dto.DashboardSummaryResponse;
import com.shop.backend.dashboard.dto.RevenueTrendPointResponse;
import java.util.List;

public interface DashboardAdminService {

    DashboardSummaryResponse getSummary(String period);

    List<RevenueTrendPointResponse> getRevenueTrend(String period);

    List<CustomerSegmentResponse> getCustomerSegments();
}
