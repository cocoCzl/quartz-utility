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
import io.github.cococzl.coquartz.config.CoQuartzProperties;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

public class CoQuartzJobListener implements JobListener {

    private static final Logger log = LoggerFactory.getLogger(CoQuartzJobListener.class);

    private final AsyncTaskLogService asyncTaskLogService;
    private final CoQuartzMetrics metrics;
    private final AlertEventPublisher alertEventPublisher;
    private final CoQuartzProperties properties;
    private final LogSanitizer logSanitizer;
    private final ReliableAuditService reliableAuditService;

    public CoQuartzJobListener(AsyncTaskLogService asyncTaskLogService, CoQuartzMetrics metrics, AlertEventPublisher alertEventPublisher) {
        this(asyncTaskLogService, metrics, alertEventPublisher, new CoQuartzProperties(), new DefaultLogSanitizer());
    }

    public CoQuartzJobListener(AsyncTaskLogService asyncTaskLogService, CoQuartzMetrics metrics,
                                AlertEventPublisher alertEventPublisher, CoQuartzProperties properties,
                                LogSanitizer logSanitizer) {
        this(asyncTaskLogService, metrics, alertEventPublisher, properties, logSanitizer, null);
    }

    public CoQuartzJobListener(AsyncTaskLogService asyncTaskLogService, CoQuartzMetrics metrics,
                                AlertEventPublisher alertEventPublisher, CoQuartzProperties properties,
                                LogSanitizer logSanitizer, ReliableAuditService reliableAuditService) {
        this.asyncTaskLogService = asyncTaskLogService;
        this.metrics = metrics;
        this.alertEventPublisher = alertEventPublisher;
        this.properties = properties;
        this.logSanitizer = logSanitizer == null ? new DefaultLogSanitizer() : logSanitizer;
        this.reliableAuditService = reliableAuditService;
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
        if (isEnhancedJob(context) || reliableAuditService == null || !properties.getLog().isReliableAudit()) return;
        LocalDateTime start = LocalDateTime.now();
        TaskExecutionLog log = new TaskExecutionLog();
        log.setId(UUID.randomUUID().toString());
        log.setJobKey(context.getJobDetail().getKey().toString());
        log.setTriggerKey(context.getTrigger().getKey().toString());
        log.setExecutionId(UUID.randomUUID().toString());
        log.setFireInstanceId(context.getFireInstanceId());
        log.setStartTime(start);
        log.setExecuteTime(start);
        log.setAttempt(1);
        log.setFinalAttempt(false);
        log.setExecState(LogTaskExecStateEnum.STARTED);
        try {
            reliableAuditService.recordStarted(log);
            context.put(CoQuartzConstants.RELIABLE_AUDIT_CONTEXT, log);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create reliable audit record before execution", e);
        }
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        if (isEnhancedJob(context)) {
            return;
        }

        String jobKey = context.getJobDetail().getKey().toString();
        String triggerKey = context.getTrigger().getKey().toString();
        LocalDateTime startTime = context.getFireTime() != null
                ? context.getFireTime().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                : LocalDateTime.now();
        LocalDateTime endTime = LocalDateTime.now();
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

        if (metrics != null) {
            if (execState == LogTaskExecStateEnum.SUCCESS) {
                metrics.recordSuccess(jobKey, executionTimeMs);
            } else {
                metrics.recordFailure(jobKey, executionTimeMs);
            }
        }

        if (alertEventPublisher != null && execState == LogTaskExecStateEnum.FAIL) {
            alertEventPublisher.publishFailure(jobKey, errorMessage, stackTrace);
        } else if (alertEventPublisher != null) {
            alertEventPublisher.recordSuccess(jobKey);
        }

        TaskExecutionLog taskLog = new TaskExecutionLog();
        taskLog.setId(UUID.randomUUID().toString());
        taskLog.setJobKey(jobKey);
        taskLog.setTriggerKey(triggerKey);
        taskLog.setExecutionId(UUID.randomUUID().toString());
        taskLog.setFireInstanceId(context.getFireInstanceId());
        taskLog.setDefinitionVersion(context.getJobDetail().getJobDataMap()
                .getString(CoQuartzConstants.DEFINITION_VERSION));
        try {
            org.quartz.Scheduler scheduler = context.getScheduler();
            taskLog.setSchedulerInstanceId(scheduler == null ? "unknown" : scheduler.getSchedulerInstanceId());
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

        TaskExecutionLog auditLog = (TaskExecutionLog) context.get(CoQuartzConstants.RELIABLE_AUDIT_CONTEXT);
        if (auditLog != null) {
            taskLog.setId(auditLog.getId());
            try {
                reliableAuditService.recordCompleted(taskLog);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to complete reliable audit record", e);
            }
        } else {
            try {
                asyncTaskLogService.logTaskExecutionAsync(taskLog);
            } catch (Exception e) {
                log.error("Failed to log task execution for job: {}", jobKey, e);
            }
        }
    }

    private boolean isEnhancedJob(JobExecutionContext context) {
        return context.getJobDetail().getJobDataMap().containsKey(CoQuartzConstants.ENHANCED);
    }

    private String sanitize(String value, int maxLength) {
        return CoQuartzUtils.truncate(logSanitizer.sanitize(value), maxLength);
    }
}
