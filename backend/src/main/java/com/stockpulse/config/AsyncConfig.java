package com.stockpulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dedicated, bounded thread pool for the agentic loop (@Async event listeners) so a burst of
 * stock/order updates can never spawn unbounded threads. CallerRunsPolicy applies backpressure
 * on the publishing thread rather than dropping work when the queue is full.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "agenticTaskExecutor")
    public Executor agenticTaskExecutor(
            @Value("${commerce.async.core-pool-size}") int corePoolSize,
            @Value("${commerce.async.max-pool-size}") int maxPoolSize,
            @Value("${commerce.async.queue-capacity}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("agentic-loop-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
