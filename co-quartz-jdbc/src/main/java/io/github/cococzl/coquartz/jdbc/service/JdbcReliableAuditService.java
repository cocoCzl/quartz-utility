package io.github.cococzl.coquartz.jdbc.service;

import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.event.ReliableAuditFailureEvent;
import io.github.cococzl.coquartz.service.ReliableAuditService;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

/** JDBC-backed synchronous lifecycle writer for reliable audit mode. */
public class JdbcReliableAuditService implements ReliableAuditService {
    private final TaskLogRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public JdbcReliableAuditService(TaskLogRepository repository) {
        this(repository, null);
    }

    public JdbcReliableAuditService(TaskLogRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void recordStarted(TaskExecutionLog log) {
        try {
            repository.insert(log);
        } catch (RuntimeException e) {
            publish(log, ReliableAuditFailureEvent.Phase.START);
            throw e;
        }
    }

    @Override
    public void recordCompleted(TaskExecutionLog log) {
        try {
            repository.updateLifecycle(log);
        } catch (RuntimeException e) {
            publish(log, ReliableAuditFailureEvent.Phase.COMPLETE);
            throw e;
        }
    }

    @Override
    public int recoverInterruptedBefore(LocalDateTime cutoff, String schedulerInstanceId, boolean clustered) {
        int recovered = 0;
        for (TaskExecutionLog log : repository.findStartedBefore(cutoff)) {
            // In a cluster we cannot prove a remote node has stopped from the log table alone.
            // Leave remote ownership untouched; that node can finish its own lifecycle safely.
            if (clustered && log.getSchedulerInstanceId() != null
                    && !log.getSchedulerInstanceId().equals(schedulerInstanceId)) {
                continue;
            }
            if (repository.markInterrupted(log.getId(), LocalDateTime.now())) recovered++;
        }
        return recovered;
    }

    private void publish(TaskExecutionLog log, ReliableAuditFailureEvent.Phase phase) {
        if (eventPublisher == null) return;
        try {
            eventPublisher.publishEvent(new ReliableAuditFailureEvent(this, log.getJobKey(), phase));
        } catch (RuntimeException ignored) {
            // The original storage error remains the authoritative failure signal.
        }
    }
}
