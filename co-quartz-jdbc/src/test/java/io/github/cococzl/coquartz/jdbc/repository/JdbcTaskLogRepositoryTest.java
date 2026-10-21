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

import static org.assertj.core.api.Assertions.*;

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
        log.setExecutionId("execution-" + attempt);
        log.setFireInstanceId("fire-" + attempt);
        log.setSchedulerInstanceId("node-a");
        log.setDefinitionVersion("v1");
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
        assertThat(logs.get(0).getExecutionId()).isEqualTo("execution-1");
        assertThat(logs.get(0).getFireInstanceId()).isEqualTo("fire-1");
    }

    @Test
    void batchRollbackIsolatesInvalidRowsAndAcceptsIdenticalDuplicateIds() {
        var first = createLog("batch", LogTaskExecStateEnum.SUCCESS, 1, true);
        var invalid = createLog("batch", LogTaskExecStateEnum.SUCCESS, 1, true);
        invalid.setJobKey(null);
        var last = createLog("batch", LogTaskExecStateEnum.SUCCESS, 1, true);
        assertThat(repository.insertBatch(List.of(first, invalid, last))).containsExactly(invalid);
        assertThat(repository.latestLogs("batch", 10)).hasSize(2);
        assertThat(repository.insertBatch(List.of(first, last))).isEmpty();
        assertThat(repository.latestLogs("batch", 10)).hasSize(2);
        first.setExecutionId("different");
        assertThat(repository.insertBatch(List.of(first))).containsExactly(first);
    }

    @Test
    void reliableAuditLifecycleUpdatesStartedRecordIdempotently() {
        TaskExecutionLog started = createLog("DEFAULT.auditJob", LogTaskExecStateEnum.STARTED, 1, false);
        started.setEndTime(null);
        started.setExecutionTimeMs(null);
        repository.insert(started);

        started.setExecState(LogTaskExecStateEnum.SUCCESS);
        started.setEndTime(LocalDateTime.now());
        started.setExecutionTimeMs(42L);
        started.setFinalAttempt(true);
        repository.updateLifecycle(started);
        repository.updateLifecycle(started);

        List<TaskExecutionLog> logs = repository.latestLogs("DEFAULT.auditJob", 10);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getExecState()).isEqualTo(LogTaskExecStateEnum.SUCCESS);
        assertThat(logs.get(0).getExecutionTimeMs()).isEqualTo(42L);
        assertThat(logs.get(0).isFinalAttempt()).isTrue();
    }

    @Test
    void auditCompletionRejectsMissingRecordAndConflictingTerminalOrIdentity() {
        var missing = createLog("audit", LogTaskExecStateEnum.SUCCESS, 1, true);
        assertThatThrownBy(() -> repository.updateLifecycle(missing)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
        var completed = createLog("audit", LogTaskExecStateEnum.SUCCESS, 1, true);
        repository.insert(completed);
        completed.setExecState(LogTaskExecStateEnum.FAIL);
        assertThatThrownBy(() -> repository.updateLifecycle(completed)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflict");
        var started = createLog("audit", LogTaskExecStateEnum.STARTED, 1, false);
        repository.insert(started);
        started.setExecState(LogTaskExecStateEnum.SUCCESS);
        started.setExecutionId("different-execution");
        assertThatThrownBy(() -> repository.updateLifecycle(started)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity");
        assertThat(repository.findStartedBefore(LocalDateTime.now())).hasSize(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"start", "end", "execute", "originTrigger", "originTime"})
    void duplicateCompletionRejectsChangedTimestampsAndOrigin(String field) {
        var completed = createLog("audit", LogTaskExecStateEnum.SUCCESS, 1, true);
        completed.setOriginTriggerKey("DEFAULT.initial");
        completed.setOriginScheduledTime(1000L);
        repository.insert(completed);
        switch (field) {
            case "start" -> completed.setStartTime(completed.getStartTime().plusDays(1));
            case "end" -> completed.setEndTime(completed.getEndTime().plusDays(1));
            case "execute" -> completed.setExecuteTime(completed.getExecuteTime().plusDays(1));
            case "originTrigger" -> completed.setOriginTriggerKey("DEFAULT.other");
            case "originTime" -> completed.setOriginScheduledTime(2000L);
            default -> throw new AssertionError(field);
        }
        assertThatThrownBy(() -> repository.updateLifecycle(completed)).isInstanceOf(IllegalStateException.class);
        assertThat(repository.insertBatch(List.of(completed))).containsExactly(completed);
    }

    @Test
    void identicalCompletionUsesLegacyTimestampColumnPrecision() {
        for (String column : List.of("start_time", "end_time", "execute_time")) {
            jdbcTemplate.execute("ALTER TABLE quartz_task_log ALTER COLUMN " + column + " TIMESTAMP(0)");
        }
        var completed = createLog("legacy-audit", LogTaskExecStateEnum.SUCCESS, 1, true);
        completed.setStartTime(completed.getStartTime().withNano(999_999_999));
        completed.setEndTime(completed.getEndTime().withNano(999_999_999));
        completed.setExecuteTime(completed.getExecuteTime().withNano(999_999_999));
        repository.insert(completed);
        repository.updateLifecycle(completed);
        assertThat(repository.insertBatch(List.of(completed))).isEmpty();
        completed.setEndTime(completed.getEndTime().plusSeconds(1));
        assertThatThrownBy(() -> repository.updateLifecycle(completed)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflict");
    }

    @Test
    void cleanupKeepsStartedAuditRowsEvenWhenOlderThanRetention() {
        var started = createLog("long-running", LogTaskExecStateEnum.STARTED, 1, false);
        started.setExecuteTime(LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(100));
        started.setStartTime(started.getExecuteTime());
        repository.insert(started);
        var completed = createLog("old-completed", LogTaskExecStateEnum.SUCCESS, 1, true);
        completed.setExecuteTime(started.getExecuteTime()); repository.insert(completed);
        assertThat(repository.cleanup(30)).isEqualTo(1);
        assertThat(repository.latestLogs("long-running", 10)).hasSize(1);
    }

    @Test
    void paginationRejectsInvalidSizeAndOrdersEqualTimestampsById() {
        var query = new TaskLogQuery(); query.setPage(0);
        assertThatThrownBy(() -> repository.pageLogs(query)).isInstanceOf(IllegalArgumentException.class);
        query.setPage(1); query.setSize(1001);
        assertThatThrownBy(() -> repository.pageLogs(query)).isInstanceOf(IllegalArgumentException.class);
        var time = LocalDateTime.of(2026, 1, 1, 0, 0);
        for (String id : List.of("a", "c", "b")) {
            var entry = createLog("ties", LogTaskExecStateEnum.SUCCESS, 1, true);
            entry.setId(id); entry.setStartTime(time); repository.insert(entry);
        }
        query.setSize(1); query.setJobKey("ties");
        assertThat(repository.pageLogs(query).getRecords()).extracting(TaskExecutionLog::getId).containsExactly("c");
        query.setPage(2);
        assertThat(repository.pageLogs(query).getRecords()).extracting(TaskExecutionLog::getId).containsExactly("b");
    }

    @Test
    void startedRecordsCanBeAtomicallyMarkedInterrupted() {
        TaskExecutionLog old = createLog("DEFAULT.oldAudit", LogTaskExecStateEnum.STARTED, 1, false);
        old.setStartTime(LocalDateTime.now().minusMinutes(2));
        old.setEndTime(null);
        old.setExecutionTimeMs(null);
        repository.insert(old);
        TaskExecutionLog recent = createLog("DEFAULT.recentAudit", LogTaskExecStateEnum.STARTED, 1, false);
        recent.setEndTime(null);
        recent.setExecutionTimeMs(null);
        repository.insert(recent);

        List<TaskExecutionLog> stale = repository.findStartedBefore(LocalDateTime.now().minusMinutes(1));
        assertThat(stale).extracting(TaskExecutionLog::getId).containsExactly(old.getId());
        assertThat(repository.markInterrupted(old.getId(), LocalDateTime.now())).isTrue();
        assertThat(repository.markInterrupted(old.getId(), LocalDateTime.now())).isFalse();

        assertThat(repository.latestLogs("DEFAULT.oldAudit", 1).get(0).getExecState())
                .isEqualTo(LogTaskExecStateEnum.INTERRUPTED);
        assertThat(repository.latestLogs("DEFAULT.recentAudit", 1).get(0).getExecState())
                .isEqualTo(LogTaskExecStateEnum.STARTED);
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
    void pageLogs_filtersByExecutionId() {
        repository.insert(createLog("DEFAULT.job1", LogTaskExecStateEnum.SUCCESS, 1, true));
        repository.insert(createLog("DEFAULT.job1", LogTaskExecStateEnum.SUCCESS, 2, true));
        TaskLogQuery query = new TaskLogQuery();
        query.setExecutionId("execution-2");

        PageResult<TaskExecutionLog> page = repository.pageLogs(query);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords().get(0).getExecutionId()).isEqualTo("execution-2");
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
