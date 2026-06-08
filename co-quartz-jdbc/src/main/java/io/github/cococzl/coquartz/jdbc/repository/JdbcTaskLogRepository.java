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
                exec_state, error_message, stack_trace, attempt, is_final_attempt, execute_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM quartz_task_log";
    private static final String COUNT_SUCCESS_SQL = "SELECT COUNT(*) FROM quartz_task_log WHERE exec_state = ?";
    private static final String COUNT_FAIL_SQL = "SELECT COUNT(*) FROM quartz_task_log WHERE exec_state = ?";
    private static final String COUNT_BY_JOB_KEY_SQL = "SELECT COUNT(*) FROM quartz_task_log WHERE job_key = ?";
    private static final String COUNT_SUCCESS_BY_JOB_KEY_SQL = "SELECT COUNT(*) FROM quartz_task_log WHERE job_key = ? AND exec_state = ?";
    private static final String COUNT_FAIL_BY_JOB_KEY_SQL = "SELECT COUNT(*) FROM quartz_task_log WHERE job_key = ? AND exec_state = ?";

    private final JdbcTemplate jdbcTemplate;

    private String cachedDialect;

    public JdbcTaskLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private String detectDialect() {
        if (cachedDialect != null) {
            return cachedDialect;
        }
        try {
            String dbName = jdbcTemplate.getDataSource().getConnection().getMetaData().getDatabaseProductName().toLowerCase();
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
                log.getExecuteTime()
        );
    }

    @Override
    public PageResult<TaskExecutionLog> pageLogs(TaskLogQuery query) {
        StringBuilder sql = new StringBuilder("SELECT * FROM quartz_task_log WHERE 1=1");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM quartz_task_log WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();

        if (query.getJobKey() != null && !query.getJobKey().isEmpty()) {
            sql.append(" AND job_key = ?");
            countSql.append(" AND job_key = ?");
            params.add(query.getJobKey());
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

        sql.append(" ORDER BY start_time DESC");
        int offset = (query.getPage() - 1) * query.getSize();
        sql.append(" LIMIT ? OFFSET ?");

        List<Object> pageParams = new java.util.ArrayList<>(params);
        pageParams.add(query.getSize());
        pageParams.add(offset);

        List<TaskExecutionLog> records = jdbcTemplate.query(sql.toString(), ROW_MAPPER, pageParams.toArray());
        return new PageResult<>(records, total, query.getPage(), query.getSize());
    }

    @Override
    public List<TaskExecutionLog> latestLogs(String jobKey, int limit) {
        String sql = "SELECT * FROM quartz_task_log WHERE job_key = ? ORDER BY start_time DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, jobKey, limit);
    }

    @Override
    public List<TaskExecutionLog> failedLogs(int limit) {
        String sql = "SELECT * FROM quartz_task_log WHERE exec_state = ? ORDER BY start_time DESC LIMIT ?";
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
        String dialect = detectDialect();
        String sql;
        if (dialect.contains("postgresql")) {
            sql = "DELETE FROM quartz_task_log WHERE execute_time < CURRENT_TIMESTAMP - INTERVAL '" + daysToKeep + " days'";
        } else if (dialect.contains("mysql")) {
            sql = "DELETE FROM quartz_task_log WHERE execute_time < DATE_SUB(NOW(), INTERVAL ? DAY)";
            return jdbcTemplate.update(sql, daysToKeep);
        } else {
            sql = "DELETE FROM quartz_task_log WHERE execute_time < DATEADD(DAY, -?, CURRENT_TIMESTAMP)";
            return jdbcTemplate.update(sql, daysToKeep);
        }
        return jdbcTemplate.update(sql);
    }

    @Override
    public List<TaskExecutionLog> findRecentByJobKey(String jobKey, int limit) {
        String sql = "SELECT * FROM quartz_task_log WHERE job_key = ? ORDER BY start_time DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, jobKey, limit);
    }

    @Override
    public List<TaskExecutionLog> findByTimeRange(LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT * FROM quartz_task_log WHERE start_time >= ? AND start_time <= ? ORDER BY start_time DESC";
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