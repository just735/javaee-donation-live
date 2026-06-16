package com.javaee.donation.finance.controller;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.CommissionRuleRequest;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.common.model.StreamerBalanceResponse;
import com.javaee.donation.finance.service.FinanceSettlementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceSettlementService financeSettlementService;

    public FinanceController(FinanceSettlementService financeSettlementService) {
        this.financeSettlementService = financeSettlementService;
    }

    @PostMapping("/rewards/settle")
    public ApiResponse<String> settle(@RequestBody RewardRequest request) {
        return ApiResponse.success(TraceContext.getTraceId(), financeSettlementService.settle(request));
    }

    @PostMapping("/commission-rules")
    public ApiResponse<String> saveCommissionRule(@RequestBody CommissionRuleRequest request) {
        return ApiResponse.success(TraceContext.getTraceId(), financeSettlementService.saveCommissionRule(request));
    }

    @GetMapping("/streamers/{streamerId}/balance")
    public ApiResponse<StreamerBalanceResponse> balance(@PathVariable String streamerId) {
        return ApiResponse.success(TraceContext.getTraceId(), financeSettlementService.getBalance(streamerId));
    }
}
