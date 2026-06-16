package com.javaee.donation.viewer.service;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.common.model.TopViewerResponse;
import com.javaee.donation.common.model.ViewerProfileResponse;
import com.javaee.donation.viewer.client.AnalyticsClient;
import com.javaee.donation.viewer.client.FinanceClient;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ViewerRewardService {

    private final FinanceClient financeClient;
    private final AnalyticsClient analyticsClient;

    public ViewerRewardService(FinanceClient financeClient, AnalyticsClient analyticsClient) {
        this.financeClient = financeClient;
        this.analyticsClient = analyticsClient;
    }

    public String reward(RewardRequest request) {
        ApiResponse<String> response = financeClient.settle(request);
        if (response == null || !response.isSuccess()) {
            return "reward accepted but finance pending";
        }
        return response.getData();
    }

    public ViewerProfileResponse getProfile(String viewerId) {
        ApiResponse<ViewerProfileResponse> response = analyticsClient.profile(viewerId);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            return new ViewerProfileResponse(viewerId, viewerId, "PENDING");
        }
        return response.getData();
    }

    public List<TopViewerResponse> getTopViewers(String streamerId, Integer limit) {
        ApiResponse<List<TopViewerResponse>> response = analyticsClient.topViewers(streamerId, limit);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            return Collections.emptyList();
        }
        return response.getData();
    }
}
