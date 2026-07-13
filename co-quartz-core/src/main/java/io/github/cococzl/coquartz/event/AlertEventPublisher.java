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
    private final ConcurrentHashMap<String, FailureWindow> failureWindows = new ConcurrentHashMap<>();

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
            eventPublisher.publishEvent(new TaskFailureEvent(this, jobKey, errorMessage, stackTrace));
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
        dispatch(() -> {
            eventPublisher.publishEvent(new TaskSlowEvent(this, jobKey, executionTimeMs, thresholdMs));
        }, "TaskSlowEvent", jobKey);
    }

    public void publishConsecutiveFailureIfNeeded(String jobKey) {
        int threshold = properties.getMonitoring().getConsecutiveFailureThreshold();
        FailureWindow window = failureWindows.computeIfAbsent(jobKey, ignored -> new FailureWindow());
        int failures = window.failures.incrementAndGet();
        if (failures >= threshold && window.alerted.compareAndSet(false, true)) {
            dispatch(() -> eventPublisher.publishEvent(new TaskConsecutiveFailureEvent(this, jobKey, threshold, failures)),
                    "TaskConsecutiveFailureEvent", jobKey);
        }
    }

    public void recordSuccess(String jobKey) { failureWindows.remove(jobKey); }

    public void publishLogPipeline(TaskLogPipelineEvent.Type type, long count) {
        dispatch(() -> eventPublisher.publishEvent(new TaskLogPipelineEvent(this, type, count)),
                "TaskLogPipelineEvent", "log-pipeline");
    }

    private void dispatch(Runnable action, String eventName, String jobKey) {
        try {
            executor.execute(() -> {
                try { action.run(); } catch (Exception e) { log.error("Failed to publish {} for job: {}", eventName, jobKey, e); }
            });
        } catch (Exception e) { log.error("Failed to dispatch {} for job: {}", eventName, jobKey, e); }
    }

    private static final class FailureWindow {
        private final java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicBoolean alerted = new java.util.concurrent.atomic.AtomicBoolean();
    }
}
