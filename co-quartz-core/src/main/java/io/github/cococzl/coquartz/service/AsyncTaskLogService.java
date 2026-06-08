package io.github.cococzl.coquartz.service;

import io.github.cococzl.coquartz.dto.TaskExecutionLog;

public interface AsyncTaskLogService {

    void logTaskExecutionAsync(TaskExecutionLog log);

    void flushLogsImmediately();

    void shutdown();

    int getQueueSize();
}