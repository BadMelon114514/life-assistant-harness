/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.McpServer;
import com.badmelon.farming.mapper.McpServerMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class McpServerService {
    private final McpServerMapper mcpServerMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public McpServerService(McpServerMapper mcpServerMapper) {
        this.mcpServerMapper = mcpServerMapper;
    }

    public List<McpServer> findAll() {
        return this.mcpServerMapper.findAll();
    }

    public List<McpServer> findActive() {
        return this.mcpServerMapper.findActive();
    }

    public McpServer findById(Long id) {
        return this.mcpServerMapper.findById(id);
    }

    /** 列限长保护：description/endpoint/name 超出列宽时截断，避免 Data too long 落库失败 */
    private static String cut(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    public McpServer save(McpServer server) {
        server.setName(cut(server.getName(), 100));
        server.setEndpoint(cut(server.getEndpoint(), 255));
        server.setDescription(cut(server.getDescription(), 255));
        McpServer existing;
        McpServer mcpServer = existing = server.getId() != null ? this.mcpServerMapper.findById(server.getId()) : null;
        if (existing == null) {
            server.setStatus(server.getStatus() == null ? "inactive" : server.getStatus());
            this.mcpServerMapper.insert(server);
        } else {
            if (server.getStatus() == null) {
                server.setStatus(existing.getStatus());
            }
            this.mcpServerMapper.update(server);
        }
        return server;
    }

    public void delete(Long id) {
        this.mcpServerMapper.deleteById(id);
    }

    public McpServer toggle(Long id) {
        McpServer server = this.mcpServerMapper.findById(id);
        if (server == null) {
            return null;
        }
        server.setStatus("active".equals(server.getStatus()) ? "inactive" : "active");
        this.mcpServerMapper.update(server);
        return server;
    }

    public MapResult test(Long id) {
        McpServer server = this.mcpServerMapper.findById(id);
        if (server == null) {
            return new MapResult(false, "\u670d\u52a1\u4e0d\u5b58\u5728");
        }
        try {
            if ("stdio".equalsIgnoreCase(server.getServerType())) {
                server.setLastChecked(LocalDateTime.now());
                this.mcpServerMapper.update(server);
                return new MapResult(true, "stdio \u670d\u52a1\u5df2\u4fdd\u5b58\u914d\u7f6e\uff0c\u547d\u4ee4: " + (server.getEndpoint() == null ? "(\u7a7a)" : server.getEndpoint()));
            }
            if (server.getEndpoint() == null || server.getEndpoint().isBlank()) {
                return new MapResult(false, "\u672a\u914d\u7f6e\u670d\u52a1\u5730\u5740");
            }
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(server.getEndpoint())).timeout(Duration.ofSeconds(8L)).GET().build();
            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            server.setLastChecked(LocalDateTime.now());
            if (response.statusCode() < 400) {
                server.setStatus("active");
                this.mcpServerMapper.update(server);
                return new MapResult(true, "\u8fde\u63a5\u6210\u529f\uff0cHTTP " + response.statusCode());
            }
            server.setStatus("inactive");
            this.mcpServerMapper.update(server);
            return new MapResult(false, "\u8fde\u63a5\u5931\u8d25\uff0cHTTP " + response.statusCode());
        }
        catch (Exception e) {
            server.setLastChecked(LocalDateTime.now());
            server.setStatus("inactive");
            this.mcpServerMapper.update(server);
            return new MapResult(false, "\u8fde\u63a5\u5f02\u5e38: " + e.getMessage());
        }
    }

    public static class MapResult {
        public final boolean ok;
        public final String message;

        public MapResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }
}

