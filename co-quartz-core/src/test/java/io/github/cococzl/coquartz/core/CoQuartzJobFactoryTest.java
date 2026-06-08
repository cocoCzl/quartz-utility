package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.event.AlertEventPublisher;
import io.github.cococzl.coquartz.metrics.CoQuartzMetrics;
import io.github.cococzl.coquartz.service.AsyncTaskLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.quartz.*;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoQuartzJobFactoryTest {

    private CoQuartzJobFactory jobFactory;

    @Mock
    private AutowireCapableBeanFactory beanFactory;

    @Mock
    private ObjectProvider<AsyncTaskLogService> asyncTaskLogServiceProvider;

    @Mock
    private ScheduledExecutorService timeoutExecutor;

    @Mock
    private ObjectProvider<AlertEventPublisher> alertEventPublisherProvider;

    private CoQuartzProperties properties;

    @Mock
    private ObjectProvider<CoQuartzMetrics> metricsProvider;

    @Mock
    private AsyncTaskLogService asyncTaskLogService;

    @Mock
    private AlertEventPublisher alertEventPublisher;

    @Mock
    private CoQuartzMetrics metrics;

    @BeforeEach
    void setUp() {
        properties = new CoQuartzProperties();
        when(asyncTaskLogServiceProvider.getIfAvailable()).thenReturn(asyncTaskLogService);
        when(alertEventPublisherProvider.getIfAvailable()).thenReturn(alertEventPublisher);
        when(metricsProvider.getIfAvailable()).thenReturn(metrics);

        jobFactory = new CoQuartzJobFactory();
        jobFactory.setBeanFactory(beanFactory);
        jobFactory.setAsyncTaskLogServiceProvider(asyncTaskLogServiceProvider);
        jobFactory.setTimeoutExecutor(timeoutExecutor);
        jobFactory.setAlertEventPublisherProvider(alertEventPublisherProvider);
        jobFactory.setProperties(properties);
        jobFactory.setMetricsProvider(metricsProvider);
    }

    private TriggerFiredBundle createBundle(JobDataMap jobDataMap) {
        JobDetail jobDetail = JobBuilder.newJob(TestJob.class)
                .withIdentity("testJob", "DEFAULT")
                .usingJobData(jobDataMap)
                .storeDurably()
                .build();

        SimpleTriggerImpl trigger = new SimpleTriggerImpl("testTrigger", "DEFAULT");
        trigger.setJobDataMap(jobDataMap);
        trigger.setJobKey(jobDetail.getKey());

        Date now = new Date();

        return new TriggerFiredBundle(jobDetail, trigger, null, false, now, null, null, null);
    }

    public static class TestJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
        }
    }

    @Test
    void isEnhancedJob_withEnhancedFlagTrue() throws Exception {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(CoQuartzConstants.ENHANCED, true);
        jobDataMap.put(CoQuartzConstants.CONCURRENT, true);

        TriggerFiredBundle bundle = createBundle(jobDataMap);
        Object result = jobFactory.createJobInstance(bundle);

        assertThat(result).isInstanceOf(EnhancedJob.class);
    }

    @Test
    void isEnhancedJob_withEnhancedFlagFalse() throws Exception {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(CoQuartzConstants.ENHANCED, false);

        TriggerFiredBundle bundle = createBundle(jobDataMap);
        Object result = jobFactory.createJobInstance(bundle);

        assertThat(result).isNotInstanceOf(EnhancedJob.class);
    }

    @Test
    void isEnhancedJob_withoutEnhancedFlag() throws Exception {
        JobDataMap jobDataMap = new JobDataMap();

        TriggerFiredBundle bundle = createBundle(jobDataMap);
        Object result = jobFactory.createJobInstance(bundle);

        assertThat(result).isNotInstanceOf(EnhancedJob.class);
    }

    @Test
    void isNonConcurrent_wrapsWithNonConcurrentJobWrapper() throws Exception {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(CoQuartzConstants.ENHANCED, true);
        jobDataMap.put(CoQuartzConstants.CONCURRENT, false);

        TriggerFiredBundle bundle = createBundle(jobDataMap);
        Object result = jobFactory.createJobInstance(bundle);

        assertThat(result).isInstanceOf(NonConcurrentJobWrapper.class);
        NonConcurrentJobWrapper wrapper = (NonConcurrentJobWrapper) result;
        assertThat(wrapper.getDelegate()).isInstanceOf(EnhancedJob.class);
    }

    @Test
    void isConcurrent_doesNotWrapWithNonConcurrent() throws Exception {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(CoQuartzConstants.ENHANCED, true);
        jobDataMap.put(CoQuartzConstants.CONCURRENT, true);

        TriggerFiredBundle bundle = createBundle(jobDataMap);
        Object result = jobFactory.createJobInstance(bundle);

        assertThat(result).isInstanceOf(EnhancedJob.class);
        assertThat(result).isNotInstanceOf(NonConcurrentJobWrapper.class);
    }

    @Test
    void nonEnhancedJob_withoutConcurrentKey_defaultNonConcurrent() throws Exception {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(CoQuartzConstants.ENHANCED, true);

        TriggerFiredBundle bundle = createBundle(jobDataMap);
        Object result = jobFactory.createJobInstance(bundle);

        assertThat(result).isInstanceOf(NonConcurrentJobWrapper.class);
    }

    @Test
    void concurrentAsString_true() throws Exception {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(CoQuartzConstants.ENHANCED, true);
        jobDataMap.put(CoQuartzConstants.CONCURRENT, "true");

        TriggerFiredBundle bundle = createBundle(jobDataMap);
        Object result = jobFactory.createJobInstance(bundle);

        assertThat(result).isInstanceOf(EnhancedJob.class);
    }

    @Test
    void concurrentAsString_false() throws Exception {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(CoQuartzConstants.ENHANCED, true);
        jobDataMap.put(CoQuartzConstants.CONCURRENT, "false");

        TriggerFiredBundle bundle = createBundle(jobDataMap);
        Object result = jobFactory.createJobInstance(bundle);

        assertThat(result).isInstanceOf(NonConcurrentJobWrapper.class);
    }

    @Test
    void autowiresBean() throws Exception {
        JobDataMap jobDataMap = new JobDataMap();
        TriggerFiredBundle bundle = createBundle(jobDataMap);

        jobFactory.createJobInstance(bundle);

        verify(beanFactory).autowireBean(any());
    }

    @Test
    void nullAsyncTaskLogService_noWrapping() throws Exception {
        ObjectProvider<AsyncTaskLogService> nullProvider = mock();
        when(nullProvider.getIfAvailable()).thenReturn(null);

        CoQuartzJobFactory factory = new CoQuartzJobFactory();
        factory.setAsyncTaskLogServiceProvider(nullProvider);
        factory.setBeanFactory(beanFactory);

        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(CoQuartzConstants.ENHANCED, true);
        TriggerFiredBundle bundle = createBundle(jobDataMap);

        Object result = factory.createJobInstance(bundle);
        assertThat(result).isNotInstanceOf(EnhancedJob.class);
    }
}