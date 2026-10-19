/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.controller;

import com.badmelon.farming.entity.MonitorLog;
import com.badmelon.farming.entity.Teammate;
import com.badmelon.farming.service.MonitorService;
import com.badmelon.farming.service.TeammateService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/monitor"})
public class MonitorController {
    private final MonitorService monitorService;
    private final TeammateService teammateService;

    public MonitorController(MonitorService monitorService, TeammateService teammateService) {
        this.monitorService = monitorService;
        this.teammateService = teammateService;
    }

    @GetMapping(value={"/overview"})
    public Map<String, Object> overview() {
        List<Teammate> teammates = this.teammateService.findAll();
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("teammates", teammates);
        HashMap<String, Number> summary = new HashMap<String, Number>();
        summary.put("total", teammates.size());
        summary.put("working", teammates.stream().filter(t -> "WORK".equals(t.getStatus())).count());
        summary.put("idle", teammates.stream().filter(t -> "IDLE".equals(t.getStatus())).count());
        summary.put("shutdown", teammates.stream().filter(t -> "SHUTDOWN".equals(t.getStatus())).count());
        double avg = teammates.stream().mapToInt(t -> t.getProgress() == null ? 0 : t.getProgress()).average().orElse(0.0);
        summary.put("avgProgress", Math.round(avg));
        resp.put("summary", summary);
        resp.put("logs", this.monitorService.recentLogs(100));
        return resp;
    }

    @GetMapping(value={"/logs"})
    public List<MonitorLog> logs() {
        return this.monitorService.recentLogs(100);
    }
}

