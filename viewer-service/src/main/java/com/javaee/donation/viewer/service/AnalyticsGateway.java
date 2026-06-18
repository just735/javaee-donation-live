package com.javaee.donation.viewer.service;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.TopViewerResponse;
import com.javaee.donation.common.model.ViewerProfileResponse;
import com.javaee.donation.viewer.client.AnalyticsClient;
import com.javaee.donation.viewer.constant.ViewerConstants;
import com.javaee.donation.viewer.dto.TopViewersFetchResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsGateway {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsGateway.class);

    private final AnalyticsClient analyticsClient;
    private final ViewerFallbackService fallbackService;
    private final CircuitBreaker profileCircuitBreaker;
    private final CircuitBreaker topViewersCircuitBreaker;
    private final TimeLimiter profileTimeLimiter;
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(4);

    public AnalyticsGateway(AnalyticsClient analyticsClient,
                            ViewerFallbackService fallbackService,
                            CircuitBreakerRegistry circuitBreakerRegistry,
                            TimeLimiterRegistry timeLimiterRegistry) {
        this.analyticsClient = analyticsClient;
        this.fallbackService = fallbackService;
        this.profileCircuitBreaker = circuitBreakerRegistry.circuitBreaker("analyticsProfile");
        this.topViewersCircuitBreaker = circuitBreakerRegistry.circuitBreaker("analyticsTopViewers");
        this.profileTimeLimiter = timeLimiterRegistry.timeLimiter("analyticsProfile");
    }

    public ViewerProfileResponse getProfile(String viewerId) {
        String traceId = TraceContext.getTraceId();
        log.info("[{}][{}] call analytics profile, viewerId={}",
                traceId, ViewerConstants.SERVICE_NAME, viewerId);
        try {
            CompletableFuture<ViewerProfileResponse> future = CompletableFuture.supplyAsync(() ->
                    profileCircuitBreaker.executeSupplier(() -> fetchProfile(viewerId)), asyncExecutor);
            return profileTimeLimiter.executeFutureSupplier(() -> future);
        } catch (CompletionException | TimeoutException exception) {
            Throwable cause = exception instanceof CompletionException
                    ? exception.getCause() : exception;
            return fallbackService.profileFallback(viewerId, cause != null ? cause : exception);
        } catch (Exception exception) {
            return fallbackService.profileFallback(viewerId, exception);
        }
    }

    public TopViewersFetchResult getTopViewers(String streamerId, Integer limit) {
        String traceId = TraceContext.getTraceId();
        log.info("[{}][{}] call analytics top viewers, streamerId={}, limit={}",
                traceId, ViewerConstants.SERVICE_NAME, streamerId, limit);
        try {
            List<TopViewerResponse> viewers = topViewersCircuitBreaker.executeSupplier(
                    () -> fetchTopViewers(streamerId, limit));
            return new TopViewersFetchResult(viewers, false, null);
        } catch (Exception exception) {
            List<TopViewerResponse> viewers = fallbackService.topViewersFallback(streamerId, limit, exception);
            return new TopViewersFetchResult(viewers, true, ViewerConstants.TOP_VIEWERS_DEGRADED_HINT);
        }
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
