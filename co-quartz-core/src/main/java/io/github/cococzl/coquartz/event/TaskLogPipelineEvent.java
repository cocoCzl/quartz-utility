package io.github.cococzl.coquartz.event;

import org.springframework.context.ApplicationEvent;

/** Published when the best-effort execution-log pipeline drops or cannot persist records. */
public class TaskLogPipelineEvent extends ApplicationEvent {
    public enum Type { QUEUE_FULL, PERMANENT_WRITE_FAILURE, SHUTDOWN_UNFLUSHED }

    private final Type type;
    private final long count;

    public TaskLogPipelineEvent(Object source, Type type, long count) {
        super(source);
        this.type = type;
        this.count = count;
    }

    public Type getType() { return type; }
    public long getCount() { return count; }
}
