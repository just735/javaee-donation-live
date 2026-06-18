package com.javaee.donation.viewer.service;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.viewer.dto.ProfileQueryResult;
import com.javaee.donation.viewer.dto.TopViewersFetchResult;
import com.javaee.donation.viewer.dto.TopViewersQueryResult;
import com.javaee.donation.viewer.dto.ViewerRewardResponse;
import com.javaee.donation.viewer.exception.ViewerBusinessException;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViewerRewardServiceTest {

    @Mock
    private FinanceGateway financeGateway;
    @Mock
    private AnalyticsGateway analyticsGateway;
    @Mock
    private TopViewerCacheService topViewerCacheService;
    @Mock
    private RewardNotificationService rewardNotificationService;

    private ViewerRewardService viewerRewardService;

    @BeforeEach
    void setUp() {
        viewerRewardService = new ViewerRewardService(
                financeGateway, analyticsGateway, topViewerCacheService,
                new RewardRequestValidator(), rewardNotificationService);
    }

    @Test
    void rewardShouldFailWhenValidationFails() {
        RewardRequest request = new RewardRequest();

        ViewerBusinessException exception = assertThrows(ViewerBusinessException.class,
                () -> viewerRewardService.reward(request));
        assertEquals("INVALID_REWARD_NO", exception.getCode());
    }

    @Test
    void rewardShouldReturnSettleResult() {
        RewardRequest request = validRequest();
        ViewerRewardResponse settleData = ViewerRewardResponse.builder()
                .rewardNo("r-001")
                .settleStatus("SETTLED")
                .build();
        when(financeGateway.settle(request)).thenReturn(ApiResponse.success("trace", settleData));

        ViewerRewardResponse response = viewerRewardService.reward(request);

        assertEquals("SETTLED", response.getSettleStatus());
        assertEquals("打赏成功", response.getMessage());
        verify(rewardNotificationService).notifyAsync(any(), eq(response));
    }

    @Test
    void profileShouldReturnDegradedHintWhenPending() {
        when(analyticsGateway.getProfile("v-1"))
                .thenReturn(new com.javaee.donation.common.model.ViewerProfileResponse("v-1", "v-1", "PENDING"));

        ProfileQueryResult result = viewerRewardService.getProfile("v-1");

        assertTrue(result.isDegraded());
        assertEquals("画像正在计算中或服务暂时不可用，请稍后再试", result.getHintMessage());
    }

    @Test
    void topViewersShouldReturnDegradedResult() {
        when(topViewerCacheService.get("s-1", 10)).thenReturn(null);
        when(analyticsGateway.getTopViewers("s-1", 10))
                .thenReturn(new TopViewersFetchResult(Collections.emptyList(), true, "Top10 观众数据暂时不可用，请稍后再试"));

        TopViewersQueryResult result = viewerRewardService.getTopViewers("s-1", 10);

        assertTrue(result.isDegraded());
        assertTrue(result.getViewers().isEmpty());
    }

    private RewardRequest validRequest() {
        RewardRequest request = new RewardRequest();
        request.setRewardNo("r-001");
        request.setViewerId("v-1");
        request.setStreamerId("s-1");
        request.setRewardAmount(new BigDecimal("10.00"));
        return request;
    }
}
