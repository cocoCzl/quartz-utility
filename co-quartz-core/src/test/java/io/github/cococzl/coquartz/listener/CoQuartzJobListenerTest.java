package io.github.cococzl.coquartz.listener;

import io.github.cococzl.coquartz.core.CoQuartzConstants;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.*;

import java.util.Date;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoQuartzJobListenerTest {

    @Mock
    private AsyncTaskLogService asyncTaskLogService;

    @Mock
    private CoQuartzMetrics metrics;

    @Mock
    private AlertEventPublisher alertEventPublisher;

    @Mock
    private JobExecutionContext context;

    @Mock
    private JobDetail jobDetail;

    @Mock
    private Trigger trigger;

    private CoQuartzJobListener listener;

    @BeforeEach
    void setUp() {
        listener = new CoQuartzJobListener(asyncTaskLogService, metrics, alertEventPublisher);
    }

    @Test
    void getName_returnsCoQuartzJobListener() {
        assertThat(listener.getName()).isEqualTo("coQuartzJobListener");
    }

    @Test
    void jobWasExecuted_success_logsSuccess() {
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(context.getTrigger()).thenReturn(trigger);
        when(jobDetail.getKey()).thenReturn(new JobKey("testJob", "DEFAULT"));
        when(trigger.getKey()).thenReturn(new TriggerKey("testTrigger", "DEFAULT"));
        when(context.getFireTime()).thenReturn(new Date());
        when(context.getJobRunTime()).thenReturn(100L);
        JobDataMap dataMap = new JobDataMap();
        when(jobDetail.getJobDataMap()).thenReturn(dataMap);

        listener.jobWasExecuted(context, null);

        ArgumentCaptor<TaskExecutionLog> logCaptor = ArgumentCaptor.forClass(TaskExecutionLog.class);
        verify(asyncTaskLogService).logTaskExecutionAsync(logCaptor.capture());
        TaskExecutionLog log = logCaptor.getValue();
        assertThat(log.getExecState()).isEqualTo(LogTaskExecStateEnum.SUCCESS);
        assertThat(log.getAttempt()).isEqualTo(1);
        assertThat(log.isFinalAttempt()).isTrue();
        verify(metrics).recordSuccess(eq("DEFAULT.testJob"), eq(100L));
    }

    @Test
    void jobWasExecuted_failure_logsFailure() {
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(context.getTrigger()).thenReturn(trigger);
        when(jobDetail.getKey()).thenReturn(new JobKey("testJob", "DEFAULT"));
        when(trigger.getKey()).thenReturn(new TriggerKey("testTrigger", "DEFAULT"));
        when(context.getFireTime()).thenReturn(new Date());
        when(context.getJobRunTime()).thenReturn(500L);
        JobDataMap dataMap = new JobDataMap();
        when(jobDetail.getJobDataMap()).thenReturn(dataMap);

        JobExecutionException exception = new JobExecutionException("test failure");
        listener.jobWasExecuted(context, exception);

        ArgumentCaptor<TaskExecutionLog> logCaptor = ArgumentCaptor.forClass(TaskExecutionLog.class);
        verify(asyncTaskLogService).logTaskExecutionAsync(logCaptor.capture());
        TaskExecutionLog log = logCaptor.getValue();
        assertThat(log.getExecState()).isEqualTo(LogTaskExecStateEnum.FAIL);
        assertThat(log.getErrorMessage()).isEqualTo("test failure");
        verify(metrics).recordFailure(eq("DEFAULT.testJob"), eq(500L));
        verify(alertEventPublisher).publishFailure(eq("DEFAULT.testJob"), anyString(), anyString());
    }

    @Test
    void jobWasExecuted_enhancedJob_skipsLogging() {
        when(context.getJobDetail()).thenReturn(jobDetail);
        JobDataMap dataMap = new JobDataMap();
        dataMap.put(CoQuartzConstants.ENHANCED, true);
        when(jobDetail.getJobDataMap()).thenReturn(dataMap);

        listener.jobWasExecuted(context, null);

        verifyNoInteractions(asyncTaskLogService);
        verifyNoInteractions(metrics);
        verifyNoInteractions(alertEventPublisher);
    }

    @Test
    void constructor_twoArgs() {
        CoQuartzJobListener listener2 = new CoQuartzJobListener(asyncTaskLogService, metrics);
        assertThat(listener2.getName()).isEqualTo("coQuartzJobListener");
    }

    @Test
    void constructor_oneArg() {
        CoQuartzJobListener listener1 = new CoQuartzJobListener(asyncTaskLogService);
        assertThat(listener1.getName()).isEqualTo("coQuartzJobListener");
    }
}