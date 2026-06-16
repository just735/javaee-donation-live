package com.javaee.donation.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.CommissionRuleRequest;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.common.model.StreamerBalanceResponse;
import com.javaee.donation.finance.entity.StreamerBalance;
import com.javaee.donation.finance.mapper.StreamerBalanceMapper;
import com.javaee.donation.finance.dto.ReconciliationResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.cloud.consul.enabled=false")
class Feature3IntegrationTest {

    @Autowired
    private FinanceSettlementService settlementService;

    @Autowired
    private BalanceReconciliationService reconciliationService;

    @Autowired
    private StreamerBalanceMapper balanceMapper;

    @BeforeEach
    void setUp() {
        TraceContext.setTraceId("test-feat3");
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    // ======== 余额预计算 ========

    @Test
    @DisplayName("【预计算】有入账记录后，预计算可重新汇总余额")
    void precompute_RecalculatesFromEvents() {
        // 入账两条
        settlementService.settle(buildReward("R-PC1", "S-PRE", "100.00"));
        settlementService.settle(buildReward("R-PC2", "S-PRE", "50.00"));

        // 篡改余额使其不一致
        StreamerBalance bal = balanceMapper.selectById(
                balanceMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StreamerBalance>()
                        .eq(StreamerBalance::getStreamerId, "S-PRE")).getId());
        bal.setTotalRewardAmount(new BigDecimal("999.00"));
        balanceMapper.updateById(bal);

        // 预计算修复
        reconciliationService.precomputeAll();

        StreamerBalance fixed = balanceMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StreamerBalance>()
                        .eq(StreamerBalance::getStreamerId, "S-PRE"));
        assertEquals(new BigDecimal("150.00"), fixed.getTotalRewardAmount());
        assertEquals(new BigDecimal("105.00"), fixed.getWithdrawableAmount());
    }

    // ======== 对账 ========

    @Test
    @DisplayName("【对账】正常数据对账一致 matched=1")
    void reconcile_Matched() {
        settlementService.settle(buildReward("R-OK", "S-REC", "100.00"));

        ReconciliationResult result = reconciliationService.reconcile(false);
        assertEquals(0, result.getMismatched());
        assertTrue(result.getMatched() >= 1);
    }

    @Test
    @DisplayName("【对账】篡改余额后对账发现不一致")
    void reconcile_Mismatch() {
        settlementService.settle(buildReward("R-MM1", "S-MIS", "100.00"));
        settlementService.settle(buildReward("R-MM2", "S-MIS", "50.00"));

        // 篡改余额
        StreamerBalance bal = balanceMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StreamerBalance>()
                        .eq(StreamerBalance::getStreamerId, "S-MIS"));
        bal.setWithdrawableAmount(new BigDecimal("10.00"));
        balanceMapper.updateById(bal);

        ReconciliationResult result = reconciliationService.reconcile(false);
        assertTrue(result.getMismatched() >= 1);
        assertTrue(result.getMismatches().stream().anyMatch(m -> "S-MIS".equals(m.getStreamerId())));
    }

    @Test
    @DisplayName("【对账】autoCorrect=true 自动修正不一致数据")
    void reconcile_AutoCorrect() {
        settlementService.settle(buildReward("R-AC", "S-AC", "100.00"));

        StreamerBalance bal = balanceMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StreamerBalance>()
                        .eq(StreamerBalance::getStreamerId, "S-AC"));
        bal.setWithdrawableAmount(new BigDecimal("5.00"));
        balanceMapper.updateById(bal);

        reconciliationService.reconcile(true);

        StreamerBalance fixed = balanceMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StreamerBalance>()
                        .eq(StreamerBalance::getStreamerId, "S-AC"));
        assertEquals(new BigDecimal("70.00"), fixed.getWithdrawableAmount());
    }

    // ======== 余额扣减（乐观锁） ========

    @Test
    @DisplayName("【扣减】正常扣减成功，余额减少")
    void deduct_Success() {
        settlementService.settle(buildReward("R-W1", "S-WD", "100.00"));

        StreamerBalanceResponse before = settlementService.getBalance("S-WD");
        assertEquals(new BigDecimal("70.00"), before.getWithdrawableAmount());

        StreamerBalanceResponse after = settlementService.deductBalance("S-WD", new BigDecimal("30.00"));
        assertEquals(new BigDecimal("40.00"), after.getWithdrawableAmount());
    }

    @Test
    @DisplayName("【扣减】余额不足时抛异常")
    void deduct_InsufficientBalance() {
        settlementService.settle(buildReward("R-W2", "S-INS", "10.00"));

        assertThrows(IllegalArgumentException.class,
                () -> settlementService.deductBalance("S-INS", new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("【扣减】扣减金额<=0抛异常")
    void deduct_NonPositive() {
        assertThrows(IllegalArgumentException.class,
                () -> settlementService.deductBalance("S-NEG", BigDecimal.ZERO));
    }

    @Test
    @DisplayName("【扣减】乐观锁版本号在扣减后递增")
    void deduct_VersionIncrements() {
        settlementService.settle(buildReward("R-W3", "S-VER", "100.00"));

        StreamerBalance before = balanceMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StreamerBalance>()
                        .eq(StreamerBalance::getStreamerId, "S-VER"));

        settlementService.deductBalance("S-VER", new BigDecimal("20.00"));

        StreamerBalance after = balanceMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StreamerBalance>()
                        .eq(StreamerBalance::getStreamerId, "S-VER"));
        assertEquals(before.getVersion() + 1, after.getVersion());
    }

    @Test
    @DisplayName("【对账】有提成规则时对账正确计算提成")
    void reconcile_WithCommissionRule() {
        CommissionRuleRequest rule = new CommissionRuleRequest();
        rule.setStreamerId("S-CMR");
        rule.setCommissionRate(new BigDecimal("0.5000"));
        rule.setEffectiveFrom("2026-01-01T00:00:00");
        settlementService.saveCommissionRule(rule);

        RewardRequest req = buildReward("R-CMR", "S-CMR", "100.00");
        req.setRewardTime("2026-06-01T12:00:00");
        settlementService.settle(req);

        ReconciliationResult result = reconciliationService.reconcile(false);
        assertEquals(0, result.getMismatched());
    }

    // ---- helper ----

    private RewardRequest buildReward(String rewardNo, String streamerId, String amount) {
        RewardRequest req = new RewardRequest();
        req.setRewardNo(rewardNo);
        req.setStreamerId(streamerId);
        req.setStreamerName("主播");
        req.setViewerId("V" + rewardNo);
        req.setViewerName("观众");
        req.setViewerGender("MALE");
        req.setRewardAmount(amount != null ? new BigDecimal(amount) : null);
        req.setRewardTime("2026-06-15T12:00:00");
        return req;
    }
}
