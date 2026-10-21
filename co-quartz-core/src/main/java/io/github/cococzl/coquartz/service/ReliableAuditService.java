package io.github.cococzl.coquartz.service;

import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import java.time.LocalDateTime;

/** Synchronous lifecycle storage used only when reliable audit mode is explicitly enabled. */
public interface ReliableAuditService {
    void recordStarted(TaskExecutionLog log);
    void recordCompleted(TaskExecutionLog log);
    default int recoverConfirmedStopped(LocalDateTime cutoff, AuditRecoveryScope scope) {
        throw new UnsupportedOperationException("Evidence-based recovery is unavailable");
    }
    /** @deprecated A time threshold and instance id do not prove that a node has stopped. */
    @Deprecated
    int recoverInterruptedBefore(LocalDateTime cutoff, String schedulerInstanceId, boolean clustered);
}
