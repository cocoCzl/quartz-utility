package io.github.cococzl.coquartz.event;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertEventPublisherTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TaskLogRepository taskLogRepository;

    private CoQuartzProperties properties;

    private AlertEventPublisher alertEventPublisher;

    @BeforeEach
    void setUp() {
        properties = new CoQuartzProperties();
    }

    @Test
    void publishFailure_publishesEvent() {
        alertEventPublisher = new AlertEventPublisher(eventPublisher, taskLogRepository, properties);

        alertEventPublisher.publishFailure("DEFAULT.testJob", "error msg", "stack trace");

        ArgumentCaptor<TaskFailureEvent> captor = ArgumentCaptor.forClass(TaskFailureEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        TaskFailureEvent event = captor.getValue();
        assertThat(event.getJobKey()).isEqualTo("DEFAULT.testJob");
        assertThat(event.getErrorMessage()).isEqualTo("error msg");
        assertThat(event.getStackTrace()).isEqualTo("stack trace");
    }

    @Test
    void publishTimeout_publishesEvent() {
        alertEventPublisher = new AlertEventPublisher(eventPublisher, taskLogRepository, properties);

        alertEventPublisher.publishTimeout("DEFAULT.testJob", 5000);

        ArgumentCaptor<TaskTimeoutEvent> captor = ArgumentCaptor.forClass(TaskTimeoutEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        TaskTimeoutEvent event = captor.getValue();
        assertThat(event.getJobKey()).isEqualTo("DEFAULT.testJob");
        assertThat(event.getTimeoutMs()).isEqualTo(5000);
    }

    @Test
    void publishSlowTask_publishesEvent() {
        alertEventPublisher = new AlertEventPublisher(eventPublisher, taskLogRepository, properties);

        alertEventPublisher.publishSlowTask("DEFAULT.testJob", 15000, 10000);

        ArgumentCaptor<TaskSlowEvent> captor = ArgumentCaptor.forClass(TaskSlowEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        TaskSlowEvent event = captor.getValue();
        assertThat(event.getJobKey()).isEqualTo("DEFAULT.testJob");
        assertThat(event.getExecutionTimeMs()).isEqualTo(15000);
        assertThat(event.getThresholdMs()).isEqualTo(10000);
    }

    @Test
    void publishConsecutiveFailureIfNeeded_allFailures_publishesEvent() {
        alertEventPublisher = new AlertEventPublisher(eventPublisher, taskLogRepository, properties);

        TaskExecutionLog log1 = TaskExecutionLog.failure("DEFAULT.testJob", "TRIGGER_testJob",
                null, null, 100, "err1", null, 1, true);
        TaskExecutionLog log2 = TaskExecutionLog.failure("DEFAULT.testJob", "TRIGGER_testJob",
                null, null, 200, "err2", null, 2, true);
        TaskExecutionLog log3 = TaskExecutionLog.failure("DEFAULT.testJob", "TRIGGER_testJob",
                null, null, 300, "err3", null, 3, true);

        when(taskLogRepository.findRecentByJobKey("DEFAULT.testJob", 3))
                .thenReturn(List.of(log1, log2, log3));

        alertEventPublisher.publishConsecutiveFailureIfNeeded("DEFAULT.testJob");

        ArgumentCaptor<TaskConsecutiveFailureEvent> captor = ArgumentCaptor.forClass(TaskConsecutiveFailureEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        TaskConsecutiveFailureEvent event = captor.getValue();
        assertThat(event.getJobKey()).isEqualTo("DEFAULT.testJob");
        assertThat(event.getThreshold()).isEqualTo(3);
        assertThat(event.getRecentFailures()).isEqualTo(3);
    }

    @Test
    void publishConsecutiveFailureIfNeeded_mixedResults_noEvent() {
        alertEventPublisher = new AlertEventPublisher(eventPublisher, taskLogRepository, properties);

        TaskExecutionLog successLog = TaskExecutionLog.success("DEFAULT.testJob", "TRIGGER_testJob",
                null, null, 100, 1, true);
        TaskExecutionLog failLog = TaskExecutionLog.failure("DEFAULT.testJob", "TRIGGER_testJob",
                null, null, 200, "err", null, 2, true);

        when(taskLogRepository.findRecentByJobKey("DEFAULT.testJob", 3))
                .thenReturn(List.of(successLog, failLog));

        alertEventPublisher.publishConsecutiveFailureIfNeeded("DEFAULT.testJob");

        verify(eventPublisher, never()).publishEvent(any(TaskConsecutiveFailureEvent.class));
    }

    @Test
    void publishConsecutiveFailureIfNeeded_fewerLogsThanThreshold_noEvent() {
        alertEventPublisher = new AlertEventPublisher(eventPublisher, taskLogRepository, properties);

        when(taskLogRepository.findRecentByJobKey("DEFAULT.testJob", 3))
                .thenReturn(Collections.singletonList(
                        TaskExecutionLog.failure("DEFAULT.testJob", "TRIGGER_testJob",
                                null, null, 100, "err", null, 1, true)));

        alertEventPublisher.publishConsecutiveFailureIfNeeded("DEFAULT.testJob");

        verify(eventPublisher, never()).publishEvent(any(TaskConsecutiveFailureEvent.class));
    }

    @Test
    void publishConsecutiveFailureIfNeeded_nullRepository_noEvent() {
        alertEventPublisher = new AlertEventPublisher(eventPublisher, properties);

        assertThatCode(() -> alertEventPublisher.publishConsecutiveFailureIfNeeded("DEFAULT.testJob"))
                .doesNotThrowAnyException();

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publishFailure_exceptionInPublisher_doesNotPropagate() {
        alertEventPublisher = new AlertEventPublisher(eventPublisher, taskLogRepository, properties);
        doThrow(new RuntimeException("publisher error")).when(eventPublisher).publishEvent(any());

        assertThatCode(() -> alertEventPublisher.publishFailure("job", "err", "stack"))
                .doesNotThrowAnyException();
    }
}