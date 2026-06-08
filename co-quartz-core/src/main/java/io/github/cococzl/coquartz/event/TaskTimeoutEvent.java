package io.github.cococzl.coquartz.event;

import org.springframework.context.ApplicationEvent;

public class TaskTimeoutEvent extends ApplicationEvent {

    private final String jobKey;
    private final long timeoutMs;

    public TaskTimeoutEvent(Object source, String jobKey, long timeoutMs) {
        super(source);
        this.jobKey = jobKey;
        this.timeoutMs = timeoutMs;
    }

    public String getJobKey() { return jobKey; }
    public long getTimeoutMs() { return timeoutMs; }
}