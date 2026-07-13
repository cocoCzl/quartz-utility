package io.github.cococzl.coquartz.service;

import io.github.cococzl.coquartz.core.CoQuartzConstants;
import io.github.cococzl.coquartz.core.CoQuartzScheduler;
import io.github.cococzl.coquartz.exception.CodeOwnedTaskModificationException;
import org.junit.jupiter.api.Test;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskAdminServiceTest {

    @Test
    void codeOwnedTaskCanBePausedResumedAndTriggeredButNotDeletedOrRescheduled() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        JobKey jobKey = JobKey.jobKey("declared", "DEFAULT");
        JobDetail jobDetail = JobBuilder.newJob(NoopJob.class).withIdentity(jobKey)
                .usingJobData(CoQuartzConstants.CODE_OWNED, "true").build();
        when(scheduler.getJobDetail(jobKey)).thenReturn(jobDetail);
        TaskAdminService service = new TaskAdminService(scheduler);

        service.pauseJob("declared", "DEFAULT");
        service.resumeJob("declared", "DEFAULT");
        service.triggerNow("declared", "DEFAULT");

        assertThatThrownBy(() -> service.deleteJob("declared", "DEFAULT"))
                .isInstanceOf(CodeOwnedTaskModificationException.class)
                .hasMessageContaining("owned by application code")
                .hasMessageContaining("modify its code definition");
        assertThatThrownBy(() -> service.rescheduleInterval("declared", "DEFAULT",
                "declared-trigger", "DEFAULT", 60))
                .isInstanceOf(CodeOwnedTaskModificationException.class);

        verify(scheduler).pauseJob(jobKey);
        verify(scheduler).resumeJob(jobKey);
        verify(scheduler).triggerJob(jobKey);
        verify(scheduler, never()).deleteJob(jobKey);
        verify(scheduler, never()).rescheduleJob(any(), any(Trigger.class));
    }

    @Test
    void dynamicTaskKeepsDeleteAndRescheduleLifecycle() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        JobKey jobKey = JobKey.jobKey("dynamic", "DEFAULT");
        JobDetail jobDetail = JobBuilder.newJob(NoopJob.class).withIdentity(jobKey)
                .usingJobData(CoQuartzConstants.CODE_OWNED, "false").build();
        when(scheduler.getJobDetail(jobKey)).thenReturn(jobDetail);
        when(scheduler.deleteJob(jobKey)).thenReturn(true);
        TaskAdminService service = new TaskAdminService(scheduler);

        service.deleteJob("dynamic", "DEFAULT");
        service.rescheduleInterval("dynamic", "DEFAULT", "dynamic-trigger", "DEFAULT", 60);

        verify(scheduler).deleteJob(jobKey);
        verify(scheduler).rescheduleJob(eq(org.quartz.TriggerKey.triggerKey("dynamic-trigger", "DEFAULT")),
                any(SimpleTrigger.class));
    }

    @Test
    void schedulerFacadeAlsoProtectsCodeOwnedDefinitions() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        JobKey jobKey = JobKey.jobKey("declared", "DEFAULT");
        when(scheduler.getJobDetail(jobKey)).thenReturn(JobBuilder.newJob(NoopJob.class).withIdentity(jobKey)
                .usingJobData(CoQuartzConstants.CODE_OWNED, "true").build());

        assertThatThrownBy(() -> new CoQuartzScheduler(scheduler).deleteJob("declared", "DEFAULT"))
                .isInstanceOf(CodeOwnedTaskModificationException.class);
        verify(scheduler, never()).deleteJob(jobKey);
    }

    public static class NoopJob implements org.quartz.Job {
        @Override
        public void execute(org.quartz.JobExecutionContext context) {
        }
    }
}
