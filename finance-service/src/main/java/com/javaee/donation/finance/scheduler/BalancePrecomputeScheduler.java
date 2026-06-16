package com.javaee.donation.finance.scheduler;

import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.finance.service.BalanceReconciliationService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BalancePrecomputeScheduler {

    private static final Logger log = LoggerFactory.getLogger(BalancePrecomputeScheduler.class);

    private final BalanceReconciliationService reconciliationService;

    public BalancePrecomputeScheduler(BalanceReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * 每5分钟全量余额预计算一次
     */
    @Scheduled(fixedRate = 300_000)
    public void precompute() {
        TraceContext.setTraceId("precompute-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            log.info("[{}] scheduled precompute triggered", TraceContext.getTraceId());
            reconciliationService.precomputeAll();
        } catch (Exception e) {
            log.error("[{}] scheduled precompute error", TraceContext.getTraceId(), e);
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 每10分钟全量对账一次，自动修正
     */
    @Scheduled(fixedRate = 600_000)
    public void autoReconcile() {
        TraceContext.setTraceId("reconcile-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            log.info("[{}] scheduled reconcile triggered", TraceContext.getTraceId());
            reconciliationService.reconcile(true);
        } catch (Exception e) {
            log.error("[{}] scheduled reconcile error", TraceContext.getTraceId(), e);
        } finally {
            TraceContext.clear();
        }
    }
}
