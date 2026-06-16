package com.javaee.donation.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.finance.dto.ReconciliationResult;
import com.javaee.donation.finance.entity.RewardEvent;
import com.javaee.donation.finance.entity.StreamerBalance;
import com.javaee.donation.finance.entity.StreamerCommissionRule;
import com.javaee.donation.finance.mapper.RewardEventMapper;
import com.javaee.donation.finance.mapper.StreamerBalanceMapper;
import com.javaee.donation.finance.mapper.StreamerCommissionRuleMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BalanceReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(BalanceReconciliationService.class);
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.3000");

    private final RewardEventMapper rewardEventMapper;
    private final StreamerBalanceMapper balanceMapper;
    private final StreamerCommissionRuleMapper commissionRuleMapper;

    public BalanceReconciliationService(RewardEventMapper rewardEventMapper,
                                         StreamerBalanceMapper balanceMapper,
                                         StreamerCommissionRuleMapper commissionRuleMapper) {
        this.rewardEventMapper = rewardEventMapper;
        this.balanceMapper = balanceMapper;
        this.commissionRuleMapper = commissionRuleMapper;
    }

    /**
     * 定时余额预计算：遍历所有主播，从打赏明细重新汇总计算可提现余额，
     * 将计算结果更新到 t_streamer_balance（乐观锁），确保查询时拿到最新值
     */
    @Transactional(rollbackFor = Exception.class)
    public int precomputeAll() {
        String traceId = TraceContext.getTraceId();
        log.info("[{}] precompute all balances start", traceId);

        // 查询所有主播ID
        List<RewardEvent> allEvents = rewardEventMapper.selectList(
                new LambdaQueryWrapper<RewardEvent>().select(RewardEvent::getStreamerId).groupBy(RewardEvent::getStreamerId));
        List<String> streamerIds = allEvents.stream()
                .map(RewardEvent::getStreamerId).distinct().collect(Collectors.toList());

        int updated = 0;
        for (String streamerId : streamerIds) {
            try {
                precomputeSingle(streamerId);
                updated++;
            } catch (Exception e) {
                log.error("[{}] precompute failed for streamerId={}", traceId, streamerId, e);
            }
        }
        log.info("[{}] precompute done, updated={}/{}", traceId, updated, streamerIds.size());
        return updated;
    }

    /**
     * 对账：按打赏明细逐条计算预期余额，与 t_streamer_balance 表中的实际余额比较
     */
    @Transactional(rollbackFor = Exception.class)
    public ReconciliationResult reconcile(boolean autoCorrect) {
        String traceId = TraceContext.getTraceId();
        log.info("[{}] reconciliation start, autoCorrect={}", traceId, autoCorrect);

        List<String> streamerIds = listAllStreamerIds();
        List<ReconciliationResult.MismatchDetail> mismatches = new ArrayList<>();
        int matched = 0;

        for (String streamerId : streamerIds) {
            // 从打赏明细汇总预期余额
            BigDecimal[] expected = computeExpectedBalance(streamerId);
            BigDecimal expectedTotalReward = expected[0];
            BigDecimal expectedTotalCommission = expected[1];
            BigDecimal expectedWithdrawable = expected[2];

            // 查实际余额
            StreamerBalance actual = balanceMapper.selectOne(
                    new LambdaQueryWrapper<StreamerBalance>().eq(StreamerBalance::getStreamerId, streamerId));
            BigDecimal actualTotalReward = actual != null ? safe(actual.getTotalRewardAmount()) : BigDecimal.ZERO;
            BigDecimal actualTotalCommission = actual != null ? safe(actual.getTotalCommissionAmount()) : BigDecimal.ZERO;
            BigDecimal actualWithdrawable = actual != null ? safe(actual.getWithdrawableAmount()) : BigDecimal.ZERO;

            if (expectedTotalReward.compareTo(actualTotalReward) == 0
                    && expectedTotalCommission.compareTo(actualTotalCommission) == 0
                    && expectedWithdrawable.compareTo(actualWithdrawable) == 0) {
                matched++;
            } else {
                boolean corrected = false;
                if (autoCorrect && actual != null) {
                    actual.setTotalRewardAmount(expectedTotalReward);
                    actual.setTotalCommissionAmount(expectedTotalCommission);
                    actual.setWithdrawableAmount(expectedWithdrawable);
                    actual.setUpdatedAt(LocalDateTime.now());
                    balanceMapper.updateById(actual);
                    corrected = true;
                    log.warn("[{}] auto-corrected streamerId={}", traceId, streamerId);
                }
                mismatches.add(ReconciliationResult.MismatchDetail.builder()
                        .streamerId(streamerId)
                        .expectedTotalReward(expectedTotalReward)
                        .expectedTotalCommission(expectedTotalCommission)
                        .expectedWithdrawable(expectedWithdrawable)
                        .actualTotalReward(actualTotalReward)
                        .actualTotalCommission(actualTotalCommission)
                        .actualWithdrawable(actualWithdrawable)
                        .autoCorrected(corrected)
                        .build());
            }
        }

        ReconciliationResult result = ReconciliationResult.builder()
                .reconciledAt(LocalDateTime.now().toString())
                .totalStreamers(streamerIds.size())
                .matched(matched)
                .mismatched(mismatches.size())
                .mismatches(mismatches)
                .build();

        log.info("[{}] reconciliation done, matched={}, mismatched={}", traceId, matched, mismatches.size());
        return result;
    }

    private void precomputeSingle(String streamerId) {
        BigDecimal[] expected = computeExpectedBalance(streamerId);
        BigDecimal totalReward = expected[0];
        BigDecimal totalCommission = expected[1];
        BigDecimal withdrawable = expected[2];

        // 乐观锁更新
        StreamerBalance balance = balanceMapper.selectOne(
                new LambdaQueryWrapper<StreamerBalance>().eq(StreamerBalance::getStreamerId, streamerId));
        if (balance == null) {
            balance = new StreamerBalance();
            balance.setStreamerId(streamerId);
            balance.setTotalRewardAmount(totalReward);
            balance.setTotalCommissionAmount(totalCommission);
            balance.setWithdrawableAmount(withdrawable);
            balance.setVersion(0L);
            balance.setCreatedAt(LocalDateTime.now());
            balance.setUpdatedAt(LocalDateTime.now());
            balanceMapper.insert(balance);
        } else {
            balance.setTotalRewardAmount(totalReward);
            balance.setTotalCommissionAmount(totalCommission);
            balance.setWithdrawableAmount(withdrawable);
            balance.setUpdatedAt(LocalDateTime.now());
            balanceMapper.updateById(balance);
        }
    }

    /**
     * 从打赏明细逐笔汇总：
     * - 总打赏金额 = SUM(reward_amount)
     * - 总提成金额 = SUM(reward_amount * commission_rate)
     * - 可提现金额 = 总打赏金额 - 总提成金额
     */
    private BigDecimal[] computeExpectedBalance(String streamerId) {
        List<RewardEvent> events = rewardEventMapper.selectList(
                new LambdaQueryWrapper<RewardEvent>()
                        .eq(RewardEvent::getStreamerId, streamerId)
                        .eq(RewardEvent::getSettleStatus, "SETTLED"));

        BigDecimal totalReward = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;

        for (RewardEvent event : events) {
            BigDecimal amount = safe(event.getRewardAmount());
            BigDecimal rate = resolveRate(streamerId, event.getRewardTime());
            BigDecimal commission = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            totalReward = totalReward.add(amount);
            totalCommission = totalCommission.add(commission);
        }

        BigDecimal withdrawable = totalReward.subtract(totalCommission);
        return new BigDecimal[]{totalReward, totalCommission, withdrawable};
    }

    private BigDecimal resolveRate(String streamerId, LocalDateTime rewardTime) {
        List<StreamerCommissionRule> rules = commissionRuleMapper.selectList(
                new LambdaQueryWrapper<StreamerCommissionRule>()
                        .eq(StreamerCommissionRule::getStreamerId, streamerId));
        for (StreamerCommissionRule rule : rules) {
            if (!rewardTime.isBefore(rule.getEffectiveFrom())
                    && (rule.getEffectiveTo() == null || rewardTime.isBefore(rule.getEffectiveTo()))) {
                return rule.getCommissionRate();
            }
        }
        return DEFAULT_COMMISSION_RATE;
    }

    private List<String> listAllStreamerIds() {
        List<StreamerBalance> balances = balanceMapper.selectList(
                new LambdaQueryWrapper<StreamerBalance>().select(StreamerBalance::getStreamerId));
        return balances.stream().map(StreamerBalance::getStreamerId).collect(Collectors.toList());
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
