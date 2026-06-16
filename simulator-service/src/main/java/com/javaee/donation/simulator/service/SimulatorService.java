package com.javaee.donation.simulator.service;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.common.model.SimulationRequest;
import com.javaee.donation.common.model.SimulationResult;
import com.javaee.donation.simulator.client.ViewerRewardClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SimulatorService {

    private final ViewerRewardClient viewerRewardClient;

    public SimulatorService(ViewerRewardClient viewerRewardClient) {
        this.viewerRewardClient = viewerRewardClient;
    }

    public SimulationResult start(SimulationRequest request) {
        int requestCount = request.getRequestCount() == null ? 10 : request.getRequestCount();
        int viewerCount = request.getViewerCount() == null ? 50 : request.getViewerCount();
        int streamerCount = request.getStreamerCount() == null ? 5 : request.getStreamerCount();
        long start = System.currentTimeMillis();
        int successCount = 0;
        int failedCount = 0;

        for (int index = 0; index < requestCount; index++) {
            RewardRequest rewardRequest = buildRewardRequest(index, viewerCount, streamerCount, request.getStreamerId());
            try {
                ApiResponse<String> response = viewerRewardClient.reward(rewardRequest);
                if (response != null && response.isSuccess()) {
                    successCount++;
                } else {
                    failedCount++;
                }
            } catch (Exception exception) {
                failedCount++;
            }
        }

        return new SimulationResult(requestCount, successCount, failedCount,
                System.currentTimeMillis() - start, "simulation finished");
    }

    public SimulationRequest defaultTemplate() {
        SimulationRequest request = new SimulationRequest();
        request.setRequestCount(20);
        request.setViewerCount(200);
        request.setStreamerCount(10);
        request.setQps(500);
        request.setStreamerId("streamer-1");
        return request;
    }

    private RewardRequest buildRewardRequest(int index, int viewerCount, int streamerCount, String fixedStreamerId) {
        RewardRequest request = new RewardRequest();
        String viewerId = "viewer-" + (index % Math.max(viewerCount, 1) + 1);
        String streamerId = fixedStreamerId == null || fixedStreamerId.isBlank()
                ? "streamer-" + (index % Math.max(streamerCount, 1) + 1)
                : fixedStreamerId;
        request.setRewardNo(UUID.randomUUID().toString());
        request.setViewerId(viewerId);
        request.setViewerName("观众" + viewerId);
        request.setViewerGender(index % 2 == 0 ? "MALE" : "FEMALE");
        request.setStreamerId(streamerId);
        request.setStreamerName("主播" + streamerId);
        request.setRewardAmount(new BigDecimal("10.00").add(BigDecimal.valueOf(index % 5)));
        request.setRewardTime(LocalDateTime.now().toString());
        return request;
    }
}
