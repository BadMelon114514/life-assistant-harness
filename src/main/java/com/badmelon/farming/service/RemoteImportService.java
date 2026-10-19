/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.McpServer;
import com.badmelon.farming.entity.Skill;
import com.badmelon.farming.service.McpServerService;
import com.badmelon.farming.service.SecurityScanner;
import com.badmelon.farming.service.SkillService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RemoteImportService {
    private final SecurityScanner securityScanner;
    private final McpServerService mcpServerService;
    private final SkillService skillService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15L)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public RemoteImportService(SecurityScanner securityScanner, McpServerService mcpServerService, SkillService skillService) {
        this.securityScanner = securityScanner;
        this.mcpServerService = mcpServerService;
        this.skillService = skillService;
    }

    public String download(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15L)).header("User-Agent", "Farming-Harness-Importer/1.0").GET().build();
            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new RuntimeException("\u4e0b\u8f7d\u5931\u8d25\uff0cHTTP " + response.statusCode());
            }
            return response.body();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("\u4e0b\u8f7d\u88ab\u4e2d\u65ad", e);
        }
        catch (Exception e) {
            throw new RuntimeException("\u4e0b\u8f7d\u5931\u8d25: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> importMcp(String url) {
        String content = this.download(url);
        SecurityScanner.Result scan = this.securityScanner.scan(content);
        if (!scan.safe) {
            throw new SecurityException("\u68c0\u6d4b\u5230\u98ce\u9669\u5185\u5bb9\uff0c\u5df2\u62d2\u7edd\u5bfc\u5165\uff1a" + scan.riskCount + " \u9879\u98ce\u9669");
        }
        McpServer server = this.parseMcp(content);
        McpServer saved = this.mcpServerService.save(server);
        return Map.of("type", "mcp", "scanLevel", scan.level, "riskCount", 0, "server", saved);
    }

    public Map<String, Object> importSkill(String url) {
        String content = this.download(url);
        SecurityScanner.Result scan = this.securityScanner.scan(content);
        if (!scan.safe) {
            throw new SecurityException("\u68c0\u6d4b\u5230\u98ce\u9669\u5185\u5bb9\uff0c\u5df2\u62d2\u7edd\u5bfc\u5165\uff1a" + scan.riskCount + " \u9879\u98ce\u9669");
        }
        Skill skill = this.parseSkill(content);
        Skill saved = this.skillService.save(skill);
        return Map.of("type", "skill", "scanLevel", scan.level, "riskCount", 0, "skill", saved);
    }

    public Map<String, Object> scanOnly(String url) {
        String content = this.download(url);
        SecurityScanner.Result scan = this.securityScanner.scan(content);
        return Map.of("safe", scan.safe, "level", scan.level, "riskCount", scan.riskCount, "hits", scan.hits, "preview", content.length() > 1000 ? content.substring(0, 1000) : content);
    }

    private McpServer parseMcp(String content) {
        McpServer server = new McpServer();
        try {
            JsonNode root = this.objectMapper.readTree(content);
            server.setName(this.text(root, "name"));
            server.setServerType(this.text(root, "serverType") != null ? this.text(root, "serverType") : this.text(root, "type"));
            server.setEndpoint(this.text(root, "endpoint"));
            server.setDescription(this.text(root, "description"));
            if (root.has("config")) {
                server.setConfig(root.get("config").toString());
            }
            if (server.getServerType() == null) {
                server.setServerType("http");
            }
            if (server.getName() == null || server.getEndpoint() == null) {
                throw new RuntimeException("MCP \u914d\u7f6e\u7f3a\u5c11 name \u6216 endpoint \u5b57\u6bb5");
            }
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            server.setName("remote-" + System.currentTimeMillis() % 100000L);
            server.setServerType("http");
            server.setEndpoint(content);
            server.setDescription("\u8fdc\u7a0b\u5bfc\u5165\uff08\u7eaf\u6587\u672c\u7aef\u70b9\uff09");
        }
        return server;
    }

    private Skill parseSkill(String content) {
        Skill skill = new Skill();
        try {
            JsonNode root = this.objectMapper.readTree(content);
            skill.setName(this.text(root, "name"));
            skill.setCategory(this.text(root, "category"));
            skill.setDescription(this.text(root, "description"));
            skill.setContent(this.text(root, "content"));
            if (root.has("enabled")) {
                skill.setEnabled(root.get("enabled").asInt(1));
            }
            if (skill.getName() == null || skill.getContent() == null) {
                throw new RuntimeException("Skill \u5b9a\u4e49\u7f3a\u5c11 name \u6216 content \u5b57\u6bb5");
            }
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException("Skill \u5bfc\u5165\u683c\u5f0f\u4e0d\u6b63\u786e\uff0c\u9700\u4e3a JSON \u5b9a\u4e49");
        }
        return skill;
    }

    private String text(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return null;
    }
}

