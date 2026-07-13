package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.enums.MisfirePolicy;
import io.github.cococzl.coquartz.exception.CoQuartzSchedulingException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.*;

import java.util.Date;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuartzTaskBuilderTest {

    public static class SampleJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
        }
    }

    @Test
    void buildJobDetail_setsEnhancedFlag() {
        JobDetail jobDetail = extractJobDetail(
                QuartzTaskBuilder.newBuilder()
                        .jobClass(SampleJob.class)
                        .jobName("testJob")
                        .cron("0 0 * * * ?")
        );

        JobDataMap dataMap = jobDetail.getJobDataMap();
        assertThat(dataMap.get(CoQuartzConstants.ENHANCED)).isEqualTo(true);
    }

    @Test
    void buildJobDetail_setsRetryConfig() {
        JobDetail jobDetail = extractJobDetail(
                QuartzTaskBuilder.newBuilder()
                        .jobClass(SampleJob.class)
                        .jobName("testJob")
                        .cron("0 0 * * * ?")
                        .retryTimes(3)
                        .retryInterval(2000)
                        .exponentialBackoff(true)
                        .backoffMultiplier(2.0)
        );

        JobDataMap dataMap = jobDetail.getJobDataMap();
        assertThat(dataMap.get(CoQuartzConstants.RETRY_TIMES)).isEqualTo(3);
        assertThat(dataMap.get(CoQuartzConstants.RETRY_INTERVAL)).isEqualTo(2000L);
        assertThat(dataMap.get(CoQuartzConstants.EXPONENTIAL_BACKOFF)).isEqualTo(true);
        assertThat(dataMap.get(CoQuartzConstants.BACKOFF_MULTIPLIER)).isEqualTo(2.0);
    }

    @Test
    void buildJobDetail_setsTimeout() {
        JobDetail jobDetail = extractJobDetail(
                QuartzTaskBuilder.newBuilder()
                        .jobClass(SampleJob.class)
                        .jobName("testJob")
                        .cron("0 0 * * * ?")
                        .timeout(5000)
        );

        assertThat(jobDetail.getJobDataMap().get(CoQuartzConstants.TIMEOUT)).isEqualTo(5000L);
    }

    @Test
    void buildJobDetail_setsConcurrent() {
        JobDetail jobDetail = extractJobDetail(
                QuartzTaskBuilder.newBuilder()
                        .jobClass(SampleJob.class)
                        .jobName("testJob")
                        .cron("0 0 * * * ?")
                        .concurrent(true)
        );

        assertThat(jobDetail.getJobDataMap().get(CoQuartzConstants.CONCURRENT)).isEqualTo(true);
        assertThat(jobDetail.getJobClass()).isEqualTo(SampleJob.class);
    }

    @Test
    void buildJobDetail_usesQuartzVisibleProxyForNonConcurrentJob() {
        JobDetail jobDetail = extractJobDetail(
                QuartzTaskBuilder.newBuilder()
                        .jobClass(SampleJob.class)
                        .jobName("testJob")
                        .cron("0 0 * * * ?")
        );

        assertThat(jobDetail.getJobClass()).isEqualTo(NonConcurrentJobWrapper.class);
        assertThat(jobDetail.getJobDataMap().getString(CoQuartzConstants.DELEGATE_JOB_CLASS))
                .isEqualTo(SampleJob.class.getName());
    }

    @Test
    void buildJobDetail_setsMisfirePolicy() {
        JobDetail jobDetail = extractJobDetail(
                QuartzTaskBuilder.newBuilder()
                        .jobClass(SampleJob.class)
                        .jobName("testJob")
                        .cron("0 0 * * * ?")
                        .misfirePolicy(MisfirePolicy.FIRE_NOW)
        );

        assertThat(jobDetail.getJobDataMap().get(CoQuartzConstants.MISFIRE_POLICY))
                .isEqualTo(MisfirePolicy.FIRE_NOW.name());
    }

    @Test
    void buildJobDetail_withJobData() {
        JobDetail jobDetail = extractJobDetail(
                QuartzTaskBuilder.newBuilder()
                        .jobClass(SampleJob.class)
                        .jobName("testJob")
                        .cron("0 0 * * * ?")
                        .jobData("customKey", "customValue")
        );

        assertThat(jobDetail.getJobDataMap().get("customKey")).isEqualTo("customValue");
    }

    @Test
    void buildJobDetail_defaultName() {
        JobDetail jobDetail = extractJobDetail(
                QuartzTaskBuilder.newBuilder()
                        .jobClass(SampleJob.class)
                        .cron("0 0 * * * ?")
        );

        assertThat(jobDetail.getKey().getName()).isEqualTo("SampleJob");
    }

    @Test
    void buildJobDetail_withDescription() {
        JobDetail jobDetail = extractJobDetail(
                QuartzTaskBuilder.newBuilder()
                        .jobClass(SampleJob.class)
                        .jobName("testJob")
                        .cron("0 0 * * * ?")
                        .description("Test description")
        );

        assertThat(jobDetail.getDescription()).isEqualTo("Test description");
    }

    @Test
    void buildJobDetail_nullJobClass_throwsException() {
        assertThatThrownBy(() ->
                QuartzTaskBuilder.newBuilder()
                        .jobName("testJob")
                        .cron("0 0 * * * ?")
                        .schedule(mock(Scheduler.class))
        ).isInstanceOf(CoQuartzSchedulingException.class)
                .hasMessageContaining("jobClass must be specified");
    }

    @Test
    void buildTrigger_cronTrigger() {
        QuartzTaskBuilder builder = QuartzTaskBuilder.newBuilder()
                .jobClass(SampleJob.class)
                .jobName("testJob")
                .cron("0 0 * * * ?");

        JobDetail jobDetail = extractJobDetail(builder);
        assertThat(jobDetail).isNotNull();
    }

    @Test
    void buildTrigger_intervalTrigger() {
        QuartzTaskBuilder builder = QuartzTaskBuilder.newBuilder()
                .jobClass(SampleJob.class)
                .jobName("testJob")
                .intervalInSeconds(60);

        JobDetail jobDetail = extractJobDetail(builder);
        assertThat(jobDetail).isNotNull();
    }

    @Test
    void buildTrigger_noCronOrInterval_throwsException() {
        assertThatThrownBy(() ->
                QuartzTaskBuilder.newBuilder()
                        .jobClass(SampleJob.class)
                        .jobName("testJob")
                        .schedule(mock(Scheduler.class))
        ).isInstanceOf(CoQuartzSchedulingException.class);
    }

    @Test
    void schedule_existingJob_reschedules() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.checkExists(any(JobKey.class))).thenReturn(true);
        when(scheduler.rescheduleJob(any(TriggerKey.class), any(Trigger.class))).thenReturn(new Date());

        QuartzTaskBuilder.newBuilder()
                .jobClass(SampleJob.class)
                .jobName("testJob")
                .cron("0 0 * * * ?")
                .schedule(scheduler);

        verify(scheduler).addJob(any(JobDetail.class), eq(true));
        verify(scheduler).rescheduleJob(any(TriggerKey.class), any(Trigger.class));
    }

    @Test
    void schedule_newJob_schedules() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.checkExists(any(JobKey.class))).thenReturn(false);
        when(scheduler.scheduleJob(any(JobDetail.class), any(Trigger.class))).thenReturn(new Date());

        QuartzTaskBuilder.newBuilder()
                .jobClass(SampleJob.class)
                .jobName("testJob")
                .cron("0 0 * * * ?")
                .schedule(scheduler);

        verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void executeNow_schedulesImmediately() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.checkExists(any(JobKey.class))).thenReturn(false);
        when(scheduler.scheduleJob(any(Trigger.class))).thenReturn(new Date());

        QuartzTaskBuilder.newBuilder()
                .jobClass(SampleJob.class)
                .jobName("testJob")
                .executeNow(scheduler);

        verify(scheduler).addJob(any(JobDetail.class), eq(true));
        verify(scheduler).scheduleJob(any(Trigger.class));
    }

    @Test
    void buildJobDetail_defaultValues() {
        JobDetail jobDetail = extractJobDetail(
                QuartzTaskBuilder.newBuilder()
                        .jobClass(SampleJob.class)
                        .jobName("testJob")
                        .cron("0 0 * * * ?")
        );

        JobDataMap dataMap = jobDetail.getJobDataMap();
        assertThat(dataMap.get(CoQuartzConstants.RETRY_TIMES)).isEqualTo(0);
        assertThat(dataMap.get(CoQuartzConstants.RETRY_INTERVAL)).isEqualTo(1000L);
        assertThat(dataMap.get(CoQuartzConstants.EXPONENTIAL_BACKOFF)).isEqualTo(false);
        assertThat(dataMap.get(CoQuartzConstants.BACKOFF_MULTIPLIER)).isEqualTo(1.5);
        assertThat(dataMap.get(CoQuartzConstants.TIMEOUT)).isEqualTo(0L);
        assertThat(dataMap.get(CoQuartzConstants.CONCURRENT)).isEqualTo(false);
        assertThat(dataMap.get(CoQuartzConstants.MISFIRE_POLICY)).isEqualTo(MisfirePolicy.SMART_POLICY.name());
    }

    private JobDetail extractJobDetail(QuartzTaskBuilder builder) {
        try {
            Scheduler scheduler = mock(Scheduler.class);
            when(scheduler.checkExists(any(JobKey.class))).thenReturn(false);
            when(scheduler.scheduleJob(any(JobDetail.class), any(Trigger.class))).thenReturn(new Date());
            builder.schedule(scheduler);
            ArgumentCaptor<JobDetail> captor = ArgumentCaptor.forClass(JobDetail.class);
            verify(scheduler).scheduleJob(captor.capture(), any(Trigger.class));
            return captor.getValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
