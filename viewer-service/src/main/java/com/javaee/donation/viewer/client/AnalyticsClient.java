package com.javaee.donation.viewer.client;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.model.TopViewerResponse;
import com.javaee.donation.common.model.ViewerProfileResponse;
import com.javaee.donation.viewer.config.FeignConfig;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "analytics-service", url = "${clients.analytics-service}",
        configuration = FeignConfig.class, fallbackFactory = AnalyticsClientFallbackFactory.class)
public interface AnalyticsClient {

    @GetMapping("/api/analytics/viewers/{viewerId}/profile")
    ApiResponse<ViewerProfileResponse> profile(@PathVariable("viewerId") String viewerId);

    @GetMapping("/api/analytics/streamers/{streamerId}/top-viewers")
    ApiResponse<List<TopViewerResponse>> topViewers(@PathVariable("streamerId") String streamerId,
                                                      @RequestParam("limit") Integer limit);
}
