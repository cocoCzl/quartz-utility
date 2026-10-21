package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.listener.CoQuartzJobListener;
import io.github.cococzl.coquartz.service.ReliableAuditService;
import io.github.cococzl.coquartz.service.TaskExecutionLogWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.listeners.JobListenerSupport;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NativeAuditExecutionTest {
    @DisallowConcurrentExecution
    public static class NativeJob implements Job {
        static final AtomicInteger executions = new AtomicInteger();
        public void execute(JobExecutionContext context) { executions.incrementAndGet(); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void auditFailuresAreJobFailuresAndNeverBreakQuartzCleanup(boolean failStart) throws Exception {
        var properties = new CoQuartzProperties();
        properties.getLog().setReliableAudit(true);
        var audit = mock(ReliableAuditService.class);
        if (failStart) doThrow(new IllegalStateException("database unavailable")).when(audit).recordStarted(any());
        else doThrow(new IllegalStateException("database unavailable")).when(audit).recordCompleted(any());
        var beans = new StaticListableBeanFactory();
        beans.addBean("audit", audit);
        var factory = new CoQuartzJobFactory();
        factory.setProperties(properties);
        factory.setReliableAuditServiceProvider(beans.getBeanProvider(ReliableAuditService.class));
        Properties config = new Properties();
        config.setProperty("org.quartz.scheduler.instanceName", "native-audit-" + System.nanoTime());
        config.setProperty("org.quartz.threadPool.threadCount", "1");
        Scheduler scheduler = new StdSchedulerFactory(config).getScheduler();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<JobExecutionException> result = new AtomicReference<>();
        try {
            NativeJob.executions.set(0);
            scheduler.setJobFactory(factory);
            scheduler.getListenerManager().addJobListener(new CoQuartzJobListener(
                    (TaskExecutionLogWriter) null, null, null, properties, null, audit));
            scheduler.getListenerManager().addJobListener(new JobListenerSupport() {
                public String getName() { return "completion"; }
                public void jobWasExecuted(JobExecutionContext context, JobExecutionException error) {
                    result.set(error);
                    completed.countDown();
                }
            });
            JobDetail job = JobBuilder.newJob(NativeJob.class).withIdentity("native").build();
            Trigger trigger = TriggerBuilder.newTrigger().withIdentity("nativeTrigger").forJob(job)
                    .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(60)).build();
            scheduler.scheduleJob(job, trigger);
            scheduler.start();
            assertThat(completed.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(result.get()).isNotNull();
            assertThat(NativeJob.executions.get()).isEqualTo(failStart ? 0 : 1);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (scheduler.getTriggerState(trigger.getKey()) == Trigger.TriggerState.BLOCKED
                    && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(scheduler.getTriggerState(trigger.getKey())).isEqualTo(Trigger.TriggerState.NORMAL);
        } finally { scheduler.shutdown(true); }
    }
}
