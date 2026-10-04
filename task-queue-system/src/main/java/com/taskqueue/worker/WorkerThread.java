package com.taskqueue.worker;

import com.taskqueue.model.Task;
import com.taskqueue.model.TaskStatus;
import com.taskqueue.repository.TaskAuditLogRepository;
import com.taskqueue.model.TaskAuditLog;
import com.taskqueue.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Runnable that picks up a single task and processes it.
 * Each worker thread is responsible for:
 * 1. Marking the task IN_PROGRESS
 * 2. Processing it via TaskProcessor
 * 3. Marking it COMPLETED, FAILED, or RETRY_PENDING
 * 4. Logging each state transition to the audit log
 */
public class WorkerThread implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(WorkerThread.class);

    private final Task task;
    private final TaskRepository taskRepository;
    private final TaskAuditLogRepository auditLogRepository;
    private final TaskProcessor taskProcessor;
    private final long initialRetryDelayMs;
    private final double retryMultiplier;

    public WorkerThread(Task task,
                        TaskRepository taskRepository,
                        TaskAuditLogRepository auditLogRepository,
                        TaskProcessor taskProcessor,
                        long initialRetryDelayMs,
                        double retryMultiplier) {
        this.task = task;
        this.taskRepository = taskRepository;
        this.auditLogRepository = auditLogRepository;
        this.taskProcessor = taskProcessor;
        this.initialRetryDelayMs = initialRetryDelayMs;
        this.retryMultiplier = retryMultiplier;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        logger.info("[{}] Picked up task id={} type={}", threadName, task.getId(), task.getTaskType());

        // Mark as IN_PROGRESS
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setStartedAt(LocalDateTime.now());
        task.setWorkerThreadId(threadName);
        taskRepository.save(task);
        logAudit(task.getId(), TaskStatus.PENDING, TaskStatus.IN_PROGRESS,
                "Task picked up by worker", threadName);

        try {
            // Execute actual processing
            String result = taskProcessor.process(task);

            // Mark as COMPLETED
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            task.setResultMessage(result);
            taskRepository.save(task);

            long durationMs = ChronoUnit.MILLIS.between(task.getStartedAt(), task.getCompletedAt());
            logger.info("[{}] Task id={} COMPLETED in {}ms", threadName, task.getId(), durationMs);
            logAudit(task.getId(), TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED,
                    "Task completed in " + durationMs + "ms. Result: " + result, threadName);

        } catch (TaskProcessingException e) {
            handleFailure(task, e, threadName);
        } catch (Exception e) {
            logger.error("[{}] Unexpected error processing task id={}", threadName, task.getId(), e);
            handleFailure(task, new TaskProcessingException(
                    "Unexpected error: " + e.getMessage(), false), threadName);
        }
    }

    private void handleFailure(Task task, TaskProcessingException e, String threadName) {
        logger.warn("[{}] Task id={} failed. Retryable={}, Attempt={}/{}",
                threadName, task.getId(), e.isRetryable(), task.getRetryCount(), task.getMaxRetries());

        task.setErrorMessage(e.getMessage());

        if (e.isRetryable() && task.getRetryCount() < task.getMaxRetries()) {
            // Schedule for retry with exponential backoff
            int newRetryCount = task.getRetryCount() + 1;
            long delayMs = (long) (initialRetryDelayMs * Math.pow(retryMultiplier, newRetryCount - 1));
            LocalDateTime nextRetry = LocalDateTime.now().plus(delayMs, ChronoUnit.MILLIS);

            task.setStatus(TaskStatus.RETRY_PENDING);
            task.setRetryCount(newRetryCount);
            task.setNextRetryAt(nextRetry);
            taskRepository.save(task);

            logger.info("[{}] Task id={} scheduled for retry #{} at {} (delay={}ms)",
                    threadName, task.getId(), newRetryCount, nextRetry, delayMs);
            logAudit(task.getId(), TaskStatus.IN_PROGRESS, TaskStatus.RETRY_PENDING,
                    String.format("Retry %d/%d scheduled at %s (backoff=%dms). Error: %s",
                            newRetryCount, task.getMaxRetries(), nextRetry, delayMs, e.getMessage()),
                    threadName);
        } else {
            // Permanently failed
            task.setStatus(TaskStatus.FAILED);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);

            logger.error("[{}] Task id={} permanently FAILED after {} retries. Error: {}",
                    threadName, task.getId(), task.getRetryCount(), e.getMessage());
            logAudit(task.getId(), TaskStatus.IN_PROGRESS, TaskStatus.FAILED,
                    "Permanently failed after " + task.getRetryCount() + " retries. Error: " + e.getMessage(),
                    threadName);
        }
    }

    private void logAudit(Long taskId, TaskStatus from, TaskStatus to, String message, String worker) {
        try {
            TaskAuditLog log = TaskAuditLog.builder()
                    .taskId(taskId)
                    .fromStatus(from)
                    .toStatus(to)
                    .message(message)
                    .workerThreadId(worker)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("Failed to write audit log for task {}: {}", taskId, e.getMessage());
        }
    }
}
