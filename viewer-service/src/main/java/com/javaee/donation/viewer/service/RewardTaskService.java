package com.javaee.donation.viewer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.viewer.config.ViewerRewardProperties;
import com.javaee.donation.viewer.constant.ViewerConstants;
import com.javaee.donation.viewer.entity.RewardIngestTask;
import com.javaee.donation.viewer.exception.ViewerBusinessException;
import com.javaee.donation.viewer.mapper.RewardIngestTaskMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RewardTaskService {

    private static final Logger log = LoggerFactory.getLogger(RewardTaskService.class);

    private final RewardIngestTaskMapper rewardIngestTaskMapper;
    private final ViewerRewardProperties properties;

    public RewardTaskService(RewardIngestTaskMapper rewardIngestTaskMapper,
                             ViewerRewardProperties properties) {
        this.rewardIngestTaskMapper = rewardIngestTaskMapper;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public RewardIngestTask createTask(RewardRequest request) {
        LocalDateTime now = LocalDateTime.now();
        RewardIngestTask task = new RewardIngestTask();
        task.setRewardNo(request.getRewardNo());
        task.setTraceId(TraceContext.getTraceId());
        task.setViewerId(request.getViewerId());
        task.setViewerName(request.getViewerName());
        task.setViewerGender(request.getViewerGender());
        task.setStreamerId(request.getStreamerId());
        task.setStreamerName(request.getStreamerName());
        task.setRewardAmount(request.getRewardAmount());
        task.setRewardTime(request.getRewardTime());
        task.setTaskStatus(ViewerConstants.TASK_STATUS_PENDING);
        task.setRetryCount(0);
        task.setNextRetryAt(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        try {
            rewardIngestTaskMapper.insert(task);
            return task;
        } catch (DuplicateKeyException exception) {
            RewardIngestTask existing = getByRewardNo(request.getRewardNo());
            if (existing != null) {
                return existing;
            }
            throw new ViewerBusinessException("DUPLICATE_REWARD", "打赏请求重复，请勿重复提交");
        }
    }

    public RewardIngestTask getByRewardNo(String rewardNo) {
        return rewardIngestTaskMapper.selectOne(new LambdaQueryWrapper<RewardIngestTask>()
                .eq(RewardIngestTask::getRewardNo, rewardNo)
                .last("LIMIT 1"));
    }

    public RewardIngestTask loadProcessableTask(String rewardNo) {
        return rewardIngestTaskMapper.selectOne(new LambdaQueryWrapper<RewardIngestTask>()
                .eq(RewardIngestTask::getRewardNo, rewardNo)
                .last("LIMIT 1"));
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markProcessing(Long taskId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusSeconds(properties.getSettlement().getProcessingLeaseSeconds());
        LambdaUpdateWrapper<RewardIngestTask> update = new LambdaUpdateWrapper<>();
        update.eq(RewardIngestTask::getId, taskId)
                .and(wrapper -> wrapper
                        .eq(RewardIngestTask::getTaskStatus, ViewerConstants.TASK_STATUS_PENDING)
                        .or()
                        .eq(RewardIngestTask::getTaskStatus, ViewerConstants.TASK_STATUS_RETRY)
                        .or(processing -> processing.eq(RewardIngestTask::getTaskStatus, ViewerConstants.TASK_STATUS_PROCESSING)
                                .le(RewardIngestTask::getProcessingDeadline, now)))
                .set(RewardIngestTask::getTaskStatus, ViewerConstants.TASK_STATUS_PROCESSING)
                .set(RewardIngestTask::getProcessingDeadline, deadline)
                .set(RewardIngestTask::getUpdatedAt, now);
        return rewardIngestTaskMapper.update(null, update) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markSettled(Long taskId, String taskStatus) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<RewardIngestTask> update = new LambdaUpdateWrapper<>();
        update.eq(RewardIngestTask::getId, taskId)
                .set(RewardIngestTask::getTaskStatus, taskStatus)
                .set(RewardIngestTask::getSettledAt, now)
                .set(RewardIngestTask::getLastError, null)
                .set(RewardIngestTask::getProcessingDeadline, null)
                .set(RewardIngestTask::getUpdatedAt, now);
        rewardIngestTaskMapper.update(null, update);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRetry(Long taskId, String errorMessage) {
        RewardIngestTask current = rewardIngestTaskMapper.selectById(taskId);
        LocalDateTime now = LocalDateTime.now();
        int nextRetryCount = current == null || current.getRetryCount() == null ? 1 : current.getRetryCount() + 1;
        LambdaUpdateWrapper<RewardIngestTask> update = new LambdaUpdateWrapper<>();
        update.eq(RewardIngestTask::getId, taskId)
                .set(RewardIngestTask::getTaskStatus, ViewerConstants.TASK_STATUS_RETRY)
                .set(RewardIngestTask::getRetryCount, nextRetryCount)
                .set(RewardIngestTask::getLastError, truncate(errorMessage))
                .set(RewardIngestTask::getNextRetryAt, now.plusSeconds(properties.getSettlement().getRetryDelaySeconds()))
                .set(RewardIngestTask::getProcessingDeadline, null)
                .set(RewardIngestTask::getUpdatedAt, now);
        rewardIngestTaskMapper.update(null, update);
    }

    public List<RewardIngestTask> listRecoverableTasks() {
        LocalDateTime now = LocalDateTime.now();
        return rewardIngestTaskMapper.selectList(new LambdaQueryWrapper<RewardIngestTask>()
                .and(wrapper -> wrapper
                        .and(pending -> pending.eq(RewardIngestTask::getTaskStatus, ViewerConstants.TASK_STATUS_PENDING)
                                .le(RewardIngestTask::getNextRetryAt, now))
                        .or(retry -> retry.eq(RewardIngestTask::getTaskStatus, ViewerConstants.TASK_STATUS_RETRY)
                                .le(RewardIngestTask::getNextRetryAt, now))
                        .or(processing -> processing.eq(RewardIngestTask::getTaskStatus, ViewerConstants.TASK_STATUS_PROCESSING)
                                .le(RewardIngestTask::getProcessingDeadline, now)))
                .orderByAsc(RewardIngestTask::getId)
                .last("LIMIT " + properties.getSettlement().getRecoveryBatchSize()));
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
