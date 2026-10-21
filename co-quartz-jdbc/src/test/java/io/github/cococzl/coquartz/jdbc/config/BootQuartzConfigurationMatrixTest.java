package io.github.cococzl.coquartz.jdbc.config;

import io.github.cococzl.coquartz.config.*;
import io.github.cococzl.coquartz.core.CoQuartzJobFactory;
import io.github.cococzl.coquartz.jdbc.job.*;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

class BootQuartzConfigurationMatrixTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, QuartzAutoConfiguration.class,
                    CoQuartzCoreAutoConfiguration.class, CoQuartzJdbcAutoConfiguration.class, DisabledCleanupAutoConfiguration.class))
            .withPropertyValues("spring.quartz.auto-startup=false", "co-quartz.annotation.enabled=false");

    @Configuration(proxyBeanMethods = false)
    static class SeparateDataSources {
        @Bean @Primary DataSource applicationDataSource() { return database(); }
        @Bean @QuartzDataSource DataSource quartzDataSource() { return database(); }
        @Bean DataSource coQuartzLogDataSource() { return database(); }
        private DataSource database() { return new DriverManagerDataSource("jdbc:h2:mem:matrix-" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""); }
    }

    @Test void bootKeepsApplicationQuartzAndLogDatabasesSeparate() {
        runner.withUserConfiguration(SeparateDataSources.class)
                .withPropertyValues("spring.quartz.job-store-type=jdbc", "spring.quartz.jdbc.initialize-schema=always",
                        "co-quartz.log.enabled=true", "co-quartz.log.auto-create-table=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var app = context.getBean("applicationDataSource", DataSource.class);
                    var quartz = context.getBean("quartzDataSource", DataSource.class);
                    var log = context.getBean("coQuartzLogDataSource", DataSource.class);
                    assertThat(context.getBean("coQuartzJdbcTemplate", JdbcTemplate.class).getDataSource()).isSameAs(log);
                    assertThat(tableCount(app, "QRTZ_JOB_DETAILS")).isZero();
                    assertThat(tableCount(app, "QUARTZ_TASK_LOG")).isZero();
                    assertThat(tableCount(quartz, "QRTZ_JOB_DETAILS")).isEqualTo(1);
                    assertThat(tableCount(quartz, "QUARTZ_TASK_LOG")).isZero();
                    assertThat(tableCount(log, "QUARTZ_TASK_LOG")).isEqualTo(1);
                    assertThat(context.getBean(Scheduler.class).isStarted()).isFalse();
                });
    }

    @Test void explicitlyNamedFactoryCustomizerControlsBootScheduler() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var factory = new CoQuartzJobFactory() {
            protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
                calls.incrementAndGet(); return super.createJobInstance(bundle);
            }
        };
        runner.withBean("coQuartzJobFactoryCustomizer", SchedulerFactoryBeanCustomizer.class,
                () -> bean -> bean.setJobFactory(factory)).run(context -> {
            assertThat(context).hasNotFailed();
            var scheduler = context.getBean(Scheduler.class);
            var detail = JobBuilder.newJob(PlainJob.class).withIdentity("custom-factory").build();
            PlainJob.executed = new CountDownLatch(1);
            scheduler.scheduleJob(detail, TriggerBuilder.newTrigger().forJob(detail).build()); scheduler.start();
            assertThat(PlainJob.executed.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(1);
        });
    }

    public static class PlainJob implements Job {
        static CountDownLatch executed;
        public void execute(JobExecutionContext context) { executed.countDown(); }
    }

    @Test void disabledLoggingRemovesAnExistingCleanupJobThroughBootConfiguration() throws Exception {
        var properties = new java.util.Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "disabled-cleanup-" + System.nanoTime());
        properties.setProperty("org.quartz.threadPool.threadCount", "1");
        var scheduler = new org.quartz.impl.StdSchedulerFactory(properties).getScheduler();
        try {
            var enabled = new CoQuartzProperties(); enabled.getLog().setEnabled(true);
            new LogCleanupRegistrar(scheduler, enabled).register();
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CoQuartzCoreAutoConfiguration.class,
                            CoQuartzJdbcAutoConfiguration.class, DisabledCleanupAutoConfiguration.class))
                    .withBean(Scheduler.class, () -> scheduler)
                    .withPropertyValues("co-quartz.log.enabled=false", "co-quartz.annotation.enabled=false")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(scheduler.checkExists(JobKey.jobKey(LogCleanupJob.JOB_NAME, LogCleanupJob.JOB_GROUP))).isFalse();
                        assertThat(context).doesNotHaveBean("coQuartzJdbcTemplate");
                    });
        } finally { if (!scheduler.isShutdown()) scheduler.shutdown(true); }
    }

    private int tableCount(DataSource source, String table) {
        return new JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name=?", Integer.class, table);
    }
}
