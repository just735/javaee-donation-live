package com.javaee.donation.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_reward_hourly_stat")
public class RewardHourlyStat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDateTime statHour;
    private String streamerId;
    private String viewerGender;
    private BigDecimal rewardAmount;
    private Long rewardCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
