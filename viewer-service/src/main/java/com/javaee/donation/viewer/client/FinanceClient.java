package com.javaee.donation.viewer.client;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.model.RewardRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "finance-service", url = "${clients.finance-service}")
public interface FinanceClient {

    @PostMapping("/api/finance/rewards/settle")
    ApiResponse<String> settle(@RequestBody RewardRequest request);
}
