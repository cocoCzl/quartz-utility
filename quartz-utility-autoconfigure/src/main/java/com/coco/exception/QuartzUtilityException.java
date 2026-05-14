package com.coco.exception;

public class QuartzUtilityException extends RuntimeException {

    private final ErrorCode errorCode;

    public QuartzUtilityException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public QuartzUtilityException(String message, Throwable cause, ErrorCode errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public QuartzUtilityException(Throwable cause, ErrorCode errorCode) {
        super(cause);
        this.errorCode = errorCode;
    }

    public QuartzUtilityException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace, ErrorCode errorCode) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * @deprecated Use {@link #getErrorCode()} instead
     */
    @Deprecated
    public int getCode() {
        return errorCode.getCode();
    }

    public enum ErrorCode {
        PARAMETER_ABNORMAL(-1001),
        INVALID_EXEC_STATE(-2001);

        private final int code;

        ErrorCode(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }
}