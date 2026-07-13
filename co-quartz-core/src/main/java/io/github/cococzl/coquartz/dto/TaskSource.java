package io.github.cococzl.coquartz.dto;

/**
 * The ownership boundary of a task visible through the operations API.
 */
public enum TaskSource {
    /** A task definition reconciled from application code. */
    DECLARATIVE,
    /** A task created through Co-Quartz's dynamic scheduling API. */
    DYNAMIC,
    /** A task managed directly by Quartz or another library. */
    EXTERNAL
}
