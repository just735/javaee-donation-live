package com.javaee.donation.viewer.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.common.model.TopViewerResponse;
import com.javaee.donation.common.model.ViewerProfileResponse;
import com.javaee.donation.viewer.constant.ViewerConstants;
import com.javaee.donation.viewer.dto.ProfileQueryResult;
import com.javaee.donation.viewer.dto.TopViewersFetchResult;
import com.javaee.donation.viewer.dto.TopViewersQueryResult;
import com.javaee.donation.viewer.dto.ViewerRewardResponse;
import com.javaee.donation.viewer.entity.RewardIngestTask;
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

    private final AnalyticsGateway analyticsGateway;
    private final TopViewerCacheService topViewerCacheService;
    private final RewardRequestValidator rewardRequestValidator;
    private final RewardTaskService rewardTaskService;
    private final RewardSettlementProcessor rewardSettlementProcessor;
    private final Executor settlementExecutor;

    public ViewerRewardService(AnalyticsGateway analyticsGateway,
                               TopViewerCacheService topViewerCacheService,
                               RewardRequestValidator rewardRequestValidator,
                               RewardTaskService rewardTaskService,
                               RewardSettlementProcessor rewardSettlementProcessor,
                               @Qualifier("settlementExecutor") Executor settlementExecutor) {
        this.analyticsGateway = analyticsGateway;
        this.topViewerCacheService = topViewerCacheService;
        this.rewardRequestValidator = rewardRequestValidator;
        this.rewardTaskService = rewardTaskService;
        this.rewardSettlementProcessor = rewardSettlementProcessor;
        this.settlementExecutor = settlementExecutor;
    }

    @SentinelResource(value = "viewerReward", blockHandler = "rewardBlockHandler")
    public ViewerRewardResponse reward(RewardRequest request) {
        String traceId = TraceContext.getTraceId();
        rewardRequestValidator.validate(request);
        log.info("[{}][{}] reward request accepted, rewardNo={}, viewerId={}, streamerId={}, amount={}",
                traceId, ViewerConstants.SERVICE_NAME,
                request.getRewardNo(), request.getViewerId(),
                request.getStreamerId(), request.getRewardAmount());

        RewardIngestTask task = rewardTaskService.createTask(request);
        if (isTerminal(task)) {
            return ViewerRewardResponse.builder()
                    .rewardNo(task.getRewardNo())
                    .settleStatus("DUPLICATE")
                    .streamerId(task.getStreamerId())
                    .rewardAmount(task.getRewardAmount())
                    .message("打赏请求已处理，请勿重复提交")
                    .build();
        }

        submitSettlement(task.getRewardNo());
        return ViewerRewardResponse.builder()
                .rewardNo(task.getRewardNo())
                .settleStatus("ACCEPTED")
                .streamerId(task.getStreamerId())
                .rewardAmount(task.getRewardAmount())
                .message("打赏请求已接收，正在处理中")
                .build();
    }

    public void submitSettlement(String rewardNo) {
        RewardIngestTask task = rewardTaskService.getByRewardNo(rewardNo);
        if (task == null || isTerminal(task)) {
            return;
        }
        try {
            settlementExecutor.execute(() -> rewardSettlementProcessor.process(rewardNo));
        } catch (RuntimeException exception) {
            rewardTaskService.markRetry(task.getId(), exception.getMessage());
            log.error("[{}][{}] settlement submit failed, rewardNo={}, error={}",
                    task.getTraceId(), ViewerConstants.SERVICE_NAME,
                    rewardNo, exception.getMessage(), exception);
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

    private boolean isTerminal(RewardIngestTask task) {
        return ViewerConstants.TASK_STATUS_SETTLED.equals(task.getTaskStatus())
                || ViewerConstants.TASK_STATUS_DUPLICATE.equals(task.getTaskStatus());
    }
}
