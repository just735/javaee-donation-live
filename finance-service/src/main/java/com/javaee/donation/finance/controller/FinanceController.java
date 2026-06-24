package com.javaee.donation.finance.controller;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.CommissionRuleRequest;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.common.model.StreamerBalanceResponse;
import com.javaee.donation.finance.dto.CommissionRuleResponse;
import com.javaee.donation.finance.dto.ReconciliationResult;
import com.javaee.donation.finance.dto.RewardSettleResponse;
import com.javaee.donation.finance.dto.WithdrawResponse;
import com.javaee.donation.finance.service.BalanceReconciliationService;
import com.javaee.donation.finance.service.FinanceSettlementService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceSettlementService financeSettlementService;
    private final BalanceReconciliationService reconciliationService;

    public FinanceController(FinanceSettlementService financeSettlementService,
                             BalanceReconciliationService reconciliationService) {
        this.financeSettlementService = financeSettlementService;
        this.reconciliationService = reconciliationService;
    }

    /**
     * POST /api/finance/rewards/settle
     * 打赏入账接口：幂等、落明细、算提成、更新余额
     */
    @PostMapping("/rewards/settle")
    public ApiResponse<RewardSettleResponse> settle(@RequestBody RewardRequest request) {
        return ApiResponse.success(TraceContext.getTraceId(), financeSettlementService.settle(request));
    }

    /**
     * POST /api/finance/commission-rules
     * 新增提成规则：旧规则自动关闭，不覆盖历史
     */
    @PostMapping("/commission-rules")
    public ApiResponse<CommissionRuleResponse> saveCommissionRule(@RequestBody CommissionRuleRequest request) {
        return ApiResponse.success(TraceContext.getTraceId(), financeSettlementService.saveCommissionRule(request));
    }

    /**
     * GET /api/finance/streamers/{streamerId}/balance
     * 查询主播可领取余额
     */
    @GetMapping("/streamers/{streamerId}/balance")
    public ApiResponse<StreamerBalanceResponse> balance(@PathVariable String streamerId) {
        return ApiResponse.success(TraceContext.getTraceId(), financeSettlementService.getBalance(streamerId));
    }

    /**
     * POST /api/finance/streamers/{streamerId}/withdraw
     * 主播提现扣减余额（带乐观锁防并发）
     */
    @PostMapping("/streamers/{streamerId}/withdraw")
    public ApiResponse<WithdrawResponse> withdraw(@PathVariable String streamerId,
                                                   @RequestBody Map<String, BigDecimal> body) {
        BigDecimal amount = body.get("amount");
        StreamerBalanceResponse before = financeSettlementService.getBalance(streamerId);
        try {
            StreamerBalanceResponse after = financeSettlementService.deductBalance(streamerId, amount);

            WithdrawResponse resp = WithdrawResponse.builder()
                    .streamerId(streamerId)
                    .withdrawAmount(amount)
                    .beforeBalance(before.getWithdrawableAmount())
                    .afterBalance(after.getWithdrawableAmount())
                    .withdrawnAt(LocalDateTime.now())
                    .status("SUCCESS")
                    .build();
            return ApiResponse.success(TraceContext.getTraceId(), resp);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(TraceContext.getTraceId(), "INSUFFICIENT_BALANCE", e.getMessage());
        }
    }

    /**
     * POST /api/finance/reconciliation/precompute
     * 手动触发全量余额预计算
     */
    @PostMapping("/reconciliation/precompute")
    public ApiResponse<String> precompute() {
        int updated = reconciliationService.precomputeAll();
        return ApiResponse.success(TraceContext.getTraceId(), "precomputed " + updated + " streamer(s)");
    }

    /**
     * POST /api/finance/reconciliation/check
     * 手动触发对账检查（可选自动修正 autoCorrect=true）
     */
    @PostMapping("/reconciliation/check")
    public ApiResponse<ReconciliationResult> check(@RequestParam(defaultValue = "false") boolean autoCorrect) {
        return ApiResponse.success(TraceContext.getTraceId(), reconciliationService.reconcile(autoCorrect));
    }
}
