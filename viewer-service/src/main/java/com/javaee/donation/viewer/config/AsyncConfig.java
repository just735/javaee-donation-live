package com.javaee.donation.viewer.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "rewardNotifyExecutor")
    public Executor rewardNotifyExecutor() {
        return Executors.newFixedThreadPool(4);
    }

    /** 异步入账线程池：专门用于异步调用财务服务，与打赏接口响应解耦 */
    @Bean(name = "settlementExecutor")
    public Executor settlementExecutor() {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(64, r -> {
            Thread t = new Thread(r, "settle-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
    }
}
