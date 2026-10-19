/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.controller;

import com.badmelon.farming.service.AssistantService;
import com.badmelon.farming.service.LocationInfoService;
import com.badmelon.farming.service.MonitorService;
import com.badmelon.farming.service.ScheduledTaskService;
import com.badmelon.farming.service.ToolScheduler;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping(value={"/api/assistant"})
public class AssistantController {
    private final AssistantService assistantService;
    private final ToolScheduler toolScheduler;
    private final MonitorService monitorService;
    private final ScheduledTaskService scheduledTaskService;
    private final LocationInfoService locationInfoService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, Future<?>> activeStreams = new ConcurrentHashMap();

    public AssistantController(AssistantService assistantService, ToolScheduler toolScheduler, MonitorService monitorService, ScheduledTaskService scheduledTaskService, LocationInfoService locationInfoService) {
        this.assistantService = assistantService;
        this.toolScheduler = toolScheduler;
        this.monitorService = monitorService;
        this.scheduledTaskService = scheduledTaskService;
        this.locationInfoService = locationInfoService;
    }

    @PostMapping(value={"/stop"})
    public Map<String, Object> stop(@RequestBody Map<String, String> body) {
        String session = body.get("session");
        HashMap<String, Object> resp = new HashMap<String, Object>();
        if (session == null || session.isBlank()) {
            resp.put("success", false);
            resp.put("error", "\u7f3a\u5c11\u4f1a\u8bdd\u6807\u8bc6");
            return resp;
        }
        Future<?> task = this.activeStreams.get(session);
        if (task == null) {
            resp.put("success", false);
            resp.put("stopped", false);
            resp.put("message", "\u8be5\u4f1a\u8bdd\u6ca1\u6709\u6b63\u5728\u6267\u884c\u7684\u4efb\u52a1");
            return resp;
        }
        boolean cancelled = task.cancel(true);
        this.activeStreams.remove(session);
        try {
            this.monitorService.log("assistant", "WARN", "\u7528\u6237\u7ec8\u6b62\u4e86\u4f1a\u8bdd <" + session + "> \u7684\u4efb\u52a1");
        }
        catch (Exception exception) {
            // empty catch block
        }
        resp.put("success", true);
        resp.put("stopped", cancelled);
        resp.put("message", cancelled ? "\u4efb\u52a1\u5df2\u7ec8\u6b62" : "\u4efb\u52a1\u7ec8\u6b62\u8bf7\u6c42\u5df2\u53d1\u9001");
        return resp;
    }

    @PostMapping(value={"/confirm"})
    public Map<String, Object> confirmHighRisk(@RequestBody Map<String, String> body) {
        String op = body.get("op");
        String endpoint = body.get("endpoint");
        String args = body.getOrDefault("args", "{}");
        HashMap<String, Object> resp = new HashMap<String, Object>();
        if (op != null && !op.isBlank()) {
            String path = body.get("path");
            String content = body.get("content");
            if (path == null || path.isBlank()) {
                resp.put("success", false);
                resp.put("error", "\u7f3a\u5c11\u76ee\u6807\u8def\u5f84");
                return resp;
            }
            try {
                String result = this.toolScheduler.executeFileOp(op, path, content);
                resp.put("success", true);
                resp.put("op", op);
                resp.put("path", path);
                resp.put("result", result);
            }
            catch (Exception e) {
                resp.put("success", false);
                resp.put("error", e.getMessage());
            }
            return resp;
        }
        if (endpoint == null || endpoint.isBlank()) {
            resp.put("success", false);
            resp.put("error", "\u7f3a\u5c11\u6267\u884c\u76ee\u6807");
            return resp;
        }
        try {
            String result = this.toolScheduler.executeConfirmed(endpoint, args);
            resp.put("success", true);
            resp.put("result", result);
        }
        catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return resp;
    }

    @GetMapping(value={"/location"})
    public Map<String, Object> location() {
        return this.locationInfoService.getLocationInfo();
    }

    @PostMapping(value={"/schedule"})
    public Map<String, Object> scheduleReminder(@RequestBody Map<String, Object> body) {
        // 取消提醒（MCP 工具路径：args 传 cancelId）
        Object cancelId = body.get("cancelId");
        if (cancelId != null) {
            Long id;
            try {
                id = Long.valueOf(String.valueOf(cancelId).trim());
            }
            catch (Exception e) {
                HashMap<String, Object> bad = new HashMap<String, Object>();
                bad.put("success", false);
                bad.put("error", "cancelId 无效");
                return bad;
            }
            boolean ok = this.scheduledTaskService.cancel(id);
            HashMap<String, Object> cancelResp = new HashMap<String, Object>();
            cancelResp.put("success", ok);
            cancelResp.put("message", ok ? "提醒已取消" : "取消失败：提醒不存在或已触发");
            return cancelResp;
        }
        String instruct = String.valueOf(body.getOrDefault("instruct", ""));
        Object dm = body.get("delayMinutes");
        int delayMinutes = 0;
        if (dm != null) {
            try {
                delayMinutes = Integer.parseInt(String.valueOf(dm).trim());
            }
            catch (Exception ignored) {}
        }
        String title = String.valueOf(body.getOrDefault("title", "⏰ 定时提醒"));
        String session = body.get("session") == null ? null : String.valueOf(body.get("session"));
        return this.scheduledTaskService.create(instruct, delayMinutes, title, session);
    }

