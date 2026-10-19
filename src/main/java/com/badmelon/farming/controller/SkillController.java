/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.controller;

import com.badmelon.farming.entity.Skill;
import com.badmelon.farming.service.OpenclawImportService;
import com.badmelon.farming.service.RemoteImportService;
import com.badmelon.farming.service.SecurityScanner;
import com.badmelon.farming.service.SkillService;
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
@RequestMapping(value={"/api/skills"})
public class SkillController {
    private final SkillService skillService;
    private final RemoteImportService remoteImportService;
    private final SecurityScanner securityScanner;
    private final OpenclawImportService openclawImportService;

    public SkillController(SkillService skillService, RemoteImportService remoteImportService, SecurityScanner securityScanner, OpenclawImportService openclawImportService) {
        this.skillService = skillService;
        this.remoteImportService = remoteImportService;
        this.securityScanner = securityScanner;
        this.openclawImportService = openclawImportService;
    }

    @PostMapping(value={"/import-openclaw"})
    public Map<String, Object> importOpenclaw() {
        return this.openclawImportService.importSkills();
    }

    @GetMapping
    public List<Skill> list() {
        return this.skillService.findAll();
    }

    @PostMapping(value={"/download"})
    public Map<String, Object> download(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        HashMap<String, Object> resp = new HashMap<String, Object>();
        if (url == null || url.isBlank()) {
            resp.put("success", false);
            resp.put("error", "\u8bf7\u63d0\u4f9b\u4e0b\u8f7d\u5730\u5740");
            return resp;
        }
        try {
            Map<String, Object> result = this.remoteImportService.importSkill(url);
            resp.put("success", true);
            resp.putAll(result);
        }
        catch (SecurityException e) {
            resp.put("success", false);
            resp.put("blocked", true);
            resp.put("error", e.getMessage());
        }
        catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return resp;
    }

    @PostMapping(value={"/scan"})
    public Map<String, Object> scan(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        HashMap<String, Object> resp = new HashMap<String, Object>();
        if (url == null || url.isBlank()) {
            resp.put("success", false);
            resp.put("error", "\u8bf7\u63d0\u4f9b\u626b\u63cf\u5730\u5740");
            return resp;
        }
        try {
            resp.put("success", true);
            resp.putAll(this.remoteImportService.scanOnly(url));
        }
        catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return resp;
    }

    @PostMapping(value={"/import-draft"})
    public Map<String, Object> importDraft(@RequestBody Skill skill) {
        HashMap<String, Object> resp = new HashMap<String, Object>();
        if (skill.getName() == null || skill.getName().isBlank() || skill.getContent() == null || skill.getContent().isBlank()) {
            resp.put("success", false);
            resp.put("error", "Skill \u8349\u7a3f\u7f3a\u5c11\u540d\u79f0\u6216\u5185\u5bb9");
            return resp;
        }
        SecurityScanner.Result scan = this.securityScanner.scan(skill.getContent());
        if (!scan.safe) {
            resp.put("success", false);
            resp.put("blocked", true);
            resp.put("riskCount", scan.riskCount);
            resp.put("hits", scan.hits);
            resp.put("error", "\u68c0\u6d4b\u5230\u98ce\u9669\u5185\u5bb9\uff0c\u5df2\u62d2\u7edd\u5bfc\u5165\uff1a" + scan.riskCount + " \u9879\u98ce\u9669");
            return resp;
        }
        Skill saved = this.skillService.save(skill);
        resp.put("success", true);
        resp.put("scanLevel", scan.level);
        resp.put("skill", saved);
        return resp;
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody Skill skill) {
        Skill saved = this.skillService.save(skill);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        resp.put("skill", saved);
        return resp;
    }

    @PostMapping(value={"/{id}/toggle"})
    public Map<String, Object> toggle(@PathVariable Long id) {
        Skill skill = this.skillService.toggle(id);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", skill != null);
        resp.put("skill", skill);
        return resp;
    }

    @DeleteMapping(value={"/{id}"})
    public Map<String, Object> delete(@PathVariable Long id) {
        this.skillService.delete(id);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        return resp;
    }
}

