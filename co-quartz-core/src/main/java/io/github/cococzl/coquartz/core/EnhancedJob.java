package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.exception.TaskTimeoutException;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.github.cococzl.coquartz.service.DefaultLogSanitizer;
import io.github.cococzl.coquartz.service.LogSanitizer;
import io.github.cococzl.coquartz.service.ReliableAuditService;
import io.github.cococzl.coquartz.service.TaskExecutionLogWriter;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.*;

public class EnhancedJob implements InterruptableJob {

    private volatile Thread executionThread;
    private Runnable cleanup = () -> { };

    void setCleanup(Runnable cleanup) { this.cleanup = cleanup; }

    private static final Logger log = LoggerFactory.getLogger(EnhancedJob.class);

    private final Job delegate;
    private final JobDataMap enhancedConfig;
    private final TaskExecutionLogWriter logWriter;
    private final ExecutorService timeoutExecutor;
    private final AlertEventPublisher alertEventPublisher;
    private final CoQuartzProperties properties;
    private final CoQuartzMetrics metrics;
    private final LogSanitizer logSanitizer;
    private final ReliableAuditService reliableAuditService;

    public EnhancedJob(Job delegate, JobDataMap enhancedConfig,
                       AsyncTaskLogService asyncTaskLogService,
                       ExecutorService timeoutExecutor,
                       AlertEventPublisher alertEventPublisher,
                       CoQuartzProperties properties,
                       CoQuartzMetrics metrics) {
        this(delegate, enhancedConfig, asyncTaskLogService, timeoutExecutor, alertEventPublisher, properties, metrics,
                new DefaultLogSanitizer(), null);
    }

    public EnhancedJob(Job delegate, JobDataMap enhancedConfig,
                       AsyncTaskLogService asyncTaskLogService,
                       ExecutorService timeoutExecutor,
                       AlertEventPublisher alertEventPublisher,
                       CoQuartzProperties properties,
                       CoQuartzMetrics metrics,
                       LogSanitizer logSanitizer) {
        this(delegate, enhancedConfig, asyncTaskLogService, timeoutExecutor, alertEventPublisher, properties, metrics,
                logSanitizer, null);
    }

    public EnhancedJob(Job delegate, JobDataMap enhancedConfig,
                       TaskExecutionLogWriter logWriter,
                       ExecutorService timeoutExecutor,
                       AlertEventPublisher alertEventPublisher,
                       CoQuartzProperties properties,
                       CoQuartzMetrics metrics,
                       LogSanitizer logSanitizer,
                       ReliableAuditService reliableAuditService) {
        this.delegate = delegate;
        this.enhancedConfig = enhancedConfig;
        this.logWriter = logWriter;
        this.timeoutExecutor = timeoutExecutor;
        this.alertEventPublisher = alertEventPublisher;
        this.properties = properties;
        this.metrics = metrics;
        this.logSanitizer = logSanitizer == null ? new DefaultLogSanitizer() : logSanitizer;
        this.reliableAuditService = reliableAuditService;
    }

    public EnhancedJob(Job delegate, JobDataMap enhancedConfig,
                       AsyncTaskLogService asyncTaskLogService,
                       ExecutorService timeoutExecutor,
                       AlertEventPublisher alertEventPublisher,
                       CoQuartzProperties properties,
                       CoQuartzMetrics metrics,
                       LogSanitizer logSanitizer,
                       ReliableAuditService reliableAuditService) {
        this.delegate = delegate;
        this.enhancedConfig = enhancedConfig;
        this.logWriter = asyncTaskLogService;
        this.timeoutExecutor = timeoutExecutor;
        this.alertEventPublisher = alertEventPublisher;
        this.properties = properties;
        this.metrics = metrics;
        this.logSanitizer = logSanitizer == null ? new DefaultLogSanitizer() : logSanitizer;
        this.reliableAuditService = reliableAuditService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try { executeInternal(context); }
        finally {
            if (alertEventPublisher != null) alertEventPublisher.clearExecutionCorrelation();
            cleanup.run();
        }
    }

