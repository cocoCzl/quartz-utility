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

        alertEventPublisher.publishConsecutiveFailureIfNeeded("DEFAULT.testJob");
        alertEventPublisher.publishConsecutiveFailureIfNeeded("DEFAULT.testJob");
        alertEventPublisher.publishConsecutiveFailureIfNeeded("DEFAULT.testJob");

        ArgumentCaptor<TaskConsecutiveFailureEvent> captor = ArgumentCaptor.forClass(TaskConsecutiveFailureEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        TaskConsecutiveFailureEvent event = captor.getValue();
        assertThat(event.getJobKey()).isEqualTo("DEFAULT.testJob");
        assertThat(event.getThreshold()).isEqualTo(3);
        assertThat(event.getRecentFailures()).isEqualTo(3);
    }

    @Test
    void successfulExecutionResetsFailureWindowAndAllowsANewAlert() {
        alertEventPublisher = new AlertEventPublisher(eventPublisher, taskLogRepository, properties);

        alertEventPublisher.publishConsecutiveFailureIfNeeded("DEFAULT.testJob");
        alertEventPublisher.publishConsecutiveFailureIfNeeded("DEFAULT.testJob");
        alertEventPublisher.recordSuccess("DEFAULT.testJob");
        alertEventPublisher.publishConsecutiveFailureIfNeeded("DEFAULT.testJob");

        verify(eventPublisher, never()).publishEvent(any(TaskConsecutiveFailureEvent.class));
    }

    @Test
    void publishConsecutiveFailureIfNeeded_fewerLogsThanThreshold_noEvent() {
        alertEventPublisher = new AlertEventPublisher(eventPublisher, taskLogRepository, properties);

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
