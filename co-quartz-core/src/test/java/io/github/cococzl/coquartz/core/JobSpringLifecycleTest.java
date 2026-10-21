package io.github.cococzl.coquartz.core;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.quartz.*;
import org.quartz.spi.TriggerFiredBundle;
import org.quartz.spi.OperableTrigger;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class JobSpringLifecycleTest {
    static class Dependency { }
    public static class LifecycleJob implements Job {
        static List<LifecycleJob> instances = new ArrayList<>();
        static AtomicInteger destroyed = new AtomicInteger();
        static AtomicInteger intercepted = new AtomicInteger();
        final Dependency dependency;
        String payload;
        boolean initialized;
        public LifecycleJob(Dependency dependency) { this.dependency = dependency; }
        public void setPayload(String payload) { this.payload = payload; }
        @PostConstruct void init() { initialized = payload != null; }
        @PreDestroy void destroy() { destroyed.incrementAndGet(); }
        public void execute(JobExecutionContext context) {
            assertThat(dependency).isNotNull();
            assertThat(initialized).isTrue();
            assertThat(payload).isEqualTo("trigger-value");
            instances.add(this);
        }
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void eachExecutionHasConstructorInjectionDataBeforeInitProxyAndDestruction(boolean concurrent) throws Exception {
        LifecycleJob.instances.clear(); LifecycleJob.destroyed.set(0); LifecycleJob.intercepted.set(0);
        var support = new BuilderScheduleRegressionTest();
        Scheduler scheduler = support.scheduler();
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(Dependency.class);
            context.registerBean("proxyProcessor", BeanPostProcessor.class, () -> new BeanPostProcessor() {
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof LifecycleJob)) return bean;
                    var proxy = new ProxyFactory(bean);
                    proxy.setInterfaces(Job.class);
                    proxy.addAdvice((MethodInterceptor) invocation -> {
                        if (invocation.getMethod().getName().equals("execute")) LifecycleJob.intercepted.incrementAndGet();
                        return invocation.proceed();
                    });
                    return proxy.getProxy();
                }
            });
            context.refresh();
            var factory = new CoQuartzJobFactory();
            factory.setBeanFactory(context.getAutowireCapableBeanFactory());
            factory.setProperties(new io.github.cococzl.coquartz.config.CoQuartzProperties());
            for (int i = 0; i < 2; i++) {
                var data = new JobDataMap();
                data.put(CoQuartzConstants.ENHANCED, true);
                data.put("payload", "job-value");
                if (!concurrent) data.put(CoQuartzConstants.DELEGATE_JOB_CLASS, LifecycleJob.class.getName());
                var detail = JobBuilder.newJob(concurrent ? LifecycleJob.class : NonConcurrentJobWrapper.class)
                        .withIdentity("lifecycle").usingJobData(data).build();
                var trigger = (OperableTrigger) TriggerBuilder.newTrigger().forJob(detail)
                        .usingJobData("payload", "trigger-value").build();
                var bundle = new TriggerFiredBundle(detail, trigger, null, false, new Date(), new Date(), null, null);
                Job job = factory.newJob(bundle, scheduler);
                job.execute(new org.quartz.impl.JobExecutionContextImpl(scheduler, bundle, job));
            }
            assertThat(LifecycleJob.instances).hasSize(2);
            assertThat(LifecycleJob.instances.get(0)).isNotSameAs(LifecycleJob.instances.get(1));
            assertThat(LifecycleJob.intercepted).hasValue(2);
            assertThat(LifecycleJob.destroyed).hasValue(2);
        } finally { scheduler.shutdown(true); }
    }
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.transaction.annotation.EnableTransactionManagement
    static class TransactionConfig {
        @org.springframework.context.annotation.Bean
        org.springframework.transaction.PlatformTransactionManager transactionManager() {
            return new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
                protected Object doGetTransaction() { return new Object(); }
                protected void doBegin(Object tx, org.springframework.transaction.TransactionDefinition definition) { }
                protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) { }
                protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) { }
            };
        }
    }

    public static class TransactionalQuartzBean extends org.springframework.scheduling.quartz.QuartzJobBean {
        static AtomicInteger executions = new AtomicInteger();
        static AtomicInteger destroyed = new AtomicInteger();
        final Dependency dependency;
        final TransactionalService service;
        String payload;
        boolean initialized;
        public TransactionalQuartzBean(Dependency dependency, TransactionalService service) { this.dependency = dependency; this.service = service; }
        public void setPayload(String payload) { this.payload = payload; }
        @PostConstruct void init() { initialized = payload != null; }
        @PreDestroy void destroy() { destroyed.incrementAndGet(); }
        protected void executeInternal(JobExecutionContext context) {
            assertThat(dependency).isNotNull();
            assertThat(initialized).isTrue();
            assertThat(payload).isEqualTo("tx-payload");
            service.verifyTransaction();
            executions.incrementAndGet();
        }
    }

    public static class TransactionalService {
        @org.springframework.transaction.annotation.Transactional
        public void verifyTransaction() {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void quartzJobBeanKeepsInjectedTransactionalServiceAndLifecycle(boolean concurrent) throws Exception {
        TransactionalQuartzBean.executions.set(0); TransactionalQuartzBean.destroyed.set(0);
        var scheduler = new BuilderScheduleRegressionTest().scheduler();
        try (var context = new AnnotationConfigApplicationContext(TransactionConfig.class, Dependency.class, TransactionalService.class)) {
            var factory = new CoQuartzJobFactory();
            factory.setBeanFactory(context.getAutowireCapableBeanFactory());
            factory.setProperties(new io.github.cococzl.coquartz.config.CoQuartzProperties());
            for (int i = 0; i < 2; i++) {
                QuartzTaskBuilder.newBuilder().jobClass(TransactionalQuartzBean.class)
                        .jobName("tx").intervalInSeconds(3600).concurrent(concurrent)
                        .jobData("payload", "tx-payload").schedule(scheduler);
                var detail = scheduler.getJobDetail(JobKey.jobKey("tx"));
                var trigger = (OperableTrigger) TriggerBuilder.newTrigger().forJob(detail).build();
                var bundle = new TriggerFiredBundle(detail, trigger, null, false, new Date(), new Date(), null, null);
                Job job = factory.newJob(bundle, scheduler);
                job.execute(new org.quartz.impl.JobExecutionContextImpl(scheduler, bundle, job));
            }
            assertThat(TransactionalQuartzBean.executions).hasValue(2);
            assertThat(TransactionalQuartzBean.destroyed).hasValue(2);
        } finally { scheduler.shutdown(true); }
    }

}
