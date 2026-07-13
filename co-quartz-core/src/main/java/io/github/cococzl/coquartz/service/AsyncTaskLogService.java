package io.github.cococzl.coquartz.service;

import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.dto.AsyncLogPipelineStatus;

public interface AsyncTaskLogService {

    void logTaskExecutionAsync(TaskExecutionLog log);

    void flushLogsImmediately();

    void shutdown();

    int getQueueSize();

    /** Returns pipeline health without exposing execution payloads. */
    default AsyncLogPipelineStatus getPipelineStatus() {
        return new AsyncLogPipelineStatus(getQueueSize(), 0, 0, 0, getQueueSize());
    }
}
