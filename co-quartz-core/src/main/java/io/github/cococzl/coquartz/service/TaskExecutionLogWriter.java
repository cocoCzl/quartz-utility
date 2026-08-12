package io.github.cococzl.coquartz.service;

import io.github.cococzl.coquartz.dto.AsyncLogPipelineStatus;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;

/** Writes execution logs without exposing whether the implementation is synchronous or queued. */
public interface TaskExecutionLogWriter {
    void write(TaskExecutionLog log);

    default void flush() {
    }

    default void shutdown() {
    }

    default AsyncLogPipelineStatus getPipelineStatus() {
        return new AsyncLogPipelineStatus(0, 0, 0, 0, 0);
    }
}
