package com.javaee.donation.analytics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_viewer_profile")
public class ViewerProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String viewerId;
    private String viewerName;
    private String profileTag;
    private BigDecimal profileScore;
    private LocalDateTime updatedAt;
}
