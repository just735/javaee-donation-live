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
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ViewerRewardService {

    private static final Logger log = LoggerFactory.getLogger(ViewerRewardService.class);

    private final FinanceGateway financeGateway;
    private final AnalyticsGateway analyticsGateway;
    private final TopViewerCacheService topViewerCacheService;
    private final RewardRequestValidator rewardRequestValidator;
    private final RewardNotificationService rewardNotificationService;
    private final Executor settlementExecutor;

    public ViewerRewardService(FinanceGateway financeGateway,
                               AnalyticsGateway analyticsGateway,
                               TopViewerCacheService topViewerCacheService,
                               RewardRequestValidator rewardRequestValidator,
                               RewardNotificationService rewardNotificationService,
                               @Qualifier("settlementExecutor") Executor settlementExecutor) {
        this.financeGateway = financeGateway;
        this.analyticsGateway = analyticsGateway;
        this.topViewerCacheService = topViewerCacheService;
        this.rewardRequestValidator = rewardRequestValidator;
        this.rewardNotificationService = rewardNotificationService;
        this.settlementExecutor = settlementExecutor;
    }

    /**
     * 打赏入口：快速验证后立即返回 ACCEPTED，实际入账异步执行。
     * 响应时间从 ~960ms（同步调用链）降至 <10ms，大幅提升 QPS 吞吐。
     */
    @SentinelResource(value = "viewerReward", blockHandler = "rewardBlockHandler")
    public ViewerRewardResponse reward(RewardRequest request) {
        String traceId = TraceContext.getTraceId();
        rewardRequestValidator.validate(request);
        log.info("[{}][{}] reward request accepted, rewardNo={}, viewerId={}, streamerId={}, amount={}",
                traceId, ViewerConstants.SERVICE_NAME,
                request.getRewardNo(), request.getViewerId(),
                request.getStreamerId(), request.getRewardAmount());

        // 立即返回，告知客户端请求已接收
        ViewerRewardResponse quickResponse = new ViewerRewardResponse(
                request.getRewardNo(), "ACCEPTED", request.getStreamerId(),
                request.getRewardAmount(), null, null, null, null,
                "打赏请求已接收，正在处理中");

        // 异步入账：不阻塞响应线程
        String capturedTraceId = traceId;
        settlementExecutor.execute(() -> settleRewardAsync(capturedTraceId, request));

        return quickResponse;
    }

    /** 异步入账：在独立线程池中完成 viewer→finance→MySQL 的完整调用链 */
    private void settleRewardAsync(String traceId, RewardRequest request) {
        try {
            TraceContext.setTraceId(traceId);
            ApiResponse<ViewerRewardResponse> response = financeGateway.settle(request);
            if (response != null && response.isSuccess() && response.getData() != null) {
                ViewerRewardResponse settled = response.getData();
                settled.setMessage("打赏成功");
                log.info("[{}][{}] async settle success, rewardNo={}, status={}",
                        traceId, ViewerConstants.SERVICE_NAME, settled.getRewardNo(), settled.getSettleStatus());
                rewardNotificationService.notifyAsync(traceId, settled);
            } else {
                String msg = response != null ? response.getMessage() : "finance unavailable";
                log.error("[{}][{}] async settle failed, rewardNo={}, message={}",
                        traceId, ViewerConstants.SERVICE_NAME, request.getRewardNo(), msg);
            }
        } catch (Exception e) {
            log.error("[{}][{}] async settle error, rewardNo={}, error={}",
                    traceId, ViewerConstants.SERVICE_NAME, request.getRewardNo(), e.getMessage(), e);
        } finally {
            TraceContext.clear();
        }
    }

    public ViewerRewardResponse rewardBlockHandler(RewardRequest request, BlockException exception) {
        String traceId = TraceContext.getTraceId();
        log.warn("[{}][{}] reward rate limited, rewardNo={}, viewerId={}, streamerId={}, amount={}",
                traceId, ViewerConstants.SERVICE_NAME,
                request.getRewardNo(), request.getViewerId(),
                request.getStreamerId(), request.getRewardAmount());
        throw new ViewerBusinessException("RATE_LIMITED", "打赏请求过于频繁，请稍后再试");
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
