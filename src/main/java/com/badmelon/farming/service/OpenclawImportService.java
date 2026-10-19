/*
 * 从项目内置资源库（src/main/resources/openclaw/）批量导入 Skill 与 MCP 服务器。
 * 这些资源已固化在项目代码库中，不依赖外部 E:\openclaw 目录。
 * - Skills: 解析 classpath:/openclaw/skills.json 中的内置技能定义。
 * - MCP:    解析 classpath:/openclaw/mcp.json 中的内置 MCP 服务器定义。
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.McpServer;
import com.badmelon.farming.entity.Skill;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class OpenclawImportService {
    private static final String SKILLS_RESOURCE = "openclaw/skills.json";
    private static final String MCP_RESOURCE = "openclaw/mcp.json";

    private final SkillService skillService;
    private final McpServerService mcpServerService;
    private final SecurityScanner securityScanner;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenclawImportService(SkillService skillService, McpServerService mcpServerService,
                                 SecurityScanner securityScanner) {
        this.skillService = skillService;
        this.mcpServerService = mcpServerService;
        this.securityScanner = securityScanner;
    }

    private String loadResource(String path) {
        try {
            InputStream in = new ClassPathResource(path).getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("内置资源加载失败: " + path + " (" + e.getMessage() + ")");
        }
    }

    /** 从项目内置技能库导入所有 Skill。返回 {imported, skipped} */
    public Map<String, Object> importSkills() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> imported = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();

        try {
            String raw = this.loadResource(SKILLS_RESOURCE);
            JsonNode root = this.objectMapper.readTree(raw);
            JsonNode arr = root.isArray() ? root : root.path("skills");
            for (JsonNode node : arr) {
                String name = node.path("name").asText("");
                String desc = node.path("description").asText("");
                String category = node.path("category").asText("openclaw");
                String content = node.path("content").asText("");
                if (name.isBlank() || content.isBlank()) {
                    continue;
                }
                Skill skill = new Skill();
                skill.setName(name);
                skill.setCategory(category);
                skill.setDescription(desc);
                skill.setContent(content);
                skill.setEnabled(0); // 默认停用，由用户前端启用

                SecurityScanner.Result scan = this.securityScanner.scan(content);
                if (!scan.safe) {
                    Map<String, Object> s = new HashMap<>();
                    s.put("name", name);
                    s.put("reason", "security:" + scan.riskCount);
                    skipped.add(s);
                    continue;
                }
                Skill existing = this.skillService.findByName(name);
                if (existing != null) {
                    Map<String, Object> s = new HashMap<>();
                    s.put("name", name);
                    s.put("reason", "exists");
                    skipped.add(s);
                    continue;
                }
                Skill saved = this.skillService.save(skill);
                Map<String, Object> item = new HashMap<>();
                item.put("name", saved.getName());
                item.put("id", saved.getId());
                item.put("description", desc);
                imported.add(item);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "内置技能库解析失败: " + e.getMessage());
            return result;
        }
        result.put("success", true);
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("importedCount", imported.size());
        result.put("skippedCount", skipped.size());
        return result;
    }

    /** 从项目内置 MCP 库导入所有 MCP 服务器。返回 {imported, skipped} */
    public Map<String, Object> importMcpServers() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> imported = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();

        try {
            String raw = this.loadResource(MCP_RESOURCE);
            JsonNode root = this.objectMapper.readTree(raw);
            JsonNode arr = root.isArray() ? root : root.path("servers");
            for (JsonNode node : arr) {
                McpServer server = new McpServer();
                server.setName(node.path("name").asText(""));
                server.setServerType(node.path("serverType").asText("stdio"));
                server.setEndpoint(node.path("endpoint").asText(""));
                server.setDescription(node.path("description").asText(""));
                server.setStatus("inactive");
                if (server.getName().isBlank() || server.getEndpoint().isBlank()) {
                    continue;
                }
                McpServer existing = this.mcpServerService.findAll().stream()
                        .filter(m -> server.getName().equalsIgnoreCase(m.getName()))
                        .findFirst().orElse(null);
                if (existing != null) {
                    Map<String, Object> s = new HashMap<>();
                    s.put("name", server.getName());
                    s.put("reason", "exists");
                    skipped.add(s);
                    continue;
                }
                McpServer saved = this.mcpServerService.save(server);
                Map<String, Object> item = new HashMap<>();
                item.put("name", saved.getName());
                item.put("id", saved.getId());
                item.put("serverType", saved.getServerType());
                item.put("endpoint", saved.getEndpoint());
                item.put("description", saved.getDescription());
                imported.add(item);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "内置 MCP 库解析失败: " + e.getMessage());
            return result;
        }
        result.put("success", true);
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("importedCount", imported.size());
        result.put("skippedCount", skipped.size());
        return result;
    }

    /** 供聊天助手引用的内置库能力说明 */
    public String describe() {
        return "本项目内置了 openclaw 生态精选的 Skill 技能库与常用 MCP 服务器库，可通过「技能管理」和「MCP 服务管理」页面一键导入。";
    }
}
