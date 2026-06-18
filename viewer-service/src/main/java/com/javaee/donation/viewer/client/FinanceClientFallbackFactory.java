package com.javaee.donation.viewer.client;

import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.viewer.dto.ViewerRewardResponse;
import com.javaee.donation.viewer.exception.ViewerBusinessException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class FinanceClientFallbackFactory implements FallbackFactory<FinanceClient> {

    @Override
    public FinanceClient create(Throwable cause) {
        return new FinanceClient() {
            @Override
            public com.javaee.donation.common.api.ApiResponse<ViewerRewardResponse> settle(RewardRequest request) {
                throw new ViewerBusinessException("FINANCE_UNAVAILABLE",
                        "财务服务暂时不可用，请稍后重试");
            }
        };
    }
}
