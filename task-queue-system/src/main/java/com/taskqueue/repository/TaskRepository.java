package com.taskqueue.repository;

import com.taskqueue.model.Task;
import com.taskqueue.model.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    Page<Task> findByStatus(TaskStatus status, Pageable pageable);

    Page<Task> findByTaskType(String taskType, Pageable pageable);

    List<Task> findByStatusOrderByPriorityDescCreatedAtAsc(TaskStatus status);

    @Query("SELECT t FROM Task t WHERE t.status = 'RETRY_PENDING' AND t.nextRetryAt <= :now ORDER BY t.priority DESC, t.createdAt ASC")
    List<Task> findTasksDueForRetry(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Task t SET t.status = :status WHERE t.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.status = :status")
    long countByStatus(@Param("status") TaskStatus status);

    @Query("SELECT t.status, COUNT(t) FROM Task t GROUP BY t.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT t FROM Task t WHERE t.status = 'PENDING' ORDER BY t.priority DESC, t.createdAt ASC")
    List<Task> findPendingTasksOrderedByPriority(Pageable pageable);

    Optional<Task> findByIdAndStatus(Long id, TaskStatus status);
}
