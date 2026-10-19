/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.config;

import com.badmelon.farming.config.MonitorWsHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig
implements WebSocketConfigurer {
    private final MonitorWsHandler monitorWsHandler;

    public WebSocketConfig(MonitorWsHandler monitorWsHandler) {
        this.monitorWsHandler = monitorWsHandler;
    }

    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler((WebSocketHandler)this.monitorWsHandler, new String[]{"/ws/monitor"}).setAllowedOrigins(new String[]{"*"});
    }
}

