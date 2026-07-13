package io.github.cococzl.coquartz.dto;

import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;

import java.time.LocalDateTime;

public class TaskLogQuery {

    private String jobKey;
    private String executionId;
    private LogTaskExecStateEnum execState;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int page = 1;
    private int size = 20;

    public String getJobKey() {
        return jobKey;
    }

    public void setJobKey(String jobKey) {
        this.jobKey = jobKey;
    }
    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public LogTaskExecStateEnum getExecState() {
        return execState;
    }

    public void setExecState(LogTaskExecStateEnum execState) {
        this.execState = execState;
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

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
