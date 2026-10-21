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
    public static class ControlJob implements Job {
        static String control;
        static final AtomicInteger calls = new AtomicInteger();
        public void execute(JobExecutionContext context) throws JobExecutionException {
            if (calls.incrementAndGet() > 1) return;
            var error = new JobExecutionException("business control");
            error.setUnscheduleFiringTrigger("firing".equals(control));
            error.setUnscheduleAllTriggers("all".equals(control));
            error.setRefireImmediately("refire".equals(control));
            throw error;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"firing", "all", "refire"})
    void completionAuditFailurePreservesBusinessQuartzControl(String control) throws Exception {
        var audit = mock(ReliableAuditService.class);
        doThrow(new IllegalStateException("completion database unavailable")).when(audit).recordCompleted(any());
        var beans = new StaticListableBeanFactory(); beans.addBean("audit", audit);
        var properties = new CoQuartzProperties(); properties.getLog().setReliableAudit(true);
        var factory = new CoQuartzJobFactory(); factory.setProperties(properties);
        factory.setReliableAuditServiceProvider(beans.getBeanProvider(ReliableAuditService.class));
        Scheduler scheduler = new BuilderScheduleRegressionTest().scheduler();
        ControlJob.calls.set(0); ControlJob.control = control;
        try {
            scheduler.setJobFactory(factory);
            var completed = new CountDownLatch("refire".equals(control) ? 2 : 1);
            var firstError = new AtomicReference<JobExecutionException>();
            scheduler.getListenerManager().addJobListener(new JobListenerSupport() {
                public String getName() { return "control-completion"; }
                public void jobWasExecuted(JobExecutionContext context, JobExecutionException error) {
                    firstError.compareAndSet(null, error); completed.countDown();
                }
            });
            var job = JobBuilder.newJob(ControlJob.class).withIdentity("control").build();
            var firing = TriggerBuilder.newTrigger().withIdentity("firing").forJob(job)
                    .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(3600)).build();
            var other = TriggerBuilder.newTrigger().withIdentity("other").forJob(job)
                    .startAt(new java.util.Date(System.currentTimeMillis() + 3600000)).build();
            scheduler.scheduleJob(job, java.util.Set.of(firing, other), false);
            scheduler.start();
            assertThat(completed.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(ControlJob.calls).hasValue("refire".equals(control) ? 2 : 1);
            assertThat(firstError.get().unscheduleFiringTrigger()).isEqualTo("firing".equals(control));
            assertThat(firstError.get().unscheduleAllTriggers()).isEqualTo("all".equals(control));
            assertThat(firstError.get().refireImmediately()).isEqualTo("refire".equals(control));
            var logs = org.mockito.ArgumentCaptor.forClass(io.github.cococzl.coquartz.dto.TaskExecutionLog.class);
            verify(audit, times("refire".equals(control) ? 2 : 1)).recordCompleted(logs.capture());
            if (!"refire".equals(control)) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (scheduler.getTriggerState(firing.getKey()) != Trigger.TriggerState.COMPLETE
                        && System.nanoTime() < deadline) Thread.sleep(10);
                assertThat(scheduler.getTriggerState(firing.getKey())).isEqualTo(Trigger.TriggerState.COMPLETE);
                assertThat(scheduler.getTriggerState(other.getKey()))
                        .isEqualTo("firing".equals(control) ? Trigger.TriggerState.NORMAL : Trigger.TriggerState.COMPLETE);
                assertThat(logs.getValue().getExecState()).isEqualTo(io.github.cococzl.coquartz.enums.LogTaskExecStateEnum.FAIL);
            }
        } finally { if (!scheduler.isShutdown()) scheduler.shutdown(true); }
    }
    public static class RefiringNativeJob implements Job {
        static final AtomicInteger executions = new AtomicInteger();
        static final AtomicInteger destroyed = new AtomicInteger();
        public void execute(JobExecutionContext context) throws JobExecutionException {
            assertThat(destroyed).hasValue(0);
            if (executions.incrementAndGet() == 1) throw new JobExecutionException("refire", true);
        }
        @jakarta.annotation.PreDestroy void destroy() { destroyed.incrementAndGet(); }
    }

    @org.junit.jupiter.api.Test
    void nativeReliableAuditKeepsQuartzRefireAndDestroysOnlyAfterFinalInvocation() throws Exception {
        RefiringNativeJob.executions.set(0); RefiringNativeJob.destroyed.set(0);
        var audit = mock(ReliableAuditService.class);
        var beans = new StaticListableBeanFactory(); beans.addBean("audit", audit);
        var properties = new CoQuartzProperties(); properties.getLog().setReliableAudit(true);
        var scheduler = new BuilderScheduleRegressionTest().scheduler();
        try (var spring = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            spring.refresh();
            var factory = new CoQuartzJobFactory(); factory.setProperties(properties);
            factory.setBeanFactory(spring.getAutowireCapableBeanFactory());
            factory.setReliableAuditServiceProvider(beans.getBeanProvider(ReliableAuditService.class));
            scheduler.setJobFactory(factory);
            var completed = new CountDownLatch(2);
            scheduler.getListenerManager().addJobListener(new JobListenerSupport() {
                public String getName() { return "refire-completion"; }
                public void jobWasExecuted(JobExecutionContext context, JobExecutionException error) { completed.countDown(); }
            });
            var detail = JobBuilder.newJob(RefiringNativeJob.class).withIdentity("refiring").build();
            scheduler.scheduleJob(detail, TriggerBuilder.newTrigger().forJob(detail)
                    .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(3600)).build());
            scheduler.start();
            assertThat(completed.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(RefiringNativeJob.executions).hasValue(2);
            assertThat(RefiringNativeJob.destroyed).hasValue(1);
            var logs = org.mockito.ArgumentCaptor.forClass(io.github.cococzl.coquartz.dto.TaskExecutionLog.class);
            verify(audit, times(2)).recordCompleted(logs.capture());
            assertThat(logs.getAllValues()).extracting(io.github.cococzl.coquartz.dto.TaskExecutionLog::getAttempt).containsExactly(1, 2);
            assertThat(logs.getAllValues()).extracting(io.github.cococzl.coquartz.dto.TaskExecutionLog::isFinalAttempt).containsExactly(false, true);
            assertThat(logs.getAllValues().get(0).getExecutionId()).isEqualTo(logs.getAllValues().get(1).getExecutionId());
        } finally { scheduler.shutdown(true); }
    }
    @DisallowConcurrentExecution
    public static class NativeJob implements Job {
        static final AtomicInteger executions = new AtomicInteger();
        public void execute(JobExecutionContext context) { executions.incrementAndGet(); }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"true,false", "false,false", "true,true", "false,true"})
    void auditFailuresAreJobFailuresAndNeverBreakQuartzCleanup(boolean failStart, boolean enhanced) throws Exception {
        var properties = new CoQuartzProperties();
        properties.getLog().setReliableAudit(true);
        var audit = mock(ReliableAuditService.class);
        if (failStart) doThrow(new IllegalStateException("database unavailable")).when(audit).recordStarted(any());
        else doThrow(new IllegalStateException("database unavailable")).when(audit).recordCompleted(any());
        var beans = new StaticListableBeanFactory();
        beans.addBean("audit", audit);
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var metrics = new io.github.cococzl.coquartz.metrics.CoQuartzMetrics(registry);
        beans.addBean("metrics", metrics);
        var factory = new CoQuartzJobFactory();
        factory.setProperties(properties);
        factory.setReliableAuditServiceProvider(beans.getBeanProvider(ReliableAuditService.class));
        factory.setMetricsProvider(beans.getBeanProvider(io.github.cococzl.coquartz.metrics.CoQuartzMetrics.class));
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
                    (TaskExecutionLogWriter) null, metrics, null, properties, null, audit));
            scheduler.getListenerManager().addJobListener(new JobListenerSupport() {
                public String getName() { return "completion"; }
                public void jobWasExecuted(JobExecutionContext context, JobExecutionException error) {
                    result.set(error);
                    completed.countDown();
                }
            });
            JobDetail job = JobBuilder.newJob(NativeJob.class).withIdentity("native")
                    .usingJobData(CoQuartzConstants.ENHANCED, enhanced).build();
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
            assertThat(registry.get("co_quartz_job_active").gauge().value()).isZero();
            assertThat(registry.find("co_quartz_job_executions_total").tag("state", "success").counter()).isNull();
            assertThat(registry.find("co_quartz_job_executions_total").tag("job", "DEFAULT.native")
                    .tag("state", "fail").counter()).isNotNull().satisfies(counter -> assertThat(counter.count()).isEqualTo(1));
        } finally { scheduler.shutdown(true); metrics.close(); registry.close(); }
    }
}
