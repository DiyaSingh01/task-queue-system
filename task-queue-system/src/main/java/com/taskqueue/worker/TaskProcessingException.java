package com.taskqueue.worker;

public class TaskProcessingException extends Exception {

    private final boolean retryable;

    public TaskProcessingException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public TaskProcessingException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
