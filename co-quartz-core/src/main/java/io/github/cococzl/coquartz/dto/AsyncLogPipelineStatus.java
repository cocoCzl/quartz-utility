package io.github.cococzl.coquartz.dto;

/** Bounded queue and in-flight accounting; unflushed is work unresolved when shutdown returned. */
public record AsyncLogPipelineStatus(int queueSize, long droppedCount, long writeFailureCount,
                                     long permanentFailureCount, long unflushedCount,
                                     int inFlightCount, long persistedCount, long acceptedCount) {
    public AsyncLogPipelineStatus(int queueSize, long droppedCount, long writeFailureCount,
                                  long permanentFailureCount, long unflushedCount) {
        this(queueSize, droppedCount, writeFailureCount, permanentFailureCount, unflushedCount, 0, 0, 0);
    }
}
