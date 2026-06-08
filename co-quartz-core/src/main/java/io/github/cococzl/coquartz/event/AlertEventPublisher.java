package io.github.cococzl.coquartz.event;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

public class AlertEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlertEventPublisher.class);

    private final ApplicationEventPublisher eventPublisher;
    private final TaskLogRepository taskLogRepository;
    private final CoQuartzProperties properties;

    public AlertEventPublisher(ApplicationEventPublisher eventPublisher,
                                TaskLogRepository taskLogRepository,
                                CoQuartzProperties properties) {
        this.eventPublisher = eventPublisher;
        this.taskLogRepository = taskLogRepository;
        this.properties = properties;
    }

    public AlertEventPublisher(ApplicationEventPublisher eventPublisher,
                                CoQuartzProperties properties) {
        this.eventPublisher = eventPublisher;
        this.taskLogRepository = null;
        this.properties = properties;
    }

    public void publishFailure(String jobKey, String errorMessage, String stackTrace) {
        try {
            eventPublisher.publishEvent(new TaskFailureEvent(this, jobKey, errorMessage, stackTrace));
        } catch (Exception e) {
            log.error("Failed to publish TaskFailureEvent for job: {}", jobKey, e);
        }
    }

    public void publishTimeout(String jobKey, long timeoutMs) {
        try {
            eventPublisher.publishEvent(new TaskTimeoutEvent(this, jobKey, timeoutMs));
        } catch (Exception e) {
            log.error("Failed to publish TaskTimeoutEvent for job: {}", jobKey, e);
        }
    }

    public void publishSlowTask(String jobKey, long executionTimeMs, long thresholdMs) {
        try {
            eventPublisher.publishEvent(new TaskSlowEvent(this, jobKey, executionTimeMs, thresholdMs));
        } catch (Exception e) {
            log.error("Failed to publish TaskSlowEvent for job: {}", jobKey, e);
        }
    }

    public void publishConsecutiveFailureIfNeeded(String jobKey) {
        if (taskLogRepository == null) {
            return;
        }
        try {
            int threshold = properties.getMonitoring().getConsecutiveFailureThreshold();
            List<TaskExecutionLog> recentLogs = taskLogRepository.findRecentByJobKey(jobKey, threshold);
            if (recentLogs.size() >= threshold) {
                boolean allFailed = recentLogs.stream()
                        .allMatch(l -> l.getExecState() == LogTaskExecStateEnum.FAIL);
                if (allFailed) {
                    eventPublisher.publishEvent(new TaskConsecutiveFailureEvent(this, jobKey, threshold, recentLogs.size()));
                }
            }
        } catch (Exception e) {
            log.error("Failed to check consecutive failures for job: {}", jobKey, e);
        }
    }
}