package com.javaee.donation.viewer.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.TopViewerResponse;
import com.javaee.donation.common.model.ViewerProfileResponse;
import com.javaee.donation.viewer.client.AnalyticsClient;
import com.javaee.donation.viewer.constant.ViewerConstants;
import com.javaee.donation.viewer.dto.TopViewersFetchResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsGateway {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsGateway.class);
    private static final long PROFILE_TIMEOUT_SECONDS = 2L;

    private final AnalyticsClient analyticsClient;
    private final ViewerFallbackService fallbackService;
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(4);

    public AnalyticsGateway(AnalyticsClient analyticsClient, ViewerFallbackService fallbackService) {
        this.analyticsClient = analyticsClient;
        this.fallbackService = fallbackService;
    }

    @SentinelResource(value = "analyticsProfile", fallback = "getProfileFallback")
    public ViewerProfileResponse getProfile(String viewerId) {
        String traceId = TraceContext.getTraceId();
        log.info("[{}][{}] call analytics profile, viewerId={}",
                traceId, ViewerConstants.SERVICE_NAME, viewerId);
        try {
            return CompletableFuture.supplyAsync(() -> fetchProfile(viewerId), asyncExecutor)
                    .orTimeout(PROFILE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .get();
        } catch (Exception exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            if (cause instanceof TimeoutException) {
                log.warn("[{}][{}] profile query timeout, viewerId={}",
                        traceId, ViewerConstants.SERVICE_NAME, viewerId);
            }
            return fallbackService.profileFallback(viewerId, cause);
        }
    }

    public ViewerProfileResponse getProfileFallback(String viewerId, Throwable cause) {
        return fallbackService.profileFallback(viewerId, cause);
    }

    @SentinelResource(value = "analyticsTopViewers", fallback = "getTopViewersFallback")
    public TopViewersFetchResult getTopViewers(String streamerId, Integer limit) {
        String traceId = TraceContext.getTraceId();
        log.info("[{}][{}] call analytics top viewers, streamerId={}, limit={}",
                traceId, ViewerConstants.SERVICE_NAME, streamerId, limit);
        List<TopViewerResponse> viewers = fetchTopViewers(streamerId, limit);
        return new TopViewersFetchResult(viewers, false, null);
    }

    public TopViewersFetchResult getTopViewersFallback(String streamerId, Integer limit, Throwable cause) {
        List<TopViewerResponse> viewers = fallbackService.topViewersFallback(streamerId, limit, cause);
        return new TopViewersFetchResult(viewers, true, ViewerConstants.TOP_VIEWERS_DEGRADED_HINT);
    }

    private ViewerProfileResponse fetchProfile(String viewerId) {
        ApiResponse<ViewerProfileResponse> response = analyticsClient.profile(viewerId);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new IllegalStateException("analytics profile response invalid");
        }
        return response.getData();
    }

    private List<TopViewerResponse> fetchTopViewers(String streamerId, Integer limit) {
        ApiResponse<List<TopViewerResponse>> response = analyticsClient.topViewers(streamerId, limit);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new IllegalStateException("analytics top viewers response invalid");
        }
        return response.getData();
    }
}
