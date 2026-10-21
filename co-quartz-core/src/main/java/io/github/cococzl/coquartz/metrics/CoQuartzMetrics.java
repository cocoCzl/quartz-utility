package io.github.cococzl.coquartz.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import io.github.cococzl.coquartz.dto.AsyncLogPipelineStatus;
import io.github.cococzl.coquartz.service.TaskExecutionLogWriter;
import io.github.cococzl.coquartz.service.TaskLogRepository;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

public class CoQuartzMetrics implements AutoCloseable {
    private final java.util.concurrent.ScheduledThreadPoolExecutor sampler = new java.util.concurrent.ScheduledThreadPoolExecutor(1,
            task -> { Thread thread = new Thread(task, "co-quartz-metric-sampler"); thread.setDaemon(true); return thread; });
    private volatile double cachedAuditStarted = Double.NaN;
    private volatile long lastAuditRefresh;
    private final java.util.concurrent.atomic.AtomicLong auditRefreshFailures = new java.util.concurrent.atomic.AtomicLong();
    public void close() { sampler.shutdownNow(); }

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeJobs;
    private final AtomicInteger unconfirmedTerminations = new AtomicInteger();
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
        meterRegistry.gauge("co_quartz_job_termination_unconfirmed", unconfirmedTerminations, AtomicInteger::get);
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

    public void terminationUnconfirmed() {
        unconfirmedTerminations.incrementAndGet();
        meterRegistry.counter("co_quartz_job_interrupt_requests_total").increment();
    }

    public void terminationConfirmed() { unconfirmedTerminations.decrementAndGet(); }

    public void recordRetry(String jobKey) { count("co_quartz_job_retries_total", metricJobKey(jobKey)); }
    public void recordTimeout(String jobKey) { count("co_quartz_job_timeouts_total", metricJobKey(jobKey)); }

    private void count(String name, String job) {
        Counter.builder(name).tag("job", job).register(meterRegistry).increment();
    }

    private synchronized String metricJobKey(String jobKey) {
        if (knownJobTags.contains(jobKey) || knownJobTags.size() < maxJobTags && knownJobTags.add(jobKey)) return jobKey;
        return "other";
    }

    public void bindAlerts(io.github.cococzl.coquartz.event.AlertEventPublisher alerts) {
        Gauge.builder("co_quartz_alert_dropped", alerts, io.github.cococzl.coquartz.event.AlertEventPublisher::getDroppedCount).register(meterRegistry);
        Gauge.builder("co_quartz_alert_coalesced", alerts, io.github.cococzl.coquartz.event.AlertEventPublisher::getCoalescedCount).register(meterRegistry);
    }

    public void bindLogPipeline(TaskExecutionLogWriter pipeline) {
        Gauge.builder("co_quartz_log_queue_size", pipeline, p -> p.getPipelineStatus().queueSize()).register(meterRegistry);
        Gauge.builder("co_quartz_log_dropped", pipeline, p -> p.getPipelineStatus().droppedCount()).register(meterRegistry);
        Gauge.builder("co_quartz_log_write_failures", pipeline, p -> p.getPipelineStatus().writeFailureCount()).register(meterRegistry);
        Gauge.builder("co_quartz_log_permanent_failures", pipeline, p -> p.getPipelineStatus().permanentFailureCount()).register(meterRegistry);
        Gauge.builder("co_quartz_log_unflushed", pipeline, p -> p.getPipelineStatus().unflushedCount()).register(meterRegistry);
    }

    public void bindReliableAudit(TaskLogRepository repository) {
        Gauge.builder("co_quartz_audit_started", this, value -> value.cachedAuditStarted).register(meterRegistry);
        Gauge.builder("co_quartz_audit_refresh_failures", auditRefreshFailures, java.util.concurrent.atomic.AtomicLong::get).register(meterRegistry);
        Gauge.builder("co_quartz_audit_sample_age_seconds", this, value -> value.lastAuditRefresh == 0 ? Double.NaN
                : (System.nanoTime() - value.lastAuditRefresh) / 1_000_000_000.0).register(meterRegistry);
        sampler.scheduleWithFixedDelay(() -> {
            try { cachedAuditStarted = repository.countStarted(); lastAuditRefresh = System.nanoTime(); }
            catch (RuntimeException failure) { auditRefreshFailures.incrementAndGet(); }
        }, 0, 5, java.util.concurrent.TimeUnit.SECONDS);
    }
}
