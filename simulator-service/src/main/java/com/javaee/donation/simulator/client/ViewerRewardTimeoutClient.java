package com.javaee.donation.simulator.client;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.simulator.client.dto.ViewerRewardClientResponse;
import com.javaee.donation.simulator.config.FeignConfig;
import com.javaee.donation.simulator.config.SimulatorFeignTimeoutConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "viewer-service-timeout",
        url = "${clients.viewer-service}",
        configuration = {FeignConfig.class, SimulatorFeignTimeoutConfig.class})
public interface ViewerRewardTimeoutClient {

    @PostMapping("/api/viewers/reward")
    ApiResponse<ViewerRewardClientResponse> reward(@RequestBody RewardRequest request);
}
