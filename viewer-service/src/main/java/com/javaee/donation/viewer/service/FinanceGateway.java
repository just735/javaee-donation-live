package com.javaee.donation.viewer.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.viewer.client.FinanceClient;
import com.javaee.donation.viewer.constant.ViewerConstants;
import com.javaee.donation.viewer.dto.ViewerRewardResponse;
import com.javaee.donation.viewer.exception.ViewerBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FinanceGateway {

    private static final Logger log = LoggerFactory.getLogger(FinanceGateway.class);

    private final FinanceClient financeClient;

    public FinanceGateway(FinanceClient financeClient) {
        this.financeClient = financeClient;
    }

    @SentinelResource(value = "financeSettle", fallback = "settleFallback", blockHandler = "settleBlockHandler")
    public ApiResponse<ViewerRewardResponse> settle(RewardRequest request) {
        String traceId = TraceContext.getTraceId();
        log.info("[{}][{}] call finance settle, rewardNo={}, viewerId={}, streamerId={}, amount={}",
                traceId, ViewerConstants.SERVICE_NAME,
                request.getRewardNo(), request.getViewerId(),
                request.getStreamerId(), request.getRewardAmount());
        return financeClient.settle(request);
    }

    public ApiResponse<ViewerRewardResponse> settleFallback(RewardRequest request, Throwable cause) {
        logFinanceFailure(request, "fallback", cause.getMessage());
        throw new ViewerBusinessException("FINANCE_UNAVAILABLE", "财务服务暂时不可用，请稍后重试");
    }

    public ApiResponse<ViewerRewardResponse> settleBlockHandler(RewardRequest request, BlockException cause) {
        logFinanceFailure(request, "blocked", cause.getMessage());
        throw new ViewerBusinessException("FINANCE_DEGRADED", "财务服务繁忙，请稍后重试");
    }

    private void logFinanceFailure(RewardRequest request, String stage, String reason) {
        String traceId = TraceContext.getTraceId();
        log.warn("[{}][{}] finance settle {}, rewardNo={}, viewerId={}, streamerId={}, amount={}, reason={}",
                traceId, ViewerConstants.SERVICE_NAME, stage,
                request.getRewardNo(), request.getViewerId(),
                request.getStreamerId(), request.getRewardAmount(), reason);
    }
}
