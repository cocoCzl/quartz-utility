package com.coco.exception;

/**
 * 无效的任务执行状态异常
 * 当解析任务执行状态码时，如果状态码不在预定义范围内，则抛出此异常
 */
public class InvalidExecStateException extends QuartzUtilityException {

    // 无效的执行状态码
    public static final int INVALID_EXEC_STATE = -2001;

    /**
     * 构造函数
     *
     * @param execState 无效的执行状态码
     */
    public InvalidExecStateException(byte execState) {
        super(String.format("Invalid execution state code: %d", execState), INVALID_EXEC_STATE);
    }

    /**
     * 构造函数
     *
     * @param message 异常消息
     */
    public InvalidExecStateException(String message) {
        super(message, INVALID_EXEC_STATE);
    }

    /**
     * 构造函数
     *
     * @param message 异常消息
     * @param cause   原因
     */
    public InvalidExecStateException(String message, Throwable cause) {
        super(message, cause, INVALID_EXEC_STATE);
    }
}
