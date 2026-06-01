package com.coco.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Quartz Utility 配置属性类 支持通过 application.yml 或 application.properties 进行外部化配置
 */
@ConfigurationProperties(prefix = "quartz-utility")
public class QuartzUtilityProperties {

    private static final Logger logger = LoggerFactory.getLogger(QuartzUtilityProperties.class);

    /**
     * 日志配置
     */
    private LogConfig log = new LogConfig();

    /**
     * 监控配置
     */
    private MonitoringConfig monitoring = new MonitoringConfig();

    /**
     * 异步配置
     */
    private AsyncConfig async = new AsyncConfig();

    /**
     * 注解扫描配置
     */
    private AnnotationConfig annotation = new AnnotationConfig();

    @PostConstruct
    public void init() {
        logger.info(
                "Quartz Utility configuration initialized: log.enabled={}, monitoring.enabled={}, async.enabled={}",
                log.enabled, monitoring.enabled, async.enabled);
    }

    public LogConfig getLog() {
        return log;
    }

    public void setLog(LogConfig log) {
        this.log = log;
    }

    public MonitoringConfig getMonitoring() {
        return monitoring;
    }

    public void setMonitoring(MonitoringConfig monitoring) {
        this.monitoring = monitoring;
    }

    public AsyncConfig getAsync() {
        return async;
    }

    public void setAsync(AsyncConfig async) {
        this.async = async;
    }

    public AnnotationConfig getAnnotation() {
        return annotation;
    }

    public void setAnnotation(AnnotationConfig annotation) {
        this.annotation = annotation;
    }

    /**
     * 日志配置类
     */
    public static class LogConfig {

        /**
         * 是否启用日志记录
         */
        private boolean enabled = true;

        /**
         * 日志保留天数
         */
        private int retentionDays = 30;

        /**
         * 日志清理定时任务的 Cron 表达式
         */
        private String cleanupCron = "0 0 2 * * ?";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public String getCleanupCron() {
            return cleanupCron;
        }

        public void setCleanupCron(String cleanupCron) {
            this.cleanupCron = cleanupCron;
        }
    }

    /**
     * 监控配置类
     */
    public static class MonitoringConfig {

        /**
         * 是否启用监控
         */
        private boolean enabled = true;

        /**
         * 是否在任务失败时发送告警
         */
        private boolean alertOnFailure = true;

        /**
         * 慢任务阈值（毫秒）
         */
        private long slowTaskThresholdMs = 5000;

        /**
         * 连续失败次数告警阈值
         */
        private int consecutiveFailureThreshold = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAlertOnFailure() {
            return alertOnFailure;
        }

        public void setAlertOnFailure(boolean alertOnFailure) {
            this.alertOnFailure = alertOnFailure;
        }

        public long getSlowTaskThresholdMs() {
            return slowTaskThresholdMs;
        }

        public void setSlowTaskThresholdMs(long slowTaskThresholdMs) {
            this.slowTaskThresholdMs = slowTaskThresholdMs;
        }

        public int getConsecutiveFailureThreshold() {
            return consecutiveFailureThreshold;
        }

        public void setConsecutiveFailureThreshold(int consecutiveFailureThreshold) {
            this.consecutiveFailureThreshold = consecutiveFailureThreshold;
        }
    }

    /**
     * 异步配置类
     */
    public static class AsyncConfig {

        /**
         * 是否启用异步日志记录
         */
        private boolean enabled = true;

        /**
         * 是否创建可注入的异步执行器 Bean
         */
        private boolean executorEnabled = false;

        /**
         * 核心线程池大小
         */
        private int corePoolSize = 2;

        /**
         * 最大线程池大小
         */
        private int maxPoolSize = 5;

        /**
         * 队列容量
         */
        private int queueCapacity = 100;

        /**
         * 线程名称前缀
         */
        private String threadNamePrefix = "quartz-async-";

        /**
         * 日志队列容量
         */
        private int logQueueCapacity = 1000;

        /**
         * 日志批量写入大小
         */
        private int logBatchSize = 100;

        /**
         * 日志刷新间隔（毫秒）
         */
        private long logFlushIntervalMs = 1000;

        /**
         * 应用关闭时最多等待日志刷新的时间（毫秒）
         */
        private long shutdownFlushTimeoutMs = 10000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isExecutorEnabled() {
            return executorEnabled;
        }

        public void setExecutorEnabled(boolean executorEnabled) {
            this.executorEnabled = executorEnabled;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }

        public int getLogQueueCapacity() {
            return logQueueCapacity;
        }

        public void setLogQueueCapacity(int logQueueCapacity) {
            this.logQueueCapacity = logQueueCapacity;
        }

        public int getLogBatchSize() {
            return logBatchSize;
        }

        public void setLogBatchSize(int logBatchSize) {
            this.logBatchSize = logBatchSize;
        }

        public long getLogFlushIntervalMs() {
            return logFlushIntervalMs;
        }

        public void setLogFlushIntervalMs(long logFlushIntervalMs) {
            this.logFlushIntervalMs = logFlushIntervalMs;
        }

        public long getShutdownFlushTimeoutMs() {
            return shutdownFlushTimeoutMs;
        }

        public void setShutdownFlushTimeoutMs(long shutdownFlushTimeoutMs) {
            this.shutdownFlushTimeoutMs = shutdownFlushTimeoutMs;
        }
    }

    /**
     * 注解自动注册配置类
     */
    public static class AnnotationConfig {

        /**
         * 是否自动注册 @QuartzJob 任务
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
