package com.coco.exception;

public class QuartzUtilityException extends RuntimeException {

    // 参数异常
    public final static int PARAMETER_ABNORMAL = -1001;

    private final int code;

    public QuartzUtilityException(String message, int code) {
        super(message);
        this.code = code;
    }

    public QuartzUtilityException(String message, Throwable cause, int code) {
        super(message, cause);
        this.code = code;
    }

    public QuartzUtilityException(Throwable cause, int code) {
        super(cause);
        this.code = code;
    }

    public QuartzUtilityException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace, int code) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.code = code;
    }

}
