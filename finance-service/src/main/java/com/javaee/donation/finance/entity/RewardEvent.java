package com.javaee.donation.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_reward_event")
public class RewardEvent {

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
    private LocalDateTime rewardTime;
    private String settleStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
