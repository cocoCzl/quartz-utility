package com.coco.core;

import com.coco.enums.LogTaskExecStateEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
        ).stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    
    /**
     * 统计信息类
     */
    public static class TaskStatistics {
        private int totalExecutions;
        private int successfulExecutions;
        private int failedExecutions;
        private double successRate;

        public int getTotalExecutions() {
            return totalExecutions;
        }
        public void setTotalExecutions(int totalExecutions) {
            this.totalExecutions = totalExecutions;
        }
        
        public int getSuccessfulExecutions() {
            return successfulExecutions;
        }
        public void setSuccessfulExecutions(int successfulExecutions) {
            this.successfulExecutions = successfulExecutions;
        }
        
        public int getFailedExecutions() {
            return failedExecutions;
        }
        public void setFailedExecutions(int failedExecutions) {
            this.failedExecutions = failedExecutions;
        }
        
        public double getSuccessRate() {
            if (totalExecutions == 0) {
                return 0.0;
            }
            return (double) successfulExecutions / totalExecutions * 100;
        }
    }
    
    /**
     * 任务执行日志类
     */
    public static class TaskExecutionLog {
        private String jobKey;
        private String triggerKey;
        private byte execState;
        private String errorMessage;
        private String stackTrace;
        private Long executionTimeMs;
        private Timestamp executeTime;

        // Getters and setters
        public String getJobKey() { return jobKey; }
        public void setJobKey(String jobKey) { this.jobKey = jobKey; }

        public String getTriggerKey() { return triggerKey; }
        public void setTriggerKey(String triggerKey) { this.triggerKey = triggerKey; }

        public byte getExecState() { return execState; }
        public void setExecState(byte execState) { this.execState = execState; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public String getStackTrace() { return stackTrace; }
        public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }

        public Long getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

        public Timestamp getExecuteTime() { return executeTime; }
        public void setExecuteTime(Timestamp executeTime) { this.executeTime = executeTime; }
    }
}