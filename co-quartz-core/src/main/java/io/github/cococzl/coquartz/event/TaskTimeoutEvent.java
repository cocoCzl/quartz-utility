package io.github.cococzl.coquartz.event;

import org.springframework.context.ApplicationEvent;

public class TaskTimeoutEvent extends ApplicationEvent {

    private final String jobKey;
    private final long timeoutMs;
    private final boolean terminationConfirmed;

    public TaskTimeoutEvent(Object source, String jobKey, long timeoutMs) {
        this(source, jobKey, timeoutMs, false);
    }

    public TaskTimeoutEvent(Object source, String jobKey, long timeoutMs, boolean terminationConfirmed) {
        super(source);
        this.jobKey = jobKey;
        this.timeoutMs = timeoutMs;
        this.terminationConfirmed = terminationConfirmed;
    }

    public String getJobKey() { return jobKey; }
    public long getTimeoutMs() { return timeoutMs; }
    public boolean isTerminationConfirmed() { return terminationConfirmed; }
}
