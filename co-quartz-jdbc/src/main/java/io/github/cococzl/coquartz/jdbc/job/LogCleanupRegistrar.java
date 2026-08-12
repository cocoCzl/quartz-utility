package io.github.cococzl.coquartz.jdbc.job;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.core.QuartzTriggerFactory;
import io.github.cococzl.coquartz.enums.MisfirePolicy;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogCleanupRegistrar {

    private static final Logger log = LoggerFactory.getLogger(LogCleanupRegistrar.class);

    private final Scheduler scheduler;
    private final CoQuartzProperties properties;

    public LogCleanupRegistrar(Scheduler scheduler, CoQuartzProperties properties) {
        this.scheduler = scheduler;
        this.properties = properties;
    }

    public void register() {
        if (!properties.getLog().isEnabled()) {
            log.info("Co-Quartz log cleanup disabled");
            return;
        }

        JobKey jobKey = new JobKey(LogCleanupJob.JOB_NAME, LogCleanupJob.JOB_GROUP);
        try {
            if (scheduler.checkExists(jobKey)) {
                log.info("Co-Quartz log cleanup job already registered");
                return;
            }

            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put(LogCleanupJob.RETENTION_DAYS_KEY, properties.getLog().getRetentionDays());

            JobDetail jobDetail = JobBuilder.newJob(LogCleanupJob.class)
                    .withIdentity(jobKey)
                    .storeDurably()
                    .usingJobData(jobDataMap)
                    .build();

            String cronExpression = properties.getLog().getCleanupCron();
            Trigger trigger = QuartzTriggerFactory.build(
                            "TRIGGER_" + LogCleanupJob.JOB_NAME,
                            LogCleanupJob.JOB_GROUP,
                            cronExpression,
                            0,
                            properties.getScheduling().getDefaultTimeZone(),
                            MisfirePolicy.SMART_POLICY,
                            null,
                            null,
                            jobKey);

            scheduler.scheduleJob(jobDetail, trigger);
            log.info("Co-Quartz log cleanup job registered with cron: {}", cronExpression);
        } catch (ObjectAlreadyExistsException e) {
            log.info("Co-Quartz log cleanup job was registered by another scheduler node");
        } catch (SchedulerException e) {
            log.error("Failed to register Co-Quartz log cleanup job", e);
        }
    }
}
