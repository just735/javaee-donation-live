package com.javaee.donation.finance.service;

import com.javaee.donation.common.model.CommissionRuleRequest;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.common.model.StreamerBalanceResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class FinanceSettlementService {

    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.30");

    private final Map<String, RewardRequest> settledRewards = new ConcurrentHashMap<>();
    private final Map<String, List<CommissionRuleRequest>> commissionRules = new ConcurrentHashMap<>();
    private final Map<String, StreamerBalanceResponse> balances = new ConcurrentHashMap<>();

    public String settle(RewardRequest request) {
        RewardRequest existing = settledRewards.putIfAbsent(request.getRewardNo(), request);
        if (existing != null) {
            return "duplicate reward ignored";
        }

        BigDecimal rewardAmount = defaultAmount(request.getRewardAmount());
        BigDecimal commissionRate = resolveCommissionRate(request.getStreamerId(), parseTime(request.getRewardTime()));
        BigDecimal commissionAmount = rewardAmount.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal withdrawableAmount = rewardAmount.subtract(commissionAmount).setScale(2, RoundingMode.HALF_UP);

        balances.compute(request.getStreamerId(), (streamerId, current) -> {
            StreamerBalanceResponse balance = current == null
                    ? new StreamerBalanceResponse(streamerId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                    : current;
            balance.setTotalRewardAmount(balance.getTotalRewardAmount().add(rewardAmount));
            balance.setTotalCommissionAmount(balance.getTotalCommissionAmount().add(commissionAmount));
            balance.setWithdrawableAmount(balance.getWithdrawableAmount().add(withdrawableAmount));
            return balance;
        });
        return "reward settled";
    }

    public String saveCommissionRule(CommissionRuleRequest request) {
        commissionRules.computeIfAbsent(request.getStreamerId(), key -> new ArrayList<>()).add(request);
        commissionRules.get(request.getStreamerId()).sort(Comparator.comparing(rule -> parseTime(rule.getEffectiveFrom())));
        return "rule accepted";
    }

    public StreamerBalanceResponse getBalance(String streamerId) {
        return balances.getOrDefault(streamerId,
                new StreamerBalanceResponse(streamerId, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private BigDecimal resolveCommissionRate(String streamerId, LocalDateTime rewardTime) {
        List<CommissionRuleRequest> rules = commissionRules.get(streamerId);
        if (rules == null || rules.isEmpty()) {
            return DEFAULT_COMMISSION_RATE;
        }
        for (CommissionRuleRequest rule : rules) {
            LocalDateTime effectiveFrom = parseTime(rule.getEffectiveFrom());
            LocalDateTime effectiveTo = parseNullableTime(rule.getEffectiveTo());
            boolean matchesFrom = !rewardTime.isBefore(effectiveFrom);
            boolean matchesTo = effectiveTo == null || rewardTime.isBefore(effectiveTo);
            if (matchesFrom && matchesTo) {
                return defaultAmount(rule.getCommissionRate());
            }
        }
        return DEFAULT_COMMISSION_RATE;
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        return LocalDateTime.parse(value);
    }

    private LocalDateTime parseNullableTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value);
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
