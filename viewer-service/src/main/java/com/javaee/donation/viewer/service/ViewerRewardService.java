package com.javaee.donation.viewer.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.common.model.TopViewerResponse;
import com.javaee.donation.common.model.ViewerProfileResponse;
import com.javaee.donation.viewer.constant.ViewerConstants;
import com.javaee.donation.viewer.dto.ProfileQueryResult;
import com.javaee.donation.viewer.dto.TopViewersFetchResult;
import com.javaee.donation.viewer.dto.TopViewersQueryResult;
import com.javaee.donation.viewer.dto.ViewerRewardResponse;
import com.javaee.donation.viewer.exception.ViewerBusinessException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ViewerRewardService {

    private static final Logger log = LoggerFactory.getLogger(ViewerRewardService.class);

    private final FinanceGateway financeGateway;
    private final AnalyticsGateway analyticsGateway;
    private final TopViewerCacheService topViewerCacheService;
    private final RewardRequestValidator rewardRequestValidator;
    private final RewardNotificationService rewardNotificationService;

    public ViewerRewardService(FinanceGateway financeGateway,
                               AnalyticsGateway analyticsGateway,
                               TopViewerCacheService topViewerCacheService,
                               RewardRequestValidator rewardRequestValidator,
                               RewardNotificationService rewardNotificationService) {
        this.financeGateway = financeGateway;
        this.analyticsGateway = analyticsGateway;
        this.topViewerCacheService = topViewerCacheService;
        this.rewardRequestValidator = rewardRequestValidator;
        this.rewardNotificationService = rewardNotificationService;
    }

    @SentinelResource(value = "viewerReward", blockHandler = "rewardBlockHandler")
    public ViewerRewardResponse reward(RewardRequest request) {
        String traceId = TraceContext.getTraceId();
        rewardRequestValidator.validate(request);
        log.info("[{}][{}] reward request accepted, rewardNo={}, viewerId={}, streamerId={}, amount={}",
                traceId, ViewerConstants.SERVICE_NAME,
                request.getRewardNo(), request.getViewerId(),
                request.getStreamerId(), request.getRewardAmount());
        return settleReward(request);
    }

    public ViewerRewardResponse rewardBlockHandler(RewardRequest request, BlockException exception) {
        String traceId = TraceContext.getTraceId();
        log.warn("[{}][{}] reward rate limited, rewardNo={}, viewerId={}, streamerId={}, amount={}",
                traceId, ViewerConstants.SERVICE_NAME,
                request.getRewardNo(), request.getViewerId(),
                request.getStreamerId(), request.getRewardAmount());
        throw new ViewerBusinessException("RATE_LIMITED", "打赏请求过于频繁，请稍后再试");
    }

    private ViewerRewardResponse settleReward(RewardRequest request) {
        String traceId = TraceContext.getTraceId();
        ApiResponse<ViewerRewardResponse> response = financeGateway.settle(request);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response != null ? response.getMessage() : "finance service unavailable";
            log.error("[{}][{}] finance settle failed, api=/api/finance/rewards/settle, rewardNo={}, viewerId={}, streamerId={}, amount={}, message={}",
                    traceId, ViewerConstants.SERVICE_NAME,
                    request.getRewardNo(), request.getViewerId(),
                    request.getStreamerId(), request.getRewardAmount(), message);
            throw new ViewerBusinessException("FINANCE_ERROR", "打赏入账失败，请稍后重试");
        }

        ViewerRewardResponse rewardResponse = response.getData();
        rewardResponse.setMessage("打赏成功");
        log.info("[{}][{}] reward settled, rewardNo={}, viewerId={}, streamerId={}, amount={}, status={}",
                traceId, ViewerConstants.SERVICE_NAME,
                rewardResponse.getRewardNo(), request.getViewerId(),
                request.getStreamerId(), request.getRewardAmount(),
                rewardResponse.getSettleStatus());
        rewardNotificationService.notifyAsync(traceId, rewardResponse);
        return rewardResponse;
    }

    public ProfileQueryResult getProfile(String viewerId) {
        String traceId = TraceContext.getTraceId();
        if (viewerId == null || viewerId.isBlank()) {
            throw new ViewerBusinessException("INVALID_VIEWER_ID", "观众ID不能为空");
        }
        log.info("[{}][{}] profile query start, viewerId={}",
                traceId, ViewerConstants.SERVICE_NAME, viewerId);

        ViewerProfileResponse profile = analyticsGateway.getProfile(viewerId);
        boolean degraded = ViewerConstants.PROFILE_TAG_PENDING.equals(profile.getProfileTag());
        String hint = degraded ? ViewerConstants.PROFILE_DEGRADED_HINT : null;

        log.info("[{}][{}] profile query done, viewerId={}, tag={}, degraded={}",
                traceId, ViewerConstants.SERVICE_NAME, viewerId, profile.getProfileTag(), degraded);
        return new ProfileQueryResult(profile, degraded, hint);
    }

    public TopViewersQueryResult getTopViewers(String streamerId, Integer limit) {
        String traceId = TraceContext.getTraceId();
        if (streamerId == null || streamerId.isBlank()) {
            throw new ViewerBusinessException("INVALID_STREAMER_ID", "主播ID不能为空");
        }
        int queryLimit = limit == null || limit <= 0 ? 10 : Math.min(limit, 10);
        log.info("[{}][{}] top viewers query start, streamerId={}, limit={}",
                traceId, ViewerConstants.SERVICE_NAME, streamerId, queryLimit);

        List<TopViewerResponse> cached = topViewerCacheService.get(streamerId, queryLimit);
        if (cached != null) {
            log.info("[{}][{}] top viewers hit cache, streamerId={}, size={}",
                    traceId, ViewerConstants.SERVICE_NAME, streamerId, cached.size());
            return new TopViewersQueryResult(cached, false, null);
        }

        TopViewersFetchResult fetchResult = analyticsGateway.getTopViewers(streamerId, queryLimit);
        if (!fetchResult.isDegraded()) {
            topViewerCacheService.put(streamerId, queryLimit, fetchResult.getViewers());
        }

        log.info("[{}][{}] top viewers query done, streamerId={}, size={}, degraded={}",
                traceId, ViewerConstants.SERVICE_NAME, streamerId,
                fetchResult.getViewers().size(), fetchResult.isDegraded());
        return new TopViewersQueryResult(
                fetchResult.getViewers(), fetchResult.isDegraded(), fetchResult.getHintMessage());
    }
}
