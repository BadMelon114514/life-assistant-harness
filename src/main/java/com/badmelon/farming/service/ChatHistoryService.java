/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.ChatMessage;
import com.badmelon.farming.mapper.ChatMessageMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ChatHistoryService {
    private static final String DEFAULT_SESSION = "default";
    private final ChatMessageMapper chatMessageMapper;

    public ChatHistoryService(ChatMessageMapper chatMessageMapper) {
        this.chatMessageMapper = chatMessageMapper;
    }

    public List<ChatMessage> history(String sessionKey) {
        return this.chatMessageMapper.findBySession(sessionKey == null || sessionKey.isBlank() ? DEFAULT_SESSION : sessionKey);
    }

    public ChatMessage add(String sessionKey, String role, String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        ChatMessage msg = new ChatMessage();
        msg.setSessionKey(sessionKey == null || sessionKey.isBlank() ? DEFAULT_SESSION : sessionKey);
        msg.setRole("user".equalsIgnoreCase(role) ? "user" : "assistant");
        msg.setContent(content);
        this.chatMessageMapper.insert(msg);
        return msg;
    }

    public void clear(String sessionKey) {
        this.chatMessageMapper.deleteBySession(sessionKey == null || sessionKey.isBlank() ? DEFAULT_SESSION : sessionKey);
    }

    public List<Map<String, Object>> listSessions() {
        return this.chatMessageMapper.findSessionSummaries();
    }

    public String newSession() {
        return "s" + System.currentTimeMillis();
    }

    public boolean delete(Long id) {
        ChatMessage existing = this.chatMessageMapper.findById(id);
        if (existing == null) {
            return false;
        }
        this.chatMessageMapper.deleteById(id);
        return true;
    }
}

