package com.coco.service.impl;

import com.coco.service.TaskAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 默认任务告警服务实现
 * 使用日志记录告警信息，可以根据需要扩展为发送邮件、短信、钉钉等
 */
@Service
public class DefaultTaskAlertService implements TaskAlertService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultTaskAlertService.class);

    @Override
    public void alertOnFailure(String jobKey, String triggerKey, String errorMessage, String stackTrace) {
        logger.error("⚠️ ALERT - Task execution failed: jobKey={}, triggerKey={}, error={}",
                jobKey, triggerKey, errorMessage);
        logger.error("Stack trace: {}", stackTrace);

        // TODO: 可以在这里添加其他告警方式，如发送邮件、短信、钉钉通知等
    }

    @Override
    public void alertOnTimeout(String jobKey, String triggerKey, long executionTime, long threshold) {
        logger.warn("⚠️ ALERT - Task execution timeout: jobKey={}, triggerKey={}, executionTime={}ms, threshold={}ms",
                jobKey, triggerKey, executionTime, threshold);

        // TODO: 可以在这里添加其他告警方式
    }

    @Override
    public void alertOnConsecutiveFailures(String jobKey, String triggerKey, int failureCount) {
        logger.error("⚠️ ALERT - Task consecutive failures: jobKey={}, triggerKey={}, failureCount={}",
                jobKey, triggerKey, failureCount);

        // TODO: 可以在这里添加其他告警方式
    }

    @Override
    public void alertOnSlowTask(String jobKey, String triggerKey, long executionTime, long threshold) {
        logger.warn("⚠️ ALERT - Slow task detected: jobKey={}, triggerKey={}, executionTime={}ms, threshold={}ms",
                jobKey, triggerKey, executionTime, threshold);

        // TODO: 可以在这里添加其他告警方式
    }
}
