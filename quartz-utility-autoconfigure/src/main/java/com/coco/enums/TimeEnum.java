package com.coco.enums;

public enum TimeEnum {
    SECONDS("second"),
    MINUTES("minute"),
    HOURS("hour");

    TimeEnum(String message) {
        this.message = message;
    }

    private final String message;

    public String getMessage() {
        return message;
    }
}
