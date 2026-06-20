package com.javaee.donation.viewer.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Configuration;

/**
 * Sentinel 流控与熔断规则（服务级保护，非单实例摘除）。
 * 多节点场景下配合 Consul 服务发现 + LoadBalancer 实现实例级故障转移。
 */
@Configuration
public class SentinelRuleConfig {

    private static final int STAT_INTERVAL_MS = 20000;
    private static final int MIN_REQUEST_AMOUNT = 5;
    private static final int DEGRADE_TIME_WINDOW_SEC = 10;
    private static final double ERROR_RATIO_THRESHOLD = 0.5;
    private static final long PROFILE_MAX_RT_MS = 2000L;

    @PostConstruct
    public void initRules() {
        initFlowRules();
        initDegradeRules();
    }

    private void initFlowRules() {
        FlowRule rewardFlow = new FlowRule("viewerReward")
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(200);
        FlowRuleManager.loadRules(List.of(rewardFlow));
    }

    private void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        rules.add(buildErrorRatioRule("analyticsProfile"));
        rules.add(buildSlowRequestRule("analyticsProfile", PROFILE_MAX_RT_MS));
        rules.add(buildErrorRatioRule("analyticsTopViewers"));
        rules.add(buildErrorRatioRule("financeSettle"));

        DegradeRuleManager.loadRules(rules);
    }

    private DegradeRule buildErrorRatioRule(String resource) {
        DegradeRule rule = new DegradeRule(resource);
        rule.setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType());
        rule.setCount(ERROR_RATIO_THRESHOLD);
        rule.setTimeWindow(DEGRADE_TIME_WINDOW_SEC);
        rule.setMinRequestAmount(MIN_REQUEST_AMOUNT);
        rule.setStatIntervalMs(STAT_INTERVAL_MS);
        return rule;
    }

    private DegradeRule buildSlowRequestRule(String resource, long maxRtMs) {
        DegradeRule rule = new DegradeRule(resource);
        rule.setGrade(CircuitBreakerStrategy.SLOW_REQUEST_RATIO.getType());
        rule.setCount(maxRtMs);
        rule.setSlowRatioThreshold(ERROR_RATIO_THRESHOLD);
        rule.setTimeWindow(DEGRADE_TIME_WINDOW_SEC);
        rule.setMinRequestAmount(MIN_REQUEST_AMOUNT);
        rule.setStatIntervalMs(STAT_INTERVAL_MS);
        return rule;
    }
}
