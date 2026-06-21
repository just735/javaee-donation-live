package com.javaee.donation.simulator.config;

import com.javaee.donation.common.context.TraceContext;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    /** 观众服务重试：最多2次，间隔150ms（压测场景下快速重试） */
    private static final Retryer VIEWER_RETRYER = new Retryer.Default(150, 500, 2);

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
    public Retryer viewerRetryer() {
        return VIEWER_RETRYER;
    }
}
