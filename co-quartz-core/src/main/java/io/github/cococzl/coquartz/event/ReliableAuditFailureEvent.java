package io.github.cococzl.coquartz.event;

import org.springframework.context.ApplicationEvent;

/** Signals a synchronous reliable-audit write failure without exposing diagnostic payloads. */
public class ReliableAuditFailureEvent extends ApplicationEvent {
    public enum Phase { START, COMPLETE }

    private final String jobKey;
    private final Phase phase;

    public ReliableAuditFailureEvent(Object source, String jobKey, Phase phase) {
        super(source);
        this.jobKey = jobKey;
        this.phase = phase;
    }

    public String getJobKey() { return jobKey; }
    public Phase getPhase() { return phase; }
}
