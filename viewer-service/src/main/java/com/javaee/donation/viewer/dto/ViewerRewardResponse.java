package com.javaee.donation.viewer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 打赏接口返回 DTO，对应 POST /api/viewers/reward 的 data 字段。
 *
 * <p>由观众服务调用财务服务入账后封装返回；完整契约见 viewer-service/README.md。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViewerRewardResponse {

    /** 打赏单号，与入参 rewardNo 一致，幂等键 */
    private String rewardNo;

    /**
     * 入账状态。
     * <ul>
     *   <li>SETTLED — 新入账成功</li>
     *   <li>DUPLICATE — 重复请求，未重复入账</li>
     * </ul>
     */
    private String settleStatus;

    /** 主播 ID */
    private String streamerId;

    /** 打赏金额 */
    private BigDecimal rewardAmount;

    /** 本次适用的主播提成比例（如 0.25 表示 25%） */
    private BigDecimal commissionRate;

    /** 平台/主播分成中的提成金额 */
    private BigDecimal commissionAmount;

    /** 本次打赏计入主播可提现余额的增量 */
    private BigDecimal withdrawableAmount;

    /** 财务入账完成时间 */
    private LocalDateTime settledAt;

    /** 面向用户的提示信息，如「打赏成功」 */
    private String message;
}
