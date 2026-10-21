package io.github.cococzl.coquartz.jdbc.config;

import io.github.cococzl.coquartz.annotation.QuartzJob;
import io.github.cococzl.coquartz.config.CoQuartzCoreAutoConfiguration;
import io.github.cococzl.coquartz.core.CoQuartzConstants;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DeclarativeRestartAcceptanceTest {
    private static final JobKey KEY = JobKey.jobKey("bootRestart");
    private record Invocation(String contextId, String payload) { }
    static final List<Invocation> invocations = new CopyOnWriteArrayList<>();
    record Dependency(String contextId) { }

    @Configuration(proxyBeanMethods = false)
    static class Jobs {
        @Bean Dependency dependency() { return new Dependency(UUID.randomUUID().toString()); }
        @Bean RestartJob restartJob(Dependency dependency) { return new RestartJob(dependency); }
    }

    @QuartzJob(name = "bootRestart", cron = "0 0 0 1 1 ? 2099", retryTimes = 1, retryInterval = 4000)
    public static class RestartJob implements Job {
        final Dependency dependency;
        boolean initialized;
        public RestartJob(Dependency dependency) { this.dependency = dependency; }
        @PostConstruct void init() { initialized = true; }
        public void execute(JobExecutionContext context) throws JobExecutionException {
            assertThat(initialized).isTrue();
            invocations.add(new Invocation(dependency.contextId(), context.getMergedJobDataMap().getString("orderId")));
            if (invocations.size() == 1) throw new JobExecutionException("first attempt");
        }
    }

    @Test
    void pendingDeclarativeRetrySurvivesActualBootContextCloseAndRecreation() {
        invocations.clear();
        String suffix = UUID.randomUUID().toString();
        var runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, QuartzAutoConfiguration.class,
                        CoQuartzCoreAutoConfiguration.class, CoQuartzJdbcAutoConfiguration.class))
                .withUserConfiguration(Jobs.class)
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:boot-restart-" + suffix + ";DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa", "spring.quartz.scheduler-name=boot-restart-" + suffix,
                        "spring.quartz.job-store-type=jdbc", "spring.quartz.auto-startup=false",
                        "spring.quartz.wait-for-jobs-to-complete-on-shutdown=true",
                        "co-quartz.log.enabled=true", "co-quartz.log.reliable-audit=true", "co-quartz.log.auto-create-table=true");
        List<TaskExecutionLog> firstLogs = new CopyOnWriteArrayList<>();
        runner.withPropertyValues("spring.quartz.jdbc.initialize-schema=always").run(context -> {
            assertThat(context).hasNotFailed();
            Scheduler scheduler = context.getBean(Scheduler.class);
            assertThat(scheduler.getJobDetail(KEY).isDurable()).isFalse();
            scheduler.start();
            var data = new JobDataMap(); data.put("orderId", "order-restart");
            scheduler.triggerJob(KEY, data);
            var repository = context.getBean(TaskLogRepository.class);
            awaitLogs(repository, 1, LogTaskExecStateEnum.FAIL);
            assertThat(scheduler.getTriggersOfJob(KEY)).anySatisfy(trigger -> {
                assertThat(trigger.getKey().getGroup()).isEqualTo(CoQuartzConstants.RETRY_TRIGGER_GROUP);
                assertThat(trigger.getJobDataMap().getString("orderId")).isEqualTo("order-restart");
            });
            firstLogs.addAll(repository.latestLogs(KEY.toString(), 10));
        }); // Closes the real SchedulerFactoryBean, datasource and business context.
        runner.withPropertyValues("spring.quartz.jdbc.initialize-schema=never").run(context -> {
            assertThat(context).hasNotFailed();
            Scheduler scheduler = context.getBean(Scheduler.class);
            assertThat(scheduler.getTriggersOfJob(KEY)).anyMatch(t -> CoQuartzConstants.RETRY_TRIGGER_GROUP.equals(t.getKey().getGroup()));
            scheduler.start();
            var repository = context.getBean(TaskLogRepository.class);
            awaitLogs(repository, 2, LogTaskExecStateEnum.SUCCESS);
            List<TaskExecutionLog> logs = repository.latestLogs(KEY.toString(), 10);
            assertThat(logs).hasSize(2);
            assertThat(logs).extracting(TaskExecutionLog::getAttempt).containsExactly(2, 1);
            assertThat(logs).extracting(TaskExecutionLog::getExecutionId).containsOnly(firstLogs.get(0).getExecutionId());
            assertThat(logs).extracting(TaskExecutionLog::getTriggerKey).containsOnly(firstLogs.get(0).getTriggerKey());
            assertThat(logs).extracting(TaskExecutionLog::getFireInstanceId).doesNotHaveDuplicates();
            assertThat(logs).extracting(TaskExecutionLog::isFinalAttempt).containsExactly(true, false);
            assertThat(invocations).extracting(Invocation::payload).containsExactly("order-restart", "order-restart");
            assertThat(invocations).extracting(Invocation::contextId).doesNotHaveDuplicates();
        });
    }

    private void awaitLogs(TaskLogRepository repository, int size, LogTaskExecStateEnum latestState) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            var logs = repository.latestLogs(KEY.toString(), 10);
            if (logs.size() == size && logs.get(0).getExecState() == latestState) return;
            Thread.sleep(20);
        }
        assertThat(repository.latestLogs(KEY.toString(), 10)).hasSize(size)
                .first().extracting(TaskExecutionLog::getExecState).isEqualTo(latestState);
    }
}
