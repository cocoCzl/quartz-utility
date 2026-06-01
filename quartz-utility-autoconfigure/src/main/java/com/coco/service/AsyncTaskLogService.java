package com.coco.service;

import com.coco.config.QuartzUtilityProperties;
import com.coco.core.QuartzSign;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 异步任务日志服务（优化版）
 * 使用 BlockingQueue 缓存日志，定时批量写入，大幅提升性能
 */
public class AsyncTaskLogService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncTaskLogService.class);

    private final JdbcTemplate quartzJdbcTemplate;
    private final QuartzUtilityProperties properties;

    // 日志队列
    private final BlockingQueue<TaskLogEntry> logQueue;

    private final int batchSize;
    private final ScheduledExecutorService flushExecutor;
    private final ReentrantLock flushLock = new ReentrantLock();
    private volatile boolean shuttingDown = false;

    // 监控指标
    private final AtomicLong totalLogsReceived = new AtomicLong(0);
    private final AtomicLong totalLogsWritten = new AtomicLong(0);
    private final AtomicLong totalBatchesWritten = new AtomicLong(0);
    private final AtomicLong totalLogsDropped = new AtomicLong(0);
    private final AtomicLong totalFlushFailures = new AtomicLong(0);

    public AsyncTaskLogService(JdbcTemplate quartzJdbcTemplate, QuartzUtilityProperties properties) {
        this.quartzJdbcTemplate = quartzJdbcTemplate;
        this.properties = properties;
        this.logQueue = new LinkedBlockingQueue<>(properties.getAsync().getLogQueueCapacity());
        this.batchSize = Math.max(1, properties.getAsync().getLogBatchSize());
        this.flushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "quartz-log-flusher");
            thread.setDaemon(true);
            return thread;
        });
        long flushIntervalMs = Math.max(100, properties.getAsync().getLogFlushIntervalMs());
        this.flushExecutor.scheduleWithFixedDelay(this::flushLogsSafely, flushIntervalMs,
                flushIntervalMs, TimeUnit.MILLISECONDS);
        logger.info("AsyncTaskLogService initialized with queueCapacity={}, batchSize={}, flushIntervalMs={}",
                properties.getAsync().getLogQueueCapacity(), batchSize, flushIntervalMs);
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
        if (shuttingDown) {
            totalLogsDropped.incrementAndGet();
            logger.warn("Task log dropped because AsyncTaskLogService is shutting down: jobKey={}, triggerKey={}",
                    jobKey, triggerKey);
            return;
        }
        
        TaskLogEntry entry = new TaskLogEntry(jobKey, triggerKey, execState, 
                errorMessage, stackTrace, executionTimeMs);

        try {
            // 非阻塞方式添加到队列
            boolean added = logQueue.offer(entry);
            
            if (!added) {
                // 队列已满，立即执行批量写入，然后重新添加
                logger.warn("Log queue is full (size={}), forcing immediate flush", logQueue.size());
                flushLogsImmediately();
                added = logQueue.offer(entry);
            }

            if (!added) {
                totalLogsDropped.incrementAndGet();
                logger.error("Task log dropped because queue remains full: jobKey={}, triggerKey={}",
                        jobKey, triggerKey);
                return;
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
     * 立即执行批量写入
     */
    public void flushLogsImmediately() {
        flushLogsSafely();
    }

    /**
     * 内部批量写入实现
     */
    private void flushLogsSafely() {
        if (logQueue.isEmpty() || !flushLock.tryLock()) {
            return;
        }

        try {
            flushLogsInternal();
        } catch (Exception e) {
            totalFlushFailures.incrementAndGet();
            logger.error("Unexpected task log flush failure: {}", e.getMessage(), e);
        } finally {
            flushLock.unlock();
        }
    }

    private void flushLogsInternal() {
        List<TaskLogEntry> batch = new ArrayList<>(batchSize);
        
        // 从队列中批量取出日志
        logQueue.drainTo(batch, batchSize);
        
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
            totalFlushFailures.incrementAndGet();
            logger.error("Failed to flush {} task logs: {}", batch.size(), e.getMessage(), e);

            for (TaskLogEntry entry : batch) {
                boolean reOffered = logQueue.offer(entry);
                if (!reOffered) {
                    totalLogsDropped.incrementAndGet();
                    logger.error("Log entry dropped due to queue full: jobKey={}, triggerKey={}",
                            entry.getJobKey(), entry.getTriggerKey());
                }
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

        quartzJdbcTemplate.batchUpdate(QuartzSign.INSERT_DETAILED_SQL, new BatchPreparedStatementSetter() {
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
        shuttingDown = true;
        logger.info("Shutting down AsyncTaskLogService, flushing remaining {} logs", logQueue.size());
        flushExecutor.shutdownNow();
        
        long deadline = System.currentTimeMillis() + Math.max(0, properties.getAsync().getShutdownFlushTimeoutMs());
        while (!logQueue.isEmpty() && System.currentTimeMillis() <= deadline) {
            flushLogsSafely();
        }

        int remaining = logQueue.size();
        if (remaining > 0) {
            totalLogsDropped.addAndGet(remaining);
            logQueue.clear();
            logger.error("AsyncTaskLogService shutdown timed out, dropped {} remaining logs", remaining);
        }

        logger.info("AsyncTaskLogService shutdown complete. Total logs received: {}, written: {}, batches: {}, dropped: {}, flushFailures: {}",
                totalLogsReceived.get(), totalLogsWritten.get(), totalBatchesWritten.get(),
                totalLogsDropped.get(), totalFlushFailures.get());
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
                logQueue.size(),
                totalLogsDropped.get(),
                totalFlushFailures.get()
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
        private final long totalLogsDropped;
        private final long totalFlushFailures;

        public MonitoringMetrics(long totalLogsReceived, long totalLogsWritten, 
                                long totalBatchesWritten, int currentQueueSize,
                                long totalLogsDropped, long totalFlushFailures) {
            this.totalLogsReceived = totalLogsReceived;
            this.totalLogsWritten = totalLogsWritten;
            this.totalBatchesWritten = totalBatchesWritten;
            this.currentQueueSize = currentQueueSize;
            this.totalLogsDropped = totalLogsDropped;
            this.totalFlushFailures = totalFlushFailures;
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

        public long getTotalLogsDropped() {
            return totalLogsDropped;
        }

        public long getTotalFlushFailures() {
            return totalFlushFailures;
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
