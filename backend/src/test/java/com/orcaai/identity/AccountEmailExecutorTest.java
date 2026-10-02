package com.orcaai.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AccountEmailExecutorTest {

    @Test
    void saturationMakesTheCallerSendInsteadOfDroppingTheEmail() throws Exception {
        ThreadPoolTaskExecutor executor = AccountEmailExecutorConfig.boundedExecutor(1, 1);
        executor.initialize();
        int emails = 20;
        AtomicInteger sent = new AtomicInteger();
        Set<String> threads = ConcurrentHashMap.newKeySet();
        CountDownLatch done = new CountDownLatch(emails);
        try {
            for (int i = 0; i < emails; i++) {
                executor.execute(() -> {
                    sleep(20);
                    threads.add(Thread.currentThread().getName());
                    sent.incrementAndGet();
                    done.countDown();
                });
            }
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdown();
        }

        assertThat(sent).hasValue(emails);
        // The pool alone (1 thread + 1 queued) could not take 20 at once: the caller ran the rest.
        assertThat(threads).contains(Thread.currentThread().getName()).anyMatch(name -> name.startsWith("account-email-"));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
