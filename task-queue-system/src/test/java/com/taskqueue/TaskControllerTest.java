package com.taskqueue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskqueue.controller.TaskController;
import com.taskqueue.dto.TaskRequest;
import com.taskqueue.dto.TaskResponse;
import com.taskqueue.model.TaskStatus;
import com.taskqueue.service.TaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/v1/tasks - Should submit task and return 201")
    void submitTask_ShouldReturn201() throws Exception {
        TaskRequest request = TaskRequest.builder()
                .taskType("EMAIL")
                .payload("{\"to\":\"a@b.com\"}")
                .priority(5)
                .maxRetries(3)
                .build();

        TaskResponse response = TaskResponse.builder()
                .id(1L)
                .taskType("EMAIL")
                .status(TaskStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .priority(5)
                .build();

        when(taskService.submitTask(any(TaskRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.taskType").value("EMAIL"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/v1/tasks - Should return 400 when taskType is blank")
    void submitTask_ShouldReturn400_WhenTaskTypeBlank() throws Exception {
        TaskRequest request = TaskRequest.builder()
                .taskType("")
                .build();

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/tasks/{id} - Should return task when found")
    void getTask_ShouldReturn200_WhenFound() throws Exception {
        TaskResponse response = TaskResponse.builder()
                .id(1L)
                .taskType("REPORT")
                .status(TaskStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();

        when(taskService.getTaskById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/v1/tasks/{id} - Should return 404 when not found")
    void getTask_ShouldReturn404_WhenNotFound() throws Exception {
        when(taskService.getTaskById(999L)).thenThrow(new NoSuchElementException("Task not found with id: 999"));

        mockMvc.perform(get("/api/v1/tasks/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/tasks - Should return paginated tasks")
    void getAllTasks_ShouldReturn200() throws Exception {
        List<TaskResponse> tasks = List.of(
                TaskResponse.builder().id(1L).taskType("EMAIL").status(TaskStatus.PENDING).createdAt(LocalDateTime.now()).build()
        );
        Page<TaskResponse> page = new PageImpl<>(tasks);
        when(taskService.getAllTasks(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1));
    }

    @Test
    @DisplayName("DELETE /api/v1/tasks/{id} - Should cancel task")
    void cancelTask_ShouldReturn200() throws Exception {
        TaskResponse response = TaskResponse.builder()
                .id(1L)
                .status(TaskStatus.CANCELLED)
                .createdAt(LocalDateTime.now())
                .build();

        when(taskService.cancelTask(1L)).thenReturn(response);

        mockMvc.perform(delete("/api/v1/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("POST /api/v1/tasks/{id}/retry - Should retry failed task")
    void retryTask_ShouldReturn200() throws Exception {
        TaskResponse response = TaskResponse.builder()
                .id(1L)
                .status(TaskStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(taskService.retryTask(1L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/tasks/1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }
}
