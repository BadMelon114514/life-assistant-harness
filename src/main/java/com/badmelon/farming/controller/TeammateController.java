/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.controller;

import com.badmelon.farming.entity.Teammate;
import com.badmelon.farming.service.TeammateService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/teammates"})
public class TeammateController {
    private final TeammateService teammateService;

    public TeammateController(TeammateService teammateService) {
        this.teammateService = teammateService;
    }

    @GetMapping
    public List<Teammate> list() {
        return this.teammateService.findAll();
    }

    @PostMapping
    public Map<String, Object> spawn(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "mate-" + System.currentTimeMillis() % 10000L);
        String role = body.getOrDefault("role", "\u901a\u7528\u529e\u516c\u52a9\u624b");
        String task = body.get("task");
        Teammate t = this.teammateService.spawn(name, role, task);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        resp.put("teammate", t);
        return resp;
    }

    @PostMapping(value={"/{name}/shutdown"})
    public Map<String, Object> shutdown(@PathVariable String name) {
        this.teammateService.shutdown(name);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        return resp;
    }

    @PostMapping(value={"/{name}/progress"})
    public Map<String, Object> updateProgress(@PathVariable String name, @RequestBody Map<String, Object> body) {
        int progress = body.get("progress") != null ? Integer.parseInt(body.get("progress").toString()) : 0;
        String currentTask = (String)body.get("currentTask");
        String status = (String)body.get("status");
        String summary = (String)body.get("summary");
        this.teammateService.updateProgress(name, progress, currentTask, status, summary);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        return resp;
    }

    @PutMapping(value={"/{name}"})
    public Map<String, Object> update(@PathVariable String name, @RequestBody Map<String, String> body) {
        HashMap<String, Object> resp = new HashMap<String, Object>();
        Teammate t = this.teammateService.update(name, body.get("role"), body.get("task"));
        if (t == null) {
            resp.put("success", false);
            resp.put("error", "Teammate \u4e0d\u5b58\u5728: " + name);
            return resp;
        }
        resp.put("success", true);
        resp.put("teammate", t);
        return resp;
    }

    @DeleteMapping(value={"/{name}"})
    public Map<String, Object> delete(@PathVariable String name) {
        HashMap<String, Object> resp = new HashMap<String, Object>();
        boolean ok = this.teammateService.delete(name);
        resp.put("success", ok);
        if (!ok) {
            resp.put("error", "Teammate \u4e0d\u5b58\u5728\uff0c\u65e0\u6cd5\u5220\u9664: " + name);
        }
        return resp;
    }
}

