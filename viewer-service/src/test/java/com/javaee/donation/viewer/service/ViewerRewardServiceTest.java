package com.javaee.donation.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.viewer.constant.ViewerConstants;
import com.javaee.donation.viewer.dto.ProfileQueryResult;
import com.javaee.donation.viewer.dto.TopViewersFetchResult;
import com.javaee.donation.viewer.dto.TopViewersQueryResult;
import com.javaee.donation.viewer.dto.ViewerRewardResponse;
import com.javaee.donation.viewer.entity.RewardIngestTask;
import com.javaee.donation.viewer.exception.ViewerBusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ViewerRewardServiceTest {

    @Mock
    private AnalyticsGateway analyticsGateway;
    @Mock
    private TopViewerCacheService topViewerCacheService;
    @Mock
    private RewardTaskService rewardTaskService;
    @Mock
    private RewardSettlementProcessor rewardSettlementProcessor;
    @Mock
    private Executor settlementExecutor;

    private ViewerRewardService viewerRewardService;

    @BeforeEach
    void setUp() {
        viewerRewardService = new ViewerRewardService(
                analyticsGateway,
                topViewerCacheService,
                new RewardRequestValidator(),
                rewardTaskService,
                rewardSettlementProcessor,
                settlementExecutor);
    }

    @Test
    void rewardShouldFailWhenValidationFails() {
        RewardRequest request = new RewardRequest();

        ViewerBusinessException exception = assertThrows(ViewerBusinessException.class,
                () -> viewerRewardService.reward(request));
        assertEquals("INVALID_REWARD_NO", exception.getCode());
    }

    @Test
    void rewardShouldPersistThenReturnAccepted() {
        RewardRequest request = validRequest();
        RewardIngestTask task = pendingTask();
        when(rewardTaskService.createTask(request)).thenReturn(task);
        when(rewardTaskService.getByRewardNo("r-001")).thenReturn(task);

        ViewerRewardResponse response = viewerRewardService.reward(request);

        assertEquals("ACCEPTED", response.getSettleStatus());
        assertEquals("打赏请求已接收，正在处理中", response.getMessage());
        verify(rewardTaskService).createTask(request);
        verify(settlementExecutor).execute(any(Runnable.class));
    }

    @Test
    void rewardShouldReturnDuplicateWhenTaskAlreadyTerminal() {
        RewardRequest request = validRequest();
        RewardIngestTask task = pendingTask();
        task.setTaskStatus(ViewerConstants.TASK_STATUS_DUPLICATE);
        when(rewardTaskService.createTask(request)).thenReturn(task);

        ViewerRewardResponse response = viewerRewardService.reward(request);

        assertEquals("DUPLICATE", response.getSettleStatus());
        assertEquals("打赏请求已处理，请勿重复提交", response.getMessage());
    }

    @Test
    void submitSettlementShouldMarkRetryWhenExecutorRejected() {
        RewardIngestTask task = pendingTask();
        when(rewardTaskService.getByRewardNo("r-001")).thenReturn(task);
        doThrow(new RuntimeException("queue full")).when(settlementExecutor).execute(any(Runnable.class));

        viewerRewardService.submitSettlement("r-001");

        verify(rewardTaskService).markRetry(eq(1L), eq("queue full"));
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

    private RewardIngestTask pendingTask() {
        RewardIngestTask task = new RewardIngestTask();
        task.setId(1L);
        task.setRewardNo("r-001");
        task.setStreamerId("s-1");
        task.setRewardAmount(new BigDecimal("10.00"));
        task.setTaskStatus(ViewerConstants.TASK_STATUS_PENDING);
        task.setCreatedAt(LocalDateTime.now());
        return task;
    }
}
