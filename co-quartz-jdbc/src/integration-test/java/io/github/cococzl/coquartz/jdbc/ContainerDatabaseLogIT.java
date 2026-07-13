package io.github.cococzl.coquartz.jdbc;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;
import io.github.cococzl.coquartz.jdbc.repository.JdbcTaskLogRepository;
import io.github.cococzl.coquartz.jdbc.schema.SchemaInitializer;
import io.github.cococzl.coquartz.jdbc.service.JdbcAsyncTaskLogService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** CI-ready database coverage. The integration-tests profile must fail if Docker is unavailable. */
@Testcontainers
class ContainerDatabaseLogIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Test void mysqlFullLogPath() { verify(MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()); }
    @Test void postgresqlFullLogPath() { verify(POSTGRES.getDriverClassName(), POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()); }

    private void verify(String driver, String url, String username, String password) {
        DriverManagerDataSource source = new DriverManagerDataSource(url, username, password);
        source.setDriverClassName(driver);
        JdbcTemplate jdbc = new JdbcTemplate(source);
        CoQuartzProperties properties = new CoQuartzProperties();
        properties.getLog().setAutoCreateTable(true);
        new SchemaInitializer(jdbc, properties).initialize();
        JdbcTaskLogRepository repository = new JdbcTaskLogRepository(jdbc);
        JdbcAsyncTaskLogService async = new JdbcAsyncTaskLogService(repository, 10, 10, 60_000);
        TaskExecutionLog log = log("DEFAULT.containerAsync", LogTaskExecStateEnum.SUCCESS);
        async.logTaskExecutionAsync(log); async.flushLogsImmediately();
        assertThat(repository.latestLogs(log.getJobKey(), 1)).hasSize(1);
        TaskExecutionLog audit = log("DEFAULT.containerAudit", LogTaskExecStateEnum.STARTED);
        audit.setEndTime(null); audit.setExecutionTimeMs(null); repository.insert(audit);
        audit.setExecState(LogTaskExecStateEnum.SUCCESS); audit.setEndTime(LocalDateTime.now()); audit.setExecutionTimeMs(1L);
        repository.updateLifecycle(audit);
        assertThat(repository.latestLogs(audit.getJobKey(), 1).get(0).getExecState()).isEqualTo(LogTaskExecStateEnum.SUCCESS);
        TaskExecutionLog old = log("DEFAULT.containerCleanup", LogTaskExecStateEnum.SUCCESS);
        old.setExecuteTime(LocalDateTime.now().minusDays(40)); repository.insert(old);
        assertThat(repository.cleanup(30)).isGreaterThanOrEqualTo(1);
        assertThat(repository.pageLogs(new io.github.cococzl.coquartz.dto.TaskLogQuery()).getTotal()).isGreaterThan(0);
        async.shutdown();
    }

    private static TaskExecutionLog log(String jobKey, LogTaskExecStateEnum state) {
        TaskExecutionLog log = new TaskExecutionLog(); log.setId(UUID.randomUUID().toString()); log.setJobKey(jobKey); log.setTriggerKey("DEFAULT.trigger");
        log.setStartTime(LocalDateTime.now()); log.setEndTime(LocalDateTime.now()); log.setExecuteTime(LocalDateTime.now());
        log.setExecutionTimeMs(1L); log.setExecState(state); log.setAttempt(1); log.setFinalAttempt(true); return log;
    }
}
