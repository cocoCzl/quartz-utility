package io.github.cococzl.coquartz.jdbc.job;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;

class LogCleanupRegistrarTest {
    @Test void restartUpdatesCleanupDefinitionAndDisablingRemovesIt() throws Exception {
        var config = new Properties();
        config.setProperty("org.quartz.scheduler.instanceName", "cleanup-" + System.nanoTime());
        config.setProperty("org.quartz.threadPool.threadCount", "1");
        Scheduler scheduler = new StdSchedulerFactory(config).getScheduler();
        try {
            var properties = new CoQuartzProperties(); properties.getLog().setEnabled(true);
            var registrar = new LogCleanupRegistrar(scheduler, properties);
            registrar.register();
            properties.getLog().setRetentionDays(42);
            properties.getLog().setCleanupCron("0 0 5 * * ?");
            properties.getScheduling().setDefaultTimeZone("Asia/Shanghai");
            registrar.register();
            JobKey key = JobKey.jobKey(LogCleanupJob.JOB_NAME, LogCleanupJob.JOB_GROUP);
            assertThat(scheduler.getJobDetail(key).getJobDataMap().getInt(LogCleanupJob.RETENTION_DAYS_KEY)).isEqualTo(42);
            var trigger = (CronTrigger) scheduler.getTriggersOfJob(key).get(0);
            assertThat(trigger.getCronExpression()).isEqualTo("0 0 5 * * ?");
            assertThat(trigger.getTimeZone().getID()).isEqualTo("Asia/Shanghai");
            assertThat(scheduler.getJobDetail(key).isConcurrentExectionDisallowed()).isTrue();
            properties.getLog().setEnabled(false); registrar.register();
            assertThat(scheduler.checkExists(key)).isFalse();
        } finally { scheduler.shutdown(true); }
    }
}
