package com.javaee.donation.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.CommissionRuleRequest;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.common.model.StreamerBalanceResponse;
import com.javaee.donation.finance.dto.CommissionRuleResponse;
import com.javaee.donation.finance.dto.RewardSettleResponse;
import com.javaee.donation.finance.entity.RewardEvent;
import com.javaee.donation.finance.entity.StreamerBalance;
import com.javaee.donation.finance.mapper.RewardEventMapper;
import com.javaee.donation.finance.mapper.StreamerBalanceMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.cloud.consul.enabled=false")
class FinanceSettlementServiceIntegrationTest {

    @Autowired
    private FinanceSettlementService service;

    @Autowired
    private RewardEventMapper rewardEventMapper;

    @Autowired
    private StreamerBalanceMapper balanceMapper;

    private final String traceId = "test-" + UUID.randomUUID().toString().substring(0, 12);

    @BeforeEach
    void setUp() {
        TraceContext.setTraceId(traceId);
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("【需求1】POST /settle - 返回RewardSettleResponse含全部字段")
    void settle_ReturnsFullResponse() {
        RewardRequest request = buildReward("R-X01", "S-X01", "100.00");
        RewardSettleResponse resp = service.settle(request);

        assertNotNull(resp);
        assertEquals("R-X01", resp.getRewardNo());
        assertEquals("SETTLED", resp.getSettleStatus());
        assertEquals("S-X01", resp.getStreamerId());
        assertEquals(new BigDecimal("100.00"), resp.getRewardAmount());
        assertEquals(new BigDecimal("0.3000"), resp.getCommissionRate());
        assertEquals(new BigDecimal("30.00"), resp.getCommissionAmount());
        assertEquals(new BigDecimal("70.00"), resp.getWithdrawableAmount());
        assertNotNull(resp.getSettledAt());
    }

    @Test
    @DisplayName("【需求2】POST /commission-rules - 返回CommissionRuleResponse")
    void saveRule_ReturnsFullResponse() {
        CommissionRuleRequest req = new CommissionRuleRequest();
        req.setStreamerId("S-X02");
        req.setCommissionRate(new BigDecimal("0.4000"));
        req.setEffectiveFrom("2026-01-01T00:00:00");

        CommissionRuleResponse resp = service.saveCommissionRule(req);
        assertNotNull(resp.getId());
        assertEquals("S-X02", resp.getStreamerId());
        assertEquals(new BigDecimal("0.4000"), resp.getCommissionRate());
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), resp.getEffectiveFrom());
    }

    @Test
    @DisplayName("【需求3】GET /balance - 查询已有余额")
    void getBalance_Existing() {
        service.settle(buildReward("R-B1", "S-BAL", "200.00"));

        StreamerBalanceResponse resp = service.getBalance("S-BAL");
        assertEquals(new BigDecimal("200.00"), resp.getTotalRewardAmount());
        assertEquals(new BigDecimal("60.00"), resp.getTotalCommissionAmount());
        assertEquals(new BigDecimal("140.00"), resp.getWithdrawableAmount());
    }

    @Test
    @DisplayName("【需求3】GET /balance - 不存在自动创建返回零值")
    void getBalance_NotExist() {
        StreamerBalanceResponse resp = service.getBalance("S-NEW");
        assertEquals("S-NEW", resp.getStreamerId());
        assertEquals(BigDecimal.ZERO, resp.getTotalRewardAmount());
        assertEquals(BigDecimal.ZERO, resp.getWithdrawableAmount());
    }

    @Test
    @DisplayName("【需求5】幂等 - 同一rewardNo返回DUPLICATE，余额不变")
    void settle_Duplicate() {
        service.settle(buildReward("R-DUP", "S-DUP", "50.00"));
        RewardSettleResponse dup = service.settle(buildReward("R-DUP", "S-DUP", "50.00"));

        assertEquals("DUPLICATE", dup.getSettleStatus());

        StreamerBalance bal = balanceMapper.selectOne(
                new LambdaQueryWrapper<StreamerBalance>().eq(StreamerBalance::getStreamerId, "S-DUP"));
        assertEquals(new BigDecimal("50.00"), bal.getTotalRewardAmount());
    }

    @Test
    @DisplayName("【需求6】入账链路 - 落明细→算提成→更新余额")
    void settle_FullFlow() {
        RewardRequest req = buildReward("R-FLOW", "S-FLOW", "100.00");
        req.setViewerId("V1");
        req.setViewerName("张三");
        req.setViewerGender("MALE");
        req.setStreamerName("主播A");

        service.settle(req);

        RewardEvent event = rewardEventMapper.selectOne(
                new LambdaQueryWrapper<RewardEvent>().eq(RewardEvent::getRewardNo, "R-FLOW"));
        assertEquals("R-FLOW", event.getRewardNo());
        assertEquals(traceId, event.getTraceId());
        assertEquals("V1", event.getViewerId());
        assertEquals("张三", event.getViewerName());
        assertEquals("S-FLOW", event.getStreamerId());
        assertEquals("SETTLED", event.getSettleStatus());
        assertEquals(new BigDecimal("100.00"), event.getRewardAmount());

        StreamerBalance bal = balanceMapper.selectOne(
                new LambdaQueryWrapper<StreamerBalance>().eq(StreamerBalance::getStreamerId, "S-FLOW"));
        assertEquals(new BigDecimal("100.00"), bal.getTotalRewardAmount());
        assertEquals(new BigDecimal("30.00"), bal.getTotalCommissionAmount());
        assertEquals(new BigDecimal("70.00"), bal.getWithdrawableAmount());
    }

    @Test
    @DisplayName("【需求7】规则不覆盖 - 新规则自动关闭旧规则")
    void saveRule_AutoClosePrevious() {
        CommissionRuleRequest r1 = buildRule("S-R-CLOSE", "0.3000", "2026-01-01T00:00:00");
        CommissionRuleRequest r2 = buildRule("S-R-CLOSE", "0.5000", "2026-06-01T00:00:00");
        service.saveCommissionRule(r1);
        service.saveCommissionRule(r2);

        RewardRequest old = buildReward("R-OCL", "S-R-CLOSE", "100.00");
        old.setRewardTime("2026-03-15T12:00:00");
        assertEquals(new BigDecimal("0.3000"), service.settle(old).getCommissionRate());

        RewardRequest now = buildReward("R-NCL", "S-R-CLOSE", "100.00");
        now.setRewardTime("2026-07-01T12:00:00");
        assertEquals(new BigDecimal("0.5000"), service.settle(now).getCommissionRate());
    }

    @Test
    @DisplayName("【需求8】余额查询 - 多规则下余额正确累加")
    void getBalance_MultipleRules() {
        service.saveCommissionRule(buildRule("S-MR", "0.3000", "2026-01-01T00:00:00"));
        service.saveCommissionRule(buildRule("S-MR", "0.5000", "2026-06-01T00:00:00"));

        RewardRequest old = buildReward("R-OLD", "S-MR", "100.00");
        old.setRewardTime("2026-03-15T12:00:00");
        service.settle(old);

        RewardRequest now = buildReward("R-NOW", "S-MR", "200.00");
        now.setRewardTime("2026-07-01T12:00:00");
        service.settle(now);

        StreamerBalanceResponse bal = service.getBalance("S-MR");
        // 100*30%=30, 200*50%=100 → commission=130, withdrawable=300-130=170
        assertEquals(new BigDecimal("300.00"), bal.getTotalRewardAmount());
        assertEquals(new BigDecimal("130.00"), bal.getTotalCommissionAmount());
        assertEquals(new BigDecimal("170.00"), bal.getWithdrawableAmount());
    }

    @Test
    @DisplayName("【需求9】事务 - 版本号随每次更新递增")
    void settle_VersionIncrement() {
        for (int i = 0; i < 5; i++) {
            service.settle(buildReward("R-TX-" + i, "S-TX", "10.00"));
        }

        StreamerBalance bal = balanceMapper.selectOne(
                new LambdaQueryWrapper<StreamerBalance>().eq(StreamerBalance::getStreamerId, "S-TX"));
        assertEquals(new BigDecimal("50.00"), bal.getTotalRewardAmount());
        assertEquals(5L, bal.getVersion());
    }

    @Test
    @DisplayName("【需求10】traceId - 明细携带请求traceId")
    void settle_RewardEventCarriesTraceId() {
        TraceContext.setTraceId("CUSTOM-TID-12345");
        service.settle(buildReward("R-TID", "S-TID", "10.00"));

        RewardEvent event = rewardEventMapper.selectOne(
                new LambdaQueryWrapper<RewardEvent>().eq(RewardEvent::getRewardNo, "R-TID"));
        assertEquals("CUSTOM-TID-12345", event.getTraceId());
    }

    @Test
    @DisplayName("金额为null默认0")
    void settle_NullAmount() {
        RewardSettleResponse resp = service.settle(buildReward("R-NULL", "S-NULL", null));
        assertEquals(new BigDecimal("0"), resp.getRewardAmount());
    }

    @Test
    @DisplayName("金额精度：100.50 * 30% = 30.15, 30.15+70.35=100.50")
    void settle_PreciseAmount() {
        RewardSettleResponse resp = service.settle(buildReward("R-PREC", "S-PREC", "100.50"));

        assertEquals(new BigDecimal("30.15"), resp.getCommissionAmount());
        assertEquals(new BigDecimal("70.35"), resp.getWithdrawableAmount());
        assertEquals(new BigDecimal("100.50"),
                resp.getCommissionAmount().add(resp.getWithdrawableAmount()));
    }

    @Test
    @DisplayName("未设置规则时默认30%")
    void settle_DefaultRate() {
        RewardSettleResponse resp = service.settle(buildReward("R-DEF", "S-DEF", "100.00"));
        assertEquals(new BigDecimal("0.3000"), resp.getCommissionRate());
    }

    // ---- helper ----

    private RewardRequest buildReward(String rewardNo, String streamerId, String amount) {
        RewardRequest req = new RewardRequest();
        req.setRewardNo(rewardNo);
        req.setStreamerId(streamerId);
        req.setStreamerName("主播" + streamerId);
        req.setViewerId("V" + rewardNo);
        req.setViewerName("观众" + rewardNo);
        req.setViewerGender("MALE");
        req.setRewardAmount(amount != null ? new BigDecimal(amount) : null);
        req.setRewardTime("2026-06-15T12:00:00");
        return req;
    }

    private CommissionRuleRequest buildRule(String streamerId, String rate, String from) {
        CommissionRuleRequest req = new CommissionRuleRequest();
        req.setStreamerId(streamerId);
        req.setCommissionRate(new BigDecimal(rate));
        req.setEffectiveFrom(from);
        return req;
    }
}
