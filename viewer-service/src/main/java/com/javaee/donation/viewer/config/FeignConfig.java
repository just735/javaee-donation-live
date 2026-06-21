package com.javaee.donation.viewer.config;

import com.javaee.donation.common.context.TraceContext;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    /** 财务服务重试：最多3次，间隔100ms起步指数退避 */
    private static final Retryer FINANCE_RETRYER = new Retryer.Default(100, 1000, 3);

    /** 分析服务重试：最多2次，间隔200ms（画像查询允许短暂延迟） */
    private static final Retryer ANALYTICS_RETRYER = new Retryer.Default(200, 500, 2);

    @Bean
    public RequestInterceptor traceIdRequestInterceptor() {
        return template -> {
            String traceId = TraceContext.getTraceId();
            if (traceId != null && !traceId.isBlank()) {
                template.header("traceId", traceId);
            }
        };
    }

    @Bean
    public Retryer financeRetryer() {
        return FINANCE_RETRYER;
    }
}
