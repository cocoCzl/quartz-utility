package io.github.cococzl.coquartz.dto;

import io.github.cococzl.coquartz.enums.LogTaskExecStateEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class TaskExecutionLogTest {

    @Test
    void successFactory_setsFieldsCorrectly() {
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plusSeconds(1);

        TaskExecutionLog log = TaskExecutionLog.success("DEFAULT.testJob", "DEFAULT.testTrigger",
                startTime, endTime, 1000, 1, true);

        assertThat(log.getId()).isNotNull();
        assertThat(log.getJobKey()).isEqualTo("DEFAULT.testJob");
        assertThat(log.getTriggerKey()).isEqualTo("DEFAULT.testTrigger");
        assertThat(log.getStartTime()).isEqualTo(startTime);
        assertThat(log.getEndTime()).isEqualTo(endTime);
        assertThat(log.getExecutionTimeMs()).isEqualTo(1000);
        assertThat(log.getExecState()).isEqualTo(LogTaskExecStateEnum.SUCCESS);
        assertThat(log.getErrorMessage()).isNull();
        assertThat(log.getStackTrace()).isNull();
        assertThat(log.getAttempt()).isEqualTo(1);
        assertThat(log.isFinalAttempt()).isTrue();
        assertThat(log.getExecuteTime()).isEqualTo(startTime);
    }

    @Test
    void failureFactory_setsFieldsCorrectly() {
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plusSeconds(2);

        TaskExecutionLog log = TaskExecutionLog.failure("DEFAULT.testJob", "DEFAULT.testTrigger",
                startTime, endTime, 2000, "error message", "stack trace", 3, true);

        assertThat(log.getId()).isNotNull();
        assertThat(log.getJobKey()).isEqualTo("DEFAULT.testJob");
        assertThat(log.getTriggerKey()).isEqualTo("DEFAULT.testTrigger");
        assertThat(log.getStartTime()).isEqualTo(startTime);
        assertThat(log.getEndTime()).isEqualTo(endTime);
        assertThat(log.getExecutionTimeMs()).isEqualTo(2000);
        assertThat(log.getExecState()).isEqualTo(LogTaskExecStateEnum.FAIL);
        assertThat(log.getErrorMessage()).isEqualTo("error message");
        assertThat(log.getStackTrace()).isEqualTo("stack trace");
        assertThat(log.getAttempt()).isEqualTo(3);
        assertThat(log.isFinalAttempt()).isTrue();
        assertThat(log.getExecuteTime()).isEqualTo(startTime);
    }

    @Test
    void successFactory_generatesUniqueId() {
        TaskExecutionLog log1 = TaskExecutionLog.success("job", "trigger",
                LocalDateTime.now(), LocalDateTime.now(), 100, 1, true);
        TaskExecutionLog log2 = TaskExecutionLog.success("job", "trigger",
                LocalDateTime.now(), LocalDateTime.now(), 100, 1, true);

        assertThat(log1.getId()).isNotEqualTo(log2.getId());
    }

    @Test
    void settersAndGetters() {
        TaskExecutionLog log = new TaskExecutionLog();
        log.setId("test-id");
        log.setJobKey("job-key");
        log.setTriggerKey("trigger-key");
        log.setStartTime(LocalDateTime.now());
        log.setEndTime(LocalDateTime.now());
        log.setExecutionTimeMs(500L);
        log.setExecState(LogTaskExecStateEnum.UNKNOWN);
        log.setErrorMessage("err");
        log.setStackTrace("stack");
        log.setAttempt(2);
        log.setFinalAttempt(false);
        log.setExecuteTime(LocalDateTime.now());

        assertThat(log.getId()).isEqualTo("test-id");
        assertThat(log.getJobKey()).isEqualTo("job-key");
        assertThat(log.getTriggerKey()).isEqualTo("trigger-key");
        assertThat(log.getExecutionTimeMs()).isEqualTo(500L);
        assertThat(log.getExecState()).isEqualTo(LogTaskExecStateEnum.UNKNOWN);
        assertThat(log.getErrorMessage()).isEqualTo("err");
        assertThat(log.getStackTrace()).isEqualTo("stack");
        assertThat(log.getAttempt()).isEqualTo(2);
        assertThat(log.isFinalAttempt()).isFalse();
    }
}