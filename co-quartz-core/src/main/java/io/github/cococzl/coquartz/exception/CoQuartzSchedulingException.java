package io.github.cococzl.coquartz.exception;

public class CoQuartzSchedulingException extends CoQuartzException {

    public CoQuartzSchedulingException(String message) {
        super(message);
    }

    public CoQuartzSchedulingException(String message, Throwable cause) {
        super(message, cause);
    }
}