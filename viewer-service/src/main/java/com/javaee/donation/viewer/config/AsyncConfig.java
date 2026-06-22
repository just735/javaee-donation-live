package com.javaee.donation.viewer.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {

    private final ViewerRewardProperties properties;

    public AsyncConfig(ViewerRewardProperties properties) {
        this.properties = properties;
    }

    @Bean(name = "rewardNotifyExecutor")
    public Executor rewardNotifyExecutor() {
        return new ThreadPoolExecutor(
                4,
                4,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(256),
                runnable -> {
                    Thread thread = new Thread(runnable, "reward-notify");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Bean(name = "settlementExecutor")
    public Executor settlementExecutor() {
        AtomicInteger counter = new AtomicInteger();
        int poolSize = properties.getSettlement().getThreadPoolSize();
        int queueCapacity = properties.getSettlement().getQueueCapacity();
        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "settle-" + counter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }
}
