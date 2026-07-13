package io.github.cococzl.coquartz.service;

import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import java.time.LocalDateTime;

/** Synchronous lifecycle storage used only when reliable audit mode is explicitly enabled. */
public interface ReliableAuditService {
    void recordStarted(TaskExecutionLog log);
    void recordCompleted(TaskExecutionLog log);
    int recoverInterruptedBefore(LocalDateTime cutoff, String schedulerInstanceId, boolean clustered);
}
