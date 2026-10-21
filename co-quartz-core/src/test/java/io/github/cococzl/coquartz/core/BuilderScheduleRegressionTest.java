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
    @Test void differentGroupsCanUseSameDefaultNames() throws Exception {
        builder("same").jobGroup("first").schedule(scheduler());
        builder("same").jobGroup("second").schedule(scheduler);
        assertThat(scheduler.getTrigger(TriggerKey.triggerKey("TRIGGER_same", "first")).getJobKey())
                .isEqualTo(JobKey.jobKey("same", "first"));
        assertThat(scheduler.getTrigger(TriggerKey.triggerKey("TRIGGER_same", "second")).getJobKey())
                .isEqualTo(JobKey.jobKey("same", "second"));
    }

    @Test void legacyDefaultTriggerGroupMustBeExplicitBeforeUpdatingGroupedTask() throws Exception {
        builder("legacy").jobGroup("orders").triggerGroup("DEFAULT").schedule(scheduler());
        JobKey key = JobKey.jobKey("legacy", "orders");
        JobDetail previous = scheduler.getJobDetail(key);
        var legacyData = new JobDataMap(previous.getJobDataMap());
        legacyData.remove(CoQuartzConstants.MAIN_TRIGGER_KEY);
        scheduler.addJob(previous.getJobBuilder().setJobData(legacyData).build(), true, true);
        assertThatThrownBy(() -> builder("legacy").jobGroup("orders").intervalInSeconds(120).schedule(scheduler))
                .isInstanceOf(io.github.cococzl.coquartz.exception.CoQuartzSchedulingException.class)
                .hasMessageContaining("triggerGroup");
        assertThat(scheduler.getTriggersOfJob(key)).hasSize(1);
        assertThat(scheduler.getJobDetail(key).getJobDataMap()).isEqualTo(legacyData);
        builder("legacy").jobGroup("orders").triggerGroup("DEFAULT").intervalInSeconds(120).schedule(scheduler);
        assertThat(scheduler.getTriggersOfJob(key)).hasSize(1);
        assertThat(((SimpleTrigger) scheduler.getTrigger(TriggerKey.triggerKey("TRIGGER_legacy"))).getRepeatInterval())
                .isEqualTo(120000);
        assertThat(scheduler.getJobDetail(key).isDurable()).isFalse();
    }

    @Test void builderCannotOverwriteCodeOwnedOrExternalTasks() throws Exception {
        for (boolean codeOwned : new boolean[]{true, false}) {
            String name = codeOwned ? "code" : "external";
            var data = new JobDataMap();
            data.put(CoQuartzConstants.CODE_OWNED, String.valueOf(codeOwned));
            data.put(CoQuartzConstants.OWNER, codeOwned ? CoQuartzConstants.OWNER_VALUE : "external");
            var original = JobBuilder.newJob(SampleJob.class).withIdentity(name).storeDurably()
                    .usingJobData(data).build();
            scheduler().addJob(original, false);
            assertThatThrownBy(() -> builder(name).durable(true).schedule(scheduler))
                    .isInstanceOf(RuntimeException.class).hasMessageContaining("owned");
            assertThat(scheduler.getJobDetail(original.getKey()).getJobDataMap()).isEqualTo(data);
        }
    }

    @Test void triggerConflictDoesNotMutateEitherJob() throws Exception {
        builder("first").triggerName("shared").durable(true).jobData("payload", "original").schedule(scheduler());
        builder("second").durable(true).schedule(scheduler);
        assertThatThrownBy(() -> builder("second").triggerName("shared").jobData("payload", "wrong")
                .schedule(scheduler)).isInstanceOf(RuntimeException.class);
        assertThat(scheduler.getTrigger(TriggerKey.triggerKey("shared")).getJobKey()).isEqualTo(JobKey.jobKey("first"));
        assertThat(scheduler.getJobDetail(JobKey.jobKey("first")).getJobDataMap().getString("payload")).isEqualTo("original");
        assertThat(scheduler.getJobDetail(JobKey.jobKey("second")).getJobDataMap()).doesNotContainKey("payload");
    }

    @Test void executeNowCreatesOnlyAndExistingExecutionMustBeExplicit() throws Exception {
        builder("once").executeNow(scheduler());
        assertThatThrownBy(() -> builder("once").jobData("ignored", "data").executeNow(scheduler))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("triggerJob");
    }

}
