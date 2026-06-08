package io.github.cococzl.coquartz.jdbc.repository;

import io.github.cococzl.coquartz.dto.PageResult;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.dto.TaskLogQuery;
import io.github.cococzl.coquartz.dto.TaskStatistics;
import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;
import io.github.cococzl.coquartz.jdbc.schema.SchemaInitializer;
import io.github.cococzl.coquartz.config.CoQuartzProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcTaskLogRepositoryTest {

    private JdbcTaskLogRepository repository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb_" + System.nanoTime())
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        CoQuartzProperties properties = new CoQuartzProperties();
        properties.getLog().setAutoCreateTable(true);
        SchemaInitializer schemaInitializer = new SchemaInitializer(jdbcTemplate, properties);
        schemaInitializer.initialize();
        repository = new JdbcTaskLogRepository(jdbcTemplate);
    }

    private TaskExecutionLog createLog(String jobKey, LogTaskExecStateEnum state, int attempt, boolean isFinalAttempt) {
        TaskExecutionLog log = new TaskExecutionLog();
        log.setId(UUID.randomUUID().toString());
        log.setJobKey(jobKey);
        log.setTriggerKey("DEFAULT.TRIGGER_" + jobKey);
        log.setStartTime(LocalDateTime.now());
        log.setEndTime(LocalDateTime.now());
        log.setExecutionTimeMs(100L);
        log.setExecState(state);
        log.setAttempt(attempt);
        log.setFinalAttempt(isFinalAttempt);
        log.setExecuteTime(LocalDateTime.now());
        if (state == LogTaskExecStateEnum.FAIL) {
            log.setErrorMessage("test error");
            log.setStackTrace("test stack trace");
        }
        return log;
    }

    @Test
    void insert_andRetrieve() {
        TaskExecutionLog log = createLog("DEFAULT.testJob", LogTaskExecStateEnum.SUCCESS, 1, true);
        repository.insert(log);

        List<TaskExecutionLog> logs = repository.latestLogs("DEFAULT.testJob", 10);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getJobKey()).isEqualTo("DEFAULT.testJob");
        assertThat(logs.get(0).getExecState()).isEqualTo(LogTaskExecStateEnum.SUCCESS);
        assertThat(logs.get(0).getAttempt()).isEqualTo(1);
        assertThat(logs.get(0).isFinalAttempt()).isTrue();
    }

    @Test
    void insert_multipleLogs_latestLogsReturnsOrdered() {
        repository.insert(createLog("DEFAULT.job1", LogTaskExecStateEnum.SUCCESS, 1, true));
        repository.insert(createLog("DEFAULT.job1", LogTaskExecStateEnum.FAIL, 2, false));
        repository.insert(createLog("DEFAULT.job2", LogTaskExecStateEnum.SUCCESS, 1, true));

        List<TaskExecutionLog> logs = repository.latestLogs("DEFAULT.job1", 10);
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getAttempt()).isEqualTo(2);
        assertThat(logs.get(1).getAttempt()).isEqualTo(1);
    }

    @Test
    void failedLogs_returnsOnlyFailures() {
        repository.insert(createLog("DEFAULT.job1", LogTaskExecStateEnum.SUCCESS, 1, true));
        repository.insert(createLog("DEFAULT.job2", LogTaskExecStateEnum.FAIL, 1, false));
        repository.insert(createLog("DEFAULT.job3", LogTaskExecStateEnum.FAIL, 1, true));

        List<TaskExecutionLog> logs = repository.failedLogs(10);
        assertThat(logs).hasSize(2);
        assertThat(logs).allMatch(l -> l.getExecState() == LogTaskExecStateEnum.FAIL);
    }

    @Test
    void statistics_returnsCorrectCounts() {
        repository.insert(createLog("DEFAULT.job1", LogTaskExecStateEnum.SUCCESS, 1, true));
        repository.insert(createLog("DEFAULT.job1", LogTaskExecStateEnum.FAIL, 2, true));
        repository.insert(createLog("DEFAULT.job2", LogTaskExecStateEnum.SUCCESS, 1, true));

        TaskStatistics stats = repository.statistics();
        assertThat(stats.getTotalExecutions()).isEqualTo(3);
        assertThat(stats.getSuccessfulExecutions()).isEqualTo(2);
        assertThat(stats.getFailedExecutions()).isEqualTo(1);
    }

    @Test
    void statistics_byJobKey() {
        repository.insert(createLog("DEFAULT.job1", LogTaskExecStateEnum.SUCCESS, 1, true));
        repository.insert(createLog("DEFAULT.job1", LogTaskExecStateEnum.FAIL, 2, true));
        repository.insert(createLog("DEFAULT.job2", LogTaskExecStateEnum.SUCCESS, 1, true));

        TaskStatistics stats = repository.statistics("DEFAULT.job1");
        assertThat(stats.getTotalExecutions()).isEqualTo(2);
        assertThat(stats.getSuccessfulExecutions()).isEqualTo(1);
        assertThat(stats.getFailedExecutions()).isEqualTo(1);
    }

    @Test
    void pageLogs_returnsPaginatedResults() {
        for (int i = 0; i < 5; i++) {
            repository.insert(createLog("DEFAULT.job1", LogTaskExecStateEnum.SUCCESS, i + 1, i == 4));
        }

        TaskLogQuery query = new TaskLogQuery();
        query.setJobKey("DEFAULT.job1");
        query.setPage(1);
        query.setSize(3);

        PageResult<TaskExecutionLog> page = repository.pageLogs(query);
        assertThat(page.getRecords()).hasSize(3);
        assertThat(page.getTotal()).isEqualTo(5);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(3);
    }

    @Test
    void cleanup_deletesOldLogs() {
        TaskExecutionLog log = createLog("DEFAULT.oldJob", LogTaskExecStateEnum.SUCCESS, 1, true);
        log.setExecuteTime(LocalDateTime.now().minusDays(60));
        repository.insert(log);

        TaskExecutionLog recentLog = createLog("DEFAULT.recentJob", LogTaskExecStateEnum.SUCCESS, 1, true);
        recentLog.setExecuteTime(LocalDateTime.now());
        repository.insert(recentLog);

        int deleted = repository.cleanup(30);
        assertThat(deleted).isEqualTo(1);

        List<TaskExecutionLog> remaining = repository.latestLogs("DEFAULT.recentJob", 10);
        assertThat(remaining).hasSize(1);
    }

    @Test
    void findRecentByJobKey_returnsLatestLogs() {
        for (int i = 0; i < 5; i++) {
            repository.insert(createLog("DEFAULT.job1", LogTaskExecStateEnum.SUCCESS, i + 1, i == 4));
        }

        List<TaskExecutionLog> logs = repository.findRecentByJobKey("DEFAULT.job1", 3);
        assertThat(logs).hasSize(3);
    }

    @Test
    void findByTimeRange_returnsLogsInRange() {
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 12, 31, 23, 59);

        TaskExecutionLog log1 = createLog("DEFAULT.job1", LogTaskExecStateEnum.SUCCESS, 1, true);
        log1.setStartTime(LocalDateTime.of(2025, 6, 15, 12, 0));
        repository.insert(log1);

        TaskExecutionLog log2 = createLog("DEFAULT.job2", LogTaskExecStateEnum.SUCCESS, 1, true);
        log2.setStartTime(LocalDateTime.of(2024, 6, 15, 12, 0));
        repository.insert(log2);

        List<TaskExecutionLog> logs = repository.findByTimeRange(start, end);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getJobKey()).isEqualTo("DEFAULT.job1");
    }

    @Test
    void avgExecutionTimeByJob() {
        TaskExecutionLog log1 = createLog("DEFAULT.job1", LogTaskExecStateEnum.SUCCESS, 1, true);
        log1.setExecutionTimeMs(100L);
        repository.insert(log1);

        TaskExecutionLog log2 = createLog("DEFAULT.job1", LogTaskExecStateEnum.SUCCESS, 2, true);
        log2.setExecutionTimeMs(200L);
        repository.insert(log2);

        Map<String, Double> avgTimes = repository.avgExecutionTimeByJob();
        assertThat(avgTimes).containsKey("DEFAULT.job1");
        assertThat(avgTimes.get("DEFAULT.job1")).isBetween(149.0, 151.0);
    }
}