package io.github.cococzl.coquartz.jdbc.config;

import io.github.cococzl.coquartz.jdbc.service.JdbcAsyncTaskLogService;
import io.github.cococzl.coquartz.jdbc.service.JdbcSynchronousTaskLogWriter;
import io.github.cococzl.coquartz.service.TaskExecutionLogWriter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class CoQuartzJdbcAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CoQuartzJdbcAutoConfiguration.class))
            .withBean(DataSource.class,
                    () -> new DriverManagerDataSource("jdbc:h2:mem:co_quartz_auto_config;DB_CLOSE_DELAY=-1"))
            .withPropertyValues("co-quartz.log.enabled=true");

    @Test
    void asyncDisabledSelectsSynchronousWriter() {
        contextRunner
                .withPropertyValues("co-quartz.async.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(TaskExecutionLogWriter.class);
                    assertThat(context).hasSingleBean(JdbcSynchronousTaskLogWriter.class);
                    assertThat(context).doesNotHaveBean(JdbcAsyncTaskLogService.class);
                });
    }

    @Test
    void asyncEnabledSelectsQueuedWriter() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TaskExecutionLogWriter.class);
            assertThat(context).hasSingleBean(JdbcAsyncTaskLogService.class);
            assertThat(context).doesNotHaveBean(JdbcSynchronousTaskLogWriter.class);
        });
    }

    @Test
    void customWriterMakesJdbcWritersBackOff() {
        TaskExecutionLogWriter customWriter = log -> { };

        contextRunner
                .withBean("customTaskExecutionLogWriter", TaskExecutionLogWriter.class, () -> customWriter)
                .run(context -> {
                    assertThat(context).getBean(TaskExecutionLogWriter.class).isSameAs(customWriter);
                    assertThat(context).doesNotHaveBean(JdbcAsyncTaskLogService.class);
                    assertThat(context).doesNotHaveBean(JdbcSynchronousTaskLogWriter.class);
                });
    }
}
