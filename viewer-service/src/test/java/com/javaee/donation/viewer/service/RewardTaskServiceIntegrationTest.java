package com.javaee.donation.viewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.viewer.constant.ViewerConstants;
import com.javaee.donation.viewer.entity.RewardIngestTask;
import com.javaee.donation.viewer.mapper.RewardIngestTaskMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.cloud.consul.enabled=false")
class RewardTaskServiceIntegrationTest {

    @Autowired
    private RewardTaskService rewardTaskService;

    @Autowired
    private RewardIngestTaskMapper rewardIngestTaskMapper;

    @BeforeEach
    void setUp() {
        TraceContext.setTraceId("viewer-test-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void createTaskShouldPersistPendingTask() {
        RewardIngestTask task = rewardTaskService.createTask(buildReward("reward-task-1"));

        assertNotNull(task.getId());
        RewardIngestTask persisted = rewardTaskService.getByRewardNo("reward-task-1");
        assertEquals(ViewerConstants.TASK_STATUS_PENDING, persisted.getTaskStatus());
        assertEquals("viewer-1", persisted.getViewerId());
        assertNotNull(persisted.getNextRetryAt());
    }

    @Test
    void markRetryThenSettledShouldWork() {
        RewardIngestTask task = rewardTaskService.createTask(buildReward("reward-task-2"));

        assertTrue(rewardTaskService.markProcessing(task.getId()));
        rewardTaskService.markRetry(task.getId(), "finance down");

        RewardIngestTask retried = rewardTaskService.getByRewardNo("reward-task-2");
        assertEquals(ViewerConstants.TASK_STATUS_RETRY, retried.getTaskStatus());
        assertEquals(1, retried.getRetryCount());
        assertNotNull(retried.getNextRetryAt());

        rewardTaskService.markSettled(task.getId(), ViewerConstants.TASK_STATUS_SETTLED);
        RewardIngestTask settled = rewardTaskService.getByRewardNo("reward-task-2");
        assertEquals(ViewerConstants.TASK_STATUS_SETTLED, settled.getTaskStatus());
    }

    @Test
    void expiredProcessingTaskShouldBeRecoverable() {
        RewardIngestTask task = rewardTaskService.createTask(buildReward("reward-task-3"));
        assertTrue(rewardTaskService.markProcessing(task.getId()));

        RewardIngestTask loaded = rewardTaskService.getByRewardNo("reward-task-3");
        loaded.setTaskStatus(ViewerConstants.TASK_STATUS_PROCESSING);
        loaded.setProcessingDeadline(LocalDateTime.now().minusSeconds(1));
        rewardIngestTaskMapper.updateById(loaded);

        List<RewardIngestTask> recoverable = rewardTaskService.listRecoverableTasks();
        assertFalse(recoverable.isEmpty());
        assertEquals("reward-task-3", recoverable.get(0).getRewardNo());
        assertTrue(rewardTaskService.markProcessing(loaded.getId()));
    }

    private RewardRequest buildReward(String rewardNo) {
        RewardRequest request = new RewardRequest();
        request.setRewardNo(rewardNo);
        request.setViewerId("viewer-1");
        request.setViewerName("观众1");
        request.setViewerGender("MALE");
        request.setStreamerId("streamer-1");
        request.setStreamerName("主播1");
        request.setRewardAmount(new BigDecimal("10.00"));
        request.setRewardTime("2026-06-22T12:00:00");
        return request;
    }
}
