package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnhancedJobTest {

    @Mock
    private Job delegate;

    @Mock
    private AsyncTaskLogService asyncTaskLogService;

    @Mock
    private AlertEventPublisher alertEventPublisher;

    @Mock
    private CoQuartzMetrics metrics;

    private CoQuartzProperties properties;

    @Mock
    private JobExecutionContext context;

    @Mock
    private JobDetail jobDetail;

    @Mock
    private Trigger trigger;

    private JobDataMap enhancedConfig;

    private ScheduledExecutorService timeoutExecutor;

    @BeforeEach
    void setUp() {
        properties = new CoQuartzProperties();
        enhancedConfig = new JobDataMap();
        enhancedConfig.put(CoQuartzConstants.ENHANCED, true);
        timeoutExecutor = new ScheduledThreadPoolExecutor(2);

        lenient().when(context.getJobDetail()).thenReturn(jobDetail);
        lenient().when(context.getTrigger()).thenReturn(trigger);
        lenient().when(jobDetail.getKey()).thenReturn(new JobKey("testJob", "DEFAULT"));
        lenient().when(trigger.getKey()).thenReturn(new TriggerKey("testTrigger", "DEFAULT"));
    }

    private EnhancedJob createEnhancedJob() {
        return new EnhancedJob(delegate, enhancedConfig, asyncTaskLogService,
                timeoutExecutor, alertEventPublisher, properties, metrics);
    }

    @Test
    void execute_success_logsAndReturns() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 0);
        EnhancedJob job = createEnhancedJob();

        job.execute(context);

        verify(delegate).execute(context);
        verify(asyncTaskLogService).logTaskExecutionAsync(argThat(log ->
                log.getExecState() == LogTaskExecStateEnum.SUCCESS &&
                log.getAttempt() == 1 &&
                log.isFinalAttempt()
        ));
        verify(metrics).jobStarted();
        verify(metrics).jobFinished();
        verify(metrics).recordSuccess(eq("DEFAULT.testJob"), anyLong());
    }

    @Test
    void execute_failure_noRetry_throwsException() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 0);
        doThrow(new JobExecutionException("test error")).when(delegate).execute(context);
        EnhancedJob job = createEnhancedJob();

        assertThatThrownBy(() -> job.execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasMessage("test error");

        verify(asyncTaskLogService).logTaskExecutionAsync(argThat(log ->
                log.getExecState() == LogTaskExecStateEnum.FAIL &&
                log.getAttempt() == 1 &&
                log.isFinalAttempt() &&
                log.getErrorMessage() != null
        ));
        verify(alertEventPublisher).publishFailure(eq("DEFAULT.testJob"), anyString(), anyString());
        verify(alertEventPublisher).publishConsecutiveFailureIfNeeded("DEFAULT.testJob");
        verify(metrics).recordFailure(eq("DEFAULT.testJob"), anyLong());
    }

    @Test
    void execute_failure_withRetry_succeedsOnSecondAttempt() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 2);
        enhancedConfig.put(CoQuartzConstants.RETRY_INTERVAL, 0);
        doThrow(new JobExecutionException("fail"))
                .doNothing()
                .when(delegate).execute(context);
        EnhancedJob job = createEnhancedJob();

        job.execute(context);

        verify(delegate, times(2)).execute(context);

        ArgumentCaptor<TaskExecutionLog> logCaptor = ArgumentCaptor.forClass(TaskExecutionLog.class);
        verify(asyncTaskLogService, times(2)).logTaskExecutionAsync(logCaptor.capture());

        var logs = logCaptor.getAllValues();
        assertThat(logs.get(0).getExecState()).isEqualTo(LogTaskExecStateEnum.FAIL);
        assertThat(logs.get(0).getAttempt()).isEqualTo(1);
        assertThat(logs.get(1).getExecState()).isEqualTo(LogTaskExecStateEnum.SUCCESS);
        assertThat(logs.get(1).getAttempt()).isEqualTo(2);
        assertThat(logs.get(1).isFinalAttempt()).isTrue();

        verify(alertEventPublisher).publishFailure(eq("DEFAULT.testJob"), anyString(), anyString());
    }

    @Test
    void execute_failure_allRetriesExhausted() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 2);
        enhancedConfig.put(CoQuartzConstants.RETRY_INTERVAL, 0);
        doThrow(new JobExecutionException("persistent error")).when(delegate).execute(context);
        EnhancedJob job = createEnhancedJob();

        assertThatThrownBy(() -> job.execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasMessage("persistent error");

        verify(delegate, times(3)).execute(context);

        ArgumentCaptor<TaskExecutionLog> logCaptor = ArgumentCaptor.forClass(TaskExecutionLog.class);
        verify(asyncTaskLogService, times(3)).logTaskExecutionAsync(logCaptor.capture());

        var logs = logCaptor.getAllValues();
        assertThat(logs).allMatch(log -> log.getExecState() == LogTaskExecStateEnum.FAIL);
        assertThat(logs.get(2).isFinalAttempt()).isTrue();
        assertThat(logs.get(2).getAttempt()).isEqualTo(3);

        verify(alertEventPublisher, times(3)).publishFailure(eq("DEFAULT.testJob"), anyString(), anyString());
        verify(alertEventPublisher).publishConsecutiveFailureIfNeeded("DEFAULT.testJob");
    }

    @Test
    void execute_nonJobExecutionException_wrapsInJobExecutionException() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 0);
        doThrow(new RuntimeException("runtime error")).when(delegate).execute(context);
        EnhancedJob job = createEnhancedJob();

        assertThatThrownBy(() -> job.execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void execute_success_slowTask_publishesSlowTaskEvent() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 0);
        properties.getMonitoring().setSlowTaskThresholdMs(1);
        doAnswer(invocation -> {
            Thread.sleep(10);
            return null;
        }).when(delegate).execute(context);
        EnhancedJob job = createEnhancedJob();

        job.execute(context);

        verify(alertEventPublisher).publishSlowTask(eq("DEFAULT.testJob"), anyLong(), eq(1L));
    }

    @Test
    void execute_success_fastTask_noSlowTaskEvent() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 0);
        properties.getMonitoring().setSlowTaskThresholdMs(999999);
        EnhancedJob job = createEnhancedJob();

        job.execute(context);

        verify(alertEventPublisher, never()).publishSlowTask(anyString(), anyLong(), anyLong());
    }

    @Test
    void execute_nullMetrics_noException() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 0);
        EnhancedJob job = new EnhancedJob(delegate, enhancedConfig, asyncTaskLogService,
                timeoutExecutor, alertEventPublisher, properties, null);

        job.execute(context);

        verify(delegate).execute(context);
    }

    @Test
    void execute_nullAlertEventPublisher_noException() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 0);
        doThrow(new JobExecutionException("err")).when(delegate).execute(context);
        EnhancedJob job = new EnhancedJob(delegate, enhancedConfig, asyncTaskLogService,
                timeoutExecutor, null, properties, metrics);

        assertThatThrownBy(() -> job.execute(context)).isInstanceOf(JobExecutionException.class);
    }

    @Test
    void execute_timeout_publishesTimeoutEvent() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 0);
        enhancedConfig.put(CoQuartzConstants.TIMEOUT, 1);
        doAnswer(invocation -> {
            Thread.sleep(5000);
            return null;
        }).when(delegate).execute(context);
        EnhancedJob job = createEnhancedJob();

        try {
            job.execute(context);
        } catch (JobExecutionException e) {
            assertThat(e.getMessage()).startsWith("Task timed out");
        }

        verify(asyncTaskLogService).logTaskExecutionAsync(argThat(log ->
                log.getExecState() == LogTaskExecStateEnum.FAIL
        ));
        verify(alertEventPublisher).publishTimeout(eq("DEFAULT.testJob"), eq(1L));
    }

    @Test
    void execute_withExponentialBackoff_increasesDelay() {
        RetryContext ctx = new RetryContext(2, 100, true, 2.0);
        assertThat(ctx.getNextRetryDelay()).isEqualTo(100);
        ctx.recordAttempt();
        assertThat(ctx.getNextRetryDelay()).isEqualTo(200);
    }
}