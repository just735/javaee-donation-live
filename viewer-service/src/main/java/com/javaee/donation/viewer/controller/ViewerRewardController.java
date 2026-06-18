package com.javaee.donation.viewer.controller;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.viewer.dto.ViewerRewardResponse;
import com.javaee.donation.common.model.TopViewerResponse;
import com.javaee.donation.common.model.ViewerProfileResponse;
import com.javaee.donation.viewer.dto.ProfileQueryResult;
import com.javaee.donation.viewer.dto.TopViewersQueryResult;
import com.javaee.donation.viewer.service.ViewerRewardService;
import com.javaee.donation.viewer.support.ViewerApiResponses;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 观众服务 HTTP 接口（端口 8081）。
 *
 * <p>统一响应 {@link ApiResponse}，含 traceId。详细字段说明见 {@code viewer-service/README.md}。
 *
 * <p>入参 DTO（donation-common）：
 * <ul>
 *   <li>{@link RewardRequest} — 打赏入参</li>
 *   <li>{@link ViewerProfileResponse} — 画像返回</li>
 *   <li>{@link TopViewerResponse} — TOP10 单条记录</li>
 * </ul>
 * 打赏返回使用本模块 {@link ViewerRewardResponse}。
 */
@RestController
@RequestMapping("/api/viewers")
public class ViewerRewardController {

    private final ViewerRewardService viewerRewardService;

    public ViewerRewardController(ViewerRewardService viewerRewardService) {
        this.viewerRewardService = viewerRewardService;
    }

    /**
     * 观众发起打赏。
     *
     * <p>POST /api/viewers/reward
     *
     * <p>入参 {@link RewardRequest}：rewardNo（幂等）、viewerId、streamerId、rewardAmount（&gt;0）必填；
     * viewerName、viewerGender、streamerName、rewardTime 可选。
     *
     * <p>返回 {@link ViewerRewardResponse}：settleStatus、commissionRate、withdrawableAmount 等；
     * 经财务服务入账，幂等由 rewardNo 保证。
     */
    @PostMapping("/reward")
    public ApiResponse<ViewerRewardResponse> reward(@RequestBody RewardRequest request) {
        return ApiResponse.success(TraceContext.getTraceId(), viewerRewardService.reward(request));
    }

    /**
     * 查询观众消费画像。
     *
     * <p>GET /api/viewers/{viewerId}/profile
     *
     * <p>入参：viewerId（路径参数）。
     *
     * <p>返回 {@link ViewerProfileResponse}：profileTag 为 HIGH / MEDIUM / LOW；
     * 降级时 code=PROFILE_DEGRADED，profileTag=PENDING，message 为友好提示（2 秒超时或下游异常）。
     */
    @GetMapping("/{viewerId}/profile")
    public ApiResponse<ViewerProfileResponse> profile(@PathVariable String viewerId) {
        ProfileQueryResult result = viewerRewardService.getProfile(viewerId);
        String traceId = TraceContext.getTraceId();
        if (result.isDegraded()) {
            return ViewerApiResponses.success(traceId, "PROFILE_DEGRADED", result.getHintMessage(), result.getProfile());
        }
        return ApiResponse.success(traceId, result.getProfile());
    }

    /**
     * 查询某主播打赏金额 TOP 观众。
     *
     * <p>GET /api/viewers/streamers/{streamerId}/top-viewers
     *
     * <p>入参：streamerId（路径）；limit（Query，默认 10，最大 10）。
     *
     * <p>返回 {@link TopViewerResponse} 列表：viewerName、totalRewardAmount、rewardCount；
     * 数据来自经营分析服务预汇总；降级时 code=TOP_VIEWERS_DEGRADED，返回空列表。
     */
    @GetMapping("/streamers/{streamerId}/top-viewers")
    public ApiResponse<List<TopViewerResponse>> topViewers(@PathVariable String streamerId,
                                                           @RequestParam(defaultValue = "10") Integer limit) {
        TopViewersQueryResult result = viewerRewardService.getTopViewers(streamerId, limit);
        String traceId = TraceContext.getTraceId();
        if (result.isDegraded()) {
            return ViewerApiResponses.success(traceId, "TOP_VIEWERS_DEGRADED",
                    result.getHintMessage(), result.getViewers());
        }
        return ApiResponse.success(traceId, result.getViewers());
    }
}
