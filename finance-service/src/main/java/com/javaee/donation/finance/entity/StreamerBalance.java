package com.javaee.donation.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_streamer_balance")
public class StreamerBalance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String streamerId;
    private BigDecimal totalRewardAmount;
    private BigDecimal totalCommissionAmount;
    private BigDecimal withdrawableAmount;

    @Version
    private Long version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
