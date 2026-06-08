package io.github.cococzl.coquartz.core;

public class RetryContext {

    private int maxRetryTimes;
    private long retryInterval;
    private boolean exponentialBackoff;
    private double backoffMultiplier;
    private int currentAttempt;
    private long currentDelay;

    public RetryContext(int maxRetryTimes, long retryInterval, boolean exponentialBackoff, double backoffMultiplier) {
        this.maxRetryTimes = maxRetryTimes;
        this.retryInterval = retryInterval;
        this.exponentialBackoff = exponentialBackoff;
        this.backoffMultiplier = backoffMultiplier;
        this.currentAttempt = 0;
        this.currentDelay = retryInterval;
    }

    public boolean canRetry() {
        return currentAttempt <= maxRetryTimes;
    }

    public void recordAttempt() {
        currentAttempt++;
    }

    public long getNextRetryDelay() {
        if (!exponentialBackoff) {
            return retryInterval;
        }
        long delay = currentDelay;
        currentDelay = Math.min((long) (currentDelay * backoffMultiplier), 60000);
        return delay;
    }

    public int getCurrentAttempt() {
        return currentAttempt;
    }

    public int getMaxRetryTimes() {
        return maxRetryTimes;
    }

    public void reset() {
        currentAttempt = 0;
        currentDelay = retryInterval;
    }
}