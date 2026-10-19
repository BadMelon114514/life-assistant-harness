/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.HarnessTask;
import com.badmelon.farming.mapper.HarnessTaskMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TaskService {
    private final HarnessTaskMapper taskMapper;

    public TaskService(HarnessTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    public List<HarnessTask> findAll() {
        return this.taskMapper.findAll();
    }

    public HarnessTask findByKey(String key) {
        return this.taskMapper.findByKey(key);
    }

    public HarnessTask create(String title, String description, List<String> blockedBy) {
        HarnessTask task = new HarnessTask();
        task.setTaskKey("task_" + UUID.randomUUID().toString().substring(0, 8));
        task.setTitle(title);
        task.setDescription(description);
        task.setStatus("pending");
        task.setProgress(0);
        if (blockedBy != null && !blockedBy.isEmpty()) {
            task.setBlockedBy(String.join((CharSequence)",", blockedBy));
        }
        this.taskMapper.insert(task);
        return task;
    }

    public HarnessTask create(HarnessTask task) {
        if (task == null) {
            return null;
        }
        if (task.getTaskKey() == null || task.getTaskKey().isBlank()) {
            task.setTaskKey("task_" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (task.getStatus() == null || task.getStatus().isBlank()) {
            task.setStatus("pending");
        }
        if (task.getProgress() == null) {
            task.setProgress(0);
        }
        this.taskMapper.insert(task);
        return task;
    }

    public boolean depsDone(HarnessTask task) {
        for (String depKey : task.getBlockedKeys()) {
            HarnessTask dep = this.taskMapper.findByKey(depKey);
            if (dep != null && "completed".equals(dep.getStatus())) continue;
            return false;
        }
        return true;
    }

    public HarnessTask claim(String key, String owner) {
        HarnessTask task = this.taskMapper.findByKey(key);
        if (task != null && "pending".equals(task.getStatus())) {
            task.setOwner(owner);
            task.setStatus("in_progress");
            this.taskMapper.update(task);
        }
        return task;
    }

    public HarnessTask updateProgress(String key, int progress, String status) {
        HarnessTask task = this.taskMapper.findByKey(key);
        if (task != null) {
            task.setProgress(progress);
            if (status != null) {
                task.setStatus(status);
            }
            this.taskMapper.update(task);
        }
        return task;
    }

    public HarnessTask complete(String key, String result) {
        HarnessTask task = this.taskMapper.findByKey(key);
        if (task != null) {
            task.setStatus("completed");
            task.setProgress(100);
            task.setResult(result);
            this.taskMapper.update(task);
        }
        return task;
    }

    public HarnessTask fail(String key, String reason) {
        HarnessTask task = this.taskMapper.findByKey(key);
        if (task != null) {
            task.setStatus("failed");
            task.setResult(reason);
            this.taskMapper.update(task);
        }
        return task;
    }

    public List<HarnessTask> unclaimedReady() {
        return this.taskMapper.findAll().stream().filter(t -> "pending".equals(t.getStatus())).filter(this::depsDone).toList();
    }

    public void delete(String key) {
        this.taskMapper.deleteByKey(key);
    }
}

