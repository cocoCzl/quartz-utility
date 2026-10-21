package io.github.cococzl.coquartz.listener;

import io.github.cococzl.coquartz.core.CoQuartzConstants;
import io.github.cococzl.coquartz.core.CoQuartzUtils;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.github.cococzl.coquartz.service.DefaultLogSanitizer;
import io.github.cococzl.coquartz.service.LogSanitizer;
import io.github.cococzl.coquartz.service.ReliableAuditService;
import io.github.cococzl.coquartz.service.TaskExecutionLogWriter;
import io.github.cococzl.coquartz.config.CoQuartzProperties;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;

public class CoQuartzJobListener implements JobListener {

    private static final Logger log = LoggerFactory.getLogger(CoQuartzJobListener.class);

    private final TaskExecutionLogWriter logWriter;
    private final CoQuartzMetrics metrics;
    private final AlertEventPublisher alertEventPublisher;
    private final CoQuartzProperties properties;
    private final LogSanitizer logSanitizer;
    private final ReliableAuditService reliableAuditService;
    private final ObjectProvider<TaskExecutionLogWriter> logWriterProvider;
    private final ObjectProvider<CoQuartzMetrics> metricsProvider;
    private final ObjectProvider<AlertEventPublisher> alertPublisherProvider;
    private final ObjectProvider<ReliableAuditService> reliableAuditProvider;

    public CoQuartzJobListener(ObjectProvider<TaskExecutionLogWriter> logWriterProvider,
                               ObjectProvider<CoQuartzMetrics> metricsProvider,
                               ObjectProvider<AlertEventPublisher> alertPublisherProvider,
                               CoQuartzProperties properties, LogSanitizer logSanitizer,
                               ObjectProvider<ReliableAuditService> reliableAuditProvider) {
        this.logWriter = null;
        this.metrics = null;
        this.alertEventPublisher = null;
        this.reliableAuditService = null;
        this.logWriterProvider = logWriterProvider;
        this.metricsProvider = metricsProvider;
        this.alertPublisherProvider = alertPublisherProvider;
        this.reliableAuditProvider = reliableAuditProvider;
        this.properties = properties;
        this.logSanitizer = logSanitizer == null ? new DefaultLogSanitizer() : logSanitizer;
    }

    public CoQuartzJobListener(AsyncTaskLogService asyncTaskLogService, CoQuartzMetrics metrics, AlertEventPublisher alertEventPublisher) {
        this(asyncTaskLogService, metrics, alertEventPublisher, new CoQuartzProperties(), new DefaultLogSanitizer());
    }

    public CoQuartzJobListener(AsyncTaskLogService asyncTaskLogService, CoQuartzMetrics metrics,
                                AlertEventPublisher alertEventPublisher, CoQuartzProperties properties,
                                LogSanitizer logSanitizer) {
        this(asyncTaskLogService, metrics, alertEventPublisher, properties, logSanitizer, null);
    }

    public CoQuartzJobListener(TaskExecutionLogWriter logWriter, CoQuartzMetrics metrics,
                               AlertEventPublisher alertEventPublisher, CoQuartzProperties properties,
                               LogSanitizer logSanitizer, ReliableAuditService reliableAuditService) {
        this.logWriter = logWriter;
        this.metrics = metrics;
        this.alertEventPublisher = alertEventPublisher;
        this.properties = properties;
        this.logSanitizer = logSanitizer == null ? new DefaultLogSanitizer() : logSanitizer;
        this.reliableAuditService = reliableAuditService;
        this.logWriterProvider = null;
        this.metricsProvider = null;
        this.alertPublisherProvider = null;
        this.reliableAuditProvider = null;
    }

