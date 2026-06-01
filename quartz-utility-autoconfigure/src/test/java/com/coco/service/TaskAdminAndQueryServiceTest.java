package com.coco.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.coco.dto.TaskInfo;
import com.coco.dto.TaskScheduleRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;

class TaskAdminAndQueryServiceTest {

    private Scheduler scheduler;

    @AfterEach
    void shutdownScheduler() throws Exception {
        if (scheduler != null) {
            scheduler.shutdown(true);
        }
    }

    @Test
    void schedulesQueriesAndReschedulesTask() throws Exception {
        scheduler = StdSchedulerFactory.getDefaultScheduler();
        com.coco.core.CoQuartzScheduler coScheduler = new com.coco.core.CoQuartzScheduler(scheduler);
        TaskAdminService adminService = new TaskAdminService(coScheduler);
        TaskQueryService queryService = new TaskQueryService(coScheduler);

        TaskScheduleRequest request = new TaskScheduleRequest();
        request.setJobClass(SampleJob.class);
        request.setJobName("adminJob");
        request.setJobGroup("adminGroup");
        request.setIntervalSeconds(15);
        request.setDescription("managed job");

        adminService.schedule(request);

        assertThat(adminService.exists("adminJob", "adminGroup")).isTrue();
        TaskInfo info = queryService.getJobDetail("adminJob", "adminGroup");
        assertThat(info.getDescription()).isEqualTo("managed job");
        assertThat(info.getRepeatIntervalMs()).isEqualTo(15_000L);

        adminService.rescheduleInterval("adminJob", "adminGroup", 30);

        TaskInfo rescheduled = queryService.getJobDetail("adminJob", "adminGroup");
        assertThat(rescheduled.getRepeatIntervalMs()).isEqualTo(30_000L);
        assertThat(queryService.listJobs()).hasSize(1);
        assertThat(adminService.delete("adminJob", "adminGroup")).isTrue();
    }

    public static class SampleJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
        }
    }
}
