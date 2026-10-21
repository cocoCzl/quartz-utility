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
    private io.github.cococzl.coquartz.event.AlertEventPublisher alerts;
    public void setAlertPublisher(io.github.cococzl.coquartz.event.AlertEventPublisher alerts) { this.alerts = alerts; }

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
        return 0; // Legacy callers cannot provide stopped-node evidence; never close potentially live work.
    }

    @Override
    public int recoverConfirmedStopped(LocalDateTime cutoff, io.github.cococzl.coquartz.service.AuditRecoveryScope scope) {
        int recovered = 0;
        for (TaskExecutionLog log : repository.findStartedBefore(cutoff)) {
            if (!scope.applicationNamespace().equals(log.getApplicationNamespace())
                    || !scope.schedulerName().equals(log.getSchedulerName())
                    || !scope.confirmedStoppedInstances().contains(log.getSchedulerInstanceId())) continue;
            if (repository.markInterrupted(log.getId(), LocalDateTime.now(java.time.ZoneOffset.UTC))) recovered++;
        }
        return recovered;
    }

    private void publish(TaskExecutionLog log, ReliableAuditFailureEvent.Phase phase) {
        if (alerts != null) { alerts.publishAuditFailure(log.getJobKey(), phase); return; }
        if (eventPublisher == null) return;
        try {
            eventPublisher.publishEvent(new ReliableAuditFailureEvent(this, log.getJobKey(), phase));
        } catch (RuntimeException ignored) {
            // The original storage error remains the authoritative failure signal.
        }
    }
}
