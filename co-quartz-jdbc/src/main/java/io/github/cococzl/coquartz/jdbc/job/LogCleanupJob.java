package io.github.cococzl.coquartz.jdbc.job;

import io.github.cococzl.coquartz.service.TaskLogRepository;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class LogCleanupJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(LogCleanupJob.class);

    public static final String JOB_NAME = "CO_QUARTZ_LOG_CLEANUP_JOB";
    public static final String JOB_GROUP = "CO_QUARTZ_INTERNAL";
    public static final String RETENTION_DAYS_KEY = "retentionDays";
    private TaskLogRepository taskLogRepository;

    @Autowired
    public void setTaskLogRepository(TaskLogRepository taskLogRepository) {
        this.taskLogRepository = taskLogRepository;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        if (taskLogRepository == null) {
            throw new JobExecutionException("TaskLogRepository was not injected into the cleanup job");
        }

        int retentionDays = context.getJobDetail().getJobDataMap().getIntValue(RETENTION_DAYS_KEY);
        log.info("Co-Quartz log cleanup started, retention days: {}", retentionDays);
        try {
            int deleted = taskLogRepository.cleanup(retentionDays);
            log.info("Co-Quartz log cleanup completed, deleted {} records older than {} days", deleted, retentionDays);
        } catch (Exception e) {
            log.error("Co-Quartz log cleanup failed", e);
            throw new JobExecutionException("Log cleanup failed", e);
        }
    }
}
