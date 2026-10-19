/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.controller;

import com.badmelon.farming.entity.Skill;
import com.badmelon.farming.service.ClawHubService;
import com.badmelon.farming.service.SecurityScanner;
import com.badmelon.farming.service.SkillService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/clawhub"})
public class ClawHubController {
    private final ClawHubService clawHubService;
    private final SecurityScanner securityScanner;
    private final SkillService skillService;

    public ClawHubController(ClawHubService clawHubService, SecurityScanner securityScanner, SkillService skillService) {
        this.clawHubService = clawHubService;
        this.securityScanner = securityScanner;
        this.skillService = skillService;
    }

    @GetMapping(value={"/search"})
    public Map<String, Object> search(@RequestParam(value="q") String q) {
        HashMap<String, Object> resp = new HashMap<String, Object>();
        List<Map<String, Object>> skills = this.clawHubService.search(q);
        resp.put("success", true);
        resp.put("skills", skills);
        resp.put("count", skills.size());
        return resp;
    }

    @PostMapping(value={"/install"})
    public Map<String, Object> install(@RequestBody Map<String, String> body) {
        List<Map<String, Object>> hits;
        String slug = body.get("slug");
        String version = body.get("version");
        HashMap<String, Object> resp = new HashMap<String, Object>();
        if (slug == null || slug.isBlank()) {
            resp.put("success", false);
            resp.put("error", "\u7f3a\u5c11\u6280\u80fd\u6807\u8bc6");
            return resp;
        }
        Object md = this.clawHubService.fetchSkillMd(slug, version);
        // 内容端点不可用或返回错位数据（与 slug 不匹配）时，以搜索元信息兜底
        if ((md == null || ((String)md).isBlank() || !this.clawHubService.contentMatches((String)md, slug, slug))
                && !(hits = this.clawHubService.search(slug)).isEmpty()) {
            Map<String, Object> hit = hits.get(0);
            String name = (String)hit.getOrDefault("name", slug);
            String summary = (String)hit.getOrDefault("summary", "");
            String desc = (String)hit.getOrDefault("description", "");
            md = "\u540d\u79f0: " + name + "\n\u63cf\u8ff0: " + summary + "\n\n" + desc + "\n\u6765\u6e90: ClawHub (" + slug + ")";
        }
        if (md == null || ((String)md).isBlank()) {
            resp.put("success", false);
            resp.put("error", "\u65e0\u6cd5\u83b7\u53d6\u6280\u80fd\u5185\u5bb9\uff08ClawHub \u63a5\u53e3\u53ef\u80fd\u8c03\u6574\uff09");
            return resp;
        }
        SecurityScanner.Result scan = this.securityScanner.scan((String)md);
        if (!scan.safe) {
            resp.put("success", false);
            resp.put("blocked", true);
            resp.put("riskCount", scan.riskCount);
            resp.put("error", "\u6280\u80fd\u5185\u5bb9\u542b\u98ce\u9669\u7279\u5f81\uff0c\u5df2\u62d2\u7edd\u5b89\u88c5\uff1a" + scan.riskCount + " \u9879");
            return resp;
        }
        Map<String, String> parsed = this.parseSkillMd((String)md, slug);
        Skill skill = new Skill();
        skill.setName(parsed.get("name"));
        skill.setCategory("ClawHub");
        // description 列限长 255，截断超长摘要
        String desc = parsed.get("description");
        skill.setDescription(desc != null && desc.length() > 250 ? desc.substring(0, 250) : desc);
        skill.setContent((String)md);
        skill.setEnabled(1);
        if (this.skillService.findByName(parsed.get("name")) != null) {
            resp.put("success", true);
            resp.put("source", "clawhub");
            resp.put("slug", slug);
            resp.put("alreadyExists", true);
            resp.put("message", "同名技能已存在，已跳过重复安装");
            return resp;
        }
        Skill saved = this.skillService.save(skill);
        resp.put("success", true);
        resp.put("source", "clawhub");
        resp.put("slug", slug);
        resp.put("skill", saved);
        return resp;
    }

    private Map<String, String> parseSkillMd(String md, String fallback) {
        String[] lines;
        HashMap<String, String> result = new HashMap<String, String>();
        String name = fallback;
        String description = "";
        for (String line : lines = md.split("\n")) {
            String t = line.trim();
            if (t.startsWith("name:") && fallback.equals(name)) {
                name = t.substring(5).trim().replace("\"", "").replace("'", "");
                continue;
            }
            if (!t.startsWith("description:")) continue;
            description = t.substring(12).trim().replace("\"", "").replace("'", "");
        }
        result.put("name", name);
        result.put("description", description);
        return result;
    }
}

