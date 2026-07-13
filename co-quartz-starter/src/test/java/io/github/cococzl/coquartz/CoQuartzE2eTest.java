package io.github.cococzl.coquartz;

import io.github.cococzl.coquartz.annotation.QuartzJob;
import io.github.cococzl.coquartz.annotation.QuartzTask;
import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.core.CoQuartzScheduler;
import io.github.cococzl.coquartz.core.CoQuartzConstants;
import io.github.cococzl.coquartz.core.QuartzTaskBuilder;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.event.TaskConsecutiveFailureEvent;
import io.github.cococzl.coquartz.event.TaskFailureEvent;
import io.github.cococzl.coquartz.event.TaskTimeoutEvent;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import io.github.cococzl.coquartz.service.TaskAdminService;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import io.github.cococzl.coquartz.service.TaskQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@org.springframework.context.annotation.Import({CoQuartzE2eTest.TestEventListener.class, CoQuartzE2eTest.TestService.class, CoQuartzE2eTest.MethodTaskBean.class, CoQuartzE2eTest.SimpleAnnotatedJob.class, CoQuartzE2eTest.RetryAnnotatedJob.class})
class CoQuartzE2eTest {

    @SpringBootApplication(scanBasePackages = "io.github.cococzl.coquartz")
    static class TestApplication {
    }

    @Component
    static class TestEventListener {
        final List<TaskFailureEvent> failureEvents = new CopyOnWriteArrayList<>();
        final List<TaskTimeoutEvent> timeoutEvents = new CopyOnWriteArrayList<>();
        final List<TaskConsecutiveFailureEvent> consecutiveFailureEvents = new CopyOnWriteArrayList<>();

        @EventListener
        void onFailure(TaskFailureEvent event) {
            failureEvents.add(event);
        }

        @EventListener
        void onTimeout(TaskTimeoutEvent event) {
            timeoutEvents.add(event);
        }

        @EventListener
        void onConsecutiveFailure(TaskConsecutiveFailureEvent event) {
            consecutiveFailureEvents.add(event);
        }
    }

    @Component
    static class TestService {
        public String greet() { return "hello"; }
    }

    @Component
    static class MethodTaskBean {
        final AtomicInteger count = new AtomicInteger(0);

        @QuartzTask(name = "declarativeMethodTask", intervalSeconds = 1, concurrent = true)
        public void run() {
            count.incrementAndGet();
        }
    }

    @QuartzJob(name = "simpleAnnotatedJob", cron = "0/2 * * * * ?", concurrent = true)
    @Component
    static class SimpleAnnotatedJob implements Job {
        static final AtomicInteger COUNT = new AtomicInteger(0);
        @Autowired TestService testService;

        @Override
        public void execute(JobExecutionContext ctx) throws JobExecutionException {
            if (testService == null) throw new JobExecutionException("not autowired");
            COUNT.incrementAndGet();
        }
    }

    @QuartzJob(name = "retryAnnotatedJob", cron = "0/2 * * * * ?", retryTimes = 2, retryInterval = 100, concurrent = true)
    @Component
    static class RetryAnnotatedJob implements Job {
        static final AtomicInteger COUNT = new AtomicInteger(0);
        static volatile boolean shouldFail = true;

        @Override
        public void execute(JobExecutionContext ctx) throws JobExecutionException {
            COUNT.incrementAndGet();
            if (shouldFail) throw new JobExecutionException("intentional");
        }
    }

    static class PlainJob implements Job {
        static final AtomicInteger COUNT = new AtomicInteger(0);
        @Override
        public void execute(JobExecutionContext ctx) { COUNT.incrementAndGet(); }
    }

    static class TimeoutJob implements Job {
        @Override
        public void execute(JobExecutionContext ctx) throws JobExecutionException {
            try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    static class FailingJob implements Job {
        @Override
        public void execute(JobExecutionContext ctx) throws JobExecutionException {
            throw new JobExecutionException("intentional failure");
        }
    }

    static class AlwaysFailJob implements Job {
        @Override
        public void execute(JobExecutionContext ctx) throws JobExecutionException {
            throw new JobExecutionException("always fail");
        }
    }

    @Autowired Scheduler scheduler;
    @Autowired CoQuartzScheduler coQuartzScheduler;
    @Autowired TaskAdminService taskAdminService;
    @Autowired TaskQueryService taskQueryService;
    @Autowired AsyncTaskLogService asyncTaskLogService;
    @Autowired TaskLogRepository taskLogRepository;
    @Autowired CoQuartzProperties properties;
    @Autowired TestEventListener eventListener;
    @Autowired MethodTaskBean methodTaskBean;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        PlainJob.COUNT.set(0);
        eventListener.failureEvents.clear();
        eventListener.timeoutEvents.clear();
        eventListener.consecutiveFailureEvents.clear();
    }

    @Test
    void contextLoadsAndPropertiesAreBound() {
        assertThat(scheduler).isNotNull();
        assertThat(coQuartzScheduler).isNotNull();
        assertThat(taskAdminService).isNotNull();
        assertThat(taskQueryService).isNotNull();
        assertThat(asyncTaskLogService).isNotNull();
        assertThat(taskLogRepository).isNotNull();
        assertThat(properties.getLog().isEnabled()).isTrue();
        assertThat(properties.getLog().getRetentionDays()).isEqualTo(30);
        assertThat(properties.getMonitoring().getConsecutiveFailureThreshold()).isEqualTo(3);
        assertThat(properties.getTimeoutPool().getCoreSize()).isEqualTo(2);
    }

