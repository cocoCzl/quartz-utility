package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.annotation.QuartzTask;
import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.enums.MisfirePolicy;
import io.github.cococzl.coquartz.exception.CoQuartzConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Date;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuartzJobReconciliationTest {

    private Scheduler scheduler;

    @AfterEach
    void tearDown() throws Exception {
        if (scheduler != null) {
            scheduler.shutdown(true);
        }
    }

    @Test
    void repeatedReconciliationLeavesTriggerUnchanged() throws Exception {
        scheduler = newScheduler();
        try (GenericApplicationContext context = context("versionedTaskBean", InitialTaskBean.class)) {
            QuartzJobAnnotationProcessor processor = processor(context);
            processor.registerJobs();
            TriggerKey triggerKey = TriggerKey.triggerKey("TRIGGER_reconcileTask", "DEFAULT");
            Trigger original = scheduler.getTrigger(triggerKey);
            Date nextFireTime = original.getNextFireTime();
            Date startTime = original.getStartTime();
            String jobFingerprint = scheduler.getJobDetail(JobKey.jobKey("reconcileTask", "DEFAULT"))
                    .getJobDataMap().getString(CoQuartzConstants.JOB_FINGERPRINT);
            String scheduleFingerprint = original.getJobDataMap()
                    .getString(CoQuartzConstants.SCHEDULE_FINGERPRINT);

            processor.registerJobs();

            Trigger unchanged = scheduler.getTrigger(triggerKey);
            assertThat(scheduler.getJobKeys(org.quartz.impl.matchers.GroupMatcher.anyJobGroup()))
                    .containsExactly(JobKey.jobKey("reconcileTask", "DEFAULT"));
            assertThat(scheduler.getTriggersOfJob(JobKey.jobKey("reconcileTask", "DEFAULT")))
                    .hasSize(1);
            assertThat(unchanged.getNextFireTime()).isEqualTo(nextFireTime);
            assertThat(unchanged.getStartTime()).isEqualTo(startTime);
            assertThat(scheduler.getJobDetail(JobKey.jobKey("reconcileTask", "DEFAULT"))
                    .getJobDataMap().getString(CoQuartzConstants.JOB_FINGERPRINT))
                    .isEqualTo(jobFingerprint);
            assertThat(unchanged.getJobDataMap().getString(CoQuartzConstants.SCHEDULE_FINGERPRINT))
                    .isEqualTo(scheduleFingerprint);
        }
    }

    @Test
    void changedScheduleUpdatesCronExpression() throws Exception {
        scheduler = newScheduler();
        try (GenericApplicationContext first = context("versionedTaskBean", InitialTaskBean.class)) {
            processor(first).registerJobs();
        }
        try (GenericApplicationContext second = context("versionedTaskBean", ChangedTaskBean.class)) {
            processor(second).registerJobs();
        }

        CronTrigger trigger = (CronTrigger) scheduler.getTrigger(
                TriggerKey.triggerKey("TRIGGER_reconcileTask", "DEFAULT"));
        assertThat(trigger.getCronExpression()).isEqualTo("0 0 10 * * ?");
        assertThat(trigger.getTimeZone().getID()).isEqualTo("Asia/Shanghai");
        assertThat(trigger.getMisfireInstruction())
                .isEqualTo(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
    }

    @Test
    void changedExecutionPolicyUpdatesJobWithoutReschedulingTrigger() throws Exception {
        scheduler = newScheduler();
        TriggerKey triggerKey = TriggerKey.triggerKey("TRIGGER_reconcileTask", "DEFAULT");
        try (GenericApplicationContext first = context("versionedTaskBean", InitialTaskBean.class)) {
            processor(first).registerJobs();
        }
        Date nextFireTime = scheduler.getTrigger(triggerKey).getNextFireTime();
        Date startTime = scheduler.getTrigger(triggerKey).getStartTime();
        String scheduleFingerprint = scheduler.getTrigger(triggerKey).getJobDataMap()
                .getString(CoQuartzConstants.SCHEDULE_FINGERPRINT);

        try (GenericApplicationContext second = context("versionedTaskBean", PolicyChangedTaskBean.class)) {
            processor(second).registerJobs();
        }

        JobDetail updated = scheduler.getJobDetail(JobKey.jobKey("reconcileTask", "DEFAULT"));
        assertThat(updated.getJobDataMap().getInt(CoQuartzConstants.RETRY_TIMES)).isEqualTo(3);
        assertThat(scheduler.getTrigger(triggerKey).getNextFireTime()).isEqualTo(nextFireTime);
        assertThat(scheduler.getTrigger(triggerKey).getStartTime()).isEqualTo(startTime);
        assertThat(scheduler.getTrigger(triggerKey).getJobDataMap()
                .getString(CoQuartzConstants.SCHEDULE_FINGERPRINT)).isEqualTo(scheduleFingerprint);
    }

    @Test
    void changedSchedulePreservesPausedState() throws Exception {
        scheduler = newScheduler();
        try (GenericApplicationContext first = context("versionedTaskBean", InitialTaskBean.class)) {
            processor(first).registerJobs();
        }
        JobKey jobKey = JobKey.jobKey("reconcileTask", "DEFAULT");
        TriggerKey triggerKey = TriggerKey.triggerKey("TRIGGER_reconcileTask", "DEFAULT");
        scheduler.pauseJob(jobKey);
        assertThat(scheduler.getTriggerState(triggerKey)).isEqualTo(Trigger.TriggerState.PAUSED);

        try (GenericApplicationContext second = context("versionedTaskBean", ChangedTaskBean.class)) {
            processor(second).registerJobs();
        }

        assertThat(scheduler.getTriggerState(triggerKey)).isEqualTo(Trigger.TriggerState.PAUSED);
        assertThat(((CronTrigger) scheduler.getTrigger(triggerKey)).getCronExpression())
                .isEqualTo("0 0 10 * * ?");
    }

    @Test
    void deletedCodeOwnedTaskIsRestored() throws Exception {
        scheduler = newScheduler();
        try (GenericApplicationContext first = context("versionedTaskBean", InitialTaskBean.class)) {
            processor(first).registerJobs();
        }
        JobKey jobKey = JobKey.jobKey("reconcileTask", "DEFAULT");
        scheduler.deleteJob(jobKey);
        try (GenericApplicationContext second = context("versionedTaskBean", InitialTaskBean.class)) {
            processor(second).registerJobs();
        }

        assertThat(scheduler.checkExists(jobKey)).isTrue();
        assertThat(scheduler.getTriggersOfJob(jobKey)).hasSize(1);
    }

    @Test
    void externalTaskIdentityIsNeverOverwritten() throws Exception {
        scheduler = newScheduler();
        JobKey jobKey = JobKey.jobKey("reconcileTask", "DEFAULT");
        JobDetail external = JobBuilder.newJob(ExternalJob.class).withIdentity(jobKey).build();
        Trigger externalTrigger = TriggerBuilder.newTrigger()
                .withIdentity("externalTrigger", "DEFAULT")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 8 * * ?"))
                .forJob(jobKey)
                .build();
        scheduler.scheduleJob(external, externalTrigger);

        try (GenericApplicationContext context = context("initialTaskBean", InitialTaskBean.class)) {
            assertThatThrownBy(() -> processor(context).registerJobs())
                    .isInstanceOf(CoQuartzConfigurationException.class)
                    .hasMessageContaining("reconcileTask")
                    .hasMessageContaining("refusing to overwrite");
        }

        assertThat(scheduler.getJobDetail(jobKey).getJobClass()).isEqualTo(ExternalJob.class);
        assertThat(scheduler.getTriggersOfJob(jobKey)).extracting(t -> t.getKey().getName())
                .containsExactly("externalTrigger");
    }

    @Test
    void ownershipConflictPreventsAnyPartialReconciliation() throws Exception {
        scheduler = newScheduler();
        JobKey conflictKey = JobKey.jobKey("conflictTask", "DEFAULT");
        JobDetail external = JobBuilder.newJob(ExternalJob.class).withIdentity(conflictKey).build();
        Trigger externalTrigger = TriggerBuilder.newTrigger()
                .withIdentity("externalConflictTrigger", "DEFAULT")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 8 * * ?"))
                .forJob(conflictKey)
                .build();
        scheduler.scheduleJob(external, externalTrigger);

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("newTaskBean", NewTaskBean.class);
            context.registerBean("conflictingTaskBean", ConflictingTaskBean.class);
            context.refresh();

            assertThatThrownBy(() -> processor(context).registerJobs())
                    .isInstanceOf(CoQuartzConfigurationException.class)
                    .hasMessageContaining("conflictTask");
        }

        assertThat(scheduler.checkExists(JobKey.jobKey("newTask", "DEFAULT"))).isFalse();
        assertThat(scheduler.getJobDetail(conflictKey).getJobClass()).isEqualTo(ExternalJob.class);
    }

    @Test
    void ownedTaskWithoutFingerprintsIsUpgradedInPlace() throws Exception {
        scheduler = newScheduler();
        JobKey jobKey = JobKey.jobKey("reconcileTask", "DEFAULT");
        org.quartz.JobDataMap dataMap = new org.quartz.JobDataMap();
        dataMap.put(CoQuartzConstants.OWNER, CoQuartzConstants.OWNER_VALUE);
        dataMap.put(CoQuartzConstants.CODE_OWNED, "true");
        JobDetail existing = JobBuilder.newJob(MethodInvokingJob.class)
                .withIdentity(jobKey)
                .usingJobData(dataMap)
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("TRIGGER_reconcileTask", "DEFAULT")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 8 * * ?"))
                .forJob(jobKey)
                .build();
        scheduler.scheduleJob(existing, trigger);
        scheduler.pauseJob(jobKey);

        try (GenericApplicationContext context = context("versionedTaskBean", InitialTaskBean.class)) {
            QuartzJobAnnotationProcessor processor = processor(context);
            processor.registerJobs();
            TriggerKey triggerKey = TriggerKey.triggerKey("TRIGGER_reconcileTask", "DEFAULT");
            Date startTime = scheduler.getTrigger(triggerKey).getStartTime();
            Date nextFireTime = scheduler.getTrigger(triggerKey).getNextFireTime();

            processor.registerJobs();

            assertThat(scheduler.getTrigger(triggerKey).getStartTime()).isEqualTo(startTime);
            assertThat(scheduler.getTrigger(triggerKey).getNextFireTime()).isEqualTo(nextFireTime);
        }

        JobDetail upgraded = scheduler.getJobDetail(jobKey);
        assertThat(upgraded.getJobDataMap().getString(CoQuartzConstants.JOB_FINGERPRINT)).isNotBlank();
        assertThat(upgraded.getJobDataMap().getString(CoQuartzConstants.DEFINITION_VERSION)).isNotBlank();
        Trigger upgradedTrigger = scheduler.getTrigger(TriggerKey.triggerKey(
                "TRIGGER_reconcileTask", "DEFAULT"));
        assertThat(upgradedTrigger.getJobDataMap().getString(CoQuartzConstants.SCHEDULE_FINGERPRINT))
                .isNotBlank();
        assertThat(((CronTrigger) upgradedTrigger).getCronExpression()).isEqualTo("0 0 9 * * ?");
        assertThat(scheduler.getTriggerState(upgradedTrigger.getKey())).isEqualTo(Trigger.TriggerState.PAUSED);
    }

    @Test
    void disabledDefinitionRemovesExistingCodeOwnedTask() throws Exception {
        scheduler = newScheduler();
        JobKey jobKey = JobKey.jobKey("reconcileTask", "DEFAULT");
        try (GenericApplicationContext first = context("versionedTaskBean", InitialTaskBean.class)) {
            processor(first).registerJobs();
        }

        try (GenericApplicationContext second = context("versionedTaskBean", DisabledTaskBean.class)) {
            processor(second).registerJobs();
        }

        assertThat(scheduler.checkExists(jobKey)).isFalse();
    }

    @Test
    void disabledDefinitionNeverDeletesExternalTask() throws Exception {
        scheduler = newScheduler();
        JobKey jobKey = JobKey.jobKey("reconcileTask", "DEFAULT");
        JobDetail external = JobBuilder.newJob(ExternalJob.class).withIdentity(jobKey).build();
        Trigger externalTrigger = TriggerBuilder.newTrigger()
                .withIdentity("externalTrigger", "DEFAULT")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 8 * * ?"))
                .forJob(jobKey)
                .build();
        scheduler.scheduleJob(external, externalTrigger);

        try (GenericApplicationContext context = context("versionedTaskBean", DisabledTaskBean.class)) {
            assertThatThrownBy(() -> processor(context).registerJobs())
                    .isInstanceOf(CoQuartzConfigurationException.class)
                    .hasMessageContaining("refusing to overwrite");
        }

        assertThat(scheduler.getJobDetail(jobKey).getJobClass()).isEqualTo(ExternalJob.class);
    }

    @Test
    void unknownMetadataVersionFailsClosed() throws Exception {
        scheduler = newScheduler();
        JobKey jobKey = JobKey.jobKey("reconcileTask", "DEFAULT");
        try (GenericApplicationContext first = context("versionedTaskBean", InitialTaskBean.class)) {
            processor(first).registerJobs();
        }
        JobDetail current = scheduler.getJobDetail(jobKey);
        org.quartz.JobDataMap changedData = new org.quartz.JobDataMap(current.getJobDataMap());
        changedData.put(CoQuartzConstants.METADATA_VERSION, "99");
        scheduler.addJob(current.getJobBuilder().usingJobData(changedData).build(), true, true);

        try (GenericApplicationContext second = context("versionedTaskBean", InitialTaskBean.class)) {
            assertThatThrownBy(() -> processor(second).registerJobs())
                    .isInstanceOf(CoQuartzConfigurationException.class)
                    .hasMessageContaining("metadata version 99");
        }
    }

    @Test
    void actualTriggerDriftIsCorrectedEvenWhenStoredFingerprintIsUnchanged() throws Exception {
        scheduler = newScheduler();
        TriggerKey triggerKey = TriggerKey.triggerKey("TRIGGER_reconcileTask", "DEFAULT");
        try (GenericApplicationContext first = context("versionedTaskBean", InitialTaskBean.class)) {
            processor(first).registerJobs();
        }
        Trigger current = scheduler.getTrigger(triggerKey);
        Trigger drifted = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(current.getJobKey())
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 6 * * ?"))
                .usingJobData(current.getJobDataMap())
                .build();
        scheduler.rescheduleJob(triggerKey, drifted);

        try (GenericApplicationContext second = context("versionedTaskBean", InitialTaskBean.class)) {
            processor(second).registerJobs();
        }

        assertThat(((CronTrigger) scheduler.getTrigger(triggerKey)).getCronExpression())
                .isEqualTo("0 0 9 * * ?");
    }

    @Test
    void pendingPauseIsRestoredWhenCanonicalTriggerIsMissing() throws Exception {
        scheduler = newScheduler();
        JobKey jobKey = JobKey.jobKey("reconcileTask", "DEFAULT");
        org.quartz.JobDataMap dataMap = new org.quartz.JobDataMap();
        dataMap.put(CoQuartzConstants.OWNER, CoQuartzConstants.OWNER_VALUE);
        dataMap.put(CoQuartzConstants.CODE_OWNED, "true");
        dataMap.put(CoQuartzConstants.METADATA_VERSION, CoQuartzConstants.METADATA_VERSION_VALUE);
        dataMap.put(CoQuartzConstants.PAUSE_RESTORE_PENDING, "true");
        JobDetail pending = JobBuilder.newJob(MethodInvokingJob.class)
                .withIdentity(jobKey)
                .storeDurably(true)
                .usingJobData(dataMap)
                .build();
        scheduler.addJob(pending, false);

        try (GenericApplicationContext context = context("versionedTaskBean", InitialTaskBean.class)) {
            processor(context).registerJobs();
        }

        TriggerKey triggerKey = TriggerKey.triggerKey("TRIGGER_reconcileTask", "DEFAULT");
        assertThat(scheduler.getTriggerState(triggerKey)).isEqualTo(Trigger.TriggerState.PAUSED);
        assertThat(scheduler.getJobDetail(jobKey).getJobDataMap()
                .getString(CoQuartzConstants.PAUSE_RESTORE_PENDING)).isNull();
    }

    private Scheduler newScheduler() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "reconcile-" + System.nanoTime());
        properties.setProperty("org.quartz.scheduler.instanceId", "NON_CLUSTERED");
        properties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        properties.setProperty("org.quartz.threadPool.threadCount", "1");
        properties.setProperty("org.quartz.threadPool.makeThreadsDaemons", "true");
        properties.setProperty("org.quartz.scheduler.makeSchedulerThreadDaemon", "true");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        return new StdSchedulerFactory(properties).getScheduler();
    }

    private GenericApplicationContext context(String beanName, Class<?> beanClass) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(beanName, beanClass);
        context.refresh();
        return context;
    }

    private QuartzJobAnnotationProcessor processor(GenericApplicationContext context) {
        return new QuartzJobAnnotationProcessor(
                new CoQuartzScheduler(scheduler),
                context,
                new CoQuartzProperties(),
                new MethodTaskRegistry(context));
    }

    static class InitialTaskBean {
        @QuartzTask(name = "reconcileTask", cron = "0 0 9 * * ?", timeZone = "UTC")
        public void run() {
        }
    }

    static class ChangedTaskBean {
        @QuartzTask(name = "reconcileTask", cron = "0 0 10 * * ?", timeZone = "Asia/Shanghai",
                misfirePolicy = MisfirePolicy.FIRE_NOW)
        public void run() {
        }
    }

    static class PolicyChangedTaskBean {
        @QuartzTask(name = "reconcileTask", cron = "0 0 9 * * ?", timeZone = "UTC", retryTimes = 3)
        public void run() {
        }
    }

    static class NewTaskBean {
        @QuartzTask(name = "newTask", cron = "0 0 7 * * ?", timeZone = "UTC")
        public void run() {
        }
    }

    static class ConflictingTaskBean {
        @QuartzTask(name = "conflictTask", cron = "0 0 9 * * ?", timeZone = "UTC")
        public void run() {
        }
    }

    static class DisabledTaskBean {
        @QuartzTask(name = "reconcileTask", enabled = false)
        private void retiredMethodCanBeInvalidBecauseOnlyIdentityIsNeeded() {
        }
    }

    public static class ExternalJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
        }
    }
}
