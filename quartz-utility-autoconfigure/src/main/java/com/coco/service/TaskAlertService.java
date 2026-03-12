package com.coco.service;

/**
 * 任务告警服务接口
 * 提供任务执行过程中的告警功能
 */
public interface TaskAlertService {

    /**
     * 任务失败告警
     *
     * @param jobKey       任务标识
     * @param triggerKey   触发器标识
     * @param errorMessage 错误信息
     * @param stackTrace   堆栈跟踪
     */
    void alertOnFailure(String jobKey, String triggerKey, String errorMessage, String stackTrace);

    /**
     * 任务超时告警
     *
     * @param jobKey        任务标识
     * @param triggerKey    触发器标识
     * @param executionTime 执行时间（毫秒）
     * @param threshold     超时阈值（毫秒）
     */
    void alertOnTimeout(String jobKey, String triggerKey, long executionTime, long threshold);

    /**
     * 连续失败告警
     *
     * @param jobKey       任务标识
     * @param triggerKey   触发器标识
     * @param failureCount 连续失败次数
     */
    void alertOnConsecutiveFailures(String jobKey, String triggerKey, int failureCount);

    /**
     * 慢任务告警
     *
     * @param jobKey        任务标识
     * @param triggerKey    触发器标识
     * @param executionTime 执行时间（毫秒）
     * @param threshold     慢任务阈值（毫秒）
     */
    void alertOnSlowTask(String jobKey, String triggerKey, long executionTime, long threshold);
}
