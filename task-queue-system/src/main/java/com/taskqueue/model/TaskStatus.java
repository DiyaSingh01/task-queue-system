package com.taskqueue.model;

public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    RETRY_PENDING,
    CANCELLED
}
