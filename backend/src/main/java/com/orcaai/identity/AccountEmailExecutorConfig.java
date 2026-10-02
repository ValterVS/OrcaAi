package com.orcaai.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Small, bounded pool for account emails. Boot's default executor has an unbounded queue, which a
 * flood of sign-up or reset requests could grow without limit.
 *
 * <p>When the queue is full the email is dropped and logged instead of failing the request (the
 * transaction has already committed); the user can ask for it again.
 */
@Configuration(proxyBeanMethods = false)
class AccountEmailExecutorConfig {

    static final String EXECUTOR = "accountEmailExecutor";

    private static final Logger log = LoggerFactory.getLogger(AccountEmailExecutorConfig.class);

    @Bean(name = EXECUTOR, defaultCandidate = false)
    ThreadPoolTaskExecutor accountEmailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("account-email-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setRejectedExecutionHandler((task, pool) -> log.warn("Account email dropped: queue is full"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}
