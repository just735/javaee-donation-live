package com.javaee.donation.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.finance.entity.RewardEvent;
import com.javaee.donation.finance.entity.StreamerBalance;
import com.javaee.donation.finance.mapper.RewardEventMapper;
import com.javaee.donation.finance.mapper.StreamerBalanceMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 入账批量攒批处理器。
 *
 * <p>设计思路（替代MQ的最简方案）：
 * <ol>
 *   <li>请求进入内存队列，settle() 立即返回</li>
 *   <li>后台线程定时（或达到阈值）从队列取出批量数据</li>
 *   <li>一次事务内完成：批量INSERT打赏明细 + 按主播聚合UPDATE余额</li>
 * </ol>
 *
 * <p>性能提升原理：
 * <ul>
 *   <li>N笔入账从 N×(INSERT+SELECT+UPDATE) ≈ 4N 次 DB操作</li>
 *   <li>降为 N/BATCH_SIZE × (batchInsert + 聚合UPDATE) ≈ N/50 × ~15 次</li>
 *   <li>减少事务次数、redo日志刷盘次数、索引页刷新次数</li>
 * </ul>
 */
@Component
public class SettlementBatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(SettlementBatchProcessor.class);

    /** 攒批阈值：攒够多少条就触发一次落库 */
    static final int BATCH_SIZE = 50;
    /** 最大等待时间(ms)：超过此时间即使没攒够也强制落库 */
    static final long FLUSH_INTERVAL_MS = 80;

    private final BlockingQueue<RewardRequest> queue = new LinkedBlockingQueue<>(10000);
    private final RewardEventMapper rewardEventMapper;
    private final StreamerBalanceMapper balanceMapper;
    private final FinanceSettlementService settlementService;

    /** 已入队计数（用于监控） */
    private final AtomicInteger enqueuedCount = new AtomicInteger(0);
    private final AtomicInteger flushedCount = new AtomicInteger(0);

    public SettlementBatchProcessor(RewardEventMapper rewardEventMapper,
                                     StreamerBalanceMapper balanceMapper,
                                     @Lazy FinanceSettlementService settlementService) {
        this.rewardEventMapper = rewardEventMapper;
        this.balanceMapper = balanceMapper;
        this.settlementService = settlementService;
        startFlushThread();
    }

    /**
     * 入账请求入队（非阻塞，由调用方快速返回）
     */
    public boolean enqueue(RewardRequest request) {
        boolean offered = queue.offer(request);
        if (offered) {
            enqueuedCount.incrementAndGet();
        } else {
            log.warn("[{}] settlement queue full, dropping rewardNo={}",
                    TraceContext.getTraceId(), request.getRewardNo());
        }
        return offered;
    }

    /**
     * 后台刷新线程：定时从队列取批并落库
     */
    private void startFlushThread() {
        Thread flushThread = new Thread(() -> {
            log.info("SettlementBatchProcessor flush thread started, batchSize={}, flushIntervalMs={}",
                    BATCH_SIZE, FLUSH_INTERVAL_MS);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    List<RewardRequest> batch = drainBatch();
                    if (!batch.isEmpty()) {
                        flush(batch);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("SettlementBatchProcessor flush error", e);
                }
            }
            log.info("SettlementBatchProcessor flush thread stopped");
        }, "batch-settle-flush");
        flushThread.setDaemon(true);
        flushThread.start();
    }

    /**
     * 从队列中取一批数据（最多BATCH_SIZE条，最多等FLUSH_INTERVAL_MS毫秒）
     */
    private List<RewardRequest> drainBatch() throws InterruptedException {
        List<RewardRequest> batch = new ArrayList<>(BATCH_SIZE);
        // 先取第一条，带超时等待
        RewardRequest first = queue.poll(FLUSH_INTERVAL_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (first == null) {
            return batch;
        }
        batch.add(first);
        // 非阻塞取剩余的
        queue.drainTo(batch, BATCH_SIZE - 1);
        return batch;
    }

    /**
     * 批量落库：单次事务完成 批量INSERT明细 + 聚合UPDATE余额
     */
    @Transactional(rollbackFor = Exception.class)
    public synchronized void flush(List<RewardRequest> batch) {
        String batchTraceId = TraceContext.getTraceId();
        int size = batch.size();
        log.info("[{}] batch flush start, count={}", batchTraceId, size);

        // ---- 1. 构建 RewardEvent 列表，计算提成 ----
        List<RewardEvent> events = new ArrayList<>(size);
        for (RewardRequest req : batch) {
            LocalDateTime rewardTime = parseTime(req.getRewardTime());
            BigDecimal amount = safeAmount(req.getRewardAmount());
            BigDecimal rate = settlementService.resolveCommissionRateCached(
                    req.getStreamerId(), rewardTime);
            BigDecimal commissionAmount = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal withdrawable = amount.subtract(commissionAmount);

            RewardEvent event = new RewardEvent();
            event.setRewardNo(req.getRewardNo());
            event.setTraceId(TraceContext.getTraceId());
            event.setViewerId(req.getViewerId());
            event.setViewerName(req.getViewerName());
            event.setViewerGender(req.getViewerGender());
            event.setStreamerId(req.getStreamerId());
            event.setStreamerName(req.getStreamerName());
            event.setRewardAmount(amount);
            event.setRewardTime(rewardTime);
            event.setCommissionRate(rate);
            event.setCommissionAmount(commissionAmount);
            event.setWithdrawableAmount(withdrawable);
            event.setSettleStatus("SETTLED");
            event.setCreatedAt(LocalDateTime.now());
            event.setUpdatedAt(LocalDateTime.now());
            events.add(event);
        }

        // ---- 2. 批量插入打赏明细 ----
        int inserted = 0;
        int duplicate = 0;
        List<RewardEvent> successfulEvents = new ArrayList<>();
        for (RewardEvent event : events) {
            try {
                rewardEventMapper.insert(event);
                inserted++;
                successfulEvents.add(event);
            } catch (DuplicateKeyException e) {
                duplicate++;
            }
        }
        log.info("[{}] batch insert done, inserted={}, duplicated={}", batchTraceId, inserted, duplicate);

        // ---- 3. 按streamerId聚合余额增量，逐个更新 ----
        Map<String, BalanceDelta> deltas = new java.util.HashMap<>();
        for (RewardEvent event : successfulEvents) {
            deltas.merge(event.getStreamerId(),
                    new BalanceDelta(event.getRewardAmount(), event.getCommissionAmount(), event.getWithdrawableAmount()),
                    BalanceDelta::add);
        }

        int updatedBalances = 0;
        for (Map.Entry<String, BalanceDelta> entry : deltas.entrySet()) {
            String streamerId = entry.getKey();
            BalanceDelta delta = entry.getValue();
            updateBalanceAggregated(streamerId, delta.totalReward, delta.totalCommission, delta.totalWithdrawable);
            updatedBalances++;
        }

        flushedCount.addAndGet(size);
        log.info("[{}] batch flush done, totalEvents={}, inserted={}, duplicate={}, balancesUpdated={}",
                batchTraceId, size, inserted, duplicate, updatedBalances);
    }

    /**
     * 聚合更新主播余额：将多笔打赏合并为一次 UPDATE
     * 使用 SET total_reward_amount = total_reward_amount + X 避免乐观锁读-改-写循环
     */
    private void updateBalanceAggregated(String streamerId,
                                         BigDecimal totalRewardIncrement,
                                         BigDecimal totalCommissionIncrement,
                                         BigDecimal totalWithdrawableIncrement) {
        // ensure balance row exists
        StreamerBalance existing = findOrCreateBalance(streamerId);

        // Direct atomic increment via SQL expression (no optimistic lock needed for pure addition)
        var wrapper = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<StreamerBalance>();
        wrapper.eq(StreamerBalance::getStreamerId, streamerId)
                .setSql("total_reward_amount = COALESCE(total_reward_amount,0) + " + totalRewardIncrement)
                .setSql("total_commission_amount = COALESCE(total_commission_amount,0) + " + totalCommissionIncrement)
                .setSql("withdrawable_amount = COALESCE(withdrawable_amount,0) + " + totalWithdrawableIncrement)
                .set(StreamerBalance::getVersion, existing.getVersion() + 1)
                .set(StreamerBalance::getUpdatedAt, LocalDateTime.now());

        int rows = balanceMapper.update(null, wrapper);
        if (rows == 0) {
            log.warn("[{}] balance update affected 0 rows for streamerId={}", TraceContext.getTraceId(), streamerId);
        }
    }

    private StreamerBalance findOrCreateBalance(String streamerId) {
        LambdaQueryWrapper<StreamerBalance> qw = new LambdaQueryWrapper<>();
        qw.eq(StreamerBalance::getStreamerId, streamerId);
        StreamerBalance balance = balanceMapper.selectOne(qw);
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

    public int getEnqueuedCount() { return enqueuedCount.get(); }
    public int getFlushedCount() { return flushedCount.get(); }
    public int getQueueSize() { return queue.size(); }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) return LocalDateTime.now();
        return LocalDateTime.parse(value);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /** 主播余额增量聚合值 */
    record BalanceDelta(BigDecimal totalReward, BigDecimal totalCommission, BigDecimal totalWithdrawable) {
        static BalanceDelta add(BalanceDelta a, BalanceDelta b) {
            return new BalanceDelta(
                    a.totalReward.add(b.totalReward),
                    a.totalCommission.add(b.totalCommission),
                    a.totalWithdrawable.add(b.totalWithdrawable));
        }
    }
}
