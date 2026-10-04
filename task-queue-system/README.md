# Distributed Task Queue System

A production-style **asynchronous task queue** built with Java and Spring Boot. Tasks are submitted via REST APIs, processed by a configurable pool of worker threads, and tracked with full status history and retry support using exponential backoff.

---

## Features

- **REST API** for task submission, status tracking, cancellation, and manual retry
- **Multithreaded worker pool** — configurable number of concurrent worker threads
- **Priority queue** — higher-priority tasks are picked up first
- **Automatic retry with exponential backoff** — failed tasks are retried after increasing delays
- **Full audit trail** — every status transition is logged with timestamp and worker thread info
- **Queue statistics** — live snapshot of task counts by status and active worker count
- **Crash recovery** — on startup, tasks stuck in `IN_PROGRESS` are reset to `PENDING`
- **MySQL** for persistence; H2 for tests

---

## Tech Stack

| Layer      | Technology                          |
|------------|-------------------------------------|
| Language   | Java 17                             |
| Framework  | Spring Boot 3.2                     |
| Database   | MySQL 8                             |
| ORM        | Spring Data JPA / Hibernate         |
| Build      | Maven                               |
| Testing    | JUnit 5, Mockito, MockMvc           |

---

## Architecture

```
Client (REST)
     │
     ▼
TaskController  ──►  TaskService  ──►  TaskRepository (MySQL)
                          │
                          ▼
                  TaskQueueService (Scheduler)
                          │
                    ┌─────┴──────┐
                    ▼            ▼
             WorkerThread   WorkerThread   ...  (thread pool)
                    │
                    ▼
             TaskProcessor
             (EMAIL / REPORT / IMAGE_RESIZE / DATA_EXPORT / NOTIFICATION)
```

**Task Lifecycle:**

```
PENDING → IN_PROGRESS → COMPLETED
                      ↘ RETRY_PENDING → PENDING (→ IN_PROGRESS → ...)
                      ↘ FAILED
                      ↘ CANCELLED (from PENDING only)
```

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- MySQL 8.x running locally

### 1. Clone the repository

```bash
git clone https://github.com/your-username/task-queue-system.git
cd task-queue-system
```

### 2. Set up MySQL

```sql
CREATE DATABASE taskqueuedb;
```

Or just run the app — Spring Boot will auto-create the schema via JPA.

### 3. Configure database credentials

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/taskqueuedb?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=your_password
```

### 4. Build and run

```bash
mvn clean install
mvn spring-boot:run
```

Server starts at `http://localhost:8080`

---

## API Reference

### Submit a Task

```http
POST /api/v1/tasks
Content-Type: application/json

{
  "taskType": "EMAIL",
  "payload": "{\"to\": \"user@example.com\", \"subject\": \"Hello\"}",
  "priority": 8,
  "maxRetries": 3
}
```

**Task types supported:** `EMAIL`, `REPORT`, `IMAGE_RESIZE`, `DATA_EXPORT`, `NOTIFICATION`

**Response:**
```json
{
  "success": true,
  "message": "Task submitted successfully. Task ID: 42",
  "data": {
    "id": 42,
    "taskType": "EMAIL",
    "status": "PENDING",
    "priority": 8,
    "createdAt": "2024-01-15T10:30:00"
  }
}
```

---

### Get Task Status

```http
GET /api/v1/tasks/{id}
```

---

### List All Tasks (with filters & pagination)

```http
GET /api/v1/tasks?status=PENDING&page=0&size=10&sortBy=createdAt&direction=desc
```

**Query params:**
| Param | Default | Description |
|-------|---------|-------------|
| `status` | *(all)* | Filter by status: PENDING, IN_PROGRESS, COMPLETED, FAILED, RETRY_PENDING, CANCELLED |
| `page` | 0 | Page number |
| `size` | 10 | Items per page |
| `sortBy` | createdAt | Sort field |
| `direction` | desc | asc or desc |

---

### Cancel a Task

```http
DELETE /api/v1/tasks/{id}
```

Only works for tasks in `PENDING` state.

---

### Manually Retry a Failed Task

