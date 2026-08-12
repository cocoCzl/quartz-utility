package io.github.cococzl.coquartz.service;

import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.dto.AsyncLogPipelineStatus;

/** @deprecated Prefer {@link TaskExecutionLogWriter}; retained for source compatibility. */
@Deprecated(forRemoval = false)
public interface AsyncTaskLogService extends TaskExecutionLogWriter {

    void logTaskExecutionAsync(TaskExecutionLog log);

    @Override
    default void write(TaskExecutionLog log) {
        logTaskExecutionAsync(log);
    }

    void flushLogsImmediately();

    @Override
    default void flush() {
        flushLogsImmediately();
    }

    void shutdown();

    int getQueueSize();

    /** Returns pipeline health without exposing execution payloads. */
    default AsyncLogPipelineStatus getPipelineStatus() {
        return new AsyncLogPipelineStatus(getQueueSize(), 0, 0, 0, getQueueSize());
    }
}
