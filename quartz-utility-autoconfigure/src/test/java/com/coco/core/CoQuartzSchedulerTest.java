package com.coco.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

class CoQuartzSchedulerTest {

    private Scheduler scheduler;

    @AfterEach
    void shutdownScheduler() throws Exception {
        if (scheduler != null) {
            scheduler.shutdown(true);
        }
    }

    @Test
    void defaultKeysAreDerivedFromJobClass() throws Exception {
        scheduler = StdSchedulerFactory.getDefaultScheduler();
        CoQuartzScheduler coScheduler = new CoQuartzScheduler(scheduler);

        QuartzComponent component = new QuartzComponent.Builder()
                .setTimeEnum(com.coco.enums.TimeEnum.SECONDS)
                .setTimeInterval(30)
                .build();

        coScheduler.scheduleSimpleIntervalJob(FirstJob.class, component);
        coScheduler.scheduleSimpleIntervalJob(SecondJob.class, component);

        assertThat(scheduler.checkExists(SchedulerCore.getDefaultJobKey(FirstJob.class))).isTrue();
        assertThat(scheduler.checkExists(SchedulerCore.getDefaultJobKey(SecondJob.class))).isTrue();
    }

    @Test
    void reschedulingExistingJobUpdatesDataAndInterval() throws Exception {
        scheduler = StdSchedulerFactory.getDefaultScheduler();
        CoQuartzScheduler coScheduler = new CoQuartzScheduler(scheduler);
        JobKey jobKey = SchedulerCore.getJobKey("rescheduledJob", "test");
        org.quartz.TriggerKey triggerKey = SchedulerCore.getTriggerKey("rescheduledJob", "test");

        QuartzComponent firstComponent = new QuartzComponent.Builder()
                .setTimeEnum(com.coco.enums.TimeEnum.SECONDS)
                .setTimeInterval(30)
                .build();
        QuartzComponent secondComponent = new QuartzComponent.Builder()
                .setTimeEnum(com.coco.enums.TimeEnum.SECONDS)
                .setTimeInterval(45)
                .build();

        JobDataMap firstData = new JobDataMap();
        firstData.put("version", "one");
        coScheduler.scheduleSimpleIntervalJob(FirstJob.class, jobKey, triggerKey, firstData, null, firstComponent);

        JobDataMap secondData = new JobDataMap();
        secondData.put("version", "two");
        coScheduler.scheduleSimpleIntervalJob(FirstJob.class, jobKey, triggerKey, secondData, null, secondComponent);

        assertThat(scheduler.getJobDetail(jobKey).getJobDataMap().getString("version")).isEqualTo("two");
        Trigger trigger = scheduler.getTrigger(triggerKey);
        assertThat(trigger).isInstanceOf(SimpleTrigger.class);
        assertThat(((SimpleTrigger) trigger).getRepeatInterval()).isEqualTo(45_000L);
    }

    @Test
    void builderPreservesIntervalsThatAreNotFullMinutes() throws Exception {
        scheduler = StdSchedulerFactory.getDefaultScheduler();
        CoQuartzScheduler coScheduler = new CoQuartzScheduler(scheduler);

        QuartzTaskBuilder.newBuilder()
                .jobClass(FirstJob.class)
                .jobName("ninetySecondJob")
                .jobGroup("test")
                .intervalInSeconds(90)
                .schedule(coScheduler);

        SimpleTrigger trigger = (SimpleTrigger) scheduler.getTrigger(
                SchedulerCore.getTriggerKey("ninetySecondJob", "DEFAULT"));

        assertThat(trigger.getRepeatInterval()).isEqualTo(90_000L);
    }

    public static class FirstJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
        }
    }

    public static class SecondJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
        }
    }
}
