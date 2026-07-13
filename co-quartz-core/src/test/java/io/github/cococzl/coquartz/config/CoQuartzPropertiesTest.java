package io.github.cococzl.coquartz.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CoQuartzPropertiesTest {

    @Test
    void defaultLogConfig() {
        CoQuartzProperties properties = new CoQuartzProperties();
        CoQuartzProperties.LogConfig log = properties.getLog();

        assertThat(log.isEnabled()).isTrue();
        assertThat(log.getRetentionDays()).isEqualTo(30);
        assertThat(log.getCleanupCron()).isEqualTo("0 0 2 * * ?");
        assertThat(log.isAutoCreateTable()).isFalse();
    }

    @Test
    void defaultAsyncConfig() {
        CoQuartzProperties properties = new CoQuartzProperties();
        CoQuartzProperties.AsyncConfig async = properties.getAsync();

        assertThat(async.isEnabled()).isTrue();
        assertThat(async.getLogQueueCapacity()).isEqualTo(1000);
        assertThat(async.getLogBatchSize()).isEqualTo(100);
        assertThat(async.getLogFlushIntervalMs()).isEqualTo(1000);
        assertThat(async.getShutdownFlushTimeoutMs()).isEqualTo(10000);
    }

    @Test
    void defaultMonitoringConfig() {
        CoQuartzProperties properties = new CoQuartzProperties();
        CoQuartzProperties.MonitoringConfig monitoring = properties.getMonitoring();

        assertThat(monitoring.isEnabled()).isTrue();
        assertThat(monitoring.getSlowTaskThresholdMs()).isEqualTo(30000);
        assertThat(monitoring.getConsecutiveFailureThreshold()).isEqualTo(3);
    }

    @Test
    void defaultTimeoutPoolConfig() {
        CoQuartzProperties properties = new CoQuartzProperties();
        CoQuartzProperties.TimeoutPoolConfig pool = properties.getTimeoutPool();

        assertThat(pool.getCoreSize()).isEqualTo(2);
        assertThat(pool.getMaxSize()).isEqualTo(5);
        assertThat(pool.getShutdownAwaitMs()).isEqualTo(10000);
    }

    @Test
    void defaultAnnotationConfig() {
        CoQuartzProperties properties = new CoQuartzProperties();
        CoQuartzProperties.AnnotationConfig annotation = properties.getAnnotation();

        assertThat(annotation.isEnabled()).isTrue();
    }

    @Test
    void defaultSchedulingConfig() {
        CoQuartzProperties properties = new CoQuartzProperties();

        assertThat(properties.getScheduling().getDefaultTimeZone()).isEqualTo("UTC");
    }

    @Test
    void settersModifyValues() {
        CoQuartzProperties properties = new CoQuartzProperties();
        properties.getLog().setRetentionDays(7);
        properties.getLog().setEnabled(false);
        properties.getMonitoring().setConsecutiveFailureThreshold(5);
        properties.getTimeoutPool().setCoreSize(4);

        assertThat(properties.getLog().getRetentionDays()).isEqualTo(7);
        assertThat(properties.getLog().isEnabled()).isFalse();
        assertThat(properties.getMonitoring().getConsecutiveFailureThreshold()).isEqualTo(5);
        assertThat(properties.getTimeoutPool().getCoreSize()).isEqualTo(4);
    }
}
