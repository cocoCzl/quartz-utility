package io.github.cococzl.coquartz.exception;

public class CoQuartzException extends RuntimeException {

    public CoQuartzException(String message) {
        super(message);
    }

    public CoQuartzException(String message, Throwable cause) {
        super(message, cause);
    }
}