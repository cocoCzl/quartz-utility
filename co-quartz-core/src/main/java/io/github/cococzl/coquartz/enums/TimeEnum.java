package io.github.cococzl.coquartz.enums;

public enum TimeEnum {

    SECONDS("second"),
    MINUTES("minute"),
    HOURS("hour");

    private final String description;

    TimeEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}