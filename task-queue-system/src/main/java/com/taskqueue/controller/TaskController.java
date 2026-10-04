package com.taskqueue.controller;

import com.taskqueue.dto.ApiResponse;
import com.taskqueue.dto.QueueStatsResponse;
import com.taskqueue.dto.TaskRequest;
import com.taskqueue.dto.TaskResponse;
import com.taskqueue.model.TaskAuditLog;
import com.taskqueue.model.TaskStatus;
import com.taskqueue.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * POST /api/v1/tasks
     * Submit a new task to the queue.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> submitTask(@Valid @RequestBody TaskRequest request) {
        TaskResponse response = taskService.submitTask(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Task submitted successfully. Task ID: " + response.getId()));
    }

    /**
     * GET /api/v1/tasks/{id}
     * Get a task by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(@PathVariable Long id) {
        try {
            TaskResponse response = taskService.getTaskById(id);
            return ResponseEntity.ok(ApiResponse.success(response, "Task found"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/v1/tasks
     * Get all tasks with pagination.
     * Optional filter: ?status=PENDING&page=0&size=10&sort=createdAt,desc
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<TaskResponse>>> getAllTasks(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<TaskResponse> result = (status != null)
                ? taskService.getTasksByStatus(status, pageable)
                : taskService.getAllTasks(pageable);

        return ResponseEntity.ok(ApiResponse.success(result,
                "Returned " + result.getNumberOfElements() + " tasks (page " + page + " of " + result.getTotalPages() + ")"));
    }

    /**
     * DELETE /api/v1/tasks/{id}
     * Cancel a PENDING task.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> cancelTask(@PathVariable Long id) {
        try {
            TaskResponse response = taskService.cancelTask(id);
            return ResponseEntity.ok(ApiResponse.success(response, "Task cancelled successfully"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * POST /api/v1/tasks/{id}/retry
     * Manually retry a FAILED task.
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<TaskResponse>> retryTask(@PathVariable Long id) {
        try {
            TaskResponse response = taskService.retryTask(id);
            return ResponseEntity.ok(ApiResponse.success(response, "Task re-queued for retry"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/v1/tasks/{id}/audit
     * Get full audit trail for a task.
     */
    @GetMapping("/{id}/audit")
    public ResponseEntity<ApiResponse<List<TaskAuditLog>>> getAuditLog(@PathVariable Long id) {
        try {
            List<TaskAuditLog> logs = taskService.getTaskAuditLog(id);
            return ResponseEntity.ok(ApiResponse.success(logs, logs.size() + " audit entries found"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/v1/tasks/stats
     * Get queue statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<QueueStatsResponse>> getStats() {
        QueueStatsResponse stats = taskService.getQueueStats();
        return ResponseEntity.ok(ApiResponse.success(stats, "Queue statistics retrieved"));
    }
}
