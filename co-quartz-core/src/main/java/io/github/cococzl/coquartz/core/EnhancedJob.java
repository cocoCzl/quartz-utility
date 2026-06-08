package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

public class EnhancedJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(EnhancedJob.class);

    private final Job delegate;
    private final JobDataMap enhancedConfig;
    private final AsyncTaskLogService asyncTaskLogService;
    private final ScheduledExecutorService timeoutExecutor;
    private final AlertEventPublisher alertEventPublisher;
    private final CoQuartzProperties properties;
    private final CoQuartzMetrics metrics;

    public EnhancedJob(Job delegate, JobDataMap enhancedConfig,
                       AsyncTaskLogService asyncTaskLogService,
                       ScheduledExecutorService timeoutExecutor,
                       AlertEventPublisher alertEventPublisher,
                       CoQuartzProperties properties,
                       CoQuartzMetrics metrics) {
        this.delegate = delegate;
        this.enhancedConfig = enhancedConfig;
        this.asyncTaskLogService = asyncTaskLogService;
        this.timeoutExecutor = timeoutExecutor;
        this.alertEventPublisher = alertEventPublisher;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        int retryTimes = getIntConfig(CoQuartzConstants.RETRY_TIMES, 0);
        long retryInterval = getLongConfig(CoQuartzConstants.RETRY_INTERVAL, 1000);
        boolean exponentialBackoff = getBooleanConfig(CoQuartzConstants.EXPONENTIAL_BACKOFF, false);
        double backoffMultiplier = getDoubleConfig(CoQuartzConstants.BACKOFF_MULTIPLIER, 1.5);
        long timeoutMs = getLongConfig(CoQuartzConstants.TIMEOUT, 0);
        String jobKey = context.getJobDetail().getKey().toString();

        RetryContext retryContext = new RetryContext(retryTimes, retryInterval, exponentialBackoff, backoffMultiplier);
        Exception lastException = null;

        if (metrics != null) {
            metrics.jobStarted();
        }

        try {
            while (true) {
                retryContext.recordAttempt();
                int attempt = retryContext.getCurrentAttempt();

                LocalDateTime startTime = LocalDateTime.now();
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
                    if (timeoutMs > 0 && e.getMessage() != null && e.getMessage().startsWith("Task timed out")) {
                        timedOut = true;
                    }
                    execState = LogTaskExecStateEnum.FAIL;
                    errorMessage = CoQuartzUtils.truncate(e.getMessage(), 500);
                    stackTrace = CoQuartzUtils.truncate(CoQuartzUtils.getStackTraceAsString(e), 4000);
                    lastException = e;
                } catch (Exception e) {
                    execState = LogTaskExecStateEnum.FAIL;
                    errorMessage = CoQuartzUtils.truncate(e.getMessage(), 500);
                    stackTrace = CoQuartzUtils.truncate(CoQuartzUtils.getStackTraceAsString(e), 4000);
                    lastException = new JobExecutionException(e);
                }

                long executionTimeMs = System.currentTimeMillis() - startMs;
                LocalDateTime endTime = LocalDateTime.now();
                String triggerKey = context.getTrigger().getKey().toString();

                boolean noMoreRetries = !retryContext.canRetry();

                TaskExecutionLog taskLog;
                if (execState == LogTaskExecStateEnum.SUCCESS) {
                    taskLog = TaskExecutionLog.success(jobKey, triggerKey, startTime, endTime, executionTimeMs, attempt, true);
                } else {
                    taskLog = TaskExecutionLog.failure(jobKey, triggerKey, startTime, endTime, executionTimeMs, errorMessage, stackTrace, attempt, noMoreRetries);
                }

                logExecution(taskLog);

                if (metrics != null) {
                    if (execState == LogTaskExecStateEnum.SUCCESS) {
                        metrics.recordSuccess(jobKey, executionTimeMs);
                    } else {
                        metrics.recordFailure(jobKey, executionTimeMs);
                    }
                }

                if (execState == LogTaskExecStateEnum.SUCCESS) {
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
                        alertEventPublisher.publishTimeout(jobKey, timeoutMs);
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

                long delay = retryContext.getNextRetryDelay();
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new JobExecutionException("Retry interrupted", ie);
                }
            }
        } finally {
            if (metrics != null) {
                metrics.jobFinished();
            }
        }
    }

    private void executeWithTimeout(JobExecutionContext context, long timeoutMs) throws Exception {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                delegate.execute(context);
            } catch (JobExecutionException e) {
                throw new CompletionException(e);
            }
        }, timeoutExecutor);

        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new JobExecutionException("Task timed out after " + timeoutMs + "ms");
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
            if (asyncTaskLogService != null) {
                asyncTaskLogService.logTaskExecutionAsync(taskLog);
            }
        } catch (Exception e) {
            log.error("Failed to log task execution for job: {}", taskLog.getJobKey(), e);
        }
    }

    private int getIntConfig(String key, int defaultValue) {
        Object val = enhancedConfig.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) return Integer.parseInt((String) val);
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