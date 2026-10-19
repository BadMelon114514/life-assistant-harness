/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.config;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class MonitorWsHandler
extends TextWebSocketHandler {
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public void afterConnectionEstablished(WebSocketSession session) {
        this.sessions.add(session);
    }

    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        this.sessions.remove(session);
    }

    public void broadcast(String json) {
        TextMessage msg = new TextMessage((CharSequence)json);
        for (WebSocketSession session : this.sessions) {
            try {
                if (!session.isOpen()) continue;
                session.sendMessage((WebSocketMessage)msg);
            }
            catch (IOException ignored) {
                this.sessions.remove(session);
            }
        }
    }

    public int clientCount() {
        return this.sessions.size();
    }
}

