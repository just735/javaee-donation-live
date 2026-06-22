package com.javaee.donation.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.CommissionRuleRequest;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.common.model.StreamerBalanceResponse;
import com.javaee.donation.finance.dto.CommissionRuleResponse;
import com.javaee.donation.finance.dto.RewardSettleResponse;
import com.javaee.donation.finance.entity.RewardEvent;
import com.javaee.donation.finance.entity.StreamerBalance;
import com.javaee.donation.finance.entity.StreamerCommissionRule;
import com.javaee.donation.finance.mapper.RewardEventMapper;
import com.javaee.donation.finance.mapper.StreamerBalanceMapper;
import com.javaee.donation.finance.mapper.StreamerCommissionRuleMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceSettlementService {

    private static final Logger log = LoggerFactory.getLogger(FinanceSettlementService.class);
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.3000");
    /** 提成规则缓存TTL：5秒 */
    private static final long COMMISSION_CACHE_TTL_MS = 5000L;

    private final RewardEventMapper rewardEventMapper;
    private final StreamerCommissionRuleMapper commissionRuleMapper;
    private final StreamerBalanceMapper balanceMapper;

    /** 内存缓存：streamerId → (commissionRate, cachedAt) */
    private final Map<String, CachedCommission> commissionCache = new ConcurrentHashMap<>();

    public FinanceSettlementService(RewardEventMapper rewardEventMapper,
                                    StreamerCommissionRuleMapper commissionRuleMapper,
                                    StreamerBalanceMapper balanceMapper) {
        this.rewardEventMapper = rewardEventMapper;
        this.commissionRuleMapper = commissionRuleMapper;
        this.balanceMapper = balanceMapper;
    }

    /**
     * 入账结算（高性能版）：
     * 1. 跳过预查，直接 INSERT，靠唯一索引 uk_reward_no 保证幂等
     * 2. 提成规则走内存缓存，避免每次查库
     * 3. 每次入账从 3-4 次 DB 操作降到 1-2 次
     */
    @Transactional(rollbackFor = Exception.class)
    public RewardSettleResponse settle(RewardRequest request) {
        String traceId = TraceContext.getTraceId();
        log.info("[{}] settle reward start, rewardNo={}, streamerId={}, amount={}",
                traceId, request.getRewardNo(), request.getStreamerId(), request.getRewardAmount());

        LocalDateTime rewardTime = parseTime(request.getRewardTime());

        BigDecimal rewardAmount = safeAmount(request.getRewardAmount());
        BigDecimal commissionRate = resolveCommissionRateCached(request.getStreamerId(), rewardTime);
        BigDecimal commissionAmount = rewardAmount.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal withdrawableIncrement = rewardAmount.subtract(commissionAmount);

        // 1. 直接插入打赏明细，利用唯一索引保证幂等（省去一次 SELECT 预查）
        RewardEvent event = new RewardEvent();
        event.setRewardNo(request.getRewardNo());
        event.setTraceId(traceId);
        event.setViewerId(request.getViewerId());
        event.setViewerName(request.getViewerName());
        event.setViewerGender(request.getViewerGender());
        event.setStreamerId(request.getStreamerId());
        event.setStreamerName(request.getStreamerName());
        event.setRewardAmount(rewardAmount);
        event.setRewardTime(rewardTime);
        event.setCommissionRate(commissionRate);
        event.setCommissionAmount(commissionAmount);
        event.setWithdrawableAmount(withdrawableIncrement);
        event.setSettleStatus("SETTLED");
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        try {
            rewardEventMapper.insert(event);
        } catch (DuplicateKeyException e) {
            // 唯一键冲突 → 幂等重复，回查返回已有记录
            LambdaQueryWrapper<RewardEvent> qw = new LambdaQueryWrapper<>();
            qw.eq(RewardEvent::getRewardNo, request.getRewardNo());
            RewardEvent existing = rewardEventMapper.selectOne(qw);
            log.info("[{}] duplicate reward (unique key), rewardNo={}", traceId, request.getRewardNo());
            return RewardSettleResponse.builder()
                    .rewardNo(request.getRewardNo())
                    .settleStatus("DUPLICATE")
                    .streamerId(existing.getStreamerId())
                    .rewardAmount(existing.getRewardAmount())
                    .commissionRate(existing.getCommissionRate())
                    .commissionAmount(existing.getCommissionAmount())
                    .withdrawableAmount(existing.getWithdrawableAmount())
                    .settledAt(existing.getCreatedAt())
                    .build();
        }
        log.info("[{}] reward event inserted, id={}", traceId, event.getId());

        // 2. 更新主播余额（乐观锁重试）
        updateStreamerBalance(request.getStreamerId(), rewardAmount, commissionAmount, withdrawableIncrement);

        log.info("[{}] settle reward success, rewardNo={}, commissionRate={}, commission={}, withdrawable={}",
                traceId, request.getRewardNo(), commissionRate, commissionAmount, withdrawableIncrement);

        return RewardSettleResponse.builder()
                .rewardNo(request.getRewardNo())
                .settleStatus("SETTLED")
                .streamerId(request.getStreamerId())
                .rewardAmount(rewardAmount)
                .commissionRate(commissionRate)
                .commissionAmount(commissionAmount)
                .withdrawableAmount(withdrawableIncrement)
                .settledAt(event.getCreatedAt())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public CommissionRuleResponse saveCommissionRule(CommissionRuleRequest request) {
        String traceId = TraceContext.getTraceId();
        log.info("[{}] save commission rule, streamerId={}, rate={}, from={}",
                traceId, request.getStreamerId(), request.getCommissionRate(), request.getEffectiveFrom());

        LocalDateTime effectiveFrom = parseTime(request.getEffectiveFrom());
        BigDecimal rate = safeAmount(request.getCommissionRate());

        // 关闭上一个规则：将当前主播最新的未关闭规则的effective_to设为新规则的生效时间
        closePreviousRule(request.getStreamerId(), effectiveFrom);

        // 插入新规则
        StreamerCommissionRule rule = new StreamerCommissionRule();
        rule.setStreamerId(request.getStreamerId());
        rule.setCommissionRate(rate);
        rule.setEffectiveFrom(effectiveFrom);
        rule.setEffectiveTo(request.getEffectiveTo() != null && !request.getEffectiveTo().isBlank()
                ? parseTime(request.getEffectiveTo()) : null);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        commissionRuleMapper.insert(rule);

        // 清除该主播的提成缓存，使新规则立即生效
        commissionCache.remove(request.getStreamerId());

        log.info("[{}] commission rule created, id={}", traceId, rule.getId());

        return CommissionRuleResponse.builder()
                .id(rule.getId())
                .streamerId(rule.getStreamerId())
                .commissionRate(rule.getCommissionRate())
                .effectiveFrom(rule.getEffectiveFrom())
                .effectiveTo(rule.getEffectiveTo())
                .createdAt(rule.getCreatedAt())
                .build();
    }

    /**
     * 余额扣减（带乐观锁，最多重试3次），用于主播提现
     */
    @Transactional(rollbackFor = Exception.class)
    public StreamerBalanceResponse deductBalance(String streamerId, BigDecimal deductAmount) {
        String traceId = TraceContext.getTraceId();
        BigDecimal amount = safeAmount(deductAmount);
        log.info("[{}] deduct balance, streamerId={}, amount={}", traceId, streamerId, amount);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("deduct amount must be positive");
        }

        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            StreamerBalance balance = ensureBalance(streamerId);
            BigDecimal currentWithdrawable = safeAmount(balance.getWithdrawableAmount());

            if (currentWithdrawable.compareTo(amount) < 0) {
                throw new IllegalArgumentException("insufficient balance: withdrawable="
                        + currentWithdrawable + ", deduct=" + amount);
            }

            Long currentVersion = balance.getVersion();
            BigDecimal newWithdrawable = currentWithdrawable.subtract(amount);

            LambdaUpdateWrapper<StreamerBalance> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(StreamerBalance::getStreamerId, streamerId)
                    .eq(StreamerBalance::getVersion, currentVersion)
                    .set(StreamerBalance::getWithdrawableAmount, newWithdrawable)
                    .set(StreamerBalance::getVersion, currentVersion + 1)
                    .set(StreamerBalance::getUpdatedAt, LocalDateTime.now());

            int updated = balanceMapper.update(null, updateWrapper);
            if (updated > 0) {
                log.info("[{}] deduct success, streamerId={}, before={}, after={}",
                        traceId, streamerId, currentWithdrawable, newWithdrawable);
                return new StreamerBalanceResponse(streamerId,
                        safeAmount(balance.getTotalRewardAmount()),
                        safeAmount(balance.getTotalCommissionAmount()),
                        newWithdrawable);
            }
            log.warn("[{}] deduct optimistic lock retry {}/{} for streamerId={}",
                    traceId, i + 1, maxRetries, streamerId);
        }
        throw new RuntimeException("deduct balance failed after " + maxRetries + " retries");
    }

    public StreamerBalanceResponse getBalance(String streamerId) {
        String traceId = TraceContext.getTraceId();
        log.info("[{}] query balance, streamerId={}", traceId, streamerId);

        StreamerBalance balance = ensureBalance(streamerId);
        return new StreamerBalanceResponse(
                balance.getStreamerId(),
                balance.getTotalRewardAmount() != null ? balance.getTotalRewardAmount() : BigDecimal.ZERO,
                balance.getTotalCommissionAmount() != null ? balance.getTotalCommissionAmount() : BigDecimal.ZERO,
                balance.getWithdrawableAmount() != null ? balance.getWithdrawableAmount() : BigDecimal.ZERO
        );
    }

    // ---- 私有方法 ----

    /**
     * 按打赏时间匹配当前生效的提成规则（带内存缓存，TTL=5秒）
     */
    private BigDecimal resolveCommissionRateCached(String streamerId, LocalDateTime rewardTime) {
        if (Duration.between(rewardTime, LocalDateTime.now()).abs().toMinutes() >= 1) {
            return resolveCommissionRate(streamerId, rewardTime);
        }
        CachedCommission cached = commissionCache.get(streamerId);
        if (cached != null && (System.currentTimeMillis() - cached.cachedAt) < COMMISSION_CACHE_TTL_MS) {
            return cached.rate;
        }
        BigDecimal rate = resolveCommissionRate(streamerId, rewardTime);
        commissionCache.put(streamerId, new CachedCommission(rate, System.currentTimeMillis()));
        return rate;
    }

    /**
     * 从数据库查询生效的提成规则（纯读，无事务）
     */
    private BigDecimal resolveCommissionRate(String streamerId, LocalDateTime rewardTime) {
        LambdaQueryWrapper<StreamerCommissionRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StreamerCommissionRule::getStreamerId, streamerId)
                .le(StreamerCommissionRule::getEffectiveFrom, rewardTime)
                .and(w -> w.isNull(StreamerCommissionRule::getEffectiveTo)
                        .or().gt(StreamerCommissionRule::getEffectiveTo, rewardTime))
                .orderByDesc(StreamerCommissionRule::getEffectiveFrom)
                .last("LIMIT 1");
        StreamerCommissionRule rule = commissionRuleMapper.selectOne(wrapper);
        if (rule == null) {
            return DEFAULT_COMMISSION_RATE;
        }
        return rule.getCommissionRate();
    }

    /**
     * 更新主播余额，带乐观锁重试（最多3次）
     */
    private void updateStreamerBalance(String streamerId, BigDecimal rewardAmount,
                                        BigDecimal commissionAmount, BigDecimal withdrawableIncrement) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            StreamerBalance balance = ensureBalance(streamerId);
            Long currentVersion = balance.getVersion();

            LambdaUpdateWrapper<StreamerBalance> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(StreamerBalance::getStreamerId, streamerId)
                    .eq(StreamerBalance::getVersion, currentVersion)
                    .setSql("total_reward_amount = total_reward_amount + " + rewardAmount)
                    .setSql("total_commission_amount = total_commission_amount + " + commissionAmount)
                    .setSql("withdrawable_amount = withdrawable_amount + " + withdrawableIncrement)
                    .set(StreamerBalance::getVersion, currentVersion + 1)
                    .set(StreamerBalance::getUpdatedAt, LocalDateTime.now());

            int updated = balanceMapper.update(null, updateWrapper);
            if (updated > 0) {
                return;
            }
            log.warn("[{}] optimistic lock retry {}/{} for streamerId={}",
                    TraceContext.getTraceId(), i + 1, maxRetries, streamerId);
        }
        throw new RuntimeException("update streamer balance failed after " + maxRetries + " retries, streamerId=" + streamerId);
    }

    /**
     * 查询余额，不存在则创建
     */
    private StreamerBalance ensureBalance(String streamerId) {
        LambdaQueryWrapper<StreamerBalance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StreamerBalance::getStreamerId, streamerId);
        StreamerBalance balance = balanceMapper.selectOne(wrapper);
        if (balance == null) {
            balance = new StreamerBalance();
            balance.setStreamerId(streamerId);
            balance.setTotalRewardAmount(BigDecimal.ZERO);
            balance.setTotalCommissionAmount(BigDecimal.ZERO);
            balance.setWithdrawableAmount(BigDecimal.ZERO);
            balance.setVersion(0L);
            balance.setCreatedAt(LocalDateTime.now());
            balance.setUpdatedAt(LocalDateTime.now());
            balanceMapper.insert(balance);
        }
        return balance;
    }

    /**
     * 关闭前一条未关闭的规则：将其 effective_to 设为新规则的生效时间
     */
    private void closePreviousRule(String streamerId, LocalDateTime effectiveFrom) {
        LambdaQueryWrapper<StreamerCommissionRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StreamerCommissionRule::getStreamerId, streamerId)
                .isNull(StreamerCommissionRule::getEffectiveTo)
                .orderByDesc(StreamerCommissionRule::getEffectiveFrom)
                .last("LIMIT 1");
        StreamerCommissionRule previous = commissionRuleMapper.selectOne(wrapper);
        if (previous != null) {
            previous.setEffectiveTo(effectiveFrom);
            previous.setUpdatedAt(LocalDateTime.now());
            commissionRuleMapper.updateById(previous);
            log.info("[{}] closed previous rule id={}, effectiveTo={}",
                    TraceContext.getTraceId(), previous.getId(), effectiveFrom);
        }
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        return LocalDateTime.parse(value);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /** 提成规则缓存值 */
    private record CachedCommission(BigDecimal rate, long cachedAt) {
    }
}
