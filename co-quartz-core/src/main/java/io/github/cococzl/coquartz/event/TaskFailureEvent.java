package io.github.cococzl.coquartz.event;

import org.springframework.context.ApplicationEvent;

public class TaskFailureEvent extends ApplicationEvent {

    private final String jobKey;
    private final String errorMessage;
    private final String stackTrace;

    public TaskFailureEvent(Object source, String jobKey, String errorMessage, String stackTrace) {
        super(source);
        this.jobKey = jobKey;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
    }

    public String getJobKey() { return jobKey; }
    public String getErrorMessage() { return errorMessage; }
    public String getStackTrace() { return stackTrace; }
}