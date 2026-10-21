package io.github.cococzl.coquartz.event;

/** Stable association for a single attempt; node-local events are not a cluster aggregate. */
public record TaskExecutionCorrelation(String executionId, int attempt, String schedulerInstanceId, boolean finalAttempt) { }
