package com.taskqueue.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "task_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus toStatus;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(length = 50)
    private String workerThreadId;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
