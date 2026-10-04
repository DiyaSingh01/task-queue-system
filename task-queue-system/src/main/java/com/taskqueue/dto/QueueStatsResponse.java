package com.taskqueue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueStatsResponse {

    private long totalTasks;
    private Map<String, Long> statusCounts;
    private int activeWorkers;
    private int maxWorkers;
    private long pendingTasks;
    private long inProgressTasks;
    private long completedTasks;
    private long failedTasks;
    private long retryPendingTasks;
}
