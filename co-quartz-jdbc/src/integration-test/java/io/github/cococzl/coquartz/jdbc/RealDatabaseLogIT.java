package io.github.cococzl.coquartz.jdbc;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.dto.TaskLogQuery;
import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;
import io.github.cococzl.coquartz.jdbc.repository.JdbcTaskLogRepository;
import io.github.cococzl.coquartz.jdbc.schema.SchemaInitializer;
import io.github.cococzl.coquartz.jdbc.service.JdbcAsyncTaskLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in end-to-end verification against operator-provided MySQL and PostgreSQL instances. */
class RealDatabaseLogIT {
    private static final String MYSQL_DB = "co_quartz_task15_it";
    private static final String POSTGRES_SCHEMA = "co_quartz_task15_it";

    @Test
    @EnabledIfSystemProperty(named = "co.quartz.test.mysql.url", matches = ".+")
    void mysqlMigrationAsyncAuditQueryAndCleanup() throws Exception {
        String rootUrl = System.getProperty("co.quartz.test.mysql.url");
        String user = System.getProperty("co.quartz.test.mysql.user");
        String password = System.getProperty("co.quartz.test.mysql.password");
        try (var connection = DriverManager.getConnection(rootUrl, user, password); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + MYSQL_DB);
        }
        verify("com.mysql.cj.jdbc.Driver", databaseUrl(rootUrl, MYSQL_DB), user, password);
    }

    @Test
    @EnabledIfSystemProperty(named = "co.quartz.test.postgresql.url", matches = ".+")
    void postgresqlMigrationAsyncAuditQueryAndCleanup() throws Exception {
        String rootUrl = System.getProperty("co.quartz.test.postgresql.url");
        String user = System.getProperty("co.quartz.test.postgresql.user");
        String password = System.getProperty("co.quartz.test.postgresql.password");
        try (var connection = DriverManager.getConnection(rootUrl, user, password); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + POSTGRES_SCHEMA);
        }
        String separator = rootUrl.contains("?") ? "&" : "?";
        verify("org.postgresql.Driver", rootUrl + separator + "currentSchema=" + POSTGRES_SCHEMA, user, password);
    }

    private void verify(String driver, String url, String user, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, user, password);
        dataSource.setDriverClassName(driver);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        CoQuartzProperties properties = new CoQuartzProperties();
        properties.getLog().setAutoCreateTable(true);
        new SchemaInitializer(jdbc, properties).initialize();
        JdbcTaskLogRepository repository = new JdbcTaskLogRepository(jdbc);
        JdbcAsyncTaskLogService async = new JdbcAsyncTaskLogService(repository, 10, 10, 60_000);

        TaskExecutionLog asyncLog = log("DEFAULT.realAsync", LogTaskExecStateEnum.SUCCESS);
        async.logTaskExecutionAsync(asyncLog);
        async.flushLogsImmediately();
        assertThat(repository.pageLogs(query("DEFAULT.realAsync")).getTotal()).isGreaterThanOrEqualTo(1);

        TaskExecutionLog audit = log("DEFAULT.realAudit", LogTaskExecStateEnum.STARTED);
        audit.setEndTime(null);
        audit.setExecutionTimeMs(null);
        repository.insert(audit);
        audit.setExecState(LogTaskExecStateEnum.SUCCESS);
        audit.setEndTime(LocalDateTime.now());
        audit.setExecutionTimeMs(5L);
        repository.updateLifecycle(audit);
        assertThat(repository.latestLogs("DEFAULT.realAudit", 1).get(0).getExecState())
                .isEqualTo(LogTaskExecStateEnum.SUCCESS);

        TaskExecutionLog old = log("DEFAULT.realCleanup", LogTaskExecStateEnum.SUCCESS);
        old.setExecuteTime(LocalDateTime.now().minusDays(40));
        repository.insert(old);
        assertThat(repository.cleanup(30)).isGreaterThanOrEqualTo(1);
        async.shutdown();
    }

    private static TaskExecutionLog log(String jobKey, LogTaskExecStateEnum state) {
        TaskExecutionLog log = new TaskExecutionLog();
        log.setId(UUID.randomUUID().toString());
        log.setJobKey(jobKey); log.setTriggerKey("DEFAULT.trigger");
        log.setStartTime(LocalDateTime.now()); log.setEndTime(LocalDateTime.now()); log.setExecuteTime(LocalDateTime.now());
        log.setExecutionTimeMs(1L); log.setExecState(state); log.setAttempt(1); log.setFinalAttempt(true);
        return log;
    }

    private static TaskLogQuery query(String jobKey) { TaskLogQuery query = new TaskLogQuery(); query.setJobKey(jobKey); return query; }
    private static String databaseUrl(String root, String database) {
        String base = root.endsWith("/") ? root + database : root + "/" + database;
        return base + (base.contains("?") ? "&" : "?") + "useSSL=false&allowPublicKeyRetrieval=true";
    }
}
