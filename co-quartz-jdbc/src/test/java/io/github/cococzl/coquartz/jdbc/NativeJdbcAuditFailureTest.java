package io.github.cococzl.coquartz.jdbc;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.core.CoQuartzJobFactory;
import io.github.cococzl.coquartz.jdbc.repository.JdbcTaskLogRepository;
import io.github.cococzl.coquartz.jdbc.service.JdbcReliableAuditService;
import io.github.cococzl.coquartz.service.ReliableAuditService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.listeners.JobListenerSupport;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

/** Real SQL failures must leave Quartz able to run the next attempt. */
class NativeJdbcAuditFailureTest {
    @DisallowConcurrentExecution
    public static class NativeJob implements Job {
        static JdbcTemplate jdbc;
        static boolean dropOnExecute;
        static AtomicInteger executions = new AtomicInteger();
        public void execute(JobExecutionContext context) {
            executions.incrementAndGet();
            if (dropOnExecute) jdbc.execute("DROP TABLE quartz_task_log");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void missingAuditTableAtStartOrCompletionDoesNotLeaveBlockedTrigger(boolean failStart) throws Exception {
        var db = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        var schema = new ResourceDatabasePopulator(new ClassPathResource("schema-quartz-task-log-h2.sql"));
        schema.execute(db);
        NativeJob.jdbc = new JdbcTemplate(db);
        NativeJob.executions.set(0);
        NativeJob.dropOnExecute = !failStart;
        if (failStart) NativeJob.jdbc.execute("DROP TABLE quartz_task_log");
        var properties = new CoQuartzProperties(); properties.getLog().setReliableAudit(true);
        var beans = new StaticListableBeanFactory();
        beans.addBean("audit", new JdbcReliableAuditService(new JdbcTaskLogRepository(NativeJob.jdbc)));
        var factory = new CoQuartzJobFactory(); factory.setProperties(properties);
        factory.setReliableAuditServiceProvider(beans.getBeanProvider(ReliableAuditService.class));
        var config = new Properties();
        config.setProperty("org.quartz.scheduler.instanceName", "jdbc-audit-" + System.nanoTime());
        config.setProperty("org.quartz.threadPool.threadCount", "1");
        var scheduler = new StdSchedulerFactory(config).getScheduler();
        var outcomes = new LinkedBlockingQueue<Boolean>();
        try {
            scheduler.setJobFactory(factory);
            scheduler.getListenerManager().addJobListener(new JobListenerSupport() {
                public String getName() { return "outcomes"; }
                public void jobWasExecuted(JobExecutionContext context, JobExecutionException error) {
                    outcomes.add(error == null);
                }
            });
            var detail = JobBuilder.newJob(NativeJob.class).withIdentity("native").build();
            var trigger = TriggerBuilder.newTrigger().withIdentity("periodic").forJob(detail)
                    .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(3600)).build();
            scheduler.scheduleJob(detail, trigger); scheduler.start();
            assertThat(outcomes.poll(5, TimeUnit.SECONDS)).isFalse();
            assertThat(NativeJob.executions).hasValue(failStart ? 0 : 1);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (scheduler.getTriggerState(trigger.getKey()) == Trigger.TriggerState.BLOCKED
                    && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(scheduler.getTriggerState(trigger.getKey())).isEqualTo(Trigger.TriggerState.NORMAL);
            schema.execute(db); NativeJob.dropOnExecute = false;
            scheduler.triggerJob(detail.getKey());
            assertThat(outcomes.poll(5, TimeUnit.SECONDS)).isTrue();
            assertThat(NativeJob.executions).hasValue(failStart ? 1 : 2);
        } finally { scheduler.shutdown(true); db.shutdown(); }
    }
}
