package io.github.cococzl.coquartz.jdbc.service;

import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.service.TaskExecutionLogWriter;
import io.github.cococzl.coquartz.service.TaskLogRepository;

/** Writes execution logs on the calling thread. */
public class JdbcSynchronousTaskLogWriter implements TaskExecutionLogWriter {
    private final TaskLogRepository repository;

    public JdbcSynchronousTaskLogWriter(TaskLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void write(TaskExecutionLog log) {
        repository.insert(log);
    }
}
