package com.coco.core;

import com.coco.enums.LogTaskExecStateEnum;
import java.sql.Timestamp;
import java.util.Optional;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class BaseAbstractQuartzJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(BaseAbstractQuartzJob.class);

    @Autowired
    @Qualifier("quartzJdbcTemplate")
    private JdbcTemplate quartzJdbcTemplate;

    /**
     * 抽象方法，子类需要实现具体的任务执行逻辑
     *
     * @param context 任务执行上下文
     * @throws Throwable 可能抛出的异常
     */
    protected abstract void executeQuartz(JobExecutionContext context) throws Throwable;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        long startTime = System.currentTimeMillis();
        String jobKey = context.getJobDetail().getKey().toString();
        String triggerKey = context.getTrigger().getKey().toString();

        logger.info("开始执行任务: jobKey={}, triggerKey={}", jobKey, triggerKey);

        byte execState = LogTaskExecStateEnum.EXEC_SUCCESS.getCode();
        String errorMessage = null;
        String stackTrace = null;
        try {
            // 调用抽象方法执行具体任务
            executeQuartz(context);
        } catch (Throwable e) {
            execState = LogTaskExecStateEnum.EXEC_FAIL.getCode();
            errorMessage = e.getMessage();
            stackTrace = getStackTraceAsString(e);
            logger.error("任务执行失败: jobKey={}, triggerKey={}, error={}", jobKey, triggerKey, e.getMessage(), e);
            // 将捕获的异常封装为JobExecutionException抛出
            throw new JobExecutionException(e);
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            logger.info("任务执行完成: jobKey={}, triggerKey={}, executionTime={}ms, status={}", 
                jobKey, triggerKey, executionTime, execState == LogTaskExecStateEnum.EXEC_SUCCESS.getCode() ? "SUCCESS" : "FAILED");

            // 记录详细日志
            insertDetailedTaskLog(jobKey, triggerKey, execState, errorMessage, stackTrace, executionTime);
        }
    }

    /**
     * 获取异常的完整堆栈跟踪
     */
    private String getStackTraceAsString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.toString()).append("\n");
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        
        Throwable cause = throwable.getCause();
        if (cause != null) {
            sb.append("Caused by: ");
            sb.append(getStackTraceAsString(cause));
        }
        
        return sb.toString();
    }

    private Optional<Integer> checkExists(String jobKey, String triggerKey) {
        return quartzJdbcTemplate.query(SELECT_SQL, (rs, rowNum) -> rs.getInt("id"), jobKey,
                triggerKey).stream().findFirst();
    }

    /**
     * 根据任务日志的 ID 更新执行状态、错误信息和执行时间
     *
     * @param id            任务日志的 ID
     * @param execState     执行状态，0 失败，1 成功
     * @param errorMessage  错误信息
     * @return 更新的记录数
     */
    private int updateTaskLog(byte execState, String errorMessage, int id) {
        return quartzJdbcTemplate.update(UPDATE_SQL, execState, errorMessage,
                new Timestamp(System.currentTimeMillis()), id);
    }

    /**
     * 插入任务日志记录
     *
     * @param jobKey        job 标识
     * @param triggerKey    trigger 标识
     * @param execState     执行状态（0 失败，1 成功）
     * @param errorMessage  错误信息（可为 null）
     * @return 插入操作影响的行数
     */
    public int insertTaskLog(String jobKey, String triggerKey, int execState, String errorMessage) {
        return quartzJdbcTemplate.update(INSERT_SQL, jobKey, triggerKey, execState, errorMessage,
                new Timestamp(System.currentTimeMillis()));
    }

    /**
     * 插入详细的任务日志记录，包含执行时间等信息
     */
    public void insertDetailedTaskLog(String jobKey, String triggerKey, int execState,
            String errorMessage, String stackTrace, long executionTimeInMs) {
        quartzJdbcTemplate.update(INSERT_DETAILED_SQL, jobKey, triggerKey, execState,
                errorMessage, stackTrace, executionTimeInMs,
                new Timestamp(System.currentTimeMillis()));
    }

    private static final String INSERT_SQL = "INSERT INTO quartz_task_log (job_key, trigger_key, exec_state, error_message, execute_time) VALUES (?, ?, ?, ?, ?)";
    private static final String UPDATE_SQL = "UPDATE quartz_task_log SET exec_state = ?, error_message = ?, execute_time = ? WHERE id = ?";
    private static final String SELECT_SQL = "SELECT id FROM quartz_task_log WHERE job_key = ? AND trigger_key = ?";
    private static final String INSERT_DETAILED_SQL = "INSERT INTO quartz_task_log (job_key, trigger_key, exec_state, error_message, stack_trace, execution_time_ms, execute_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
}

