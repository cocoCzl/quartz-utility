package io.github.cococzl.coquartz.jdbc.service;

import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.event.ReliableAuditFailureEvent;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.List;

class JdbcReliableAuditServiceTest {

    @Test
    void failedStartWritePublishesOperationalEventAndPreservesStorageFailure() {
        TaskLogRepository repository = mock(TaskLogRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        TaskExecutionLog log = new TaskExecutionLog();
        log.setJobKey("DEFAULT.auditJob");
        doThrow(new IllegalStateException("database unavailable")).when(repository).insert(log);

        assertThatThrownBy(() -> new JdbcReliableAuditService(repository, events).recordStarted(log))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        ArgumentCaptor<ReliableAuditFailureEvent> event = ArgumentCaptor.forClass(ReliableAuditFailureEvent.class);
        verify(events).publishEvent(event.capture());
        org.assertj.core.api.Assertions.assertThat(event.getValue().getPhase())
                .isEqualTo(ReliableAuditFailureEvent.Phase.START);
    }

    @Test
    void clusteredRecoveryLeavesRecordsOwnedByOtherNodesUntouched() {
        TaskLogRepository repository = mock(TaskLogRepository.class);
        TaskExecutionLog local = new TaskExecutionLog();
        local.setId("local");
        local.setSchedulerInstanceId("node-a");
        TaskExecutionLog remote = new TaskExecutionLog();
        remote.setId("remote");
        remote.setSchedulerInstanceId("node-b");
        when(repository.findStartedBefore(any())).thenReturn(List.of(local, remote));
        when(repository.markInterrupted(eq("local"), any())).thenReturn(true);

        int recovered = new JdbcReliableAuditService(repository).recoverInterruptedBefore(
                LocalDateTime.now(), "node-a", true);

        org.assertj.core.api.Assertions.assertThat(recovered).isEqualTo(1);
        verify(repository).markInterrupted(eq("local"), any());
        verify(repository, never()).markInterrupted(eq("remote"), any());
    }
}
