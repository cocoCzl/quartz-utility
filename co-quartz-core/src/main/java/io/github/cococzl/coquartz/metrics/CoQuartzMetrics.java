package io.github.cococzl.coquartz.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CoQuartzMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeJobs;

    public CoQuartzMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.activeJobs = new AtomicInteger(0);
        meterRegistry.gauge("co_quartz_job_active", activeJobs, AtomicInteger::get);
    }

    public void recordSuccess(String jobKey, long durationMs) {
        Counter.builder("co_quartz_job_executions_total")
                .tag("jobKey", jobKey)
                .tag("state", "success")
                .description("Number of job executions")
                .register(meterRegistry)
                .increment();
        Timer.builder("co_quartz_job_execution_duration")
                .tag("jobKey", jobKey)
                .description("Job execution duration")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordFailure(String jobKey, long durationMs) {
        Counter.builder("co_quartz_job_executions_total")
                .tag("jobKey", jobKey)
                .tag("state", "fail")
                .description("Number of job executions")
                .register(meterRegistry)
                .increment();
        Timer.builder("co_quartz_job_execution_duration")
                .tag("jobKey", jobKey)
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
}