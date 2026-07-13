package io.github.cococzl.coquartz.exception;

import org.quartz.JobExecutionException;

/** A cooperative timeout: interruption was requested, not a forced thread stop. */
public class TaskTimeoutException extends JobExecutionException {
    private final boolean terminationConfirmed;

    public TaskTimeoutException(long timeoutMs, boolean terminationConfirmed) {
        super(terminationConfirmed
                ? "Task timed out after " + timeoutMs + "ms and acknowledged interruption"
                : "Task timed out after " + timeoutMs + "ms; interruption requested but termination is unconfirmed");
        this.terminationConfirmed = terminationConfirmed;
    }

    public boolean isTerminationConfirmed() { return terminationConfirmed; }
}
