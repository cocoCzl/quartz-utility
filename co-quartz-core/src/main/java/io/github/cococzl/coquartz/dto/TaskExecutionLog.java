package io.github.cococzl.coquartz.dto;

import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;

import java.time.LocalDateTime;

public class TaskExecutionLog {

    private String id;
    private String jobKey;
    private String triggerKey;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long executionTimeMs;
    private LogTaskExecStateEnum execState;
    private String errorMessage;
    private String stackTrace;
    private int attempt;
    private boolean isFinalAttempt;
    private LocalDateTime executeTime;

    public TaskExecutionLog() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getJobKey() {
        return jobKey;
    }

    public void setJobKey(String jobKey) {
        this.jobKey = jobKey;
    }

    public String getTriggerKey() {
        return triggerKey;
    }

    public void setTriggerKey(String triggerKey) {
        this.triggerKey = triggerKey;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public LogTaskExecStateEnum getExecState() {
        return execState;
    }

    public void setExecState(LogTaskExecStateEnum execState) {
        this.execState = execState;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public boolean isFinalAttempt() {
        return isFinalAttempt;
    }

    public void setFinalAttempt(boolean finalAttempt) {
        isFinalAttempt = finalAttempt;
    }

    public LocalDateTime getExecuteTime() {
        return executeTime;
    }

    public void setExecuteTime(LocalDateTime executeTime) {
        this.executeTime = executeTime;
    }

    public static TaskExecutionLog success(String jobKey, String triggerKey,
                                            LocalDateTime startTime, LocalDateTime endTime,
                                            long executionTimeMs, int attempt, boolean isFinalAttempt) {
        TaskExecutionLog log = new TaskExecutionLog();
        log.setId(java.util.UUID.randomUUID().toString());
        log.setJobKey(jobKey);
        log.setTriggerKey(triggerKey);
        log.setStartTime(startTime);
        log.setEndTime(endTime);
        log.setExecutionTimeMs(executionTimeMs);
        log.setExecState(LogTaskExecStateEnum.SUCCESS);
        log.setAttempt(attempt);
        log.setFinalAttempt(isFinalAttempt);
        log.setExecuteTime(startTime);
        return log;
    }

    public static TaskExecutionLog failure(String jobKey, String triggerKey,
                                            LocalDateTime startTime, LocalDateTime endTime,
                                            long executionTimeMs, String errorMessage,
                                            String stackTrace, int attempt, boolean isFinalAttempt) {
        TaskExecutionLog log = new TaskExecutionLog();
        log.setId(java.util.UUID.randomUUID().toString());
        log.setJobKey(jobKey);
        log.setTriggerKey(triggerKey);
        log.setStartTime(startTime);
        log.setEndTime(endTime);
        log.setExecutionTimeMs(executionTimeMs);
        log.setExecState(LogTaskExecStateEnum.FAIL);
        log.setErrorMessage(errorMessage);
        log.setStackTrace(stackTrace);
        log.setAttempt(attempt);
        log.setFinalAttempt(isFinalAttempt);
        log.setExecuteTime(startTime);
        return log;
    }
}