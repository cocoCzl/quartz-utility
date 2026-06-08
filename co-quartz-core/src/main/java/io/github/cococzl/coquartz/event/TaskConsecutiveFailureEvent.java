package io.github.cococzl.coquartz.event;

import org.springframework.context.ApplicationEvent;

public class TaskConsecutiveFailureEvent extends ApplicationEvent {

    private final String jobKey;
    private final int threshold;
    private final int recentFailures;

    public TaskConsecutiveFailureEvent(Object source, String jobKey, int threshold, int recentFailures) {
        super(source);
        this.jobKey = jobKey;
        this.threshold = threshold;
        this.recentFailures = recentFailures;
    }

    public String getJobKey() { return jobKey; }
    public int getThreshold() { return threshold; }
    public int getRecentFailures() { return recentFailures; }
}