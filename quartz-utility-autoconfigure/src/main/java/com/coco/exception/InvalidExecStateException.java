package com.coco.exception;

public class InvalidExecStateException extends QuartzUtilityException {

    public InvalidExecStateException(int execState) {
        super(String.format("Invalid execution state code: %d", execState), ErrorCode.INVALID_EXEC_STATE);
    }

    public InvalidExecStateException(String message) {
        super(message, ErrorCode.INVALID_EXEC_STATE);
    }

    public InvalidExecStateException(String message, Throwable cause) {
        super(message, cause, ErrorCode.INVALID_EXEC_STATE);
    }
}