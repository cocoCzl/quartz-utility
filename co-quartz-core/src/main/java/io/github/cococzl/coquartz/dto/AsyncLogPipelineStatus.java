package io.github.cococzl.coquartz.dto;

/** Public, low-cardinality health snapshot for the asynchronous execution-log pipeline. */
public record AsyncLogPipelineStatus(int queueSize, long droppedCount, long writeFailureCount,
                                     long permanentFailureCount, long unflushedCount) {
}
