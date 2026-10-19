/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.controller;

import com.badmelon.farming.entity.Setting;
import com.badmelon.farming.service.SettingsService;
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
@RequestMapping(value={"/api/settings"})
public class SettingsController {
    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public List<Setting> getAll() {
        return this.settingsService.getAll();
    }

    @GetMapping(value={"/{key}"})
    public Setting get(@PathVariable String key) {
        return this.settingsService.get(key);
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        String key = (String)body.get("key");
        String value = (String)body.getOrDefault("value", "");
        String desc = (String)body.get("description");
        Integer isSecret = body.get("isSecret") != null ? Integer.parseInt(body.get("isSecret").toString()) : 0;
        Setting saved = this.settingsService.save(key, value, desc, isSecret);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        resp.put("setting", saved);
        return resp;
    }

    @PostMapping(value={"/api-key"})
    public Map<String, Object> saveApiKey(@RequestBody Map<String, Object> body) {
        String value = (String)body.getOrDefault("value", "");
        this.settingsService.save("api.key", value, "API \u5bc6\u94a5\uff08\u654f\u611f\uff09", 1);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        resp.put("configured", !value.isBlank());
        return resp;
    }

    @DeleteMapping(value={"/{key}"})
    public Map<String, Object> delete(@PathVariable String key) {
        this.settingsService.delete(key);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        return resp;
    }
}

