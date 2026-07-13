package io.github.cococzl.coquartz.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import io.github.cococzl.coquartz.dto.AsyncLogPipelineStatus;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.github.cococzl.coquartz.service.TaskLogRepository;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

public class CoQuartzMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeJobs;
    private final int maxJobTags;
    private final ConcurrentHashMap.KeySetView<String, Boolean> knownJobTags = ConcurrentHashMap.newKeySet();

    public CoQuartzMetrics(MeterRegistry meterRegistry) {
        this(meterRegistry, 100);
    }

    public CoQuartzMetrics(MeterRegistry meterRegistry, int maxJobTags) {
        this.meterRegistry = meterRegistry;
        this.maxJobTags = Math.max(0, maxJobTags);
        this.activeJobs = new AtomicInteger(0);
        meterRegistry.gauge("co_quartz_job_active", activeJobs, AtomicInteger::get);
    }

    public void recordSuccess(String jobKey, long durationMs) {
        Counter.builder("co_quartz_job_executions_total")
                .tag("job", metricJobKey(jobKey))
                .tag("state", "success")
                .description("Number of job executions")
                .register(meterRegistry)
                .increment();
        Timer.builder("co_quartz_job_execution_duration")
                .tag("job", metricJobKey(jobKey))
                .description("Job execution duration")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordFailure(String jobKey, long durationMs) {
        Counter.builder("co_quartz_job_executions_total")
                .tag("job", metricJobKey(jobKey))
                .tag("state", "fail")
                .description("Number of job executions")
                .register(meterRegistry)
                .increment();
        Timer.builder("co_quartz_job_execution_duration")
                .tag("job", metricJobKey(jobKey))
                .description("Job execution duration")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void jobStarted() {
        activeJobs.incrementAndGet();
    }

    public void jobFinished() {
        activeJobs.decrementAndGet();
    }

    public void recordRetry(String jobKey) { count("co_quartz_job_retries_total", metricJobKey(jobKey)); }
    public void recordTimeout(String jobKey) { count("co_quartz_job_timeouts_total", metricJobKey(jobKey)); }

    private void count(String name, String job) {
        Counter.builder(name).tag("job", job).register(meterRegistry).increment();
    }

    private String metricJobKey(String jobKey) {
        if (knownJobTags.contains(jobKey) || knownJobTags.size() < maxJobTags && knownJobTags.add(jobKey)) return jobKey;
        return "other";
    }

    public void bindLogPipeline(AsyncTaskLogService pipeline) {
        Gauge.builder("co_quartz_log_queue_size", pipeline, p -> p.getPipelineStatus().queueSize()).register(meterRegistry);
        Gauge.builder("co_quartz_log_dropped", pipeline, p -> p.getPipelineStatus().droppedCount()).register(meterRegistry);
        Gauge.builder("co_quartz_log_write_failures", pipeline, p -> p.getPipelineStatus().writeFailureCount()).register(meterRegistry);
        Gauge.builder("co_quartz_log_permanent_failures", pipeline, p -> p.getPipelineStatus().permanentFailureCount()).register(meterRegistry);
        Gauge.builder("co_quartz_log_unflushed", pipeline, p -> p.getPipelineStatus().unflushedCount()).register(meterRegistry);
    }

    public void bindReliableAudit(TaskLogRepository repository) {
        Gauge.builder("co_quartz_audit_started", repository, TaskLogRepository::countStarted).register(meterRegistry);
    }
}
