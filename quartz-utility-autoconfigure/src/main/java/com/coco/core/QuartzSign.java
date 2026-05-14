package com.coco.core;

public class QuartzSign {
    public static final String GROUP = "DEFAULT";
    public static final String JOB_KEY_PREFIX = "JOB_";
    public static final String TRIGGER_KEY_PREFIX = "TRIGGER_";

    public static final String INSERT_DETAILED_SQL = "INSERT INTO quartz_task_log (job_key, trigger_key, exec_state, error_message, stack_trace, execution_time_ms, execute_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
}