    @GetMapping(value={"/schedule"})
    public List<Map<String, Object>> scheduleList(@RequestParam(required=false) String session) {
        return this.scheduledTaskService.list(session);
    }

    @DeleteMapping(value={"/schedule/{id}"})
    public Map<String, Object> cancelSchedule(@PathVariable Long id) {
        boolean ok = this.scheduledTaskService.cancel(id);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", ok);
        resp.put("message", ok ? "提醒已取消" : "取消失败：提醒不存在或已触发");
        return resp;
    }

    @PostMapping(value={"/chat"})
    public Map<String, Object> chat(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        String session = body.get("session");
        return this.assistantService.chat(message, session);
    }

    @PostMapping(value={"/chat-stream"}, consumes={"application/json"}, produces={"text/event-stream"})
    public SseEmitter chatStream(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        String session = body.get("session");
        SseEmitter emitter = new SseEmitter(Long.valueOf(0L));
        emitter.onCompletion(() -> this.activeStreams.remove(session));
        emitter.onTimeout(() -> this.activeStreams.remove(session));
        Future<?> task = this.executor.submit(() -> {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    try {
                        emitter.send(SseEmitter.event().name("done").data((Object)"\uff08\u4efb\u52a1\u5df2\u88ab\u7528\u6237\u7ec8\u6b62\uff09"));
                    }
                    catch (IOException iOException) {
                        // empty catch block
                    }
                    emitter.complete();
                    return;
                }
                this.assistantService.streamChat(message, session, event -> {
                    if (event == null) {
                        return;
                    }
                    if (event.startsWith("done:")) {
                        try {
                            emitter.send(SseEmitter.event().name("done").data((Object)event.substring(5)));
                        }
                        catch (IOException iOException) {}
                    } else if (event.startsWith("confirm:")) {
                        try {
                            emitter.send(SseEmitter.event().name("confirm").data((Object)event.substring(8)));
                        }
                        catch (IOException iOException) {}
                    } else if (event.startsWith("skill_draft:")) {
                        try {
                            emitter.send(SseEmitter.event().name("skill_draft").data((Object)event.substring(12)));
                        }
                        catch (IOException iOException) {}
                    } else if (event.startsWith("mcp_draft:")) {
                        try {
                            emitter.send(SseEmitter.event().name("mcp_draft").data((Object)event.substring(10)));
                        }
                        catch (IOException iOException) {}
                    } else if (event.startsWith("summary:")) {
                        try {
                            emitter.send(SseEmitter.event().name("summary").data((Object)event.substring(8)));
                        }
                        catch (IOException iOException) {
                            // empty catch block
                        }
                    }
                }, chunk -> {
                    try {
                        emitter.send(SseEmitter.event().name("token").data(chunk));
                    }
                    catch (IOException iOException) {
                        // empty catch block
                    }
                });
                emitter.complete();
            }
            catch (Exception e) {
                if (!(e instanceof InterruptedException)) {
                    try {
                        this.monitorService.log("assistant", "ERROR", "\u6d41\u5f0f\u5904\u7406\u5f02\u5e38\uff1a" + this.truncate(e.getMessage(), 200));
                    }
                    catch (Exception exception) {
                        // empty catch block
                    }
                    try {
                        emitter.send(SseEmitter.event().name("error").data((Object)e.getMessage()));
                    }
                    catch (IOException iOException) {}
                } else {
                    try {
                        emitter.send(SseEmitter.event().name("done").data((Object)"\uff08\u4efb\u52a1\u5df2\u88ab\u7528\u6237\u7ec8\u6b62\uff09"));
                    }
                    catch (IOException iOException) {
                        // empty catch block
                    }
                }
                emitter.completeWithError((Throwable)e);
            }
            finally {
                this.activeStreams.remove(session);
            }
        });
        if (session != null) {
            this.activeStreams.put(session, task);
        }
        return emitter;
    }

    private String truncate(String s, int len) {
        if (s == null) {
            return "";
        }
        return s.length() <= len ? s : s.substring(0, len);
    }
}

