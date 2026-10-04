package com.taskqueue.service;

import com.taskqueue.config.TaskQueueConfig;
import com.taskqueue.model.Task;
import com.taskqueue.model.TaskStatus;
import com.taskqueue.repository.TaskAuditLogRepository;
import com.taskqueue.repository.TaskRepository;
import com.taskqueue.worker.TaskProcessor;
import com.taskqueue.worker.WorkerThread;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TaskQueueService {

    private static final Logger logger = LoggerFactory.getLogger(TaskQueueService.class);

    private final TaskRepository taskRepository;
    private final TaskAuditLogRepository auditLogRepository;
    private final TaskProcessor taskProcessor;
    private final ExecutorService workerExecutor;
    private final TaskQueueConfig config;

    @Value("${taskqueue.retry.initial-delay-ms:2000}")
    private long initialRetryDelayMs;

    @Value("${taskqueue.retry.multiplier:2.0}")
    private double retryMultiplier;

    @Value("${taskqueue.worker.thread-count:5}")
    private int maxWorkers;

    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private Semaphore workerSemaphore;

    public TaskQueueService(TaskRepository taskRepository,
                             TaskAuditLogRepository auditLogRepository,
                             TaskProcessor taskProcessor,
                             ExecutorService workerExecutor,
                             TaskQueueConfig config) {
        this.taskRepository = taskRepository;
        this.auditLogRepository = auditLogRepository;
        this.taskProcessor = taskProcessor;
        this.workerExecutor = workerExecutor;
        this.config = config;
    }

    @PostConstruct
    public void init() {
        this.workerSemaphore = new Semaphore(maxWorkers);
        logger.info("TaskQueueService initialized with {} worker threads", maxWorkers);

        // Reset any IN_PROGRESS tasks from a previous crashed run
        recoverStuckTasks();
    }

    /**
     * Poll for PENDING tasks and dispatch them to worker threads.
     * Runs every 1 second.
     */
    @Scheduled(fixedDelayString = "${taskqueue.worker.poll-interval-ms:1000}")
    @Transactional
    public void pollAndDispatch() {
        if (workerSemaphore.availablePermits() == 0) {
            logger.debug("All {} workers busy. Skipping poll.", maxWorkers);
            return;
        }

        int availableSlots = workerSemaphore.availablePermits();
        List<Task> pendingTasks = taskRepository.findPendingTasksOrderedByPriority(
                PageRequest.of(0, availableSlots));

        for (Task task : pendingTasks) {
            if (workerSemaphore.tryAcquire()) {
                activeWorkers.incrementAndGet();
                workerExecutor.submit(() -> {
                    try {
                        WorkerThread worker = new WorkerThread(
                                task, taskRepository, auditLogRepository,
                                taskProcessor, initialRetryDelayMs, retryMultiplier);
                        worker.run();
                    } finally {
                        activeWorkers.decrementAndGet();
                        workerSemaphore.release();
                    }
                });
            }
        }
    }

    /**
     * Re-queue tasks that are past their retry time.
     * Runs every 5 seconds.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void requeueRetryTasks() {
        List<Task> retryTasks = taskRepository.findTasksDueForRetry(LocalDateTime.now());
        for (Task task : retryTasks) {
            logger.info("Re-queuing task id={} for retry #{}", task.getId(), task.getRetryCount());
            task.setStatus(TaskStatus.PENDING);
            taskRepository.save(task);
        }
        if (!retryTasks.isEmpty()) {
            logger.info("Re-queued {} tasks for retry", retryTasks.size());
        }
    }

    /**
     * Recover tasks stuck in IN_PROGRESS (e.g. after server crash).
     */
    private void recoverStuckTasks() {
        List<Task> stuckTasks = taskRepository.findByStatusOrderByPriorityDescCreatedAtAsc(TaskStatus.IN_PROGRESS);
        if (!stuckTasks.isEmpty()) {
            logger.warn("Found {} stuck IN_PROGRESS tasks. Resetting to PENDING.", stuckTasks.size());
            stuckTasks.forEach(task -> {
                task.setStatus(TaskStatus.PENDING);
                task.setWorkerThreadId(null);
            });
            taskRepository.saveAll(stuckTasks);
        }
    }

    public int getActiveWorkers() {
        return activeWorkers.get();
    }

    public int getMaxWorkers() {
        return maxWorkers;
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down TaskQueueService...");
        workerExecutor.shutdown();
        try {
            if (!workerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                workerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("TaskQueueService shutdown complete.");
    }
}
