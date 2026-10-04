package com.taskqueue.service;

import com.taskqueue.dto.QueueStatsResponse;
import com.taskqueue.dto.TaskRequest;
import com.taskqueue.dto.TaskResponse;
import com.taskqueue.model.Task;
import com.taskqueue.model.TaskAuditLog;
import com.taskqueue.model.TaskStatus;
import com.taskqueue.repository.TaskAuditLogRepository;
import com.taskqueue.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@Transactional
public class TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final TaskAuditLogRepository auditLogRepository;
    private final TaskQueueService taskQueueService;

    public TaskService(TaskRepository taskRepository,
                       TaskAuditLogRepository auditLogRepository,
                       TaskQueueService taskQueueService) {
        this.taskRepository = taskRepository;
        this.auditLogRepository = auditLogRepository;
        this.taskQueueService = taskQueueService;
    }

    /**
     * Submit a new task to the queue.
     */
    public TaskResponse submitTask(TaskRequest request) {
        Task task = Task.builder()
                .taskType(request.getTaskType().toUpperCase())
                .payload(request.getPayload())
                .priority(request.getPriority())
                .maxRetries(request.getMaxRetries())
                .status(TaskStatus.PENDING)
                .build();

        Task saved = taskRepository.save(task);
        logger.info("Task submitted: id={}, type={}, priority={}", saved.getId(), saved.getTaskType(), saved.getPriority());

        return toResponse(saved);
    }

    /**
     * Get task by ID.
     */
    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Task not found with id: " + id));
        return toResponse(task);
    }

    /**
     * Get all tasks with pagination.
     */
    @Transactional(readOnly = true)
    public Page<TaskResponse> getAllTasks(Pageable pageable) {
        return taskRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Get tasks by status with pagination.
     */
    @Transactional(readOnly = true)
    public Page<TaskResponse> getTasksByStatus(TaskStatus status, Pageable pageable) {
        return taskRepository.findByStatus(status, pageable).map(this::toResponse);
    }

    /**
     * Cancel a PENDING task.
     */
    public TaskResponse cancelTask(Long id) {
        Task task = taskRepository.findByIdAndStatus(id, TaskStatus.PENDING)
                .orElseThrow(() -> new IllegalStateException(
                        "Task id=" + id + " cannot be cancelled. It must be in PENDING state."));

        task.setStatus(TaskStatus.CANCELLED);
        task.setCompletedAt(LocalDateTime.now());
        task.setResultMessage("Cancelled by user");
        Task saved = taskRepository.save(task);

        // Audit log
        TaskAuditLog log = TaskAuditLog.builder()
                .taskId(id)
                .fromStatus(TaskStatus.PENDING)
                .toStatus(TaskStatus.CANCELLED)
                .message("Cancelled via API")
                .build();
        auditLogRepository.save(log);

        logger.info("Task id={} cancelled by user", id);
        return toResponse(saved);
    }

    /**
     * Retry a FAILED task manually.
     */
    public TaskResponse retryTask(Long id) {
        Task task = taskRepository.findByIdAndStatus(id, TaskStatus.FAILED)
                .orElseThrow(() -> new IllegalStateException(
                        "Task id=" + id + " cannot be retried. It must be in FAILED state."));

        TaskStatus oldStatus = task.getStatus();
        task.setStatus(TaskStatus.PENDING);
        task.setRetryCount(0);
        task.setErrorMessage(null);
        task.setNextRetryAt(null);
        task.setCompletedAt(null);
        Task saved = taskRepository.save(task);

        TaskAuditLog log = TaskAuditLog.builder()
                .taskId(id)
                .fromStatus(oldStatus)
                .toStatus(TaskStatus.PENDING)
                .message("Manually retried via API")
                .build();
        auditLogRepository.save(log);

        logger.info("Task id={} manually retried", id);
        return toResponse(saved);
    }

    /**
     * Get audit trail for a task.
     */
    @Transactional(readOnly = true)
    public List<TaskAuditLog> getTaskAuditLog(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new NoSuchElementException("Task not found with id: " + id);
        }
        return auditLogRepository.findByTaskIdOrderByTimestampAsc(id);
    }

    /**
     * Get queue statistics.
     */
    @Transactional(readOnly = true)
    public QueueStatsResponse getQueueStats() {
        long total = taskRepository.count();
        long pending = taskRepository.countByStatus(TaskStatus.PENDING);
        long inProgress = taskRepository.countByStatus(TaskStatus.IN_PROGRESS);
        long completed = taskRepository.countByStatus(TaskStatus.COMPLETED);
        long failed = taskRepository.countByStatus(TaskStatus.FAILED);
        long retryPending = taskRepository.countByStatus(TaskStatus.RETRY_PENDING);

        Map<String, Long> statusCounts = new HashMap<>();
        statusCounts.put("PENDING", pending);
        statusCounts.put("IN_PROGRESS", inProgress);
        statusCounts.put("COMPLETED", completed);
        statusCounts.put("FAILED", failed);
        statusCounts.put("RETRY_PENDING", retryPending);
        statusCounts.put("CANCELLED", taskRepository.countByStatus(TaskStatus.CANCELLED));

        return QueueStatsResponse.builder()
                .totalTasks(total)
                .statusCounts(statusCounts)
                .activeWorkers(taskQueueService.getActiveWorkers())
                .maxWorkers(taskQueueService.getMaxWorkers())
                .pendingTasks(pending)
                .inProgressTasks(inProgress)
                .completedTasks(completed)
                .failedTasks(failed)
                .retryPendingTasks(retryPending)
                .build();
    }

    // ---- Mapper ----

    private TaskResponse toResponse(Task task) {
        Long durationMs = null;
        if (task.getStartedAt() != null && task.getCompletedAt() != null) {
            durationMs = ChronoUnit.MILLIS.between(task.getStartedAt(), task.getCompletedAt());
        }

        return TaskResponse.builder()
                .id(task.getId())
                .taskType(task.getTaskType())
                .payload(task.getPayload())
                .status(task.getStatus())
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .resultMessage(task.getResultMessage())
                .errorMessage(task.getErrorMessage())
                .retryCount(task.getRetryCount())
                .maxRetries(task.getMaxRetries())
                .priority(task.getPriority())
                .workerThreadId(task.getWorkerThreadId())
                .nextRetryAt(task.getNextRetryAt())
                .processingDurationMs(durationMs)
                .build();
    }
}
