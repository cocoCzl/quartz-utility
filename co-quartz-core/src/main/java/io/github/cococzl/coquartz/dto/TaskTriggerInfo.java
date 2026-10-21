package io.github.cococzl.coquartz.dto;

public record TaskTriggerInfo(String name, String group, String purpose, String state,
                              java.util.Date nextFireTime) { }
