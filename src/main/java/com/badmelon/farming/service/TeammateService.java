/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.HarnessTask;
import com.badmelon.farming.entity.Teammate;
import com.badmelon.farming.mapper.TeammateMapper;
import com.badmelon.farming.service.AiService;
import com.badmelon.farming.service.MonitorService;
import com.badmelon.farming.service.TaskService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TeammateService {
    private static final Logger log = LoggerFactory.getLogger(TeammateService.class);
    private final TeammateMapper teammateMapper;
    private final TaskService taskService;
    private final MonitorService monitorService;
    private final AiService aiService;
    @Value(value="${farming.harness.poll-interval:5000}")
    private long pollInterval;
    @Value(value="${farming.harness.work-dir:./data/work}")
    private String workDir;
    private final Map<String, TeammateThread> runningThreads = new ConcurrentHashMap<String, TeammateThread>();

    public TeammateService(TeammateMapper teammateMapper, TaskService taskService, MonitorService monitorService, AiService aiService) {
        this.teammateMapper = teammateMapper;
        this.taskService = taskService;
        this.monitorService = monitorService;
        this.aiService = aiService;
    }

    public List<Teammate> findAll() {
        return this.teammateMapper.findAll();
    }

    public Teammate spawn(String name, String role, String initialTask) {
        Teammate t = new Teammate();
        t.setName(name);
        t.setRole(role);
        t.setStatus("WORK");
        t.setCurrentTask(initialTask);
        t.setProgress(0);
        t.setLogSummary("\u5df2\u542f\u52a8");
        t.setHeartbeatAt(LocalDateTime.now());
        Teammate existing = this.teammateMapper.findByName(name);
        if (existing != null) {
            t.setId(existing.getId());
            this.teammateMapper.update(t);
        } else {
            this.teammateMapper.insert(t);
        }
        this.startThread(name);
        this.monitorService.log(name, "INFO", "Teammate \u8fdb\u7a0b\u5df2\u542f\u52a8: " + name + " (" + role + ")");
        return t;
    }

    private void startThread(String name) {
        this.runningThreads.computeIfAbsent(name, n -> {
            TeammateThread thread = new TeammateThread((String)n);
            thread.start();
            return thread;
        });
    }

    public void shutdown(String name) {
        TeammateThread thread = this.runningThreads.get(name);
        if (thread != null) {
            thread.requestStop();
        }
        this.teammateMapper.updateStatus(name, "SHUTDOWN");
        this.monitorService.log(name, "WARN", "Teammate \u5df2\u505c\u6b62: " + name);
    }

    public void updateProgress(String name, int progress, String currentTask, String status, String summary) {
        Teammate t = this.teammateMapper.findByName(name);
        if (t == null) {
            return;
        }
        t.setProgress(progress);
        if (currentTask != null) {
            t.setCurrentTask(currentTask);
        }
        if (status != null) {
            t.setStatus(status);
        }
        if (summary != null) {
            t.setLogSummary(summary);
        }
        t.setHeartbeatAt(LocalDateTime.now());
        this.teammateMapper.update(t);
    }

    public void initDefaultWorkers() {
        List<Teammate> all = this.teammateMapper.findAll();
        if (all.isEmpty()) {
            this.spawn("mate-\u65e5\u7a0b\u52a9\u7406", "\u751f\u6d3b\u65e5\u7a0b\u4e0e\u63d0\u9192\u52a9\u7406", "\u5f85\u547d");
            this.spawn("mate-\u8d44\u8baf\u7f16\u8f91", "\u70ed\u70b9\u8d44\u8baf\u6574\u7406\u5458", "\u5f85\u547d");
            this.spawn("mate-\u6570\u636e\u7edf\u8ba1", "\u751f\u6d3b\u6570\u636e\u7edf\u8ba1\u5206\u6790\u52a9\u624b", "\u5f85\u547d");
        } else {
            // \u91cd\u542f\u573a\u666f: \u5bf9\u5df2\u5b58\u5728\u4e14\u672a SHUTDOWN \u7684 Teammate \u542f\u52a8\u5b88\u62a4\u7ebf\u7a0b
            for (Teammate t : all) {
                if (!"SHUTDOWN".equals(t.getStatus())) {
                    this.startThread(t.getName());
                    this.monitorService.log(t.getName(), "INFO", "Teammate \u91cd\u542f\u540e\u5b88\u62a4\u7ebf\u7a0b\u542f\u52a8");
                }
            }
        }
    }

    public int runningCount() {
        return this.runningThreads.size();
    }

    /** 更新 Teammate 基本信息（角色/任务描述），返回更新后实体 */
    public Teammate update(String name, String role, String task) {
        Teammate t = this.teammateMapper.findByName(name);
        if (t == null) {
            return null;
        }
        if (role != null) {
            t.setRole(role);
        }
        if (task != null) {
            t.setCurrentTask(task);
        }
        this.teammateMapper.update(t);
        return t;
    }

    /** 删除 Teammate：先停守护线程并置 SHUTDOWN，再物理删除记录 */
    public boolean delete(String name) {
        TeammateThread thread = this.runningThreads.get(name);
        if (thread != null) {
            thread.requestStop();
            this.runningThreads.remove(name);
        }
        this.monitorService.log(name, "WARN", "Teammate \u5df2\u5220\u9664: " + name);
        return this.teammateMapper.deleteByName(name) > 0;
    }

    private class TeammateThread
    extends Thread {
        private final String name;
        private final AtomicBoolean running;
        private int tick;

        TeammateThread(String name) {
            super("teammate-" + name);
            this.running = new AtomicBoolean(true);
            this.tick = 0;
            this.name = name;
            this.setDaemon(true);
        }

        void requestStop() {
            this.running.set(false);
        }

        @Override
        public void run() {
            while (this.running.get()) {
                try {
                    Teammate me = TeammateService.this.teammateMapper.findByName(this.name);
                    if (me == null) {
                        Thread.sleep(2000L);
                        continue;
                    }
                    String status = me.getStatus();
                    if ("SHUTDOWN".equals(status)) {
                        this.running.set(false);
                        break;
                    }
                    this.heartbeat(me);
                    HarnessTask task = this.pickTask();
                    if (task != null) {
                        this.executeTask(me, task);
                        continue;
                    }
                    if (!"IDLE".equals(status)) {
                        TeammateService.this.updateProgress(this.name, me.getProgress(), "\u5f85\u547d", "IDLE", "\u7b49\u5f85\u4efb\u52a1\u8ba4\u9886");
                    }
                    Thread.sleep(TeammateService.this.pollInterval);
                }
                catch (InterruptedException e) {
                    this.running.set(false);
                }
                catch (Exception e) {
                    log.warn("Teammate {} \u5f02\u5e38: {}", (Object)this.name, (Object)e.getMessage());
                    TeammateService.this.monitorService.log(this.name, "ERROR", "\u5f02\u5e38: " + e.getMessage());
                    try {
                        Thread.sleep(TeammateService.this.pollInterval);
                    }
                    catch (InterruptedException interruptedException) {}
                }
            }
            TeammateService.this.monitorService.log(this.name, "WARN", "Teammate \u8fdb\u7a0b\u9000\u51fa: " + this.name);
        }

        private void heartbeat(Teammate me) {
            ++this.tick;
            if (this.tick % 6 == 0) {
                TeammateService.this.monitorService.log(this.name, "INFO", "\u5fc3\u8df3\u6b63\u5e38\uff0c\u72b6\u6001=" + me.getStatus());
            }
        }

        private HarnessTask pickTask() {
            List<HarnessTask> ready = TeammateService.this.taskService.unclaimedReady();
            return ready.isEmpty() ? null : ready.get(0);
        }

        private void executeTask(Teammate me, HarnessTask task) {
            TeammateService.this.updateProgress(this.name, task.getProgress() != null ? task.getProgress() : 0, "\u6267\u884c\u4efb\u52a1: " + task.getTitle(), "WORK", "\u8ba4\u9886\u4efb\u52a1 " + task.getTaskKey());
            TeammateService.this.monitorService.log(this.name, "INFO", "\u8ba4\u9886\u4efb\u52a1 [" + task.getTaskKey() + "] " + task.getTitle());
            String[] steps = this.splitSteps(task.getDescription());
            int total = Math.max(1, steps.length);
            for (int i = 0; i < total; ++i) {
                if (!this.running.get()) {
                    return;
                }
                int pct = (int)((double)(i + 1) * 100.0 / (double)total);
                TeammateService.this.updateProgress(this.name, pct, "\u6b65\u9aa4 " + (i + 1) + "/" + total + ": " + steps[i], "WORK", steps[i]);
                TeammateService.this.monitorService.log(this.name, "INFO", "[" + task.getTaskKey() + "] \u6b65\u9aa4" + (i + 1) + "/" + total + ": " + steps[i]);
                try {
                    Thread.sleep(1500L);
                    continue;
                }
                catch (InterruptedException e) {
                    this.running.set(false);
                }
            }
            Object result = "\u4efb\u52a1 [" + task.getTaskKey() + "] " + task.getTitle() + " \u5df2\u5b8c\u6210\u3002";
            if (TeammateService.this.aiService.isConfigured()) {
                try {
                    result = TeammateService.this.aiService.summarize("\u751f\u6d3b\u52a9\u7406\u573a\u666f\u4efb\u52a1\u5b8c\u6210\u62a5\u544a\uff1a" + task.getDescription(), 200);
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            TeammateService.this.taskService.complete(task.getTaskKey(), (String)result);
            TeammateService.this.updateProgress(this.name, 100, "\u4efb\u52a1\u5b8c\u6210: " + task.getTitle(), "IDLE", "\u4efb\u52a1 " + task.getTaskKey() + " \u5b8c\u6210\uff0c\u7ed3\u679c\u5df2\u56de\u4f20\uff08summary \u534f\u8bae\uff09");
            TeammateService.this.monitorService.log(this.name, "INFO", "\u4efb\u52a1 [" + task.getTaskKey() + "] \u5b8c\u6210\uff0csummary \u5df2\u56de\u4f20 Lead");
        }

        private String[] splitSteps(String description) {
            String[] stringArray;
            if (description == null || description.isBlank()) {
                return new String[]{"\u5206\u6790", "\u5904\u7406", "\u6c47\u603b"};
            }
            String[] parts = description.split("\\n|\uff1b|;|\uff0c|,");
            ArrayList<String> steps = new ArrayList<String>();
            for (String p : parts) {
                String s = p.trim();
                if (s.isBlank() || s.length() > 50) continue;
                steps.add(s);
            }
            if (steps.isEmpty()) {
                String[] stringArray2 = new String[2];
                stringArray2[0] = "\u5904\u7406";
                stringArray = stringArray2;
                stringArray2[1] = "\u6c47\u603b";
            } else {
                stringArray = steps.toArray(new String[0]);
            }
            return stringArray;
        }
    }
}

