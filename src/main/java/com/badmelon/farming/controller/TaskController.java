/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.controller;

import com.badmelon.farming.entity.HarnessTask;
import com.badmelon.farming.service.TaskService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/tasks"})
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public List<HarnessTask> list() {
        return this.taskService.findAll();
    }

    @GetMapping(value={"/{key}"})
    public HarnessTask get(@PathVariable String key) {
        return this.taskService.findByKey(key);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String title = (String)body.get("title");
        String description = (String)body.getOrDefault("description", "");
        List blockedBy = (List)body.get("blockedBy");
        HarnessTask task = this.taskService.create(title, description, blockedBy);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        resp.put("task", task);
        return resp;
    }

    @PostMapping(value={"/{key}/claim"})
    public Map<String, Object> claim(@PathVariable String key, @RequestBody(required=false) Map<String, Object> body) {
        String owner = body != null && body.get("owner") != null ? (String)body.get("owner") : "manual";
        HarnessTask task = this.taskService.claim(key, owner);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", task != null);
        resp.put("task", task);
        return resp;
    }

    @PostMapping(value={"/{key}/progress"})
    public Map<String, Object> progress(@PathVariable String key, @RequestBody Map<String, Object> body) {
        int progress = body.get("progress") != null ? Integer.parseInt(body.get("progress").toString()) : 0;
        String status = (String)body.get("status");
        HarnessTask task = this.taskService.updateProgress(key, progress, status);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        resp.put("task", task);
        return resp;
    }

    @PostMapping(value={"/{key}/complete"})
    public Map<String, Object> complete(@PathVariable String key, @RequestBody(required=false) Map<String, Object> body) {
        String result = body != null ? (String)body.getOrDefault("result", "") : "";
        HarnessTask task = this.taskService.complete(key, result);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        resp.put("task", task);
        return resp;
    }

    @DeleteMapping(value={"/{key}"})
    public Map<String, Object> delete(@PathVariable String key) {
        this.taskService.delete(key);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        return resp;
    }
}

