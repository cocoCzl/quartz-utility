package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.exception.TaskTimeoutException;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.github.cococzl.coquartz.service.LogSanitizer;
import io.github.cococzl.coquartz.service.ReliableAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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

    @Mock
    private ReliableAuditService reliableAuditService;

    private CoQuartzProperties properties;

    @Mock
    private JobExecutionContext context;

    @Mock
    private JobDetail jobDetail;

    @Mock
    private Trigger trigger;
    @Mock
    private Scheduler scheduler;

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
        lenient().when(context.getScheduler()).thenReturn(scheduler);
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
    void execute_failure_withRetry_schedulesDelayedRetry() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 2);
        enhancedConfig.put(CoQuartzConstants.RETRY_INTERVAL, 0);
        doThrow(new JobExecutionException("fail")).when(delegate).execute(context);
        EnhancedJob job = createEnhancedJob();

        job.execute(context);

        verify(delegate).execute(context);
        ArgumentCaptor<Trigger> retryTrigger = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).scheduleJob(retryTrigger.capture());
        assertThat(retryTrigger.getValue().getKey().getGroup()).isEqualTo(CoQuartzConstants.RETRY_TRIGGER_GROUP);
        assertThat(retryTrigger.getValue().getJobDataMap().getInt(CoQuartzConstants.RETRY_ATTEMPT)).isEqualTo(2);
        assertThat(retryTrigger.getValue().getJobDataMap().getString(CoQuartzConstants.RETRY_EXECUTION_ID)).isNotBlank();
        assertThat(retryTrigger.getValue().getStartTime()).isNotNull();

        ArgumentCaptor<TaskExecutionLog> logCaptor = ArgumentCaptor.forClass(TaskExecutionLog.class);
        verify(asyncTaskLogService).logTaskExecutionAsync(logCaptor.capture());

        var logs = logCaptor.getAllValues();
        assertThat(logs.get(0).getExecState()).isEqualTo(LogTaskExecStateEnum.FAIL);
        assertThat(logs.get(0).getAttempt()).isEqualTo(1);
        assertThat(logs.get(0).getExecutionId()).isNotBlank();

        verify(alertEventPublisher).publishFailure(eq("DEFAULT.testJob"), anyString(), anyString());
    }

    @Test
    void delayedRetryDoesNotBlockCurrentWorkerThread() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 1);
        enhancedConfig.put(CoQuartzConstants.RETRY_INTERVAL, 5_000L);
        doThrow(new JobExecutionException("fail")).when(delegate).execute(context);

        long startedAt = System.nanoTime();
        createEnhancedJob().execute(context);
        long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        ArgumentCaptor<Trigger> retryTrigger = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).scheduleJob(retryTrigger.capture());
        assertThat(elapsedMs).isLessThan(1_000);
        assertThat(retryTrigger.getValue().getStartTime().getTime())
                .isGreaterThanOrEqualTo(System.currentTimeMillis() + 4_000);
    }

    @Test
    void execute_failure_allRetriesExhausted() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 2);
        enhancedConfig.put(CoQuartzConstants.RETRY_INTERVAL, 0);
        doThrow(new JobExecutionException("persistent error")).when(delegate).execute(context);
        EnhancedJob job = createEnhancedJob();

        job.execute(context);
        verify(scheduler).scheduleJob(any(Trigger.class));

        ArgumentCaptor<TaskExecutionLog> logCaptor = ArgumentCaptor.forClass(TaskExecutionLog.class);
        verify(asyncTaskLogService).logTaskExecutionAsync(logCaptor.capture());

        var logs = logCaptor.getAllValues();
        assertThat(logs).allMatch(log -> log.getExecState() == LogTaskExecStateEnum.FAIL);
        assertThat(logs.get(0).isFinalAttempt()).isFalse();

        verify(alertEventPublisher).publishFailure(eq("DEFAULT.testJob"), anyString(), anyString());
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
        verify(alertEventPublisher).publishTimeout(eq("DEFAULT.testJob"), eq(1L), anyBoolean());
    }

    @Test
    void disabledStackCaptureOmitsStackTraceFromLogAndFailureEvent() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 0);
        properties.getLog().setCaptureStackTrace(false);
        doThrow(new JobExecutionException("token=private-value")).when(delegate).execute(context);

        assertThatThrownBy(() -> createEnhancedJob().execute(context)).isInstanceOf(JobExecutionException.class);

        verify(asyncTaskLogService).logTaskExecutionAsync(argThat(log ->
                log.getStackTrace() == null && "token=***".equals(log.getErrorMessage())));
        verify(alertEventPublisher).publishFailure("DEFAULT.testJob", "token=***", null);
    }

    @Test
    void customLogSanitizerIsAppliedBeforeLoggingAndPublishing() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 0);
        doThrow(new JobExecutionException("private diagnostic")).when(delegate).execute(context);
        LogSanitizer sanitizer = value -> value == null ? null : "sanitized";
        EnhancedJob job = new EnhancedJob(delegate, enhancedConfig, asyncTaskLogService, timeoutExecutor,
                alertEventPublisher, properties, metrics, sanitizer);

        assertThatThrownBy(() -> job.execute(context)).isInstanceOf(JobExecutionException.class);

        verify(asyncTaskLogService).logTaskExecutionAsync(argThat(log ->
                "sanitized".equals(log.getErrorMessage()) && "sanitized".equals(log.getStackTrace())));
        verify(alertEventPublisher).publishFailure("DEFAULT.testJob", "sanitized", "sanitized");
    }

    @Test
    void reliableAuditCreatesStartedRecordBeforeDelegateAndCompletesSameRecord() throws Exception {
        properties.getLog().setReliableAudit(true);
        EnhancedJob job = new EnhancedJob(delegate, enhancedConfig, asyncTaskLogService, timeoutExecutor,
                alertEventPublisher, properties, metrics, value -> value, reliableAuditService);

        job.execute(context);

        InOrder inOrder = inOrder(reliableAuditService, delegate);
        inOrder.verify(reliableAuditService).recordStarted(argThat((TaskExecutionLog log) ->
                log.getExecState() == LogTaskExecStateEnum.STARTED && log.getEndTime() == null));
        inOrder.verify(delegate).execute(context);
        inOrder.verify(reliableAuditService).recordCompleted(argThat((TaskExecutionLog log) ->
                log.getExecState() == LogTaskExecStateEnum.SUCCESS && log.getEndTime() != null));
        verify(asyncTaskLogService, never()).logTaskExecutionAsync(any());
    }

    @Test
    void timeoutThatIgnoresInterruptReportsUnconfirmedTermination() throws Exception {
        enhancedConfig.put(CoQuartzConstants.RETRY_TIMES, 0);
        enhancedConfig.put(CoQuartzConstants.TIMEOUT, 10);
        doAnswer(invocation -> {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(200);
            while (System.nanoTime() < deadline) {
                try { Thread.sleep(10); } catch (InterruptedException ignored) { }
            }
            return null;
        }).when(delegate).execute(context);

        assertThatThrownBy(() -> createEnhancedJob().execute(context))
                .isInstanceOf(TaskTimeoutException.class)
                .hasMessageContaining("termination is unconfirmed");
        verify(alertEventPublisher).publishTimeout("DEFAULT.testJob", 10L, false);
    }

    @Test
    void execute_withExponentialBackoff_increasesDelay() {
        RetryContext ctx = new RetryContext(2, 100, true, 2.0);
        assertThat(ctx.getNextRetryDelay()).isEqualTo(100);
        ctx.recordAttempt();
        assertThat(ctx.getNextRetryDelay()).isEqualTo(200);
    }
}
