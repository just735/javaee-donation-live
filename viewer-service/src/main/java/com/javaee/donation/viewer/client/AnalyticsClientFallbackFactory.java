package com.javaee.donation.viewer.client;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.model.TopViewerResponse;
import com.javaee.donation.common.model.ViewerProfileResponse;
import com.javaee.donation.viewer.service.ViewerFallbackService;
import java.util.List;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsClientFallbackFactory implements FallbackFactory<AnalyticsClient> {

    private final ViewerFallbackService fallbackService;

    public AnalyticsClientFallbackFactory(ViewerFallbackService fallbackService) {
        this.fallbackService = fallbackService;
    }

    @Override
    public AnalyticsClient create(Throwable cause) {
        return new AnalyticsClient() {
            @Override
            public ApiResponse<ViewerProfileResponse> profile(String viewerId) {
                return fallbackService.profileApiFallback(viewerId, cause);
            }

            @Override
            public ApiResponse<List<TopViewerResponse>> topViewers(String streamerId, Integer limit) {
                return fallbackService.topViewersApiFallback(streamerId, limit, cause);
            }
        };
    }
}
