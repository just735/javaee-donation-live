package com.javaee.donation.viewer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_reward_ingest_task")
public class RewardIngestTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String rewardNo;
    private String traceId;
    private String viewerId;
    private String viewerName;
    private String viewerGender;
    private String streamerId;
    private String streamerName;
    private BigDecimal rewardAmount;
    private String rewardTime;
    private String taskStatus;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime nextRetryAt;
    private LocalDateTime processingDeadline;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
