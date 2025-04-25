package com.coco.core;


public enum LogTaskExecStateEnum {

    EXEC_FAIL((byte) 0),
    EXEC_SUCCESS((byte) 1),
    UNKNOWN((byte) -99);

    private final byte code;

    LogTaskExecStateEnum(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static LogTaskExecStateEnum parse(byte code) {
        for (LogTaskExecStateEnum statusEnum : LogTaskExecStateEnum.values()) {
            if (statusEnum.code == code) {
                return statusEnum;
            }
        }
        throw new RuntimeException("Unknown execute status, " + code + ".");
    }
}
