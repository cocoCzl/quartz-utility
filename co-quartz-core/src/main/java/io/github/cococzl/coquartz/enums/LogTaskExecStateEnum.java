package io.github.cococzl.coquartz.enums;

public enum LogTaskExecStateEnum {

    FAIL(0),
    SUCCESS(1),
    STARTED(2),
    INTERRUPTED(3),
    UNKNOWN(-99);

    private final int code;

    LogTaskExecStateEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static LogTaskExecStateEnum parse(int code) {
        for (LogTaskExecStateEnum state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
