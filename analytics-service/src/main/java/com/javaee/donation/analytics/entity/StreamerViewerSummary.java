package com.javaee.donation.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_streamer_viewer_summary")
public class StreamerViewerSummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String streamerId;
    private String viewerId;
    private String viewerName;
    private BigDecimal totalRewardAmount;
    private Long rewardCount;
    private LocalDateTime updatedAt;
}
