package io.github.cococzl.coquartz.listener;

import io.github.cococzl.coquartz.core.CoQuartzConstants;
import io.github.cococzl.coquartz.core.CoQuartzUtils;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
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

    public CoQuartzJobListener(AsyncTaskLogService asyncTaskLogService, CoQuartzMetrics metrics, AlertEventPublisher alertEventPublisher) {
        this.asyncTaskLogService = asyncTaskLogService;
        this.metrics = metrics;
        this.alertEventPublisher = alertEventPublisher;
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
            errorMessage = CoQuartzUtils.truncate(jobException.getMessage(), 500);
            stackTrace = CoQuartzUtils.truncate(CoQuartzUtils.getStackTraceAsString(jobException), 4000);
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
        }

        TaskExecutionLog taskLog = new TaskExecutionLog();
        taskLog.setId(UUID.randomUUID().toString());
        taskLog.setJobKey(jobKey);
        taskLog.setTriggerKey(triggerKey);
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
            asyncTaskLogService.logTaskExecutionAsync(taskLog);
        } catch (Exception e) {
            log.error("Failed to log task execution for job: {}", jobKey, e);
        }
    }

    private boolean isEnhancedJob(JobExecutionContext context) {
        return context.getJobDetail().getJobDataMap().containsKey(CoQuartzConstants.ENHANCED);
    }
}