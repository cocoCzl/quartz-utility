package com.coco.core;

/**
 * 重试上下文
 * 存储任务重试的相关信息
 */
public class RetryContext {

    private final int maxRetryTimes;
    private final long retryInterval;
    private final boolean exponentialBackoff;
    private final double backoffMultiplier;

    private int currentRetryCount = 0;
    private long lastRetryTime = 0;
    private Throwable lastError;

    public RetryContext(int maxRetryTimes, long retryInterval) {
        this(maxRetryTimes, retryInterval, false, 1.0);
    }

    public RetryContext(int maxRetryTimes, long retryInterval, 
                       boolean exponentialBackoff, double backoffMultiplier) {
        this.maxRetryTimes = maxRetryTimes;
        this.retryInterval = retryInterval;
        this.exponentialBackoff = exponentialBackoff;
        this.backoffMultiplier = backoffMultiplier;
    }

    /**
     * 是否还可以重试
     */
    public boolean canRetry() {
        return currentRetryCount < maxRetryTimes;
    }

    /**
     * 记录一次重试
     */
    public void recordRetry(Throwable error) {
        this.currentRetryCount++;
        this.lastError = error;
        this.lastRetryTime = System.currentTimeMillis();
    }

    /**
     * 获取下次重试的等待时间（毫秒）
     */
    public long getNextRetryDelay() {
        if (!exponentialBackoff) {
            return retryInterval;
        }

        // 指数退避：每次重试等待时间 = 基础间隔 * (乘数 ^ 重试次数)
        long delay = (long) (retryInterval * Math.pow(backoffMultiplier, currentRetryCount));
        
        // 最大延迟不超过 60 秒
        return Math.min(delay, 60000);
    }

    /**
     * 重置重试计数
     */
    public void reset() {
        this.currentRetryCount = 0;
        this.lastError = null;
        this.lastRetryTime = 0;
    }

    // Getters
    public int getMaxRetryTimes() {
        return maxRetryTimes;
    }

    public long getRetryInterval() {
        return retryInterval;
    }

    public boolean isExponentialBackoff() {
        return exponentialBackoff;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public int getCurrentRetryCount() {
        return currentRetryCount;
    }

    public long getLastRetryTime() {
        return lastRetryTime;
    }

    public Throwable getLastError() {
        return lastError;
    }
}
