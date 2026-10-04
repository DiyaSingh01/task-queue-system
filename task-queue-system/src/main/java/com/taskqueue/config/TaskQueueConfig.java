package com.taskqueue.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class TaskQueueConfig {

    @Value("${taskqueue.worker.thread-count:5}")
    private int workerThreadCount;

    @Bean(name = "workerExecutor", destroyMethod = "shutdown")
    public ExecutorService workerExecutorService() {
        return Executors.newFixedThreadPool(workerThreadCount, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("TaskWorker-" + thread.getId());
            thread.setDaemon(false);
            return thread;
        });
    }

    @Bean(name = "scheduledExecutor", destroyMethod = "shutdown")
    public ScheduledExecutorService scheduledExecutorService() {
        return Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("TaskScheduler-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    }

    public int getWorkerThreadCount() {
        return workerThreadCount;
    }
}
