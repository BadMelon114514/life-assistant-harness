/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.config.MonitorWsHandler;
import com.badmelon.farming.entity.MonitorLog;
import com.badmelon.farming.entity.Teammate;
import com.badmelon.farming.mapper.MonitorLogMapper;
import com.badmelon.farming.mapper.TeammateMapper;
import com.badmelon.farming.util.SecretRedaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MonitorService {
    private final TeammateMapper teammateMapper;
    private final MonitorLogMapper logMapper;
    private final MonitorWsHandler wsHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MonitorService(TeammateMapper teammateMapper, MonitorLogMapper logMapper, MonitorWsHandler wsHandler) {
        this.teammateMapper = teammateMapper;
        this.logMapper = logMapper;
        this.wsHandler = wsHandler;
    }

    @Scheduled(fixedDelay=2000L)
    public void pushSnapshot() {
        try {
            List<Teammate> teammates = this.teammateMapper.findAll();
            List<MonitorLog> recentLogs = this.logMapper.findLatest(50);
            HashMap<String, Object> snapshot = new HashMap<String, Object>();
            snapshot.put("type", "snapshot");
            snapshot.put("timestamp", System.currentTimeMillis());
            snapshot.put("teammates", teammates);
            snapshot.put("logs", recentLogs);
            int total = teammates.size();
            long working = teammates.stream().filter(t -> "WORK".equals(t.getStatus())).count();
            long idle = teammates.stream().filter(t -> "IDLE".equals(t.getStatus())).count();
            long shutdown = teammates.stream().filter(t -> "SHUTDOWN".equals(t.getStatus())).count();
            HashMap<String, Number> summary = new HashMap<String, Number>();
            summary.put("total", total);
            summary.put("working", working);
            summary.put("idle", idle);
            summary.put("shutdown", shutdown);
            snapshot.put("summary", summary);
            this.wsHandler.broadcast(this.objectMapper.writeValueAsString(snapshot));
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public void log(String source, String level, String message) {
        MonitorLog log = new MonitorLog();
        log.setSource(source);
        log.setLevel(level);
        log.setMessage(SecretRedaction.redact(message));
        this.logMapper.insert(log);
        try {
            HashMap<String, Object> payload = new HashMap<String, Object>();
            payload.put("type", "log");
            payload.put("log", log);
            this.wsHandler.broadcast(this.objectMapper.writeValueAsString(payload));
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public List<MonitorLog> recentLogs(int limit) {
        return this.logMapper.findLatest(limit);
    }
}

