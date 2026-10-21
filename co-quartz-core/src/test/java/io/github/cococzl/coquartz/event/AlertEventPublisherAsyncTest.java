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
    void productionAlertExecutorHasBoundedBacklogAndNeverRunsRejectedWorkOnCaller() throws Exception {
        var executor = new io.github.cococzl.coquartz.config.CoQuartzCoreAutoConfiguration().coQuartzAlertExecutor();
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        try {
            executor.execute(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException ignored) { } });
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            int rejected = 0;
            for (int i = 0; i < 2000; i++) {
                try { executor.execute(() -> { }); } catch (java.util.concurrent.RejectedExecutionException full) { rejected++; }
            }
            assertThat(rejected).isPositive();
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void repeatedFailuresAreCoalescedAndDeletedJobStateIsCapacityBounded() {
        var config = new CoQuartzProperties();
        var delivered = new AtomicInteger();
        var publisher = new AlertEventPublisher(event -> delivered.incrementAndGet(), config);
        for (int i = 0; i < 10000; i++) publisher.publishFailure("same", "failure", null);
        assertThat(delivered).hasValue(1);
        assertThat(publisher.getCoalescedCount()).isEqualTo(9999);
        for (int i = 0; i < 5000; i++) publisher.publishConsecutiveFailureIfNeeded("removed-" + i);
        assertThat(publisher.getTrackedFailureJobCount()).isLessThanOrEqualTo(1024);
    }

    @Test
    void asynchronousFailureEventKeepsAttemptCorrelationAfterCallerClearsContext() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            var delivered = new CountDownLatch(1);
            var result = new java.util.concurrent.atomic.AtomicReference<TaskFailureEvent>();
            var publisher = new AlertEventPublisher(event -> {
                if (event instanceof TaskFailureEvent failure) result.set(failure);
                delivered.countDown();
            }, new CoQuartzProperties(), executor);
            var entry = new io.github.cococzl.coquartz.dto.TaskExecutionLog();
            entry.setExecutionId("execution-42"); entry.setAttempt(2); entry.setSchedulerInstanceId("node-a"); entry.setFinalAttempt(true);
            publisher.setExecutionCorrelation(entry);
            publisher.publishFailure("job", "failure", null);
            publisher.clearExecutionCorrelation();
            assertThat(delivered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(result.get().getCorrelation()).isEqualTo(new TaskExecutionCorrelation("execution-42", 2, "node-a", true));
        } finally { executor.shutdownNow(); }
    }

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
