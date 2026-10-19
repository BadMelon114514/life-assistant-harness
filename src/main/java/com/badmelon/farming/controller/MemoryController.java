/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.controller;

import com.badmelon.farming.entity.MemoryItem;
import com.badmelon.farming.service.MemoryService;
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
@RequestMapping(value={"/api/memory"})
public class MemoryController {
    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public List<MemoryItem> list() {
        return this.memoryService.findAll();
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        String key = (String)body.get("memoryKey");
        String content = (String)body.get("content");
        int importance = body.get("importance") != null ? Integer.parseInt(body.get("importance").toString()) : 5;
        String category = (String)body.get("category");
        HashMap<String, Object> resp = new HashMap<String, Object>();
        if (key == null || key.isBlank()) {
            resp.put("success", false);
            resp.put("error", "\u8bb0\u5fc6 key \u4e0d\u80fd\u4e3a\u7a7a");
            return resp;
        }
        if (content == null || content.isBlank()) {
            resp.put("success", false);
            resp.put("error", "\u8bb0\u5fc6\u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a");
            return resp;
        }
        MemoryItem item = this.memoryService.remember(key, content, importance, category);
        resp.put("success", true);
        resp.put("memory", item);
        return resp;
    }

    @DeleteMapping(value={"/{key}"})
    public Map<String, Object> delete(@PathVariable String key) {
        this.memoryService.forget(key);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        return resp;
    }
}

