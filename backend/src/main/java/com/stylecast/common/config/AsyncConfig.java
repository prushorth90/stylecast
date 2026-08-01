package com.stylecast.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async} methods and defines the bounded thread pool live
 * recommendation generation jobs (see {@code
 * com.stylecast.recommendation.LiveRecommendationJobService}) run on - kept
 * separate from Spring's default {@code SimpleAsyncTaskExecutor} (which is
 * unbounded and creates a new thread per task) so a burst of generation
 * requests across many events can never exhaust system threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("liveRecommendationExecutor")
    public ThreadPoolTaskExecutor liveRecommendationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("live-rec-gen-");
        executor.initialize();
        return executor;
    }
}
