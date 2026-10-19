/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.controller;

import com.badmelon.farming.entity.McpServer;
import com.badmelon.farming.service.McpServerService;
import com.badmelon.farming.service.OpenclawImportService;
import com.badmelon.farming.service.RemoteImportService;
import com.badmelon.farming.service.SecurityScanner;
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
@RequestMapping(value={"/api/mcp"})
public class McpController {
    private final McpServerService mcpServerService;
    private final RemoteImportService remoteImportService;
    private final SecurityScanner securityScanner;
    private final OpenclawImportService openclawImportService;

    public McpController(McpServerService mcpServerService, RemoteImportService remoteImportService, SecurityScanner securityScanner, OpenclawImportService openclawImportService) {
        this.mcpServerService = mcpServerService;
        this.remoteImportService = remoteImportService;
        this.securityScanner = securityScanner;
        this.openclawImportService = openclawImportService;
    }

    @PostMapping(value={"/import-openclaw"})
    public Map<String, Object> importOpenclaw() {
        return this.openclawImportService.importMcpServers();
    }

    @GetMapping
    public List<McpServer> list() {
        return this.mcpServerService.findAll();
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
            Map<String, Object> result = this.remoteImportService.importMcp(url);
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
    public Map<String, Object> importDraft(@RequestBody McpServer server) {
        HashMap<String, Object> resp = new HashMap<String, Object>();
        if (server.getName() == null || server.getName().isBlank() || server.getEndpoint() == null || server.getEndpoint().isBlank()) {
            resp.put("success", false);
            resp.put("error", "MCP \u8349\u7a3f\u7f3a\u5c11\u540d\u79f0\u6216\u5730\u5740");
            return resp;
        }
        SecurityScanner.Result scan = this.securityScanner.scan(server.getEndpoint() + (String)(server.getConfig() == null ? "" : "\n" + server.getConfig()));
        if (!scan.safe) {
            resp.put("success", false);
            resp.put("blocked", true);
            resp.put("riskCount", scan.riskCount);
            resp.put("hits", scan.hits);
            resp.put("error", "\u68c0\u6d4b\u5230\u98ce\u9669\u5185\u5bb9\uff0c\u5df2\u62d2\u7edd\u5bfc\u5165\uff1a" + scan.riskCount + " \u9879\u98ce\u9669");
            return resp;
        }
        McpServer saved = this.mcpServerService.save(server);
        resp.put("success", true);
        resp.put("scanLevel", scan.level);
        resp.put("server", saved);
        return resp;
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody McpServer server) {
        McpServer saved = this.mcpServerService.save(server);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        resp.put("server", saved);
        return resp;
    }

    @PostMapping(value={"/{id}/toggle"})
    public Map<String, Object> toggle(@PathVariable Long id) {
        McpServer server = this.mcpServerService.toggle(id);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", server != null);
        resp.put("server", server);
        return resp;
    }

    @PostMapping(value={"/{id}/test"})
    public Map<String, Object> test(@PathVariable Long id) {
        McpServerService.MapResult result = this.mcpServerService.test(id);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", result.ok);
        resp.put("message", result.message);
        return resp;
    }

    @DeleteMapping(value={"/{id}"})
    public Map<String, Object> delete(@PathVariable Long id) {
        this.mcpServerService.delete(id);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        return resp;
    }
}

