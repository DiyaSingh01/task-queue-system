package com.taskqueue.worker;

import com.taskqueue.model.Task;
import com.taskqueue.model.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Simulates actual task processing logic.
 * In a real application, this would dispatch to different handlers
 * based on taskType (e.g., send email, generate report, resize image).
 */
@Component
public class TaskProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TaskProcessor.class);
    private static final Random random = new Random();

    /**
     * Processes a task and returns a result message.
     * Simulates processing time and occasional failures for demo purposes.
     *
     * @param task The task to process
     * @return Result message on success
     * @throws TaskProcessingException on failure
     */
    public String process(Task task) throws TaskProcessingException {
        logger.info("Processing task [id={}, type={}, thread={}]",
                task.getId(), task.getTaskType(), Thread.currentThread().getName());

        try {
            return switch (task.getTaskType().toUpperCase()) {
                case "EMAIL" -> processEmailTask(task);
                case "REPORT" -> processReportTask(task);
                case "IMAGE_RESIZE" -> processImageResizeTask(task);
                case "DATA_EXPORT" -> processDataExportTask(task);
                case "NOTIFICATION" -> processNotificationTask(task);
                default -> processGenericTask(task);
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskProcessingException("Task interrupted: " + e.getMessage(), true);
        }
    }

    private String processEmailTask(Task task) throws InterruptedException, TaskProcessingException {
        logger.debug("Sending email for task id={}", task.getId());
        simulateWork(500, 1500);
        simulateRandomFailure(10); // 10% failure rate
        return "Email sent successfully. Payload processed: " + truncate(task.getPayload());
    }

    private String processReportTask(Task task) throws InterruptedException, TaskProcessingException {
        logger.debug("Generating report for task id={}", task.getId());
        simulateWork(1000, 3000);
        simulateRandomFailure(15); // 15% failure rate
        return "Report generated successfully. Size: " + (random.nextInt(500) + 100) + " KB";
    }

    private String processImageResizeTask(Task task) throws InterruptedException, TaskProcessingException {
        logger.debug("Resizing image for task id={}", task.getId());
        simulateWork(200, 800);
        simulateRandomFailure(5); // 5% failure rate
        return "Image resized. Dimensions: " + (random.nextInt(1920) + 100) + "x" + (random.nextInt(1080) + 100);
    }

    private String processDataExportTask(Task task) throws InterruptedException, TaskProcessingException {
        logger.debug("Exporting data for task id={}", task.getId());
        simulateWork(2000, 5000);
        simulateRandomFailure(20); // 20% failure rate - more complex task
        return "Data export completed. Records processed: " + (random.nextInt(10000) + 1000);
    }

    private String processNotificationTask(Task task) throws InterruptedException, TaskProcessingException {
        logger.debug("Sending notification for task id={}", task.getId());
        simulateWork(100, 400);
        simulateRandomFailure(8); // 8% failure rate
        return "Notification dispatched. Channel: " + (random.nextBoolean() ? "PUSH" : "SMS");
    }

    private String processGenericTask(Task task) throws InterruptedException, TaskProcessingException {
        logger.debug("Processing generic task id={}", task.getId());
        simulateWork(500, 2000);
        simulateRandomFailure(12);
        return "Task [" + task.getTaskType() + "] completed successfully.";
    }

    private void simulateWork(int minMs, int maxMs) throws InterruptedException {
        int delay = minMs + random.nextInt(maxMs - minMs);
        Thread.sleep(delay);
    }

    private void simulateRandomFailure(int failurePercent) throws TaskProcessingException {
        if (random.nextInt(100) < failurePercent) {
            throw new TaskProcessingException(
                    "Simulated processing failure (transient error). Will retry if retries remain.",
                    true // is retryable
            );
        }
    }

    private String truncate(String s) {
        if (s == null) return "null";
        return s.length() > 50 ? s.substring(0, 47) + "..." : s;
    }
}
