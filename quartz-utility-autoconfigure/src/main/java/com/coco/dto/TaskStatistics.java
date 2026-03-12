package com.coco.dto;

/**
 * 任务统计信息
 */
public class TaskStatistics {
    private int totalExecutions;
    private int successfulExecutions;
    private int failedExecutions;

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

    /**
     * 获取成功率（百分比）
     */
    public double getSuccessRate() {
        if (totalExecutions == 0) {
            return 0.0;
        }
        return (double) successfulExecutions / totalExecutions * 100;
    }
}
