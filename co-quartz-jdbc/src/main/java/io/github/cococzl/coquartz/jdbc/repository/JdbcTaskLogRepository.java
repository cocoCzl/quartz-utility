package io.github.cococzl.coquartz.jdbc.repository;

import io.github.cococzl.coquartz.dto.PageResult;
import io.github.cococzl.coquartz.dto.TaskExecutionLog;
import io.github.cococzl.coquartz.dto.TaskLogQuery;
import io.github.cococzl.coquartz.dto.TaskStatistics;
import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;
import io.github.cococzl.coquartz.service.TaskLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JdbcTaskLogRepository implements TaskLogRepository {

    private static final String INSERT_SQL = """
            INSERT INTO quartz_task_log (id, job_key, trigger_key, start_time, end_time, execution_time_ms,
                exec_state, error_message, stack_trace, attempt, is_final_attempt, execute_time, execution_id, fire_instance_id, scheduler_instance_id, definition_version, scheduler_name, application_namespace, origin_trigger_key, origin_scheduled_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM quartz_task_log";
    private static final String UPDATE_LIFECYCLE_SQL = """
            UPDATE quartz_task_log SET end_time = ?, execution_time_ms = ?, exec_state = ?, error_message = ?, stack_trace = ?, is_final_attempt = ?
            WHERE id = ? AND exec_state = ?
              AND job_key = ? AND attempt = ?
              AND (execution_id = ? OR (execution_id IS NULL AND ? IS NULL))
              AND (fire_instance_id = ? OR (fire_instance_id IS NULL AND ? IS NULL))
            """;
    private static final String FIND_STARTED_BEFORE_SQL = "SELECT * FROM quartz_task_log WHERE exec_state = ? AND start_time < ?";
    private static final String MARK_INTERRUPTED_SQL = """
            UPDATE quartz_task_log SET end_time = ?, exec_state = ?, is_final_attempt = TRUE
            WHERE id = ? AND exec_state = ?
            """;
    private static final String COUNT_SUCCESS_SQL = "SELECT COUNT(*) FROM quartz_task_log WHERE exec_state = ?";
    private static final String COUNT_FAIL_SQL = "SELECT COUNT(*) FROM quartz_task_log WHERE exec_state = ?";
    private static final String COUNT_BY_JOB_KEY_SQL = "SELECT COUNT(*) FROM quartz_task_log WHERE job_key = ?";
    private static final String COUNT_SUCCESS_BY_JOB_KEY_SQL = "SELECT COUNT(*) FROM quartz_task_log WHERE job_key = ? AND exec_state = ?";
    private static final String COUNT_FAIL_BY_JOB_KEY_SQL = "SELECT COUNT(*) FROM quartz_task_log WHERE job_key = ? AND exec_state = ?";

    private final JdbcTemplate jdbcTemplate;

    private String cachedDialect;
    private volatile Map<String, String> timestampTypes;

    public JdbcTaskLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private String detectDialect() {
        if (cachedDialect != null) {
            return cachedDialect;
        }
        try (java.sql.Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            String dbName = connection.getMetaData().getDatabaseProductName().toLowerCase();
            cachedDialect = dbName;
            return dbName;
        } catch (Exception e) {
            cachedDialect = "h2";
            return "h2";
        }
    }

    private static final RowMapper<TaskExecutionLog> ROW_MAPPER = (rs, rowNum) -> {
        TaskExecutionLog log = new TaskExecutionLog();
        log.setId(rs.getString("id"));
        log.setJobKey(rs.getString("job_key"));
        log.setTriggerKey(rs.getString("trigger_key"));
        log.setExecutionId(rs.getString("execution_id"));
        log.setFireInstanceId(rs.getString("fire_instance_id"));
        log.setSchedulerInstanceId(rs.getString("scheduler_instance_id"));
        log.setDefinitionVersion(rs.getString("definition_version"));
        log.setSchedulerName(rs.getString("scheduler_name"));
        log.setApplicationNamespace(rs.getString("application_namespace"));
        log.setOriginTriggerKey(rs.getString("origin_trigger_key"));
        log.setOriginScheduledTime(rs.getObject("origin_scheduled_time") == null ? null : rs.getLong("origin_scheduled_time"));
        log.setStartTime(rs.getTimestamp("start_time") != null ? rs.getTimestamp("start_time").toLocalDateTime() : null);
        log.setEndTime(rs.getTimestamp("end_time") != null ? rs.getTimestamp("end_time").toLocalDateTime() : null);
        log.setExecutionTimeMs(rs.getObject("execution_time_ms") != null ? rs.getLong("execution_time_ms") : null);
        log.setExecState(LogTaskExecStateEnum.parse(rs.getInt("exec_state")));
        log.setErrorMessage(rs.getString("error_message"));
        log.setStackTrace(rs.getString("stack_trace"));
        log.setAttempt(rs.getInt("attempt"));
        log.setFinalAttempt(rs.getBoolean("is_final_attempt"));
        log.setExecuteTime(rs.getTimestamp("execute_time") != null ? rs.getTimestamp("execute_time").toLocalDateTime() : null);
        return log;
    };

    @Override
    public void insert(TaskExecutionLog log) {
        jdbcTemplate.update(INSERT_SQL,
                log.getId(),
                log.getJobKey(),
                log.getTriggerKey(),
                log.getStartTime(),
                log.getEndTime(),
                log.getExecutionTimeMs(),
                log.getExecState() != null ? log.getExecState().getCode() : LogTaskExecStateEnum.UNKNOWN.getCode(),
                log.getErrorMessage(),
                log.getStackTrace(),
                log.getAttempt(),
                log.isFinalAttempt(),
                log.getExecuteTime(), log.getExecutionId(), log.getFireInstanceId(),
                log.getSchedulerInstanceId(), log.getDefinitionVersion(), log.getSchedulerName(), log.getApplicationNamespace(),
                log.getOriginTriggerKey(), log.getOriginScheduledTime()
        );
    }

    private Object[] insertArguments(TaskExecutionLog entry) {
        return new Object[]{entry.getId(), entry.getJobKey(), entry.getTriggerKey(), entry.getStartTime(),
                entry.getEndTime(), entry.getExecutionTimeMs(), entry.getExecState() == null
                ? LogTaskExecStateEnum.UNKNOWN.getCode() : entry.getExecState().getCode(), entry.getErrorMessage(),
                entry.getStackTrace(), entry.getAttempt(), entry.isFinalAttempt(), entry.getExecuteTime(),
                entry.getExecutionId(), entry.getFireInstanceId(), entry.getSchedulerInstanceId(), entry.getDefinitionVersion(),
                entry.getSchedulerName(), entry.getApplicationNamespace(), entry.getOriginTriggerKey(), entry.getOriginScheduledTime()};
    }

    @Override
    public List<TaskExecutionLog> insertBatch(List<TaskExecutionLog> entries) {
        if (entries.isEmpty()) return List.of();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbcTemplate.getDataSource()));
        try {
            transaction.executeWithoutResult(status -> jdbcTemplate.batchUpdate(INSERT_SQL,
                    entries.stream().map(this::insertArguments).toList()));
            return List.of();
        } catch (RuntimeException batchFailure) {
            // Transaction rollback removes partial batch effects; retry rows individually to isolate invalid entries.
            List<TaskExecutionLog> failed = new java.util.ArrayList<>();
            for (TaskExecutionLog entry : entries) {
                try { insert(entry); }
                catch (org.springframework.dao.DuplicateKeyException duplicate) {
                    try { assertSameCompletion(lifecycleRecord(entry.getId()), entry); }
                    catch (RuntimeException conflict) { failed.add(entry); }
                } catch (RuntimeException failure) { failed.add(entry); }
            }
            return failed;
        }
    }

    @Override
    public void updateLifecycle(TaskExecutionLog log) {
        if (log.getExecState() == null || log.getExecState() == LogTaskExecStateEnum.STARTED
                || log.getExecState() == LogTaskExecStateEnum.UNKNOWN || log.getEndTime() == null) {
            throw new IllegalArgumentException("Audit completion requires a terminal result and end time");
        }
        TaskExecutionLog stored = lifecycleRecord(log.getId());
        if (!sameIdentity(stored, log)) throw new IllegalStateException("Audit execution identity mismatch: " + log.getId());
        if (stored.getExecState() != LogTaskExecStateEnum.STARTED) {
            assertSameCompletion(stored, log);
            return;
        }
        int affected = jdbcTemplate.update(UPDATE_LIFECYCLE_SQL, log.getEndTime(), log.getExecutionTimeMs(),
                log.getExecState().getCode(), log.getErrorMessage(), log.getStackTrace(), log.isFinalAttempt(),
                log.getId(), LogTaskExecStateEnum.STARTED.getCode(), log.getJobKey(), log.getAttempt(),
                log.getExecutionId(), new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.VARCHAR, log.getExecutionId()),
                log.getFireInstanceId(), new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.VARCHAR, log.getFireInstanceId()));
        if (affected != 1) assertSameCompletion(lifecycleRecord(log.getId()), log);
    }

    private TaskExecutionLog lifecycleRecord(String id) {
        var records = jdbcTemplate.query("SELECT * FROM quartz_task_log WHERE id = ?", ROW_MAPPER, id);
        if (records.isEmpty()) throw new IllegalStateException("Audit record missing: " + id);
        return records.get(0);
    }

    private boolean sameIdentity(TaskExecutionLog first, TaskExecutionLog second) {
        return java.util.Objects.equals(first.getJobKey(), second.getJobKey())
                && java.util.Objects.equals(first.getTriggerKey(), second.getTriggerKey())
                && java.util.Objects.equals(first.getExecutionId(), second.getExecutionId())
                && java.util.Objects.equals(first.getFireInstanceId(), second.getFireInstanceId())
                && java.util.Objects.equals(first.getSchedulerInstanceId(), second.getSchedulerInstanceId())
                && java.util.Objects.equals(first.getDefinitionVersion(), second.getDefinitionVersion())
                && java.util.Objects.equals(first.getSchedulerName(), second.getSchedulerName())
                && java.util.Objects.equals(first.getApplicationNamespace(), second.getApplicationNamespace())
                && java.util.Objects.equals(first.getOriginTriggerKey(), second.getOriginTriggerKey())
                && java.util.Objects.equals(first.getOriginScheduledTime(), second.getOriginScheduledTime())
                && first.getAttempt() == second.getAttempt();
    }

    private void assertSameCompletion(TaskExecutionLog stored, TaskExecutionLog desired) {
        if (!sameIdentity(stored, desired) || stored.getExecState() != desired.getExecState()
                || stored.isFinalAttempt() != desired.isFinalAttempt()
                || !java.util.Objects.equals(stored.getExecutionTimeMs(), desired.getExecutionTimeMs())
                || !java.util.Objects.equals(stored.getErrorMessage(), desired.getErrorMessage())
                || !java.util.Objects.equals(stored.getStackTrace(), desired.getStackTrace())
                || !sameStoredTimestamps(stored, desired)) {
            throw new IllegalStateException("Audit terminal result conflict: " + desired.getId());
        }
    }

    private boolean sameStoredTimestamps(TaskExecutionLog stored, TaskExecutionLog desired) {
        Map<String, String> types = timestampTypes();
        // Let the database apply its actual column precision, including legacy DATETIME(0).
        // Comparing Java nanoseconds directly would reject an identical completion after JDBC rounding.
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT CAST(? AS " + types.get("start_time")
                        + "), CAST(? AS " + types.get("end_time") + "), CAST(? AS " + types.get("execute_time") + ")",
                (rs, row) -> java.util.Objects.equals(rs.getObject(1, LocalDateTime.class), stored.getStartTime())
                        && java.util.Objects.equals(rs.getObject(2, LocalDateTime.class), stored.getEndTime())
                        && java.util.Objects.equals(rs.getObject(3, LocalDateTime.class), stored.getExecuteTime()),
                desired.getStartTime(), desired.getEndTime(), desired.getExecuteTime()));
    }

    private Map<String, String> timestampTypes() {
        Map<String, String> cached = timestampTypes;
        if (cached != null) return cached;
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            var metadata = connection.getMetaData();
            boolean mysql = metadata.getDatabaseProductName().toLowerCase(java.util.Locale.ROOT).contains("mysql");
            String type = mysql ? "DATETIME" : "TIMESTAMP";
            Map<String, String> found = new HashMap<>();
            if (mysql) {
                // Connector/J getColumns may report NULL DECIMAL_DIGITS for DATETIME.
                try (var statement = connection.prepareStatement("SELECT COLUMN_NAME, DATETIME_PRECISION FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quartz_task_log' "
                        + "AND COLUMN_NAME IN ('start_time','end_time','execute_time')"); var columns = statement.executeQuery()) {
                    while (columns.next()) {
                        int precision = columns.getInt(2);
                        if (columns.wasNull() || precision < 0 || precision > 6) throw new IllegalStateException("Invalid audit timestamp precision");
                        found.put(columns.getString(1).toLowerCase(java.util.Locale.ROOT), type + "(" + precision + ")");
                    }
                }
            }
            for (String table : mysql ? List.<String>of() : List.of("quartz_task_log", "QUARTZ_TASK_LOG")) {
                try (var columns = metadata.getColumns(connection.getCatalog(), connection.getSchema(), table, null)) {
                    while (columns.next()) {
                        String name = columns.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT);
                        if (!List.of("start_time", "end_time", "execute_time").contains(name)) continue;
                        int precision = columns.getInt("DECIMAL_DIGITS");
                        if (columns.wasNull() || precision < 0 || precision > 9) {
                            throw new IllegalStateException("Cannot determine audit timestamp precision: " + name);
                        }
                        found.put(name, type + "(" + precision + ")");
                    }
                }
                if (found.size() == 3) break;
            }
            if (found.size() != 3) throw new IllegalStateException("Audit timestamp columns missing");
            timestampTypes = Map.copyOf(found);
            return timestampTypes;
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("Cannot inspect audit timestamp precision", failure);
        }
    }

    @Override
    public List<TaskExecutionLog> findStartedBefore(LocalDateTime cutoff) {
        return jdbcTemplate.query(FIND_STARTED_BEFORE_SQL, ROW_MAPPER, LogTaskExecStateEnum.STARTED.getCode(), cutoff);
    }

    @Override
    public boolean markInterrupted(String id, LocalDateTime endTime) {
        return jdbcTemplate.update(MARK_INTERRUPTED_SQL, endTime, LogTaskExecStateEnum.INTERRUPTED.getCode(),
                id, LogTaskExecStateEnum.STARTED.getCode()) == 1;
    }

    @Override
    public long countStarted() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quartz_task_log WHERE exec_state = ?", Long.class,
                LogTaskExecStateEnum.STARTED.getCode());
        return count == null ? 0 : count;
    }

    @Override
    public PageResult<TaskExecutionLog> pageLogs(TaskLogQuery query) {
        if (query.getPage() < 1 || query.getSize() < 1 || query.getSize() > 1000) {
            throw new IllegalArgumentException("Page must be positive and size must be 1..1000");
        }
        if (query.getStartTime() != null && query.getEndTime() != null && query.getStartTime().isAfter(query.getEndTime())) {
            throw new IllegalArgumentException("Invalid log time window");
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM quartz_task_log WHERE 1=1");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM quartz_task_log WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();

        if (query.getJobKey() != null && !query.getJobKey().isEmpty()) {
            sql.append(" AND job_key = ?");
            countSql.append(" AND job_key = ?");
            params.add(query.getJobKey());
        }
        if (query.getExecutionId() != null && !query.getExecutionId().isEmpty()) {
            sql.append(" AND execution_id = ?");
            countSql.append(" AND execution_id = ?");
            params.add(query.getExecutionId());
        }
        if (query.getExecState() != null) {
            sql.append(" AND exec_state = ?");
            countSql.append(" AND exec_state = ?");
            params.add(query.getExecState().getCode());
        }
        if (query.getStartTime() != null) {
            sql.append(" AND start_time >= ?");
            countSql.append(" AND start_time >= ?");
            params.add(query.getStartTime());
        }
        if (query.getEndTime() != null) {
            sql.append(" AND start_time <= ?");
            countSql.append(" AND start_time <= ?");
            params.add(query.getEndTime());
        }

        long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, params.toArray());

        sql.append(" ORDER BY start_time DESC, id DESC");
        long offset = ((long) query.getPage() - 1) * query.getSize();
        sql.append(" LIMIT ? OFFSET ?");

        List<Object> pageParams = new java.util.ArrayList<>(params);
        pageParams.add(query.getSize());
        pageParams.add(offset);

        List<TaskExecutionLog> records = jdbcTemplate.query(sql.toString(), ROW_MAPPER, pageParams.toArray());
        return new PageResult<>(records, total, query.getPage(), query.getSize());
    }

    @Override
    public List<TaskExecutionLog> latestLogs(String jobKey, int limit) {
        String sql = "SELECT * FROM quartz_task_log WHERE job_key = ? ORDER BY start_time DESC, id DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, jobKey, limit);
    }

    @Override
    public List<TaskExecutionLog> failedLogs(int limit) {
        String sql = "SELECT * FROM quartz_task_log WHERE exec_state = ? ORDER BY start_time DESC, id DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, LogTaskExecStateEnum.FAIL.getCode(), limit);
    }

    @Override
    public TaskStatistics statistics() {
        long total = jdbcTemplate.queryForObject(COUNT_SQL, Long.class);
        long success = jdbcTemplate.queryForObject(COUNT_SUCCESS_SQL, Long.class, LogTaskExecStateEnum.SUCCESS.getCode());
        long failed = jdbcTemplate.queryForObject(COUNT_FAIL_SQL, Long.class, LogTaskExecStateEnum.FAIL.getCode());
        return new TaskStatistics(total, success, failed);
    }

    @Override
    public TaskStatistics statistics(String jobKey) {
        long total = jdbcTemplate.queryForObject(COUNT_BY_JOB_KEY_SQL, Long.class, jobKey);
        long success = jdbcTemplate.queryForObject(COUNT_SUCCESS_BY_JOB_KEY_SQL, Long.class, jobKey, LogTaskExecStateEnum.SUCCESS.getCode());
        long failed = jdbcTemplate.queryForObject(COUNT_FAIL_BY_JOB_KEY_SQL, Long.class, jobKey, LogTaskExecStateEnum.FAIL.getCode());
        return new TaskStatistics(total, success, failed);
    }

    @Override
    public int cleanup(int daysToKeep) {
        LocalDateTime cutoff = java.time.Instant.now().minus(java.time.Duration.ofDays(daysToKeep))
                .atZone(java.time.ZoneOffset.UTC).toLocalDateTime();
        if (daysToKeep < 1) throw new IllegalArgumentException("Retention days must be positive");
        List<String> ids = jdbcTemplate.query("SELECT id FROM quartz_task_log WHERE execute_time < ? AND exec_state IN (?, ?, ?) ORDER BY execute_time, id LIMIT 1000",
                (rs, row) -> rs.getString(1), cutoff, LogTaskExecStateEnum.SUCCESS.getCode(),
                LogTaskExecStateEnum.FAIL.getCode(), LogTaskExecStateEnum.INTERRUPTED.getCode());
        if (ids.isEmpty()) return 0;
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Object> arguments = new java.util.ArrayList<>(ids);
        arguments.add(LogTaskExecStateEnum.STARTED.getCode());
        return jdbcTemplate.update("DELETE FROM quartz_task_log WHERE id IN (" + placeholders + ") AND exec_state <> ?", arguments.toArray());
    }

    @Override
    public List<TaskExecutionLog> findRecentByJobKey(String jobKey, int limit) {
        String sql = "SELECT * FROM quartz_task_log WHERE job_key = ? ORDER BY start_time DESC, id DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, jobKey, limit);
    }

    @Override
    public List<TaskExecutionLog> findByTimeRange(LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT * FROM quartz_task_log WHERE start_time >= ? AND start_time <= ? ORDER BY start_time DESC, id DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER, start, end);
    }

    @Override
    public Map<String, Double> avgExecutionTimeByJob() {
        String sql = "SELECT job_key, AVG(execution_time_ms) as avg_time FROM quartz_task_log WHERE execution_time_ms IS NOT NULL GROUP BY job_key";
        Map<String, Double> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            result.put(rs.getString("job_key"), rs.getDouble("avg_time"));
        });
        return result;
    }
}
