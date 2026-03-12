package com.coco.service;

import com.coco.config.QuartzUtilityProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步任务日志服务（优化版）
 * 使用 BlockingQueue 缓存日志，定时批量写入，大幅提升性能
 */
@Service
@ConditionalOnProperty(prefix = "quartz-utility.async", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AsyncTaskLogService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncTaskLogService.class);

    @Autowired
    @Qualifier("quartzJdbcTemplate")
    private JdbcTemplate quartzJdbcTemplate;

    @Autowired
    private QuartzUtilityProperties properties;

    private static final String INSERT_DETAILED_SQL = 
            "INSERT INTO quartz_task_log (job_key, trigger_key, exec_state, error_message, stack_trace, execution_time_ms, execute_time) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

    // 日志队列（默认容量 1000）
    private final BlockingQueue<TaskLogEntry> logQueue;

    // 批量写入大小（默认 100）
    private static final int BATCH_SIZE = 100;

    // 监控指标
    private final AtomicLong totalLogsReceived = new AtomicLong(0);
    private final AtomicLong totalLogsWritten = new AtomicLong(0);
    private final AtomicLong totalBatchesWritten = new AtomicLong(0);

    public AsyncTaskLogService() {
        this.logQueue = new LinkedBlockingQueue<>(1000);
        logger.info("AsyncTaskLogService initialized with queue capacity: 1000");
    }

    /**
     * 异步记录任务日志（将日志添加到队列）
     *
     * @param jobKey          任务标识
     * @param triggerKey      触发器标识
     * @param execState       执行状态
     * @param errorMessage    错误信息
     * @param stackTrace      堆栈跟踪
     * @param executionTimeMs 执行时间（毫秒）
     */
    public void logTaskExecutionAsync(String jobKey, String triggerKey, int execState,
            String errorMessage, String stackTrace, long executionTimeMs) {
        
        TaskLogEntry entry = new TaskLogEntry(jobKey, triggerKey, execState, 
                errorMessage, stackTrace, executionTimeMs);

        try {
            // 非阻塞方式添加到队列
            boolean added = logQueue.offer(entry);
            
            if (!added) {
                // 队列已满，立即执行批量写入，然后重新添加
                logger.warn("Log queue is full (size={}), forcing immediate flush", logQueue.size());
                flushLogsImmediately();
                logQueue.offer(entry);
            }

            totalLogsReceived.incrementAndGet();
            
            logger.debug("Task log queued: jobKey={}, triggerKey={}, queueSize={}", 
                    jobKey, triggerKey, logQueue.size());

        } catch (Exception e) {
            logger.error("Failed to queue task log: jobKey={}, triggerKey={}, error={}", 
                    jobKey, triggerKey, e.getMessage(), e);
        }
    }

    /**
     * 定时批量写入日志（每秒执行一次）
     */
    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    public void flushLogsScheduled() {
        if (logQueue.isEmpty()) {
            return;
        }

        flushLogsInternal();
    }

    /**
     * 立即执行批量写入
     */
    public void flushLogsImmediately() {
        flushLogsInternal();
    }

    /**
     * 内部批量写入实现
     */
    private void flushLogsInternal() {
        List<TaskLogEntry> batch = new ArrayList<>(BATCH_SIZE);
        
        // 从队列中批量取出日志
        logQueue.drainTo(batch, BATCH_SIZE);
        
        if (batch.isEmpty()) {
            return;
        }

        try {
            // 批量写入数据库
            batchInsertTaskLogs(batch);
            
            totalLogsWritten.addAndGet(batch.size());
            totalBatchesWritten.incrementAndGet();
            
            logger.debug("Flushed {} task logs to database, remaining in queue: {}", 
                    batch.size(), logQueue.size());

        } catch (Exception e) {
            logger.error("Failed to flush {} task logs: {}", batch.size(), e.getMessage(), e);
            
            // 写入失败，重新放回队列（避免丢失）
            for (TaskLogEntry entry : batch) {
                logQueue.offer(entry);
            }
        }
    }

    /**
     * 批量插入任务日志到数据库
     *
     * @param logs 日志列表
     */
    private void batchInsertTaskLogs(List<TaskLogEntry> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }

        quartzJdbcTemplate.batchUpdate(INSERT_DETAILED_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                TaskLogEntry log = logs.get(i);
                ps.setString(1, log.jobKey);
                ps.setString(2, log.triggerKey);
                ps.setInt(3, log.execState);
                ps.setString(4, log.errorMessage);
                ps.setString(5, log.stackTrace);
                ps.setLong(6, log.executionTimeMs);
                ps.setTimestamp(7, log.executeTime);
            }

            @Override
            public int getBatchSize() {
                return logs.size();
            }
        });
    }

    /**
     * 应用关闭前确保所有日志写入完成
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down AsyncTaskLogService, flushing remaining {} logs", logQueue.size());
        
        // 循环刷新直到队列为空
        while (!logQueue.isEmpty()) {
            flushLogsInternal();
        }

        logger.info("AsyncTaskLogService shutdown complete. Total logs received: {}, written: {}, batches: {}",
                totalLogsReceived.get(), totalLogsWritten.get(), totalBatchesWritten.get());
    }

    /**
     * 获取队列当前大小
     */
    public int getQueueSize() {
        return logQueue.size();
    }

    /**
     * 获取监控指标
     */
    public MonitoringMetrics getMonitoringMetrics() {
        return new MonitoringMetrics(
                totalLogsReceived.get(),
                totalLogsWritten.get(),
                totalBatchesWritten.get(),
                logQueue.size()
        );
    }

    /**
     * 监控指标
     */
    public static class MonitoringMetrics {
        private final long totalLogsReceived;
        private final long totalLogsWritten;
        private final long totalBatchesWritten;
        private final int currentQueueSize;

        public MonitoringMetrics(long totalLogsReceived, long totalLogsWritten, 
                                long totalBatchesWritten, int currentQueueSize) {
            this.totalLogsReceived = totalLogsReceived;
            this.totalLogsWritten = totalLogsWritten;
            this.totalBatchesWritten = totalBatchesWritten;
            this.currentQueueSize = currentQueueSize;
        }

        public long getTotalLogsReceived() {
            return totalLogsReceived;
        }

        public long getTotalLogsWritten() {
            return totalLogsWritten;
        }

        public long getTotalBatchesWritten() {
            return totalBatchesWritten;
        }

        public int getCurrentQueueSize() {
            return currentQueueSize;
        }

        public double getAverageBatchSize() {
            return totalBatchesWritten == 0 ? 0 : (double) totalLogsWritten / totalBatchesWritten;
        }
    }

    /**
     * 任务日志条目
     */
    public static class TaskLogEntry {
        private final String jobKey;
        private final String triggerKey;
        private final int execState;
        private final String errorMessage;
        private final String stackTrace;
        private final long executionTimeMs;
        private final Timestamp executeTime;

        public TaskLogEntry(String jobKey, String triggerKey, int execState,
                String errorMessage, String stackTrace, long executionTimeMs) {
            this.jobKey = jobKey;
            this.triggerKey = triggerKey;
            this.execState = execState;
            this.errorMessage = errorMessage;
            this.stackTrace = stackTrace;
            this.executionTimeMs = executionTimeMs;
            this.executeTime = new Timestamp(System.currentTimeMillis());
        }

        // Getters
        public String getJobKey() {
            return jobKey;
        }

        public String getTriggerKey() {
            return triggerKey;
        }

        public int getExecState() {
            return execState;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getStackTrace() {
            return stackTrace;
        }

        public long getExecutionTimeMs() {
            return executionTimeMs;
        }

        public Timestamp getExecuteTime() {
            return executeTime;
        }
    }
}
