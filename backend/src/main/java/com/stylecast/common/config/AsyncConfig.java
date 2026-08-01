package com.stylecast.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.core.task.AsyncTaskExecutor;

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
    public AsyncTaskExecutor liveRecommendationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("live-rec-gen-");
        executor.initialize();
        // Live recommendation generation resolves the event via
        // CurrentUserProvider (SecurityContextHolder), but @Async hands the
        // task to a REUSED pool thread that never had the calling HTTP
        // request's SecurityContext set on it - without this wrapper,
        // every background generation would fail authentication
        // immediately. This captures the SecurityContext from the thread
        // that schedules the task (already ownership-checked synchronously
        // in LiveRecommendationJobService before dispatching) and installs
        // it on the pool thread for the duration of the task, clearing it
        // afterwards.
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }
}
