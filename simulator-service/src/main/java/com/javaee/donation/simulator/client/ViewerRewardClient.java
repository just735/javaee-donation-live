package com.javaee.donation.simulator.client;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.model.RewardRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "viewer-service", url = "${clients.viewer-service}")
public interface ViewerRewardClient {

    @PostMapping("/api/viewers/reward")
    ApiResponse<String> reward(@RequestBody RewardRequest request);
}
