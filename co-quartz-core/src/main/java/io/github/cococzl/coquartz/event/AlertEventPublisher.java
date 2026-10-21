package io.github.cococzl.coquartz.event;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class AlertEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlertEventPublisher.class);

    private final ApplicationEventPublisher eventPublisher;
    private final CoQuartzProperties properties;
    private final Executor executor;
    private io.github.cococzl.coquartz.service.LogSanitizer sanitizer = new io.github.cococzl.coquartz.service.DefaultLogSanitizer();
    public void setLogSanitizer(io.github.cococzl.coquartz.service.LogSanitizer sanitizer) { this.sanitizer = sanitizer; }
    private final java.util.LinkedHashMap<String, FailureWindow> failureWindows = new java.util.LinkedHashMap<>(16, .75f, true);
    private final java.util.LinkedHashMap<String, Long> lastDispatch = new java.util.LinkedHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong dropped = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong coalesced = new java.util.concurrent.atomic.AtomicLong();
    public long getDroppedCount() { return dropped.get(); }
    public long getCoalescedCount() { return coalesced.get(); }
    public synchronized int getTrackedFailureJobCount() { expireWindows(); return failureWindows.size(); }
    private synchronized void expireWindows() {
        long cutoff = System.nanoTime() - java.util.concurrent.TimeUnit.HOURS.toNanos(1);
        failureWindows.values().removeIf(window -> window.updated < cutoff);
        while (failureWindows.size() > 1024) failureWindows.remove(failureWindows.keySet().iterator().next());
    }

    public AlertEventPublisher(ApplicationEventPublisher eventPublisher,
                                TaskLogRepository ignoredRepository,
                                CoQuartzProperties properties) {
        this(eventPublisher, properties, Runnable::run);
    }

    public AlertEventPublisher(ApplicationEventPublisher eventPublisher,
                                CoQuartzProperties properties) {
        this(eventPublisher, properties, Runnable::run);
    }

    public AlertEventPublisher(ApplicationEventPublisher eventPublisher, CoQuartzProperties properties, Executor executor) {
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.executor = executor == null ? Runnable::run : executor;
    }

    public void publishFailure(String jobKey, String errorMessage, String stackTrace) {
        dispatch(() -> {
            eventPublisher.publishEvent(new TaskFailureEvent(this, jobKey, sanitizer.sanitize(errorMessage), properties.getLog().isCaptureStackTrace() ? sanitizer.sanitize(stackTrace) : null));
        }, "TaskFailureEvent", jobKey);
    }

    public void publishTimeout(String jobKey, long timeoutMs) {
        publishTimeout(jobKey, timeoutMs, false);
    }

    public void publishTimeout(String jobKey, long timeoutMs, boolean terminationConfirmed) {
        dispatch(() -> {
            eventPublisher.publishEvent(new TaskTimeoutEvent(this, jobKey, timeoutMs, terminationConfirmed));
        }, "TaskTimeoutEvent", jobKey);
    }

    public void publishSlowTask(String jobKey, long executionTimeMs, long thresholdMs) {
        if (!properties.getMonitoring().isEnabled()) return;
        dispatch(() -> {
            eventPublisher.publishEvent(new TaskSlowEvent(this, jobKey, executionTimeMs, thresholdMs));
        }, "TaskSlowEvent", jobKey);
    }

    public synchronized void publishConsecutiveFailureIfNeeded(String jobKey) {
        if (!properties.getMonitoring().isEnabled()) return;
        int threshold = properties.getMonitoring().getConsecutiveFailureThreshold();
        expireWindows();
        FailureWindow window = failureWindows.computeIfAbsent(jobKey, ignored -> new FailureWindow());
        window.updated = System.nanoTime();
        expireWindows();
        int failures = window.failures.incrementAndGet();
        if (failures >= threshold && window.alerted.compareAndSet(false, true)) {
            dispatch(() -> eventPublisher.publishEvent(new TaskConsecutiveFailureEvent(this, jobKey, threshold, failures)),
                    "TaskConsecutiveFailureEvent", jobKey);
        }
    }

    public synchronized void recordSuccess(String jobKey) { failureWindows.remove(jobKey); }

    public void publishLogPipeline(TaskLogPipelineEvent.Type type, long count) {
        dispatch(() -> eventPublisher.publishEvent(new TaskLogPipelineEvent(this, type, count)),
                "TaskLogPipelineEvent", "log-pipeline");
    }

    public void publishAuditFailure(String jobKey, ReliableAuditFailureEvent.Phase phase) {
        dispatch(() -> eventPublisher.publishEvent(new ReliableAuditFailureEvent(this, jobKey, phase)),
                "ReliableAuditFailureEvent-" + phase, jobKey);
    }

    private void dispatch(Runnable action, String eventName, String jobKey) {
        if (eventName.equals("TaskFailureEvent") || eventName.equals("TaskLogPipelineEvent")
                || eventName.startsWith("ReliableAuditFailureEvent")) {
            synchronized (lastDispatch) {
                long now = System.nanoTime();
                long cutoff = now - java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
                lastDispatch.values().removeIf(time -> time < cutoff);
                String identity = eventName + "|" + jobKey;
                if (lastDispatch.containsKey(identity)) { coalesced.incrementAndGet(); return; }
                if (lastDispatch.size() >= 1024) lastDispatch.remove(lastDispatch.keySet().iterator().next());
                lastDispatch.put(identity, now);
            }
        }
        try {
            executor.execute(() -> {
                try { action.run(); } catch (Exception e) { log.error("Failed to publish {} for job: {}: {}", eventName, sanitizer.sanitize(jobKey), sanitizer.sanitize(e.getMessage())); }
            });
        } catch (Exception e) { dropped.incrementAndGet(); log.error("Failed to dispatch {} for job: {}: {}", eventName, sanitizer.sanitize(jobKey), sanitizer.sanitize(e.getMessage())); }
    }

    private static final class FailureWindow {
        private long updated = System.nanoTime();
        private final java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicBoolean alerted = new java.util.concurrent.atomic.AtomicBoolean();
    }
}