    public CoQuartzJobListener(AsyncTaskLogService asyncTaskLogService, CoQuartzMetrics metrics,
                                AlertEventPublisher alertEventPublisher, CoQuartzProperties properties,
                                LogSanitizer logSanitizer, ReliableAuditService reliableAuditService) {
        this.logWriter = asyncTaskLogService;
        this.metrics = metrics;
        this.alertEventPublisher = alertEventPublisher;
        this.properties = properties;
        this.logSanitizer = logSanitizer == null ? new DefaultLogSanitizer() : logSanitizer;
        this.reliableAuditService = reliableAuditService;
        this.logWriterProvider = null;
        this.metricsProvider = null;
        this.alertPublisherProvider = null;
        this.reliableAuditProvider = null;
    }

    public CoQuartzJobListener(AsyncTaskLogService asyncTaskLogService, CoQuartzMetrics metrics) {
        this(asyncTaskLogService, metrics, null);
    }

    public CoQuartzJobListener(AsyncTaskLogService asyncTaskLogService) {
        this(asyncTaskLogService, null, null);
    }

    @Override
    public String getName() {
        return "coQuartzJobListener";
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        if (isEnhancedJob(context)) return;
        try {
            CoQuartzMetrics current = metrics();
            if (current != null) { current.jobStarted(); context.put("co-quartz.observerActive", true); }
            context.put("co-quartz.observerExecutionId", UUID.randomUUID().toString());
        } catch (RuntimeException error) { log.error("Task observation start failed: {}", sanitize(error.getMessage(), 500)); }
        // Reliable audit is part of the controlled execution decorator, never a listener veto.
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        if (isEnhancedJob(context)) return;
        try {
            observeCompletion(context, jobException);
        } catch (Exception error) {
            log.error("Task observation failed: {}", sanitize(error.getMessage(), 500));
        } finally {
            if (Boolean.TRUE.equals(context.get("co-quartz.observerActive"))) {
                try { if (metrics() != null) metrics().jobFinished(); } catch (RuntimeException ignored) { }
                context.put("co-quartz.observerActive", false);
            }
            if (alertPublisher() != null) alertPublisher().clearExecutionCorrelation();
        }
    }

