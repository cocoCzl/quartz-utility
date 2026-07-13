package io.github.cococzl.coquartz.metrics;

import io.github.cococzl.coquartz.dto.AsyncLogPipelineStatus;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoQuartzMetricsTest {
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