```http
POST /api/v1/tasks/{id}/retry
```

Only works for tasks in `FAILED` state.

---

### Get Task Audit Trail

```http
GET /api/v1/tasks/{id}/audit
```

Returns every status transition for the task with timestamps and worker thread info.

---

### Queue Statistics

```http
GET /api/v1/tasks/stats
```

```json
{
  "data": {
    "totalTasks": 250,
    "pendingTasks": 18,
    "inProgressTasks": 5,
    "completedTasks": 210,
    "failedTasks": 12,
    "retryPendingTasks": 5,
    "activeWorkers": 5,
    "maxWorkers": 5,
    "statusCounts": {
      "PENDING": 18,
      "IN_PROGRESS": 5,
      "COMPLETED": 210,
      "FAILED": 12,
      "RETRY_PENDING": 5,
      "CANCELLED": 0
    }
  }
}
```

---

## Configuration

All tunable parameters are in `application.properties`:

```properties
# Number of parallel worker threads
taskqueue.worker.thread-count=5

# How often the scheduler polls for new tasks (ms)
taskqueue.worker.poll-interval-ms=1000

# Retry settings (exponential backoff)
taskqueue.retry.max-attempts=3
taskqueue.retry.initial-delay-ms=2000
taskqueue.retry.multiplier=2.0
```

With `initial-delay-ms=2000` and `multiplier=2.0`:
- Retry 1 → wait 2s
- Retry 2 → wait 4s
- Retry 3 → wait 8s

---

## Running Tests

```bash
mvn test
```

Tests include:
- Unit tests for `TaskService` with Mockito
- Controller tests with MockMvc
- Runs against in-memory H2 database (no MySQL needed for tests)

---

## Project Structure

```
src/
├── main/
│   ├── java/com/taskqueue/
│   │   ├── TaskQueueApplication.java     # Entry point
│   │   ├── config/
│   │   │   └── TaskQueueConfig.java      # Thread pool & executor config
│   │   ├── controller/
│   │   │   └── TaskController.java       # REST endpoints
│   │   ├── dto/
│   │   │   ├── TaskRequest.java
│   │   │   ├── TaskResponse.java
│   │   │   ├── QueueStatsResponse.java
│   │   │   └── ApiResponse.java
│   │   ├── model/
│   │   │   ├── Task.java                 # JPA entity
│   │   │   ├── TaskStatus.java           # Enum: PENDING, IN_PROGRESS, etc.
│   │   │   └── TaskAuditLog.java         # State transition log entity
│   │   ├── repository/
│   │   │   ├── TaskRepository.java
│   │   │   └── TaskAuditLogRepository.java
│   │   ├── service/
│   │   │   ├── TaskService.java          # Business logic
│   │   │   └── TaskQueueService.java     # Scheduler + worker dispatch
│   │   ├── util/
│   │   │   └── GlobalExceptionHandler.java
│   │   └── worker/
│   │       ├── WorkerThread.java         # Picks up and executes tasks
│   │       ├── TaskProcessor.java        # Simulates task processing by type
│   │       └── TaskProcessingException.java
│   └── resources/
│       ├── application.properties
│       └── schema.sql                    # Reference SQL
└── test/
    └── java/com/taskqueue/
        ├── TaskServiceTest.java
        └── TaskControllerTest.java
```

---

## Sample Workflow

```bash
# 1. Submit a task
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{"taskType":"REPORT","payload":"{\"reportId\":\"Q4-2024\"}","priority":9}'

# 2. Check its status
curl http://localhost:8080/api/v1/tasks/1

# 3. View audit trail
curl http://localhost:8080/api/v1/tasks/1/audit

# 4. Queue stats
curl http://localhost:8080/api/v1/tasks/stats
```

---

## Future Improvements

- Add JWT-based authentication
- Integrate with Redis for a distributed queue (multiple app instances)
- Add task scheduling — run at a specific time (cron-style)
- Prometheus metrics endpoint for monitoring
- Dead Letter Queue (DLQ) for permanently failed tasks
- WebSocket support for real-time status updates
