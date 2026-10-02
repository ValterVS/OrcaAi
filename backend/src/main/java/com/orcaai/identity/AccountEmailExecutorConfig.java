package com.orcaai.identity;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Small, bounded pool for account emails. Boot's default executor has an unbounded queue, which a
 * flood of requests could grow without limit.
 *
 * <p>When threads and queue are full, the email is sent by the calling thread (backpressure):
 * that request gets slower, but no transactional email is dropped.
 */
@Configuration(proxyBeanMethods = false)
class AccountEmailExecutorConfig {

    static final String EXECUTOR = "accountEmailExecutor";

    @Bean(name = EXECUTOR, defaultCandidate = false)
    ThreadPoolTaskExecutor accountEmailExecutor(
            @Value("${orcaai.account.email-threads:2}") int threads,
            @Value("${orcaai.account.email-queue-capacity:500}") int queueCapacity) {
        return boundedExecutor(threads, queueCapacity);
    }

    static ThreadPoolTaskExecutor boundedExecutor(int threads, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("account-email-");
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}
