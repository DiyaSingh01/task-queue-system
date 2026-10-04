package com.taskqueue.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRequest {

    @NotBlank(message = "Task type is required")
    private String taskType;

    private String payload;

    @Min(value = 1, message = "Priority must be between 1 and 10")
    @Max(value = 10, message = "Priority must be between 1 and 10")
    @Builder.Default
    private int priority = 5;

    @Min(value = 0, message = "Max retries must be non-negative")
    @Max(value = 5, message = "Max retries cannot exceed 5")
    @Builder.Default
    private int maxRetries = 3;
}
