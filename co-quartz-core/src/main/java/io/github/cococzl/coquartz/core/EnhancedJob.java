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
import java.util.concurrent.atomic.AtomicBoolean;

public class EnhancedJob implements Job {

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

                LocalDateTime startTime = LocalDateTime.now();
                TaskExecutionLog auditLog = startReliableAudit(context, executionId, attempt, startTime);
                long startMs = System.currentTimeMillis();
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

                long executionTimeMs = System.currentTimeMillis() - startMs;
                LocalDateTime endTime = LocalDateTime.now();
                String triggerKey = context.getTrigger().getKey().toString();

                boolean noMoreRetries = attempt > retryTimes;

                TaskExecutionLog taskLog;
                if (execState == LogTaskExecStateEnum.SUCCESS) {
                    taskLog = TaskExecutionLog.success(jobKey, triggerKey, startTime, endTime, executionTimeMs, attempt, true);
                } else {
                    taskLog = TaskExecutionLog.failure(jobKey, triggerKey, startTime, endTime, executionTimeMs, errorMessage, stackTrace, attempt, noMoreRetries);
                }
                populateExecutionCorrelation(taskLog, context, executionId);
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

                try {
                    for (int index = 1; index < attempt; index++) {
                        retryContext.getNextRetryDelay();
                    }
                    scheduleRetry(context, executionId, attempt + 1, retryContext.getNextRetryDelay());
                    if (metrics != null) metrics.recordRetry(jobKey);
                    return;
                } catch (SchedulerException scheduleFailure) {
                    throw new JobExecutionException("Failed to schedule delayed retry", scheduleFailure);
                }
            }
        } finally {
            if (metrics != null) {
                metrics.jobFinished();
            }
        }
    }

    private void executeWithTimeout(JobExecutionContext context, long timeoutMs) throws Exception {
        AtomicBoolean completed = new AtomicBoolean();
        Future<?> future = timeoutExecutor.submit(() -> {
            try {
                delegate.execute(context);
            } catch (JobExecutionException e) {
                throw new CompletionException(e);
            } finally {
                completed.set(true);
            }
        }, timeoutExecutor);

        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TaskTimeoutException(timeoutMs, completed.get());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof JobExecutionException jee) {
                throw jee;
            }
            throw new JobExecutionException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JobExecutionException("Task execution interrupted", e);
        }
    }

    private void logExecution(TaskExecutionLog taskLog) {
        try {
            if (logWriter != null) {
                logWriter.write(taskLog);
            }
        } catch (Exception e) {
            log.error("Failed to log task execution for job: {}", taskLog.getJobKey(), e);
        }
    }

    private TaskExecutionLog startReliableAudit(JobExecutionContext context, String executionId, int attempt,
                                                LocalDateTime startTime) throws JobExecutionException {
        if (reliableAuditService == null || properties == null || !properties.getLog().isReliableAudit()) return null;
        TaskExecutionLog started = new TaskExecutionLog();
        started.setId(java.util.UUID.randomUUID().toString());
        started.setJobKey(context.getJobDetail().getKey().toString());
        started.setTriggerKey(context.getTrigger().getKey().toString());
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
            throw new JobExecutionException("Failed to complete reliable audit record", e);
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
        taskLog.setFireInstanceId(context.getFireInstanceId());
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        taskLog.setDefinitionVersion(jobDataMap == null ? null
                : jobDataMap.getString(CoQuartzConstants.DEFINITION_VERSION));
        try {
            Scheduler scheduler = context.getScheduler();
            taskLog.setSchedulerInstanceId(scheduler == null ? "unknown" : scheduler.getSchedulerInstanceId());
        } catch (SchedulerException e) {
            taskLog.setSchedulerInstanceId("unknown");
        }
    }

    private void scheduleRetry(JobExecutionContext context, String executionId, int nextAttempt, long delayMs)
            throws SchedulerException {
        JobDataMap data = new JobDataMap();
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
