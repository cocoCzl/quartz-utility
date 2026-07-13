package io.github.cococzl.coquartz.exception;

/**
 * Raised when an operations API attempts to change a task definition owned by application code.
 */
public class CodeOwnedTaskModificationException extends CoQuartzException {

    public CodeOwnedTaskModificationException(String message) {
        super(message);
    }
}
