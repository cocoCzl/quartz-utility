package io.github.cococzl.coquartz.event;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AlertEventPublisherAsyncTest {
    @Test
    void slowListenerDoesNotBlockCallerAndConsecutiveWindowAlertsOnceThenResets() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch delivered = new CountDownLatch(3);
            AtomicInteger consecutiveAlerts = new AtomicInteger();
            ApplicationEventPublisher events = event -> {
                try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                if (event instanceof TaskConsecutiveFailureEvent) consecutiveAlerts.incrementAndGet();
                delivered.countDown();
            };
            CoQuartzProperties properties = new CoQuartzProperties();
            properties.getMonitoring().setConsecutiveFailureThreshold(2);
            AlertEventPublisher publisher = new AlertEventPublisher(events, properties, executor);

            long started = System.nanoTime();
            publisher.publishFailure("DEFAULT.job", "failure", null);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertThat(elapsedMs).isLessThan(100);

            publisher.publishConsecutiveFailureIfNeeded("DEFAULT.job");
            publisher.publishConsecutiveFailureIfNeeded("DEFAULT.job");
            publisher.publishConsecutiveFailureIfNeeded("DEFAULT.job");
            publisher.recordSuccess("DEFAULT.job");
            publisher.publishConsecutiveFailureIfNeeded("DEFAULT.job");
            publisher.publishConsecutiveFailureIfNeeded("DEFAULT.job");

            assertThat(delivered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(consecutiveAlerts.get()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }
}
