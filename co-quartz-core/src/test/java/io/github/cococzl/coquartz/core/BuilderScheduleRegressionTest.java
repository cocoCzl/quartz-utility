package io.github.cococzl.coquartz.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;

class BuilderScheduleRegressionTest {
    private Scheduler scheduler;
    Scheduler scheduler() throws Exception {
        if (scheduler == null) {
            var config = new Properties();
            config.setProperty("org.quartz.scheduler.instanceName", "builder-regression-" + System.nanoTime());
            config.setProperty("org.quartz.threadPool.threadCount", "1");
            scheduler = new StdSchedulerFactory(config).getScheduler();
        }
        return scheduler;
    }
    @AfterEach void close() throws Exception { if (scheduler != null) scheduler.shutdown(true); }
    public static class SampleJob implements Job { public void execute(JobExecutionContext context) { } }
    QuartzTaskBuilder builder(String name) {
        return QuartzTaskBuilder.newBuilder().jobClass(SampleJob.class).jobName(name)
                .startAt(new java.util.Date(System.currentTimeMillis() + 3600000)).intervalInSeconds(3600);
    }

    @Test void defaultNonDurableTaskCanBeUpdatedAndPausedStateIsPreserved() throws Exception {
        builder("update").schedule(scheduler());
        scheduler.pauseTrigger(TriggerKey.triggerKey("TRIGGER_update"));
        builder("update").intervalInSeconds(120).jobData("payload", "new").schedule(scheduler);
        assertThat(scheduler.getJobDetail(JobKey.jobKey("update")).isDurable()).isFalse();
        assertThat(scheduler.getJobDetail(JobKey.jobKey("update")).getJobDataMap().getString("payload"))
                .isEqualTo("new");
        assertThat(((SimpleTrigger) scheduler.getTrigger(TriggerKey.triggerKey("TRIGGER_update")))
                .getRepeatInterval()).isEqualTo(120000);
        assertThat(scheduler.getTriggerState(TriggerKey.triggerKey("TRIGGER_update")))
                .isEqualTo(Trigger.TriggerState.PAUSED);
    }
}
