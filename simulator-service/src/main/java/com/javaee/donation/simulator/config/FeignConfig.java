package com.javaee.donation.simulator.config;

import com.javaee.donation.common.context.TraceContext;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    private static final Retryer NEVER_RETRY = Retryer.NEVER_RETRY;

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
        return NEVER_RETRY;
    }
}
