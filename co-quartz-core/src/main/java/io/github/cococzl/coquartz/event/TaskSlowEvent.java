package io.github.cococzl.coquartz.event;

import org.springframework.context.ApplicationEvent;

public class TaskSlowEvent extends ApplicationEvent {

    private final String jobKey;
    private final long executionTimeMs;
    private final long thresholdMs;

    public TaskSlowEvent(Object source, String jobKey, long executionTimeMs, long thresholdMs) {
        super(source);
        this.jobKey = jobKey;
        this.executionTimeMs = executionTimeMs;
        this.thresholdMs = thresholdMs;
    }

    public String getJobKey() { return jobKey; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public long getThresholdMs() { return thresholdMs; }
}