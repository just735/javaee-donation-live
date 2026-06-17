package com.javaee.donation.viewer.service;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.TopViewerResponse;
import com.javaee.donation.common.model.ViewerProfileResponse;
import com.javaee.donation.viewer.constant.ViewerConstants;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ViewerFallbackService {

    private static final Logger log = LoggerFactory.getLogger(ViewerFallbackService.class);

    public ViewerProfileResponse profileFallback(String viewerId, Throwable cause) {
        String traceId = TraceContext.getTraceId();
        log.warn("[{}][{}] profile query degraded, viewerId={}, reason={}",
                traceId, ViewerConstants.SERVICE_NAME, viewerId, cause.getMessage());
        return new ViewerProfileResponse(viewerId, viewerId, ViewerConstants.PROFILE_TAG_PENDING);
    }

    public ApiResponse<ViewerProfileResponse> profileApiFallback(String viewerId, Throwable cause) {
        String traceId = TraceContext.getTraceId();
        log.warn("[{}][{}] profile feign fallback, viewerId={}, reason={}",
                traceId, ViewerConstants.SERVICE_NAME, viewerId, cause.getMessage());
        return ApiResponse.fail(traceId, "PROFILE_DEGRADED", ViewerConstants.PROFILE_DEGRADED_HINT);
    }

    public List<TopViewerResponse> topViewersFallback(String streamerId, Integer limit, Throwable cause) {
        String traceId = TraceContext.getTraceId();
        log.warn("[{}][{}] top viewers query degraded, streamerId={}, reason={}",
                traceId, ViewerConstants.SERVICE_NAME, streamerId, cause.getMessage());
        return Collections.emptyList();
    }

    public ApiResponse<List<TopViewerResponse>> topViewersApiFallback(String streamerId, Integer limit,
                                                                      Throwable cause) {
        String traceId = TraceContext.getTraceId();
        log.warn("[{}][{}] top viewers feign fallback, streamerId={}, reason={}",
                traceId, ViewerConstants.SERVICE_NAME, streamerId, cause.getMessage());
        return ApiResponse.fail(traceId, "TOP_VIEWERS_DEGRADED", ViewerConstants.TOP_VIEWERS_DEGRADED_HINT);
    }
}
