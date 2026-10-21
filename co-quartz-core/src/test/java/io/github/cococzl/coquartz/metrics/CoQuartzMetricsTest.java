package io.github.cococzl.coquartz.metrics;

import io.github.cococzl.coquartz.dto.AsyncLogPipelineStatus;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoQuartzMetricsTest {
    @Test
    void auditGaugeScrapeNeverWaitsForSlowDatabase() {
        var registry = new SimpleMeterRegistry();
        var metrics = new CoQuartzMetrics(registry);
        var repository = org.mockito.Mockito.mock(io.github.cococzl.coquartz.service.TaskLogRepository.class);
        org.mockito.Mockito.when(repository.countStarted()).thenAnswer(call -> { Thread.sleep(300); return 3L; });
        metrics.bindReliableAudit(repository);
        long start = System.nanoTime();
        registry.get("co_quartz_audit_started").gauge().value();
        assertThat(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(100);
    }

    @Test
    void concurrentFirstSeenJobsNeverExceedTagBudget() throws Exception {
        var registry = new SimpleMeterRegistry();
        var metrics = new CoQuartzMetrics(registry, 1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(32);
        var gate = new java.util.concurrent.CyclicBarrier(32);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 32; i++) {
                String key = "job-" + i;
                tasks.add(pool.submit(() -> { try { gate.await(); metrics.recordSuccess(key, 1); }
                    catch (Exception error) { throw new RuntimeException(error); } }));
            }
            for (var task : tasks) task.get();
            long jobs = registry.find("co_quartz_job_executions_total").counters().stream()
                    .map(counter -> counter.getId().getTag("job")).filter(job -> !job.equals("other")).distinct().count();
            assertThat(jobs).isLessThanOrEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void capsDynamicJobTagsAndExposesPipelineGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CoQuartzMetrics metrics = new CoQuartzMetrics(registry, 1);
        metrics.recordSuccess("DEFAULT.first", 10);
        metrics.recordFailure("DEFAULT.second", 10);
        metrics.recordRetry("DEFAULT.second");
        metrics.recordTimeout("DEFAULT.second");
        metrics.bindLogPipeline(new AsyncTaskLogService() {
            @Override public void logTaskExecutionAsync(TaskExecutionLog log) { }
            @Override public void flushLogsImmediately() { }
            @Override public void shutdown() { }
            @Override public int getQueueSize() { return 2; }
            @Override public AsyncLogPipelineStatus getPipelineStatus() { return new AsyncLogPipelineStatus(2, 3, 4, 5, 6); }
        });

        assertThat(registry.find("co_quartz_job_executions_total").tag("job", "other").counter().count()).isEqualTo(1);
        assertThat(registry.get("co_quartz_log_unflushed").gauge().value()).isEqualTo(6);
        assertThat(registry.get("co_quartz_job_retries_total").tag("job", "other").counter().count()).isEqualTo(1);
    }
}
