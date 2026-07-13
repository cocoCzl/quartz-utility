package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.annotation.QuartzJob;
import io.github.cococzl.coquartz.annotation.QuartzTask;
import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.exception.CoQuartzConfigurationException;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.CronTrigger;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.support.GenericApplicationContext;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class QuartzJobAnnotationProcessorTest {

    @Test
    void rejectsNonPublicMethodBeforeScheduling() {
        assertInvalid(PrivateMethodBean.class, "privateMethodBean", "run", "public");
    }

    @Test
    void rejectsNonVoidMethodBeforeScheduling() {
        assertInvalid(NonVoidMethodBean.class, "nonVoidMethodBean", "run", "return void");
    }

    @Test
    void rejectsMethodWithParametersBeforeScheduling() {
        assertInvalid(ParameterizedMethodBean.class, "parameterizedMethodBean", "run", "no parameters");
    }

    @Test
    void rejectsStaticMethodBeforeScheduling() {
        assertInvalid(StaticMethodBean.class, "staticMethodBean", "run", "instance method");
    }

    @Test
    void rejectsBlankTaskNameBeforeScheduling() {
        assertInvalid(BlankNameMethodBean.class, "blankNameMethodBean", "run", "name must not be blank");
    }

    @Test
    void rejectsDuplicateMethodTaskIdentityWithoutPartialRegistration() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("firstDuplicateBean", FirstDuplicateBean.class);
            context.registerBean("secondDuplicateBean", SecondDuplicateBean.class);
            context.refresh();
            Scheduler scheduler = mock(Scheduler.class);

            QuartzJobAnnotationProcessor processor = processor(context, scheduler);

            assertThatThrownBy(processor::registerJobs)
                    .isInstanceOf(CoQuartzConfigurationException.class)
                    .hasMessageContaining("DEFAULT.duplicateTask")
                    .hasMessageContaining("firstDuplicateBean#run")
                    .hasMessageContaining("secondDuplicateBean#run");
            verifyNoInteractions(scheduler);
        }
    }

    @Test
    void rejectsIdentitySharedByMethodTaskAndTraditionalJob() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("methodConflictBean", MethodConflictBean.class);
            context.registerBean("traditionalConflictJob", TraditionalConflictJob.class);
            context.refresh();
            Scheduler scheduler = mock(Scheduler.class);

            QuartzJobAnnotationProcessor processor = processor(context, scheduler);

            assertThatThrownBy(processor::registerJobs)
                    .isInstanceOf(CoQuartzConfigurationException.class)
                    .hasMessageContaining("DEFAULT.sharedIdentity")
                    .hasMessageContaining("methodConflictBean#run")
                    .hasMessageContaining(TraditionalConflictJob.class.getName());
            verifyNoInteractions(scheduler);
        }
    }

    @Test
    void allowsSameNameInDifferentGroups() throws Exception {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("groupOneBean", GroupOneBean.class);
            context.registerBean("groupTwoBean", GroupTwoBean.class);
            context.refresh();
            Scheduler scheduler = mock(Scheduler.class);

            processor(context, scheduler).registerJobs();

            verify(scheduler, times(2)).scheduleJob(any(JobDetail.class), any(Trigger.class));
        }
    }

    @Test
    void invalidDefinitionPreventsValidDefinitionFromBeingScheduled() throws Exception {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("validBean", ValidBean.class);
            context.registerBean("blankNameMethodBean", BlankNameMethodBean.class);
            context.refresh();
            Scheduler scheduler = mock(Scheduler.class);

            assertThatThrownBy(() -> processor(context, scheduler).registerJobs())
                    .isInstanceOf(CoQuartzConfigurationException.class);
            verify(scheduler, never()).scheduleJob(any(JobDetail.class), any(Trigger.class));
        }
    }

    @Test
    void invokesMethodTaskThroughSpringAopProxy() throws Exception {
        AtomicInteger adviceInvocations = new AtomicInteger();
        ProxiedTaskTarget target = new ProxiedTaskTarget();
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setInterfaces(ProxiedTaskContract.class);
        proxyFactory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
            adviceInvocations.incrementAndGet();
            return invocation.proceed();
        });

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("proxiedTaskBean", ProxiedTaskContract.class,
                    () -> (ProxiedTaskContract) proxyFactory.getProxy());
            context.refresh();
            Scheduler scheduler = mock(Scheduler.class);
            MethodTaskRegistry registry = new MethodTaskRegistry(context);
            QuartzJobAnnotationProcessor processor = new QuartzJobAnnotationProcessor(
                    new CoQuartzScheduler(scheduler), context, new CoQuartzProperties(), registry);

            processor.registerJobs();
            registry.invoke(JobKey.jobKey("proxiedTask"));

            assertThat(target.invocations.get()).isEqualTo(1);
            assertThat(adviceInvocations.get()).isEqualTo(1);
        }
    }

    @Test
    void configuredDefaultTimeZoneIsAppliedToMethodTask() throws Exception {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("defaultZoneBean", DefaultZoneBean.class);
            context.refresh();
            Scheduler scheduler = mock(Scheduler.class);
            CoQuartzProperties properties = new CoQuartzProperties();
            properties.getScheduling().setDefaultTimeZone("Asia/Shanghai");

            processor(context, scheduler, properties).registerJobs();

            org.mockito.ArgumentCaptor<Trigger> triggerCaptor =
                    org.mockito.ArgumentCaptor.forClass(Trigger.class);
            verify(scheduler).scheduleJob(any(JobDetail.class), triggerCaptor.capture());
            assertThat(((CronTrigger) triggerCaptor.getValue()).getTimeZone().getID())
                    .isEqualTo("Asia/Shanghai");
        }
    }

    @Test
    void nonConcurrentDeclarativeTaskUsesQuartzVisibleProxyAndRetainsMethodAdapter() throws Exception {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("defaultZoneBean", DefaultZoneBean.class);
            context.refresh();
            Scheduler scheduler = mock(Scheduler.class);

            processor(context, scheduler).registerJobs();

            org.mockito.ArgumentCaptor<JobDetail> jobCaptor =
                    org.mockito.ArgumentCaptor.forClass(JobDetail.class);
            verify(scheduler).scheduleJob(jobCaptor.capture(), any(Trigger.class));
            assertThat(jobCaptor.getValue().getJobClass()).isEqualTo(NonConcurrentJobWrapper.class);
            assertThat(jobCaptor.getValue().getJobDataMap()
                    .getString(CoQuartzConstants.DELEGATE_JOB_CLASS))
                    .isEqualTo(MethodInvokingJob.class.getName());
        }
    }

    @Test
    void invalidTimeZoneFailsBeforeScheduling() {
        assertInvalidDefinition(InvalidTimeZoneBean.class, "invalidTimeZoneBean", "Mars/Olympus");
    }

    @Test
    void invalidCronFailsBeforeScheduling() {
        assertInvalidDefinition(InvalidCronBean.class, "invalidCronBean", "not-a-cron");
    }

    @Test
    void cronAndIntervalConflictFailsBeforeScheduling() {
        assertInvalidDefinition(ConflictingScheduleBean.class, "conflictingScheduleBean", "Exactly one");
    }

    private void assertInvalid(Class<?> beanClass, String beanName, String methodName, String reason) {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(beanName, beanClass);
            context.refresh();
            Scheduler scheduler = mock(Scheduler.class);

            assertThatThrownBy(() -> processor(context, scheduler).registerJobs())
                    .isInstanceOf(CoQuartzConfigurationException.class)
                    .hasMessageContaining(beanName)
                    .hasMessageContaining(methodName)
                    .hasMessageContaining(reason);
            verifyNoInteractions(scheduler);
        }
    }

    private QuartzJobAnnotationProcessor processor(GenericApplicationContext context, Scheduler scheduler) {
        return processor(context, scheduler, new CoQuartzProperties());
    }

    private QuartzJobAnnotationProcessor processor(GenericApplicationContext context, Scheduler scheduler,
                                                    CoQuartzProperties properties) {
        return new QuartzJobAnnotationProcessor(
                new CoQuartzScheduler(scheduler),
                context,
                properties,
                new MethodTaskRegistry(context));
    }

    private void assertInvalidDefinition(Class<?> beanClass, String beanName, String expectedReason) {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(beanName, beanClass);
            context.refresh();
            Scheduler scheduler = mock(Scheduler.class);

            assertThatThrownBy(() -> processor(context, scheduler).registerJobs())
                    .isInstanceOf(CoQuartzConfigurationException.class)
                    .hasMessageContaining(beanName + "#run")
                    .hasMessageContaining(expectedReason);
            verifyNoInteractions(scheduler);
        }
    }

    static class PrivateMethodBean {
        @QuartzTask(name = "privateTask", intervalSeconds = 60)
        private void run() {
        }
    }

    static class NonVoidMethodBean {
        @QuartzTask(name = "nonVoidTask", intervalSeconds = 60)
        public String run() {
            return "result";
        }
    }

    static class ParameterizedMethodBean {
        @QuartzTask(name = "parameterizedTask", intervalSeconds = 60)
        public void run(String value) {
        }
    }

    static class StaticMethodBean {
        @QuartzTask(name = "staticTask", intervalSeconds = 60)
        public static void run() {
        }
    }

    static class BlankNameMethodBean {
        @QuartzTask(name = " ", intervalSeconds = 60)
        public void run() {
        }
    }

    static class FirstDuplicateBean {
        @QuartzTask(name = "duplicateTask", intervalSeconds = 60)
        public void run() {
        }
    }

    static class SecondDuplicateBean {
        @QuartzTask(name = "duplicateTask", intervalSeconds = 60)
        public void run() {
        }
    }

    static class MethodConflictBean {
        @QuartzTask(name = "sharedIdentity", intervalSeconds = 60)
        public void run() {
        }
    }

    @QuartzJob(name = "sharedIdentity", intervalSeconds = 60)
    static class TraditionalConflictJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
        }
    }

    static class GroupOneBean {
        @QuartzTask(name = "sameName", group = "ONE", intervalSeconds = 60)
        public void run() {
        }
    }

    static class GroupTwoBean {
        @QuartzTask(name = "sameName", group = "TWO", intervalSeconds = 60)
        public void run() {
        }
    }

    static class ValidBean {
        @QuartzTask(name = "validTask", intervalSeconds = 60)
        public void run() {
        }
    }

    interface ProxiedTaskContract {
        void run();
    }

    static class ProxiedTaskTarget implements ProxiedTaskContract {
        final AtomicInteger invocations = new AtomicInteger();

        @Override
        @QuartzTask(name = "proxiedTask", intervalSeconds = 60)
        public void run() {
            invocations.incrementAndGet();
        }
    }

    static class DefaultZoneBean {
        @QuartzTask(name = "defaultZoneTask", cron = "0 0 9 * * ?")
        public void run() {
        }
    }

    static class InvalidTimeZoneBean {
        @QuartzTask(name = "invalidTimeZoneTask", cron = "0 0 9 * * ?", timeZone = "Mars/Olympus")
        public void run() {
        }
    }

    static class InvalidCronBean {
        @QuartzTask(name = "invalidCronTask", cron = "not-a-cron", timeZone = "UTC")
        public void run() {
        }
    }

    static class ConflictingScheduleBean {
        @QuartzTask(name = "conflictingTask", cron = "0 0 9 * * ?", intervalSeconds = 60)
        public void run() {
        }
    }
}
