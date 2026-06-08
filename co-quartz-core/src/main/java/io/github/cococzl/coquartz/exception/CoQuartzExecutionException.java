package io.github.cococzl.coquartz.exception;

public class CoQuartzExecutionException extends CoQuartzException {

    public CoQuartzExecutionException(String message) {
        super(message);
    }

    public CoQuartzExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}