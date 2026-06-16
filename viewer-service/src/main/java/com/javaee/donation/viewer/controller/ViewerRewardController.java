package com.javaee.donation.viewer.controller;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.common.model.TopViewerResponse;
import com.javaee.donation.common.model.ViewerProfileResponse;
import com.javaee.donation.viewer.service.ViewerRewardService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/viewers")
public class ViewerRewardController {

    private final ViewerRewardService viewerRewardService;

    public ViewerRewardController(ViewerRewardService viewerRewardService) {
        this.viewerRewardService = viewerRewardService;
    }

    @PostMapping("/reward")
    public ApiResponse<String> reward(@RequestBody RewardRequest request) {
        return ApiResponse.success(TraceContext.getTraceId(), viewerRewardService.reward(request));
    }

    @GetMapping("/{viewerId}/profile")
    public ApiResponse<ViewerProfileResponse> profile(@PathVariable String viewerId) {
        return ApiResponse.success(TraceContext.getTraceId(), viewerRewardService.getProfile(viewerId));
    }

    @GetMapping("/streamers/{streamerId}/top-viewers")
    public ApiResponse<List<TopViewerResponse>> topViewers(@PathVariable String streamerId,
                                                           @RequestParam(defaultValue = "10") Integer limit) {
        return ApiResponse.success(TraceContext.getTraceId(), viewerRewardService.getTopViewers(streamerId, limit));
    }
}
