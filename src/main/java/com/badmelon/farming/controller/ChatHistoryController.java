/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.controller;

import com.badmelon.farming.entity.ChatMessage;
import com.badmelon.farming.service.ChatHistoryService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/chat/history"})
public class ChatHistoryController {
    private final ChatHistoryService chatHistoryService;

    public ChatHistoryController(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
    }

    @GetMapping
    public List<ChatMessage> history(@RequestParam(required=false) String session) {
        return this.chatHistoryService.history(session);
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody Map<String, Object> body) {
        String session = (String)body.get("session");
        String role = (String)body.getOrDefault("role", "user");
        String content = (String)body.getOrDefault("content", "");
        ChatMessage saved = this.chatHistoryService.add(session, role, content);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", saved != null);
        resp.put("message", saved);
        return resp;
    }

    @DeleteMapping
    public Map<String, Object> clear(@RequestParam(required=false) String session) {
        this.chatHistoryService.clear(session);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        return resp;
    }

    @DeleteMapping(value={"/{id}"})
    public Map<String, Object> delete(@PathVariable Long id) {
        boolean ok = this.chatHistoryService.delete(id);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", ok);
        return resp;
    }

    @GetMapping(value={"/sessions"})
    public Map<String, Object> sessions() {
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        resp.put("sessions", this.chatHistoryService.listSessions());
        return resp;
    }

    @PostMapping(value={"/session"})
    public Map<String, Object> newSession() {
        String key = this.chatHistoryService.newSession();
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        resp.put("sessionKey", key);
        return resp;
    }

    @DeleteMapping(value={"/session/{key}"})
    public Map<String, Object> deleteSession(@PathVariable String key) {
        this.chatHistoryService.clear(key);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        return resp;
    }
}

