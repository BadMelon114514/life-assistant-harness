/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AssistantStepTracker {
    private final List<Step> steps = new ArrayList<Step>();
    private final long beginAt = System.currentTimeMillis();

    public int begin(String step) {
        Step s = new Step(step);
        this.steps.add(s);
        return this.steps.size() - 1;
    }

    public void finish(int index, String detail) {
        if (index < 0 || index >= this.steps.size()) {
            return;
        }
        Step s = this.steps.get(index);
        s.status = "done";
        s.detail = detail == null ? "" : detail;
        s.durationMs = System.currentTimeMillis() - s.startedAt;
    }

    public void skip(int index, String detail) {
        if (index < 0 || index >= this.steps.size()) {
            return;
        }
        Step s = this.steps.get(index);
        s.status = "skip";
        s.detail = detail == null ? "" : detail;
        s.durationMs = System.currentTimeMillis() - s.startedAt;
    }

    public void fail(int index, String detail) {
        if (index < 0 || index >= this.steps.size()) {
            return;
        }
        Step s = this.steps.get(index);
        s.status = "fail";
        s.detail = detail == null ? "" : detail;
        s.durationMs = System.currentTimeMillis() - s.startedAt;
    }

    public void step(String step, String detail) {
        int idx = this.begin(step);
        this.finish(idx, detail);
    }

    public List<Map<String, Object>> getSteps() {
        ArrayList<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (Step s : this.steps) {
            LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("step", s.step);
            m.put("status", s.status);
            m.put("detail", s.detail);
            m.put("durationMs", s.durationMs);
            list.add(m);
        }
        return list;
    }

    public static class Step {
        public final String step;
        public String status;
        public String detail;
        public long durationMs;
        public long startedAt;

        Step(String step) {
            this.step = step;
            this.status = "running";
            this.detail = "";
            this.startedAt = System.currentTimeMillis();
        }
    }
}

