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
            String traceId = TraceContext.currentOrCreate();
            if (traceId != null && !traceId.isBlank()) {
                template.header(TraceContext.TRACE_ID_HEADER, traceId);
                template.header(TraceContext.X_TRACE_ID_HEADER, traceId);
            }
        };
    }

    @Bean
    public Retryer viewerRetryer() {
        return NEVER_RETRY;
    }
}
