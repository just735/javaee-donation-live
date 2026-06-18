package com.javaee.donation.viewer.service;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.viewer.dto.ViewerRewardResponse;
import com.javaee.donation.viewer.client.FinanceClient;
import com.javaee.donation.viewer.constant.ViewerConstants;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FinanceGateway {

    private static final Logger log = LoggerFactory.getLogger(FinanceGateway.class);

    private final FinanceClient financeClient;
    private final CircuitBreaker financeCircuitBreaker;

    public FinanceGateway(FinanceClient financeClient, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.financeClient = financeClient;
        this.financeCircuitBreaker = circuitBreakerRegistry.circuitBreaker("financeSettle");
    }

    public ApiResponse<ViewerRewardResponse> settle(RewardRequest request) {
        String traceId = TraceContext.getTraceId();
        log.info("[{}][{}] call finance settle, rewardNo={}, streamerId={}, amount={}",
                traceId, ViewerConstants.SERVICE_NAME,
                request.getRewardNo(), request.getStreamerId(), request.getRewardAmount());
        return financeCircuitBreaker.executeSupplier(() -> financeClient.settle(request));
    }
}
