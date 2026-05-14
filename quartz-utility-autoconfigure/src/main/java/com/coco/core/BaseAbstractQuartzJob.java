package com.coco.core;

import com.coco.config.QuartzUtilityProperties;
import com.coco.enums.LogTaskExecStateEnum;
import com.coco.service.AsyncTaskLogService;
import com.coco.service.TaskAlertService;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.concurrent.*;

/**
 * Quartz 任务抽象基类
 * 提供统一的日志记录、异常处理、重试机制、超时控制和告警功能
 */
public abstract class BaseAbstractQuartzJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(BaseAbstractQuartzJob.class);

    private static final ExecutorService TIMEOUT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "quartz-timeout-executor");
        t.setDaemon(true);
        return t;
    });

    // JobDataMap 中的键名
    public static final String RETRY_TIMES_KEY = "retryTimes";
    public static final String RETRY_INTERVAL_KEY = "retryInterval";
    public static final String CURRENT_RETRY_KEY = "currentRetry";
    public static final String TIMEOUT_KEY = "timeout";
    public static final String EXPONENTIAL_BACKOFF_KEY = "exponentialBackoff";
    public static final String BACKOFF_MULTIPLIER_KEY = "backoffMultiplier";

    @Autowired
    @Qualifier("quartzJdbcTemplate")
    private JdbcTemplate quartzJdbcTemplate;

    @Autowired(required = false)
    private AsyncTaskLogService asyncTaskLogService;

    @Autowired(required = false)
    private TaskAlertService taskAlertService;

    @Autowired
    private QuartzUtilityProperties properties;

    /**
     * 抽象方法，子类需要实现具体的任务执行逻辑
     *
     * @param context 任务执行上下文
     * @throws Throwable 可能抛出的异常
     */
    protected abstract void executeQuartz(JobExecutionContext context) throws Throwable;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        long startTime = System.currentTimeMillis();
        String jobKey = context.getJobDetail().getKey().toString();
        String triggerKey = context.getTrigger().getKey().toString();

        logger.info("Starting task execution: jobKey={}, triggerKey={}", jobKey, triggerKey);

        int execState = LogTaskExecStateEnum.EXEC_SUCCESS.getCode();
        String errorMessage = null;
        String stackTrace = null;
        int retryCount = 0;

        try {
            // 获取重试配置
            RetryContext retryContext = getRetryContext(context);
            long timeout = getTimeout(context);

            // 执行任务（带重试和超时）
            executeWithRetryAndTimeout(context, retryContext, timeout);

        } catch (TimeoutException e) {
            execState = LogTaskExecStateEnum.EXEC_FAIL.getCode();
            errorMessage = "Task execution timeout";
            stackTrace = getStackTraceAsString(e);
            logger.error("Task execution timeout: jobKey={}, triggerKey={}", jobKey, triggerKey, e);

            // 发送超时告警
            if (taskAlertService != null) {
                long executionTime = System.currentTimeMillis() - startTime;
                taskAlertService.alertOnTimeout(jobKey, triggerKey, executionTime, getTimeout(context));
            }

            throw new JobExecutionException(e);

        } catch (Throwable e) {
            execState = LogTaskExecStateEnum.EXEC_FAIL.getCode();
            errorMessage = e.getMessage();
            stackTrace = getStackTraceAsString(e);
            
            // 获取重试次数
            JobDataMap dataMap = context.getJobDetail().getJobDataMap();
            retryCount = dataMap.getInt(CURRENT_RETRY_KEY);

            logger.error("Task execution failed: jobKey={}, triggerKey={}, retryCount={}, error={}",
                    jobKey, triggerKey, retryCount, e.getMessage(), e);

            // 发送失败告警
            if (taskAlertService != null && properties.getMonitoring().isAlertOnFailure()) {
                taskAlertService.alertOnFailure(jobKey, triggerKey, errorMessage, stackTrace);
            }

            // 将捕获的异常封装为JobExecutionException抛出
            throw new JobExecutionException(e);

        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            logger.info("Task execution completed: jobKey={}, triggerKey={}, executionTime={}ms, retryCount={}, status={}",
                    jobKey, triggerKey, executionTime, retryCount,
                    execState == LogTaskExecStateEnum.EXEC_SUCCESS.getCode() ? "SUCCESS" : "FAILED");

            // 慢任务检测
            if (properties.getMonitoring().isEnabled() &&
                    executionTime > properties.getMonitoring().getSlowTaskThresholdMs()) {
                logger.warn("Slow task detected: jobKey={}, triggerKey={}, executionTime={}ms",
                        jobKey, triggerKey, executionTime);
                if (taskAlertService != null) {
                    taskAlertService.alertOnSlowTask(jobKey, triggerKey, executionTime,
                            properties.getMonitoring().getSlowTaskThresholdMs());
                }
            }

            // 记录详细日志（异步或同步）
            if (asyncTaskLogService != null && properties.getAsync().isEnabled()) {
                asyncTaskLogService.logTaskExecutionAsync(jobKey, triggerKey, execState,
                        errorMessage, stackTrace, executionTime);
            } else {
                insertDetailedTaskLog(jobKey, triggerKey, execState, errorMessage, stackTrace, executionTime);
            }
        }
    }

    /**
     * 带重试和超时的任务执行
     */
    private void executeWithRetryAndTimeout(JobExecutionContext context, 
                                           RetryContext retryContext, 
                                           long timeout) throws Throwable {
        int attemptCount = 0;

        while (true) {
            attemptCount++;
            try {
                // 更新重试计数
                JobDataMap dataMap = context.getJobDetail().getJobDataMap();
                dataMap.put(CURRENT_RETRY_KEY, attemptCount - 1);

                // 执行任务（带超时控制）
                if (timeout > 0) {
                    executeWithTimeout(context, timeout);
                } else {
                    executeQuartz(context);
                }

                // 执行成功，返回
                logger.debug("Task executed successfully on attempt {}", attemptCount);
                return;

            } catch (TimeoutException e) {
                // 超时异常不重试，直接抛出
                throw e;

            } catch (Throwable e) {
                retryContext.recordRetry(e);

                // 检查是否还可以重试
                if (!retryContext.canRetry()) {
                    logger.error("Task execution failed after {} attempts, no more retries", attemptCount);
                    throw e;
                }

                // 计算等待时间
                long delay = retryContext.getNextRetryDelay();
                logger.warn("Task execution failed on attempt {}, will retry after {}ms. Error: {}", 
                        attemptCount, delay, e.getMessage());

                // 等待后重试
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new JobExecutionException("Retry interrupted", ie);
                }
            }
        }
    }

    /**
     * 带超时控制的任务执行
     */
    private void executeWithTimeout(JobExecutionContext context, long timeoutMs)
            throws Throwable, TimeoutException {

        Future<Void> future = TIMEOUT_EXECUTOR.submit(() -> {
            try {
                executeQuartz(context);
            } catch (Throwable e) {
                if (e instanceof Error) {
                    throw (Error) e;
                } else if (e instanceof Exception) {
                    throw (Exception) e;
                } else {
                    throw new RuntimeException(e);
                }
            }
            return null;
        });

        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            logger.error("Task execution timeout after {}ms", timeoutMs);
            throw new TimeoutException("Task execution timeout after " + timeoutMs + "ms");
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    /**
     * 从 JobDataMap 获取重试配置
     */
    private RetryContext getRetryContext(JobExecutionContext context) {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();

        int retryTimes = dataMap.getInt(RETRY_TIMES_KEY);
        long retryInterval = dataMap.getLong(RETRY_INTERVAL_KEY);
        boolean exponentialBackoff = dataMap.containsKey(EXPONENTIAL_BACKOFF_KEY)
                ? dataMap.getBoolean(EXPONENTIAL_BACKOFF_KEY) : true;
        double backoffMultiplier = dataMap.containsKey(BACKOFF_MULTIPLIER_KEY)
                ? dataMap.getDouble(BACKOFF_MULTIPLIER_KEY) : 1.5;

        return new RetryContext(retryTimes, retryInterval, exponentialBackoff, backoffMultiplier);
    }

    /**
     * 从 JobDataMap 获取超时配置
     */
    private long getTimeout(JobExecutionContext context) {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        return dataMap.getLong(TIMEOUT_KEY);
    }

    /**
     * 获取异常的完整堆栈跟踪
     */
    private String getStackTraceAsString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.toString()).append("\n");
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        Throwable cause = throwable.getCause();
        if (cause != null) {
            sb.append("Caused by: ");
            sb.append(getStackTraceAsString(cause));
        }

        return sb.toString();
    }

    /**
     * 插入详细的任务日志记录，包含执行时间等信息
     */
    public void insertDetailedTaskLog(String jobKey, String triggerKey, int execState,
            String errorMessage, String stackTrace, long executionTimeInMs) {
        quartzJdbcTemplate.update(QuartzSign.INSERT_DETAILED_SQL, jobKey, triggerKey, execState,
                errorMessage, stackTrace, executionTimeInMs,
                new Timestamp(System.currentTimeMillis()));
    }
}
