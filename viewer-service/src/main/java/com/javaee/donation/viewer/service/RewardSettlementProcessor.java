package com.javaee.donation.viewer.service;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.viewer.constant.ViewerConstants;
import com.javaee.donation.viewer.dto.ViewerRewardResponse;
import com.javaee.donation.viewer.entity.RewardIngestTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RewardSettlementProcessor {

    private static final Logger log = LoggerFactory.getLogger(RewardSettlementProcessor.class);

    private final RewardTaskService rewardTaskService;
    private final FinanceGateway financeGateway;
    private final RewardNotificationService rewardNotificationService;

    public RewardSettlementProcessor(RewardTaskService rewardTaskService,
                                     FinanceGateway financeGateway,
                                     RewardNotificationService rewardNotificationService) {
        this.rewardTaskService = rewardTaskService;
        this.financeGateway = financeGateway;
        this.rewardNotificationService = rewardNotificationService;
    }

    public void process(String rewardNo) {
        RewardIngestTask task = rewardTaskService.loadProcessableTask(rewardNo);
        if (task == null) {
            return;
        }
        if (ViewerConstants.TASK_STATUS_SETTLED.equals(task.getTaskStatus())
                || ViewerConstants.TASK_STATUS_DUPLICATE.equals(task.getTaskStatus())) {
            return;
        }
        if (!rewardTaskService.markProcessing(task.getId())) {
            return;
        }

        TraceContext.setTraceId(task.getTraceId());
        try {
            RewardRequest request = toRequest(task);
            doSettle(task.getTraceId(), task.getRewardNo(), request);
        } catch (Exception exception) {
            rewardTaskService.markRetry(task.getId(), exception.getMessage());
            log.error("[{}][{}] settlement error, rewardNo={}, error={}",
                    task.getTraceId(), ViewerConstants.SERVICE_NAME, task.getRewardNo(), exception.getMessage(), exception);
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 直接入账（不经过任务表，用于高性能场景）
     */
    public void processDirect(RewardRequest request) {
        String traceId = TraceContext.getTraceId();
        try {
            doSettle(traceId, request.getRewardNo(), request);
        } catch (Exception e) {
            log.error("[{}][{}] direct settle failed, rewardNo={}", traceId, ViewerConstants.SERVICE_NAME, request.getRewardNo(), e);
        }
    }

    private void doSettle(String traceId, String rewardNo, RewardRequest request) {
        ApiResponse<ViewerRewardResponse> response = financeGateway.settle(request);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String message = response != null ? response.getMessage() : "finance unavailable";
            log.warn("[{}][{}] settlement deferred, rewardNo={}, message={}",
                    traceId, ViewerConstants.SERVICE_NAME, rewardNo, message);
            return;
        }
        ViewerRewardResponse settled = response.getData();
        settled.setMessage("打赏成功");
        rewardNotificationService.notifyAsync(traceId, settled);
        log.info("[{}][{}] settlement completed, rewardNo={}, status={}",
                traceId, ViewerConstants.SERVICE_NAME, rewardNo, settled.getSettleStatus());
    }

    private RewardRequest toRequest(RewardIngestTask task) {
        RewardRequest request = new RewardRequest();
        request.setRewardNo(task.getRewardNo());
        request.setViewerId(task.getViewerId());
        request.setViewerName(task.getViewerName());
        request.setViewerGender(task.getViewerGender());
        request.setStreamerId(task.getStreamerId());
        request.setStreamerName(task.getStreamerName());
        request.setRewardAmount(task.getRewardAmount());
        request.setRewardTime(task.getRewardTime());
        return request;
    }
}
