package com.coco.service;

import com.coco.dto.PageResult;
import com.coco.dto.TaskExecutionLog;
import com.coco.dto.TaskLogQuery;
import com.coco.dto.TaskStatistics;
import com.coco.enums.LogTaskExecStateEnum;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Business-facing task execution log service.
 */
public class TaskLogService {

    private final JdbcTemplate quartzJdbcTemplate;

    public TaskLogService(JdbcTemplate quartzJdbcTemplate) {
        this.quartzJdbcTemplate = quartzJdbcTemplate;
    }

    public PageResult<TaskExecutionLog> pageLogs(TaskLogQuery query) {
        TaskLogQuery safeQuery = normalize(query);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        appendConditions(where, args, safeQuery);

        Long total = quartzJdbcTemplate.queryForObject("SELECT COUNT(*) FROM quartz_task_log" + where,
                Long.class, args.toArray());
        int offset = (safeQuery.getPage() - 1) * safeQuery.getSize();

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeQuery.getSize());
        pageArgs.add(offset);

        String sql = "SELECT job_key, trigger_key, exec_state, error_message, stack_trace, execution_time_ms, execute_time "
                + "FROM quartz_task_log" + where + " ORDER BY execute_time DESC LIMIT ? OFFSET ?";

        List<TaskExecutionLog> records = quartzJdbcTemplate.query(sql, this::mapLog, pageArgs.toArray());
        return new PageResult<>(records, total == null ? 0 : total, safeQuery.getPage(), safeQuery.getSize());
    }

    public List<TaskExecutionLog> latestLogs(String jobKey, int limit) {
        TaskLogQuery query = new TaskLogQuery();
        query.setJobKey(jobKey);
        query.setPage(1);
        query.setSize(limit);
        return pageLogs(query).getRecords();
    }

    public List<TaskExecutionLog> failedLogs(int limit) {
        TaskLogQuery query = new TaskLogQuery();
        query.setExecState(LogTaskExecStateEnum.EXEC_FAIL.getCode());
        query.setPage(1);
        query.setSize(limit);
        return pageLogs(query).getRecords();
    }

    public TaskStatistics statistics() {
        return statistics(null);
    }

    public TaskStatistics statistics(String jobKey) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) as total_executions, "
                + "SUM(CASE WHEN exec_state = ? THEN 1 ELSE 0 END) as successful_executions, "
                + "SUM(CASE WHEN exec_state = ? THEN 1 ELSE 0 END) as failed_executions "
                + "FROM quartz_task_log WHERE 1=1");
        List<Object> args = new ArrayList<>();
        args.add(LogTaskExecStateEnum.EXEC_SUCCESS.getCode());
        args.add(LogTaskExecStateEnum.EXEC_FAIL.getCode());
        if (jobKey != null && !jobKey.isBlank()) {
            sql.append(" AND job_key = ?");
            args.add(jobKey);
        }

        return quartzJdbcTemplate.query(sql.toString(), rs -> {
            if (!rs.next()) {
                return new TaskStatistics();
            }
            TaskStatistics stats = new TaskStatistics();
            stats.setTotalExecutions(rs.getInt("total_executions"));
            stats.setSuccessfulExecutions(rs.getInt("successful_executions"));
            stats.setFailedExecutions(rs.getInt("failed_executions"));
            return stats;
        }, args.toArray());
    }

    public int cleanup(int daysToKeep) {
        Timestamp cutoffDate = Timestamp.valueOf(LocalDateTime.now().minusDays(daysToKeep));
        return quartzJdbcTemplate.update("DELETE FROM quartz_task_log WHERE execute_time < ?", cutoffDate);
    }

    private TaskLogQuery normalize(TaskLogQuery query) {
        TaskLogQuery result = query == null ? new TaskLogQuery() : query;
        if (result.getPage() < 1) {
            result.setPage(1);
        }
        if (result.getSize() < 1) {
            result.setSize(20);
        }
        if (result.getSize() > 500) {
            result.setSize(500);
        }
        return result;
    }

    private void appendConditions(StringBuilder where, List<Object> args, TaskLogQuery query) {
        if (query.getJobKey() != null && !query.getJobKey().isBlank()) {
            where.append(" AND job_key = ?");
            args.add(query.getJobKey());
        }
        if (query.getExecState() != null) {
            where.append(" AND exec_state = ?");
            args.add(query.getExecState());
        }
        if (query.getStartTime() != null) {
            where.append(" AND execute_time >= ?");
            args.add(query.getStartTime());
        }
        if (query.getEndTime() != null) {
            where.append(" AND execute_time <= ?");
            args.add(query.getEndTime());
        }
    }

    private TaskExecutionLog mapLog(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        TaskExecutionLog log = new TaskExecutionLog();
        log.setJobKey(rs.getString("job_key"));
        log.setTriggerKey(rs.getString("trigger_key"));
        log.setExecState(rs.getInt("exec_state"));
        log.setErrorMessage(rs.getString("error_message"));
        log.setStackTrace(rs.getString("stack_trace"));
        log.setExecutionTimeMs(rs.getLong("execution_time_ms"));
        log.setExecuteTime(rs.getTimestamp("execute_time"));
        return log;
    }
}
