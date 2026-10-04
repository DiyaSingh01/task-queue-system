package com.taskqueue.dto;

import com.taskqueue.model.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    private Long id;
    private String taskType;
    private String payload;
    private TaskStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String resultMessage;
    private String errorMessage;
    private int retryCount;
    private int maxRetries;
    private int priority;
    private String workerThreadId;
    private LocalDateTime nextRetryAt;

    // Computed field - duration in milliseconds
    private Long processingDurationMs;
}
