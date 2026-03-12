package com.coco.core;

import com.coco.dto.TaskExecutionLog;
import com.coco.dto.TaskStatistics;
import com.coco.enums.LogTaskExecStateEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务监控服务，提供任务执行情况的查询和监控功能
 */
@Service
public class TaskMonitoringService {

    @Autowired
    @Qualifier("quartzJdbcTemplate")
    private JdbcTemplate quartzJdbcTemplate;

    /**
     * 获取任务执行统计信息
     */
    public TaskStatistics getTaskStatistics() {
        String sql = "SELECT " +
                "COUNT(*) as totalExecutions, " +
                "SUM(CASE WHEN exec_state = ? THEN 1 ELSE 0 END) as successfulExecutions, " +
                "SUM(CASE WHEN exec_state = ? THEN 1 ELSE 0 END) as failedExecutions " +
                "FROM quartz_task_log";

        return quartzJdbcTemplate.query(sql, 
            rs -> {
                if (rs.next()) {
                    TaskStatistics stats = new TaskStatistics();
                    stats.setTotalExecutions(rs.getInt("totalExecutions"));
                    stats.setSuccessfulExecutions(rs.getInt("successfulExecutions"));
                    stats.setFailedExecutions(rs.getInt("failedExecutions"));
                    return stats;
                }
                return new TaskStatistics();
            }, 
            LogTaskExecStateEnum.EXEC_SUCCESS.getCode(), 
            LogTaskExecStateEnum.EXEC_FAIL.getCode());
    }

    /**
     * 获取指定任务的执行历史
     */
    public List<TaskExecutionLog> getTaskExecutionHistory(String jobKey, int limit) {
        String sql = "SELECT job_key, trigger_key, exec_state, error_message, stack_trace, execution_time_ms, execute_time " +
                "FROM quartz_task_log WHERE job_key = ? ORDER BY execute_time DESC LIMIT ?";

        return quartzJdbcTemplate.query(sql, 
            (rs, rowNum) -> {
                TaskExecutionLog log = new TaskExecutionLog();
                log.setJobKey(rs.getString("job_key"));
                log.setTriggerKey(rs.getString("trigger_key"));
                log.setExecState(rs.getByte("exec_state"));
                log.setErrorMessage(rs.getString("error_message"));
                log.setStackTrace(rs.getString("stack_trace"));
                log.setExecutionTimeMs(rs.getLong("execution_time_ms"));
                log.setExecuteTime(rs.getTimestamp("execute_time"));
                return log;
            }, 
            jobKey, limit);
    }

    /**
     * 获取最近的失败任务
     */
    public List<TaskExecutionLog> getRecentFailedTasks(int limit) {
        String sql = "SELECT job_key, trigger_key, exec_state, error_message, stack_trace, execution_time_ms, execute_time " +
                "FROM quartz_task_log WHERE exec_state = ? ORDER BY execute_time DESC LIMIT ?";

        return quartzJdbcTemplate.query(sql, 
            (rs, rowNum) -> {
                TaskExecutionLog log = new TaskExecutionLog();
                log.setJobKey(rs.getString("job_key"));
                log.setTriggerKey(rs.getString("trigger_key"));
                log.setExecState(rs.getByte("exec_state"));
                log.setErrorMessage(rs.getString("error_message"));
                log.setStackTrace(rs.getString("stack_trace"));
                log.setExecutionTimeMs(rs.getLong("execution_time_ms"));
                log.setExecuteTime(rs.getTimestamp("execute_time"));
                return log;
            }, 
            LogTaskExecStateEnum.EXEC_FAIL.getCode(), limit);
    }

    /**
     * 清理历史日志（保留指定天数的日志）
     */
    public int cleanupLogs(int daysToKeep) {
        Timestamp cutoffDate = Timestamp.valueOf(LocalDateTime.now().minusDays(daysToKeep));
        String sql = "DELETE FROM quartz_task_log WHERE execute_time < ?";
        return quartzJdbcTemplate.update(sql, cutoffDate);
    }

    /**
     * 根据时间范围查询任务执行情况
     */
    public List<TaskExecutionLog> getTaskExecutionsByTimeRange(Timestamp startTime, Timestamp endTime) {
        String sql = "SELECT job_key, trigger_key, exec_state, error_message, stack_trace, execution_time_ms, execute_time " +
                "FROM quartz_task_log WHERE execute_time BETWEEN ? AND ? ORDER BY execute_time DESC";

        return quartzJdbcTemplate.query(sql, 
            (rs, rowNum) -> {
                TaskExecutionLog log = new TaskExecutionLog();
                log.setJobKey(rs.getString("job_key"));
                log.setTriggerKey(rs.getString("trigger_key"));
                log.setExecState(rs.getByte("exec_state"));
                log.setErrorMessage(rs.getString("error_message"));
                log.setStackTrace(rs.getString("stack_trace"));
                log.setExecutionTimeMs(rs.getLong("execution_time_ms"));
                log.setExecuteTime(rs.getTimestamp("execute_time"));
                return log;
            }, 
            startTime, endTime);
    }

    /**
     * 获取任务平均执行时间
     */
    public Map<String, Double> getAverageExecutionTimeByJob() {
        String sql = "SELECT job_key, AVG(execution_time_ms) as avg_execution_time FROM quartz_task_log " +
                "WHERE execution_time_ms IS NOT NULL GROUP BY job_key";

        return quartzJdbcTemplate.query(sql,
            (rs, rowNum) -> Map.entry(rs.getString("job_key"), rs.getDouble("avg_execution_time"))
        ).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
