package io.github.cococzl.coquartz.event;

import org.springframework.context.ApplicationEvent;

public abstract class CorrelatedTaskEvent extends ApplicationEvent {
    private TaskExecutionCorrelation correlation;
    protected CorrelatedTaskEvent(Object source) { super(source); }
    public TaskExecutionCorrelation getCorrelation() { return correlation; }
    void setCorrelation(TaskExecutionCorrelation correlation) { this.correlation = correlation; }
}
