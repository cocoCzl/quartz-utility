package com.coco.enums;

import com.coco.exception.InvalidExecStateException;

/**
 * 任务执行状态枚举
 */
public enum LogTaskExecStateEnum {

    EXEC_FAIL(0),
    EXEC_SUCCESS(1),
    UNKNOWN(-99);

    private final int code;

    LogTaskExecStateEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * 根据状态码解析枚举值
     *
     * @param code 状态码
     * @return 对应的枚举值
     * @throws InvalidExecStateException 如果状态码无效
     */
    public static LogTaskExecStateEnum parse(int code) {
        for (LogTaskExecStateEnum statusEnum : LogTaskExecStateEnum.values()) {
            if (statusEnum.code == code) {
                return statusEnum;
            }
        }
        throw new InvalidExecStateException(code);
    }
}