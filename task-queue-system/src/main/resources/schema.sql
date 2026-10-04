-- Run this to set up the database manually (Spring Boot will auto-create via JPA)
-- This file is for reference / manual setup

CREATE DATABASE IF NOT EXISTS taskqueuedb;
USE taskqueuedb;

CREATE TABLE IF NOT EXISTS tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_type VARCHAR(100) NOT NULL,
    payload TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL,
    started_at DATETIME,
    completed_at DATETIME,
    result_message TEXT,
    error_message TEXT,
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    next_retry_at DATETIME,
    worker_thread_id VARCHAR(50),
    priority INT DEFAULT 5,
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS task_audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    from_status VARCHAR(20) NOT NULL,
    to_status VARCHAR(20) NOT NULL,
    message TEXT,
    worker_thread_id VARCHAR(50),
    timestamp DATETIME NOT NULL,
    INDEX idx_task_id (task_id)
);
