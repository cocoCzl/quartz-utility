package io.github.cococzl.coquartz.jdbc.service;

import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcSynchronousTaskLogWriterTest {
    @Test
    void writesOnCallingThread() {
        TaskLogRepository repository = mock(TaskLogRepository.class);
        TaskExecutionLog log = new TaskExecutionLog();
        new JdbcSynchronousTaskLogWriter(repository).write(log);
        verify(repository).insert(log);
    }
}