    @Test
    void autoCreateTableCreatesQuartzTaskLog() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quartz_task_log", Integer.class);
        assertThat(count).isNotNull();
    }

    @Test
    void quartzJobAutoRegistersAndAutowires() throws Exception {
        assertThat(scheduler.checkExists(org.quartz.JobKey.jobKey("simpleAnnotatedJob"))).isTrue();
        await().atMost(15, TimeUnit.SECONDS).until(() -> SimpleAnnotatedJob.COUNT.get() > 0);
    }

    @Test
    void declarativeMethodTaskAutoRegistersAndExecutes() throws Exception {
        assertThat(scheduler.checkExists(JobKey.jobKey("declarativeMethodTask", CoQuartzConstants.DEFAULT_GROUP))).isTrue();
        await().atMost(10, TimeUnit.SECONDS).until(() -> methodTaskBean.count.get() > 0);
    }

    @Test
    void plainJobProducesSingleLog() throws Exception {
        QuartzTaskBuilder.newBuilder()
                .jobClass(PlainJob.class)
                .jobName("plainJob")
                .intervalInSeconds(2)
                .schedule(scheduler);

        await().atMost(10, TimeUnit.SECONDS).until(() -> PlainJob.COUNT.get() > 0);

        asyncTaskLogService.flushLogsImmediately();
        Thread.sleep(500);

        List<TaskExecutionLog> logs = taskLogRepository.latestLogs("DEFAULT.plainJob", 10);
        assertThat(logs).isNotEmpty();
        TaskExecutionLog logEntry = logs.get(0);
        assertThat(logEntry.getAttempt()).isEqualTo(1);
        assertThat(logEntry.isFinalAttempt()).isTrue();
    }

    @Test
    void retryJobProducesMultipleAttempts() throws Exception {
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> RetryAnnotatedJob.COUNT.get() >= 3);

        asyncTaskLogService.flushLogsImmediately();
        Thread.sleep(500);

        List<TaskExecutionLog> logs = taskLogRepository.latestLogs("DEFAULT.retryAnnotatedJob", 10);
        long retryLogs = logs.stream()
                .filter(l -> l.getJobKey().equals("DEFAULT.retryAnnotatedJob"))
                .count();
        assertThat(retryLogs).isGreaterThanOrEqualTo(3);
    }

    @Test
    void taskBuilderDynamicJobWithTimeout() throws Exception {
        QuartzTaskBuilder.newBuilder()
                .jobClass(TimeoutJob.class)
                .jobName("timeoutJob")
                .intervalInSeconds(2)
                .timeout(500)
                .schedule(scheduler);

        await().atMost(20, TimeUnit.SECONDS).until(() -> !eventListener.timeoutEvents.isEmpty());
        assertThat(eventListener.timeoutEvents).isNotEmpty();
    }

    @Test
    void taskFailurePublishesEvent() throws Exception {
        QuartzTaskBuilder.newBuilder()
                .jobClass(FailingJob.class)
                .jobName("failingJob")
                .intervalInSeconds(2)
                .schedule(scheduler);

        await().atMost(15, TimeUnit.SECONDS).until(() -> !eventListener.failureEvents.isEmpty());
        assertThat(eventListener.failureEvents).isNotEmpty();
    }

    @Test
    void lifecyclePauseResumeDelete() throws Exception {
        QuartzTaskBuilder.newBuilder()
                .jobClass(PlainJob.class)
                .jobName("lifecycleJob")
                .intervalInSeconds(30)
                .schedule(scheduler);

        assertThat(taskAdminService.exists("lifecycleJob", "DEFAULT")).isTrue();

        taskAdminService.pauseJob("lifecycleJob", "DEFAULT");

        taskAdminService.resumeJob("lifecycleJob", "DEFAULT");

        boolean deleted = taskAdminService.deleteJob("lifecycleJob", "DEFAULT");
        assertThat(deleted).isTrue();
        assertThat(taskAdminService.exists("lifecycleJob", "DEFAULT")).isFalse();
    }

    @Test
    void consecutiveFailureAlert() throws Exception {
        QuartzTaskBuilder.newBuilder()
                .jobClass(AlwaysFailJob.class)
                .jobName("consecutiveFailJob")
                .intervalInSeconds(2)
                .retryTimes(0)
                .schedule(scheduler);

        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> !eventListener.consecutiveFailureEvents.isEmpty());

        assertThat(eventListener.consecutiveFailureEvents).isNotEmpty();
        TaskConsecutiveFailureEvent event = eventListener.consecutiveFailureEvents.get(0);
        assertThat(event.getThreshold()).isEqualTo(3);
    }

    @Test
    void logCleanupConfigurationWorks() {
        assertThat(properties.getLog().getCleanupCron()).isEqualTo("0 0 2 * * ?");
        assertThat(properties.getLog().getRetentionDays()).isEqualTo(30);
    }
}