    private void observeCompletion(JobExecutionContext context, JobExecutionException jobException) {
        if (isEnhancedJob(context)) {
            return;
        }

        String jobKey = context.getJobDetail().getKey().toString();
        String triggerKey = context.getTrigger().getKey().toString();
        LocalDateTime startTime = context.getFireTime() != null
                ? context.getFireTime().toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDateTime()
                : LocalDateTime.now(java.time.ZoneOffset.UTC);
        LocalDateTime endTime = LocalDateTime.now(java.time.ZoneOffset.UTC);
        long executionTimeMs = context.getJobRunTime();

        LogTaskExecStateEnum execState = (jobException == null)
                ? LogTaskExecStateEnum.SUCCESS
                : LogTaskExecStateEnum.FAIL;

        String errorMessage = null;
        String stackTrace = null;
        if (jobException != null) {
            errorMessage = sanitize(jobException.getMessage(), 500);
            stackTrace = properties.getLog().isCaptureStackTrace()
                    ? sanitize(CoQuartzUtils.getStackTraceAsString(jobException), 4000) : null;
        }

        String observerExecution = (String) context.get("co-quartz.observerExecutionId");
        if (observerExecution == null) observerExecution = UUID.randomUUID().toString();
        TaskExecutionLog correlation = new TaskExecutionLog();
        correlation.setExecutionId(observerExecution); correlation.setAttempt(1); correlation.setFinalAttempt(true);
        try { correlation.setSchedulerInstanceId(context.getScheduler() == null ? "unknown" : context.getScheduler().getSchedulerInstanceId()); }
        catch (org.quartz.SchedulerException ignored) { correlation.setSchedulerInstanceId("unknown"); }
        if (alertPublisher() != null) alertPublisher().setExecutionCorrelation(correlation);
        CoQuartzMetrics currentMetrics = metrics();
        AlertEventPublisher currentAlertPublisher = alertPublisher();
        if (currentMetrics != null) {
            if (execState == LogTaskExecStateEnum.SUCCESS) {
                currentMetrics.recordSuccess(jobKey, executionTimeMs);
            } else {
                currentMetrics.recordFailure(jobKey, executionTimeMs);
            }
        }

        if (currentAlertPublisher != null && execState == LogTaskExecStateEnum.FAIL) {
            currentAlertPublisher.publishFailure(jobKey, errorMessage, stackTrace);
            currentAlertPublisher.publishConsecutiveFailureIfNeeded(jobKey);
        } else if (currentAlertPublisher != null) {
            currentAlertPublisher.recordSuccess(jobKey);
            long threshold = properties.getMonitoring().getSlowTaskThresholdMs();
            if (threshold > 0 && executionTimeMs > threshold) currentAlertPublisher.publishSlowTask(jobKey, executionTimeMs, threshold);
        }

        TaskExecutionLog taskLog = new TaskExecutionLog();
        taskLog.setId(UUID.randomUUID().toString());
        taskLog.setJobKey(jobKey);
        taskLog.setTriggerKey(triggerKey);
        taskLog.setExecutionId(observerExecution);
        taskLog.setApplicationNamespace(properties.getScheduling().getApplicationNamespace());
        taskLog.setOriginTriggerKey(triggerKey);
        taskLog.setOriginScheduledTime(context.getScheduledFireTime() == null ? null : context.getScheduledFireTime().getTime());
        taskLog.setFireInstanceId(context.getFireInstanceId());
        taskLog.setDefinitionVersion(context.getJobDetail().getJobDataMap()
                .getString(CoQuartzConstants.DEFINITION_VERSION));
        try {
            org.quartz.Scheduler scheduler = context.getScheduler();
            taskLog.setSchedulerInstanceId(scheduler == null ? "unknown" : scheduler.getSchedulerInstanceId());
            taskLog.setSchedulerName(scheduler == null ? "unknown" : scheduler.getSchedulerName());
        } catch (org.quartz.SchedulerException e) {
            taskLog.setSchedulerInstanceId("unknown");
        }
        taskLog.setStartTime(startTime);
        taskLog.setEndTime(endTime);
        taskLog.setExecutionTimeMs(executionTimeMs);
        taskLog.setExecState(execState);
        taskLog.setErrorMessage(errorMessage);
        taskLog.setStackTrace(stackTrace);
        taskLog.setAttempt(1);
        taskLog.setFinalAttempt(true);
        taskLog.setExecuteTime(startTime);

        try {
            TaskExecutionLogWriter writer = logWriter();
            if (writer != null) writer.write(taskLog);
        } catch (Exception e) {
            log.error("Failed to log task execution for job: {}: {}", sanitize(jobKey, 500), sanitize(e.getMessage(), 500));
        }
    }

    private boolean isEnhancedJob(JobExecutionContext context) {
        return context.getJobInstance() instanceof io.github.cococzl.coquartz.core.EnhancedJob
                || Boolean.parseBoolean(String.valueOf(context.getJobDetail().getJobDataMap()
                        .get(CoQuartzConstants.ENHANCED)));
    }

    private String sanitize(String value, int maxLength) {
        return CoQuartzUtils.truncate(logSanitizer.sanitize(value), maxLength);
    }

    private TaskExecutionLogWriter logWriter() {
        return logWriterProvider == null ? logWriter : logWriterProvider.getIfAvailable();
    }

    private CoQuartzMetrics metrics() {
        return metricsProvider == null ? metrics : metricsProvider.getIfAvailable();
    }

    private AlertEventPublisher alertPublisher() {
        return alertPublisherProvider == null ? alertEventPublisher : alertPublisherProvider.getIfAvailable();
    }

    private ReliableAuditService reliableAudit() {
        return reliableAuditProvider == null ? reliableAuditService : reliableAuditProvider.getIfAvailable();
    }
}