    private void executeInternal(JobExecutionContext context) throws JobExecutionException {
        executionThread = Thread.currentThread();
        int retryTimes = getIntConfig(CoQuartzConstants.RETRY_TIMES, 0);
        long retryInterval = getLongConfig(CoQuartzConstants.RETRY_INTERVAL, 1000);
        boolean exponentialBackoff = getBooleanConfig(CoQuartzConstants.EXPONENTIAL_BACKOFF, false);
        double backoffMultiplier = getDoubleConfig(CoQuartzConstants.BACKOFF_MULTIPLIER, 1.5);
        long timeoutMs = getLongConfig(CoQuartzConstants.TIMEOUT, 0);
        String jobKey = context.getJobDetail().getKey().toString();
        JobDataMap runtimeData = context.getMergedJobDataMap();
        if (runtimeData == null) {
            runtimeData = new JobDataMap();
        }
        String executionId = runtimeData.getString(CoQuartzConstants.RETRY_EXECUTION_ID);
        if (executionId == null || executionId.isBlank()) {
            executionId = java.util.UUID.randomUUID().toString();
        }
        int scheduledAttempt = getIntConfig(runtimeData, CoQuartzConstants.RETRY_ATTEMPT, 1);

        RetryContext retryContext = new RetryContext(retryTimes, retryInterval, exponentialBackoff, backoffMultiplier);
        Exception lastException = null;

        if (metrics != null) {
            metrics.jobStarted();
        }

        try {
            while (true) {
                for (int index = 0; index < scheduledAttempt; index++) {
                    retryContext.recordAttempt();
                }
                int attempt = scheduledAttempt;

                LocalDateTime startTime = LocalDateTime.now(java.time.ZoneOffset.UTC);
                TaskExecutionLog auditLog = startReliableAudit(context, executionId, attempt, startTime);
                long startMs = System.nanoTime();
                LogTaskExecStateEnum execState;
                String errorMessage = null;
                String stackTrace = null;
                boolean timedOut = false;

                try {
                    if (timeoutMs > 0) {
                        executeWithTimeout(context, timeoutMs);
                    } else {
                        delegate.execute(context);
                    }
                    execState = LogTaskExecStateEnum.SUCCESS;
                } catch (JobExecutionException e) {
                    if (e instanceof TaskTimeoutException) {
                        timedOut = true;
                    }
                    execState = LogTaskExecStateEnum.FAIL;
                    errorMessage = sanitize(e.getMessage(), 500);
                    stackTrace = stackTrace(e);
                    lastException = e;
                } catch (Exception e) {
                    execState = LogTaskExecStateEnum.FAIL;
                    errorMessage = sanitize(e.getMessage(), 500);
                    stackTrace = stackTrace(e);
                    lastException = new JobExecutionException(e);
                }

                long executionTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startMs);
                LocalDateTime endTime = LocalDateTime.now(java.time.ZoneOffset.UTC);
                String triggerKey = runtimeData.getString(CoQuartzConstants.ORIGIN_TRIGGER_KEY);
                if (triggerKey == null) triggerKey = context.getTrigger().getKey().toString();

                boolean nativeStop = lastException instanceof JobExecutionException nativeError
                        && (nativeError.unscheduleAllTriggers() || nativeError.unscheduleFiringTrigger());
                if (lastException instanceof JobExecutionException nativeError) nativeError.setRefireImmediately(false);
                boolean noMoreRetries = attempt > retryTimes || nativeStop;
                if (execState == LogTaskExecStateEnum.FAIL && !noMoreRetries) {
                    try {
                        for (int index = 1; index < attempt; index++) retryContext.getNextRetryDelay();
                        scheduleRetry(context, executionId, attempt + 1, retryContext.getNextRetryDelay());
                        if (metrics != null) metrics.recordRetry(jobKey);
                    } catch (Exception scheduleFailure) {
                        noMoreRetries = true;
                        errorMessage = sanitize("Retry scheduling failed; business failure: " + errorMessage
                                + "; scheduling failure: " + scheduleFailure.getMessage(), 500);
                        JobExecutionException terminal = new JobExecutionException("Failed to schedule delayed retry", scheduleFailure);
                        if (lastException != null) terminal.addSuppressed(lastException);
                        lastException = terminal;
                    }
                }

                TaskExecutionLog taskLog;
                if (execState == LogTaskExecStateEnum.SUCCESS) {
                    taskLog = TaskExecutionLog.success(jobKey, triggerKey, startTime, endTime, executionTimeMs, attempt, true);
                } else {
                    taskLog = TaskExecutionLog.failure(jobKey, triggerKey, startTime, endTime, executionTimeMs, errorMessage, stackTrace, attempt, noMoreRetries);
                }
                populateExecutionCorrelation(taskLog, context, executionId);
                if (alertEventPublisher != null) alertEventPublisher.setExecutionCorrelation(taskLog);
                if (auditLog != null) {
                    taskLog.setId(auditLog.getId());
                    completeReliableAudit(taskLog);
                }

                if (auditLog == null) {
                    logExecution(taskLog);
                }

                if (metrics != null) {
                    if (execState == LogTaskExecStateEnum.SUCCESS) {
                        metrics.recordSuccess(jobKey, executionTimeMs);
                    } else {
                        metrics.recordFailure(jobKey, executionTimeMs);
                        if (timedOut) metrics.recordTimeout(jobKey);
                    }
                }

                if (execState == LogTaskExecStateEnum.SUCCESS) {
                    if (alertEventPublisher != null) alertEventPublisher.recordSuccess(jobKey);
                    if (alertEventPublisher != null) {
                        long slowThreshold = properties.getMonitoring().getSlowTaskThresholdMs();
                        if (slowThreshold > 0 && executionTimeMs > slowThreshold) {
                            alertEventPublisher.publishSlowTask(jobKey, executionTimeMs, slowThreshold);
                        }
                    }
                    return;
                }

                if (alertEventPublisher != null) {
                    alertEventPublisher.publishFailure(jobKey, errorMessage, stackTrace);

                    if (timedOut) {
                        alertEventPublisher.publishTimeout(jobKey, timeoutMs,
                                ((TaskTimeoutException) lastException).isTerminationConfirmed());
                    }

                    if (noMoreRetries) {
                        alertEventPublisher.publishConsecutiveFailureIfNeeded(jobKey);
                    }
                }

                if (noMoreRetries) {
                    if (lastException instanceof JobExecutionException jee) {
                        throw jee;
                    }
                    throw new JobExecutionException(lastException);
                }

                return;
            }
        } finally {
            executionThread = null;
            if (metrics != null) {
                metrics.jobFinished();
            }
        }
    }

    @Override
    public void interrupt() throws UnableToInterruptJobException {
        try {
            if (delegate instanceof InterruptableJob interruptable) interruptable.interrupt();
        } finally {
            Thread worker = executionThread;
            if (worker != null) worker.interrupt();
        }
    }

    private static final ScheduledThreadPoolExecutor TIMEOUT_TIMER = timeoutTimer();

    private static ScheduledThreadPoolExecutor timeoutTimer() {
        var timer = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "co-quartz-timeout-timer");
            thread.setDaemon(true);
            return thread;
        });
        timer.setRemoveOnCancelPolicy(true);
        return timer;
    }

    private void executeWithTimeout(JobExecutionContext context, long timeoutMs) throws Exception {
        Thread worker = Thread.currentThread();
        Object lifecycle = new Object();
        boolean[] state = new boolean[2]; // completed, deadline exceeded
        boolean interruptedBefore = worker.isInterrupted();
        ScheduledFuture<?> deadline = TIMEOUT_TIMER.schedule(() -> {
            synchronized (lifecycle) {
                if (!state[0]) {
                    state[1] = true;
                    if (metrics != null) metrics.terminationUnconfirmed();
                    worker.interrupt();
                }
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
        Exception failure = null;
        try {
            delegate.execute(context);
        } catch (Exception error) {
            failure = error;
        } finally {
            synchronized (lifecycle) {
                state[0] = true;
                deadline.cancel(false);
                if (state[1]) {
                    if (metrics != null) metrics.terminationConfirmed();
                    // Do not leak the timer's interrupt into Quartz's next execution.
                    Thread.interrupted();
                    if (interruptedBefore) worker.interrupt();
                }
            }
        }
        if (failure instanceof JobExecutionException nativeError
                && (nativeError.unscheduleAllTriggers() || nativeError.unscheduleFiringTrigger())) throw nativeError;
        if (state[1]) throw new TaskTimeoutException(timeoutMs, true);
        if (failure != null) throw failure;
    }

    private void logExecution(TaskExecutionLog taskLog) {
        try {
            if (logWriter != null) {
                logWriter.write(taskLog);
            }
        } catch (Exception e) {
            log.error("Failed to log task execution for job: {}: {}", sanitize(taskLog.getJobKey(), 500), sanitize(e.getMessage(), 500));
        }
    }

    private TaskExecutionLog startReliableAudit(JobExecutionContext context, String executionId, int attempt,
                                                LocalDateTime startTime) throws JobExecutionException {
        if (properties == null || !properties.getLog().isReliableAudit()) return null;
        if (reliableAuditService == null) throw new JobExecutionException("ReliableAuditService is unavailable");
        TaskExecutionLog started = new TaskExecutionLog();
        started.setId(java.util.UUID.randomUUID().toString());
        started.setJobKey(context.getJobDetail().getKey().toString());
        String origin = context.getMergedJobDataMap() == null ? null
                : context.getMergedJobDataMap().getString(CoQuartzConstants.ORIGIN_TRIGGER_KEY);
        started.setTriggerKey(origin == null ? context.getTrigger().getKey().toString() : origin);
        started.setStartTime(startTime);
        started.setExecuteTime(startTime);
        started.setExecState(LogTaskExecStateEnum.STARTED);
        started.setAttempt(attempt);
        started.setFinalAttempt(false);
        populateExecutionCorrelation(started, context, executionId);
        try {
            reliableAuditService.recordStarted(started);
            return started;
        } catch (Exception e) {
            throw new JobExecutionException("Failed to create reliable audit record before execution", e);
        }
    }

    private void completeReliableAudit(TaskExecutionLog log) throws JobExecutionException {
        try {
            reliableAuditService.recordCompleted(log);
        } catch (Exception e) {
            throw new JobExecutionException("Failed to complete reliable audit record; business result="
                    + log.getExecState() + ", executionId=" + log.getExecutionId() + ", attempt=" + log.getAttempt(), e);
        }
    }

    private String stackTrace(Throwable error) {
        if (properties == null || !properties.getLog().isCaptureStackTrace()) return null;
        return sanitize(CoQuartzUtils.getStackTraceAsString(error), 4000);
    }

    private String sanitize(String value, int maxLength) {
        return CoQuartzUtils.truncate(logSanitizer.sanitize(value), maxLength);
    }

    private void populateExecutionCorrelation(TaskExecutionLog taskLog, JobExecutionContext context, String executionId) {
        taskLog.setExecutionId(executionId);
        taskLog.setApplicationNamespace(properties == null ? "default" : properties.getScheduling().getApplicationNamespace());
        JobDataMap runtime = context.getMergedJobDataMap();
        String origin = runtime == null ? null : runtime.getString(CoQuartzConstants.ORIGIN_TRIGGER_KEY);
        taskLog.setOriginTriggerKey(origin == null ? context.getTrigger().getKey().toString() : origin);
        Object scheduled = runtime == null ? null : runtime.get(CoQuartzConstants.ORIGIN_SCHEDULED_TIME);
        taskLog.setOriginScheduledTime(scheduled instanceof Number number ? number.longValue()
                : context.getScheduledFireTime() == null ? null : context.getScheduledFireTime().getTime());
        taskLog.setFireInstanceId(context.getFireInstanceId());
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        taskLog.setDefinitionVersion(jobDataMap == null ? null
                : jobDataMap.getString(CoQuartzConstants.DEFINITION_VERSION));
        try {
            Scheduler scheduler = context.getScheduler();
            taskLog.setSchedulerInstanceId(scheduler == null ? "unknown" : scheduler.getSchedulerInstanceId());
            taskLog.setSchedulerName(scheduler == null ? "unknown" : scheduler.getSchedulerName());
        } catch (SchedulerException e) {
            taskLog.setSchedulerInstanceId("unknown");
        }
    }

    private void scheduleRetry(JobExecutionContext context, String executionId, int nextAttempt, long delayMs)
            throws SchedulerException {
        JobDataMap data;
        try { data = RetryPayloadSnapshot.copy(context.getMergedJobDataMap()); }
        catch (java.io.IOException invalid) { throw new SchedulerException("Invalid retry payload", invalid); }
        JobDataMap runtime = context.getMergedJobDataMap();
        Object origin = runtime == null ? null : runtime.get(CoQuartzConstants.ORIGIN_TRIGGER_KEY);
        Object scheduled = runtime == null ? null : runtime.get(CoQuartzConstants.ORIGIN_SCHEDULED_TIME);
        data.put(CoQuartzConstants.ORIGIN_TRIGGER_KEY, origin == null ? context.getTrigger().getKey().toString() : origin);
        data.put(CoQuartzConstants.ORIGIN_SCHEDULED_TIME, scheduled == null
                ? (context.getScheduledFireTime() == null ? (context.getTrigger().getStartTime() == null ? 0L : context.getTrigger().getStartTime().getTime())
                : context.getScheduledFireTime().getTime()) : scheduled);
        data.put(CoQuartzConstants.OWNER, CoQuartzConstants.OWNER_VALUE);
        data.put(CoQuartzConstants.METADATA_VERSION, CoQuartzConstants.METADATA_VERSION_VALUE);
        data.put(CoQuartzConstants.RETRY_JOB_KEY, context.getJobDetail().getKey().toString());
        data.put(CoQuartzConstants.RETRY_EXECUTION_ID, executionId);
        data.put(CoQuartzConstants.RETRY_ATTEMPT, nextAttempt);
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("RETRY_" + context.getFireInstanceId() + "_" + nextAttempt,
                        CoQuartzConstants.RETRY_TRIGGER_GROUP)
                .forJob(context.getJobDetail().getKey())
                .usingJobData(data)
                .startAt(new java.util.Date(System.currentTimeMillis() + delayMs))
                .build();
        context.getScheduler().scheduleJob(trigger);
    }

    private int getIntConfig(String key, int defaultValue) {
        Object val = enhancedConfig.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) return Integer.parseInt((String) val);
        return defaultValue;
    }

    private int getIntConfig(JobDataMap data, String key, int defaultValue) {
        Object value = data == null ? null : data.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String string) return Integer.parseInt(string);
        return defaultValue;
    }

    private long getLongConfig(String key, long defaultValue) {
        Object val = enhancedConfig.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        return defaultValue;
    }

    private boolean getBooleanConfig(String key, boolean defaultValue) {
        Object val = enhancedConfig.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return Boolean.parseBoolean((String) val);
        return defaultValue;
    }

    private double getDoubleConfig(String key, double defaultValue) {
        Object val = enhancedConfig.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) return Double.parseDouble((String) val);
        return defaultValue;
    }
}
