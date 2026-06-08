package io.github.cococzl.coquartz.dto;

import java.util.Map;

public class TaskInfo {

    private String jobName;
    private String jobGroup;
    private String jobClassName;
    private String description;
    private boolean durable;
    private boolean recoverable;
    private Map<String, Object> jobData;
    private String triggerName;
    private String triggerGroup;
    private String triggerType;
    private String triggerState;
    private String cronExpression;
    private Long repeatIntervalMs;
    private java.util.Date previousFireTime;
    private java.util.Date nextFireTime;

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public String getJobGroup() { return jobGroup; }
    public void setJobGroup(String jobGroup) { this.jobGroup = jobGroup; }
    public String getJobClassName() { return jobClassName; }
    public void setJobClassName(String jobClassName) { this.jobClassName = jobClassName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isDurable() { return durable; }
    public void setDurable(boolean durable) { this.durable = durable; }
    public boolean isRecoverable() { return recoverable; }
    public void setRecoverable(boolean recoverable) { this.recoverable = recoverable; }
    public Map<String, Object> getJobData() { return jobData; }
    public void setJobData(Map<String, Object> jobData) { this.jobData = jobData; }
    public String getTriggerName() { return triggerName; }
    public void setTriggerName(String triggerName) { this.triggerName = triggerName; }
    public String getTriggerGroup() { return triggerGroup; }
    public void setTriggerGroup(String triggerGroup) { this.triggerGroup = triggerGroup; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getTriggerState() { return triggerState; }
    public void setTriggerState(String triggerState) { this.triggerState = triggerState; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public Long getRepeatIntervalMs() { return repeatIntervalMs; }
    public void setRepeatIntervalMs(Long repeatIntervalMs) { this.repeatIntervalMs = repeatIntervalMs; }
    public java.util.Date getPreviousFireTime() { return previousFireTime; }
    public void setPreviousFireTime(java.util.Date previousFireTime) { this.previousFireTime = previousFireTime; }
    public java.util.Date getNextFireTime() { return nextFireTime; }
    public void setNextFireTime(java.util.Date nextFireTime) { this.nextFireTime = nextFireTime; }
}