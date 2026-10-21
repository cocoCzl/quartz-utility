package io.github.cococzl.coquartz.service;

import io.github.cococzl.coquartz.core.CoQuartzConstants;
import io.github.cococzl.coquartz.dto.TaskInfo;
import io.github.cococzl.coquartz.dto.TaskSource;
import org.junit.jupiter.api.Test;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskQueryServiceTest {

    @Test
    void exposesTaskOwnershipWithoutExposingJobDataMap() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.getTriggersOfJob(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        when(scheduler.getJobDetail(JobKey.jobKey("declared", "DEFAULT"))).thenReturn(job("declared")
                .usingJobData(CoQuartzConstants.CODE_OWNED, "true").build());
        when(scheduler.getJobDetail(JobKey.jobKey("dynamic", "DEFAULT"))).thenReturn(job("dynamic")
                .usingJobData(CoQuartzConstants.OWNER, CoQuartzConstants.OWNER_VALUE)
                .usingJobData(CoQuartzConstants.TASK_SOURCE, CoQuartzConstants.SOURCE_DYNAMIC).build());
        when(scheduler.getJobDetail(JobKey.jobKey("external", "DEFAULT"))).thenReturn(job("external").build());
        TaskQueryService service = new TaskQueryService(scheduler);

        assertThat(service.getJobDetail("declared", "DEFAULT").getSource()).isEqualTo(TaskSource.DECLARATIVE);
        assertThat(service.getJobDetail("dynamic", "DEFAULT").getSource()).isEqualTo(TaskSource.DYNAMIC);
        assertThat(service.getJobDetail("external", "DEFAULT").getSource()).isEqualTo(TaskSource.EXTERNAL);
        assertThat(TaskInfo.class.getMethods()).noneMatch(method -> method.getName().equals("getJobData"));
    }

    @Test
    void mainScheduleWinsOverEarlierRetryAndActualDelegateIsShown() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        var key = JobKey.jobKey("declared");
        var detail = org.quartz.JobBuilder.newJob(io.github.cococzl.coquartz.core.NonConcurrentJobWrapper.class)
                .withIdentity(key).usingJobData(CoQuartzConstants.DELEGATE_JOB_CLASS, TaskAdminServiceTest.NoopJob.class.getName()).build();
        var retry = org.quartz.TriggerBuilder.newTrigger().withIdentity("retry", CoQuartzConstants.RETRY_TRIGGER_GROUP)
                .forJob(key).build();
        var main = org.quartz.TriggerBuilder.newTrigger().withIdentity("TRIGGER_declared")
                .withSchedule(org.quartz.CronScheduleBuilder.cronSchedule("0 0 9 * * ?")).forJob(key).build();
        when(scheduler.getJobDetail(key)).thenReturn(detail);
        org.mockito.Mockito.doReturn(java.util.List.of(retry, main)).when(scheduler).getTriggersOfJob(key);
        when(scheduler.getTriggerState(org.mockito.ArgumentMatchers.any())).thenReturn(org.quartz.Trigger.TriggerState.NORMAL);
        var info = new TaskQueryService(scheduler).getJobDetail("declared", "DEFAULT");
        assertThat(info.getTriggerType()).isEqualTo("CRON");
        assertThat(info.getJobClassName()).isEqualTo(TaskAdminServiceTest.NoopJob.class.getName());
    }

    private org.quartz.JobBuilder job(String name) {
        return JobBuilder.newJob(TaskAdminServiceTest.NoopJob.class).withIdentity(name, "DEFAULT");
    }
}
