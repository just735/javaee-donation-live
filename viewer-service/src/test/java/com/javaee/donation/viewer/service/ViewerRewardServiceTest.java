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
import java.util.concurrent.Executor;
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
    @Mock
    private Executor settlementExecutor;

    private ViewerRewardService viewerRewardService;

    @BeforeEach
    void setUp() {
        viewerRewardService = new ViewerRewardService(
                financeGateway, analyticsGateway, topViewerCacheService,
                new RewardRequestValidator(), rewardNotificationService, settlementExecutor);
    }

    @Test
    void rewardShouldFailWhenValidationFails() {
        RewardRequest request = new RewardRequest();

        ViewerBusinessException exception = assertThrows(ViewerBusinessException.class,
                () -> viewerRewardService.reward(request));
        assertEquals("INVALID_REWARD_NO", exception.getCode());
    }

    /** 异步模式：reward() 立即返回 ACCEPTED，不等待财务服务 */
    @Test
    void rewardShouldReturnAcceptedImmediately() {
        RewardRequest request = validRequest();

        ViewerRewardResponse response = viewerRewardService.reward(request);

        assertEquals("ACCEPTED", response.getSettleStatus());
        assertEquals("打赏请求已接收，正在处理中", response.getMessage());
        // 验证异步入账被提交到线程池
        verify(settlementExecutor).execute(any(Runnable.class));
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
