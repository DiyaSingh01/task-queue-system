package com.taskqueue;

import com.taskqueue.dto.TaskRequest;
import com.taskqueue.dto.TaskResponse;
import com.taskqueue.model.Task;
import com.taskqueue.model.TaskStatus;
import com.taskqueue.repository.TaskAuditLogRepository;
import com.taskqueue.repository.TaskRepository;
import com.taskqueue.service.TaskQueueService;
import com.taskqueue.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskAuditLogRepository auditLogRepository;

    @Mock
    private TaskQueueService taskQueueService;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, auditLogRepository, taskQueueService);
    }

    @Test
    @DisplayName("Should submit task successfully")
    void submitTask_ShouldReturnTaskResponse() {
        TaskRequest request = TaskRequest.builder()
                .taskType("EMAIL")
                .payload("{\"to\":\"test@example.com\"}")
                .priority(7)
                .maxRetries(3)
                .build();

        Task savedTask = Task.builder()
                .id(1L)
                .taskType("EMAIL")
                .payload("{\"to\":\"test@example.com\"}")
                .status(TaskStatus.PENDING)
                .priority(7)
                .maxRetries(3)
                .createdAt(LocalDateTime.now())
                .build();

        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        TaskResponse response = taskService.submitTask(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("EMAIL", response.getTaskType());
        assertEquals(TaskStatus.PENDING, response.getStatus());
        assertEquals(7, response.getPriority());
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    @DisplayName("Should return task by ID")
    void getTaskById_ShouldReturnTask_WhenExists() {
        Task task = Task.builder()
                .id(1L)
                .taskType("REPORT")
                .status(TaskStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        TaskResponse response = taskService.getTaskById(1L);

        assertEquals(1L, response.getId());
        assertEquals(TaskStatus.COMPLETED, response.getStatus());
    }

    @Test
    @DisplayName("Should throw exception when task not found")
    void getTaskById_ShouldThrow_WhenNotFound() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> taskService.getTaskById(999L));
    }

    @Test
    @DisplayName("Should cancel a PENDING task")
    void cancelTask_ShouldUpdateStatusToCancelled() {
        Task task = Task.builder()
                .id(1L)
                .taskType("EMAIL")
                .status(TaskStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(taskRepository.findByIdAndStatus(1L, TaskStatus.PENDING)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskResponse response = taskService.cancelTask(1L);

        assertEquals(TaskStatus.CANCELLED, response.getStatus());
        verify(auditLogRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should throw exception when cancelling non-PENDING task")
    void cancelTask_ShouldThrow_WhenNotPending() {
        when(taskRepository.findByIdAndStatus(1L, TaskStatus.PENDING)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> taskService.cancelTask(1L));
    }

    @Test
    @DisplayName("Should retry a FAILED task")
    void retryTask_ShouldResetAndRequeue() {
        Task task = Task.builder()
                .id(1L)
                .taskType("DATA_EXPORT")
                .status(TaskStatus.FAILED)
                .retryCount(3)
                .errorMessage("Processing failed")
                .createdAt(LocalDateTime.now())
                .build();

        when(taskRepository.findByIdAndStatus(1L, TaskStatus.FAILED)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskResponse response = taskService.retryTask(1L);

        assertEquals(TaskStatus.PENDING, response.getStatus());
        assertEquals(0, response.getRetryCount());
        assertNull(response.getErrorMessage());
    }

    @Test
    @DisplayName("Should return paginated tasks")
    void getAllTasks_ShouldReturnPaginatedResults() {
        List<Task> tasks = List.of(
                Task.builder().id(1L).taskType("EMAIL").status(TaskStatus.PENDING).createdAt(LocalDateTime.now()).build(),
                Task.builder().id(2L).taskType("REPORT").status(TaskStatus.COMPLETED).createdAt(LocalDateTime.now()).build()
        );
        Page<Task> page = new PageImpl<>(tasks);
        when(taskRepository.findAll(any(PageRequest.class))).thenReturn(page);

        Page<TaskResponse> result = taskService.getAllTasks(PageRequest.of(0, 10));

        assertEquals(2, result.getContent().size());
        assertEquals("EMAIL", result.getContent().get(0).getTaskType());
    }
}
