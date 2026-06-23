package com.javaee.donation.viewer.config;

import com.javaee.donation.common.context.TraceContext;
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
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
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
        return new TraceAwareExecutor(executor);
    }

    @Bean(name = "settlementExecutor")
    public Executor settlementExecutor() {
        AtomicInteger counter = new AtomicInteger();
        int poolSize = properties.getSettlement().getThreadPoolSize();
        int queueCapacity = properties.getSettlement().getQueueCapacity();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
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
        return new TraceAwareExecutor(executor);
    }

    private static final class TraceAwareExecutor implements Executor, AutoCloseable {

        private final ThreadPoolExecutor delegate;

        private TraceAwareExecutor(ThreadPoolExecutor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(TraceContext.wrap(command));
        }

        @Override
        public void close() {
            delegate.shutdown();
        }
    }
}
