package com.javaee.donation.viewer.client;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.viewer.dto.ViewerRewardResponse;
import com.javaee.donation.viewer.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "finance-service", url = "${clients.finance-service}",
        configuration = FeignConfig.class, fallbackFactory = FinanceClientFallbackFactory.class)
public interface FinanceClient {

    @PostMapping("/api/finance/rewards/settle")
    ApiResponse<ViewerRewardResponse> settle(@RequestBody RewardRequest request);
}
