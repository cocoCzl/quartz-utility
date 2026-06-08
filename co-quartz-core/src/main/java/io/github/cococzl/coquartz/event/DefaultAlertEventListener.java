package io.github.cococzl.coquartz.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

public class DefaultAlertEventListener {

    private static final Logger log = LoggerFactory.getLogger(DefaultAlertEventListener.class);

    @EventListener
    public void onTaskFailure(TaskFailureEvent event) {
        log.warn("Task failure: jobKey={}, error={}", event.getJobKey(), event.getErrorMessage());
    }

    @EventListener
    public void onTaskTimeout(TaskTimeoutEvent event) {
        log.warn("Task timeout: jobKey={}, timeoutMs={}", event.getJobKey(), event.getTimeoutMs());
    }

    @EventListener
    public void onTaskSlow(TaskSlowEvent event) {
        log.warn("Slow task: jobKey={}, executionTimeMs={}, thresholdMs={}",
                event.getJobKey(), event.getExecutionTimeMs(), event.getThresholdMs());
    }

    @EventListener
    public void onConsecutiveFailure(TaskConsecutiveFailureEvent event) {
        log.error("Consecutive failure: jobKey={}, threshold={}, recentFailures={}",
                event.getJobKey(), event.getThreshold(), event.getRecentFailures());
    }
}