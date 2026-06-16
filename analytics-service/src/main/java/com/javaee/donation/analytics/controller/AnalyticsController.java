package com.javaee.donation.analytics.controller;

import com.javaee.donation.analytics.service.AnalyticsService;
import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.HourlyStatResponse;
import com.javaee.donation.common.model.TopViewerResponse;
import com.javaee.donation.common.model.ViewerProfileResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/viewers/{viewerId}/profile")
    public ApiResponse<ViewerProfileResponse> profile(@PathVariable String viewerId) {
        return ApiResponse.success(TraceContext.getTraceId(), analyticsService.getProfile(viewerId));
    }

    @GetMapping("/streamers/{streamerId}/top-viewers")
    public ApiResponse<List<TopViewerResponse>> topViewers(@PathVariable String streamerId,
                                                           @RequestParam(defaultValue = "10") Integer limit) {
        return ApiResponse.success(TraceContext.getTraceId(), analyticsService.getTopViewers(streamerId, limit));
    }

    @GetMapping("/hourly")
    public ApiResponse<List<HourlyStatResponse>> hourly(@RequestParam String startHour,
                                                        @RequestParam String endHour,
                                                        @RequestParam(required = false) String gender,
                                                        @RequestParam(required = false) String streamerId) {
        return ApiResponse.success(TraceContext.getTraceId(),
                analyticsService.getHourlyStats(startHour, endHour, gender, streamerId));
    }

    @PostMapping("/jobs/rebuild")
    public ApiResponse<String> rebuild() {
        return ApiResponse.success(TraceContext.getTraceId(), analyticsService.rebuild());
    }
}
