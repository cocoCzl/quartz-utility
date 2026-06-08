package io.github.cococzl.coquartz.dto;

public class TaskStatistics {

    private long totalExecutions;
    private long successfulExecutions;
    private long failedExecutions;

    public TaskStatistics() {
    }

    public TaskStatistics(long totalExecutions, long successfulExecutions, long failedExecutions) {
        this.totalExecutions = totalExecutions;
        this.successfulExecutions = successfulExecutions;
        this.failedExecutions = failedExecutions;
    }

    public long getTotalExecutions() {
        return totalExecutions;
    }

    public void setTotalExecutions(long totalExecutions) {
        this.totalExecutions = totalExecutions;
    }

    public long getSuccessfulExecutions() {
        return successfulExecutions;
    }

    public void setSuccessfulExecutions(long successfulExecutions) {
        this.successfulExecutions = successfulExecutions;
    }

    public long getFailedExecutions() {
        return failedExecutions;
    }

    public void setFailedExecutions(long failedExecutions) {
        this.failedExecutions = failedExecutions;
    }

    public double getSuccessRate() {
        if (totalExecutions == 0) {
            return 0.0;
        }
        return (double) successfulExecutions / totalExecutions * 100;
    }
}