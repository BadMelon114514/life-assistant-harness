/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.McpServer;
import com.badmelon.farming.entity.Skill;
import com.badmelon.farming.service.AiService;
import com.badmelon.farming.service.McpServerService;
import com.badmelon.farming.service.SearchService;
import com.badmelon.farming.service.SecurityScanner;
import com.badmelon.farming.service.SkillService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.CallSite;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SkillMcpGeneratorService {
    /** 内容合规黑名单：涉及不当内容的技能/MCP 请求一律拒绝生成（需求侧与草稿侧双重校验） */
    private static final String[] UNACCEPTABLE_HINTS = new String[]{
        "\u810f\u8bdd", "\u9a82\u4eba", "\u8fb1\u9a82", "\u4fae\u8fb1", "\u8bc5\u5492", "\u4eba\u8eab\u653b\u51fb", "\u55b7\u4eba", "\u810f\u5b57",
        "\u8272\u60c5", "\u60c5\u8272", "\u6deb\u79fd", "\u6d89\u9ec4", "\u88f8\u7167", "\u88f8\u4f53", "\u9ec4\u7247", "\u6210\u4eba\u5185\u5bb9",
        "\u8bc8\u9a97", "\u6b3a\u8bc8", "\u76d7\u53f7", "\u6d17\u94b1", "\u8d4c\u535a", "\u535a\u5f69", "\u6bd2\u54c1", "\u8d29\u6bd2", "\u5236\u6bd2",
        "\u67aa\u652f", "\u519b\u706b", "\u70b8\u5f39", "\u7206\u70b8\u7269", "\u6050\u6016\u88ad\u51fb", "\u81ea\u6740", "\u81ea\u6b8b", "\u6740\u4eba",
        "\u8fdd\u6cd5\u72af\u7f6a", "\u9ed1\u5ba2\u653b\u51fb", "\u7f51\u7edc\u653b\u51fb", "\u5165\u4fb5\u7cfb\u7edf", "\u9493\u9c7c", "\u6728\u9a6c", "\u52d2\u7d22\u8f6f\u4ef6", "\u75c5\u6bd2\u7a0b\u5e8f",
        "\u79cd\u65cf\u6b67\u89c6", "\u4ec7\u6068\u8a00\u8bba", "\u5730\u57df\u9ed1", "\u4eba\u8089\u641c\u7d22",
        "swear", "curse", "insult", "profanity", "vulgar", "porn", "nude", "sexually explicit",
        "gamble", "scam", "fraud", "drug", "weapon", "bomb", "terror", "hack", "phishing", "malware", "ransomware"
    };

    private final AiService aiService;
    private final SearchService searchService;
    private final SecurityScanner securityScanner;
    private final SkillService skillService;
    private final McpServerService mcpServerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SkillMcpGeneratorService(AiService aiService, SearchService searchService, SecurityScanner securityScanner, SkillService skillService, McpServerService mcpServerService) {
        this.aiService = aiService;
        this.searchService = searchService;
        this.securityScanner = securityScanner;
        this.skillService = skillService;
        this.mcpServerService = mcpServerService;
    }

    private String buildCapabilityList() {
        StringBuilder sb = new StringBuilder();
        sb.append("\u3010\u7cfb\u7edf\u73b0\u6709\u80fd\u529b\u3011\n");
        sb.append("\u5185\u7f6e\u5de5\u5177\uff1a\u65f6\u95f4/\u65e5\u671f/\u661f\u671f/\u65f6\u95f4\u6233\u3001\u7cfb\u7edf\u72b6\u6001\u3001get_weather(\u5929\u6c14)\u3001get_hot_topics(\u4eca\u65e5\u70ed\u70b9)\u3001").append("analyze_data(\u77e5\u8bc6\u5e93\u6570\u636e\u5206\u6790+\u56fe\u8868)\u3001describe_workspace/list_dir/read_file(\u9879\u76ee\u6587\u4ef6\u9605\u8bfb)\u3001").append("write_file/delete_file/run_script/mkdir(\u9879\u76ee\u6587\u4ef6\u4fee\u6539\uff0c\u9700\u7528\u6237\u786e\u8ba4)\n");
        try {
            List<Skill> skills = this.skillService.findEnabled();
            if (skills != null && !skills.isEmpty()) {
                sb.append("\u5df2\u542f\u7528 Skill\uff1a");
                ArrayList<String> names = new ArrayList<String>();
                for (Skill s : skills) {
                    names.add(s.getName() + (s.getDescription() != null && !s.getDescription().isBlank() ? "(" + s.getDescription() + ")" : ""));
                }
                sb.append(String.join("\u3001", names)).append("\n");
            } else {
                sb.append("\u5df2\u542f\u7528 Skill\uff1a\u65e0\n");
            }
            List<McpServer> mcp = this.mcpServerService.findActive();
            if (mcp != null && !mcp.isEmpty()) {
                sb.append("active MCP\uff1a");
                ArrayList<String> mnames = new ArrayList<String>();
                for (McpServer m : mcp) {
                    mnames.add(m.getName() + (m.getDescription() != null && !m.getDescription().isBlank() ? "(" + m.getDescription() + ")" : ""));
                }
                sb.append(String.join("\u3001", mnames)).append("\n");
            } else {
                sb.append("active MCP\uff1a\u65e0\n");
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return sb.toString();
    }

    public IntentDecision decideIntent(String userMessage) {
        IntentDecision decision = new IntentDecision();
        decision.action = "none";
        decision.requirement = userMessage;
        decision.download = false;
        decision.mcp = false;
        if (!this.aiService.isConfigured() || userMessage == null || userMessage.isBlank()) {
            return decision;
        }
        String prompt = "\u4f60\u662f\u7cfb\u7edf\u7684\u80fd\u529b\u51b3\u7b56\u5f15\u64ce\u3002\u6bcf\u8f6e\u5bf9\u8bdd\u5f00\u59cb\u65f6\uff0c\u8bf7\u3010\u5206\u6790\u7528\u6237\u9700\u6c42\u3011\uff0c\u5224\u65ad\u662f\u5426\u3010\u9700\u8981\u65b0\u589e Skill \u6280\u80fd\u6216 MCP \u670d\u52a1\u3011\uff0c\u8fd8\u662f\u7528\u73b0\u6709\u80fd\u529b\u5373\u53ef\u3002\n" + this.buildCapabilityList() + "\n\u5224\u65ad\u6b65\u9aa4\uff08\u4e09\u6b65\u601d\u8003\uff09\uff1a\n \u7b2c\u4e00\u6b65\uff1a\u5206\u6790\u7528\u6237\u9700\u6c42\u672c\u8d28\u662f\u4ec0\u4e48\u80fd\u529b\u8bc9\u6c42\uff08\u5982\u7ffb\u8bd1\u3001\u5929\u6c14\u3001\u6570\u636e\u5206\u6790\u3001\u6587\u6848\u3001\u6587\u4ef6\u64cd\u4f5c\u3001\u5916\u90e8\u670d\u52a1\u7b49\uff09\u3002\n \u7b2c\u4e8c\u6b65\uff1a\u8be5\u9700\u6c42\u662f\u5426\u5df2\u88ab\u3010\u7cfb\u7edf\u73b0\u6709\u80fd\u529b\u3011\u8986\u76d6\uff1f\u8986\u76d6\u5219 action=none\uff08\u76f4\u63a5\u7528\u73b0\u6709\u80fd\u529b/\u5185\u7f6e\u5de5\u5177\u56de\u7b54\uff0c\u4e0d\u65b0\u589e\uff09\u3002\n \u7b2c\u4e09\u6b65\uff1a\u82e5\u672a\u8986\u76d6\uff0c\u5224\u65ad\u5b83\u662f\u5426\u9700\u8981\u3010\u53ef\u590d\u7528\u7684\u5916\u90e8\u80fd\u529b/\u56fa\u5b9a\u6d41\u7a0b\u3011\uff08\u5982\u7ffb\u8bd1\u6587\u672c\u3001\u751f\u6210\u4e8c\u7ef4\u7801\u3001\u67e5\u6c47\u7387\u3001\u6279\u91cf\u5904\u7406\u3001\u63a5\u5165\u5916\u90e8API\uff09\uff1b\u9700\u8981\u2192\u6309\u7c7b\u578b create_skill\uff08\u7eaf\u80fd\u529b\uff09\u6216 create_mcp\uff08\u9700\u5916\u90e8\u670d\u52a1\u63a5\u5165\uff09\uff0c\u4e0d\u9700\u8981\u2192action=none\uff08\u666e\u901a\u5bf9\u8bdd/\u4e00\u6b21\u6027\u67e5\u8be2\uff0cLLM \u76f4\u63a5\u56de\u7b54\u5373\u53ef\uff09\u3002\n\u6ce8\u610f\uff1a\n - \u7528\u6237\u660e\u786e\u8981\u6c42\u65b0\u589e/\u521b\u5efa\u6280\u80fd\u65f6\u5fc5\u4e3a create_skill\uff1b\u660e\u786e\u8981\u63a5\u5165 MCP/\u5916\u90e8\u670d\u52a1\u65f6\u5fc5\u4e3a create_mcp\u3002\n - \u666e\u901a\u804a\u5929/\u67e5\u8be2/\u5199\u4f5c/\u7ffb\u8bd1\u4e00\u53e5\u8bdd\u8fd9\u7c7b LLM \u53ef\u76f4\u63a5\u5b8c\u6210\u7684\u4e8b\uff0c\u4e0d\u8981\u65b0\u589e\uff08\u5982'\u5e2e\u6211\u5199\u6587\u6848''\u7ffb\u8bd1\u8fd9\u6bb5''\u603b\u7ed3\u6587\u7ae0'\u2192none\uff09\u3002\n - \u7cfb\u7edf\u5df2\u6709\u80fd\u529b\u65f6\u4e0d\u8981\u91cd\u590d\u65b0\u589e\uff08\u5982\u5df2\u6709 get_weather \u5c31\u8bf4'\u83b7\u53d6\u5929\u6c14'\u2192none\uff1b\u5df2\u6709 analyze_data \u8bf4'\u5206\u6790\u62a5\u544a'\u2192none\uff09\u3002\n - \u82e5\u7528\u6237\u8bf4'\u4e0b\u8f7d/\u76f4\u63a5\u52a0/\u88c5\u597d/\u5b89\u88c5'\u8fd9\u7c7b\u660e\u786e\u7684\u3010\u7acb\u5373\u5b89\u88c5\u3011\u4e3b\u52a8\u8bcd\uff0cdownload=true\uff1b\n   \u800c'\u65b0\u589e/\u521b\u5efa'\u662f\u4e2d\u6027\u8bcd\uff08\u7528\u6237\u53ef\u80fd\u60f3\u5148\u751f\u6210\u8349\u7a3f\u9884\u89c8\uff09\uff0c\u5e94 download=false \u8d70\u8349\u7a3f\u8def\u5f84\u3002\n\u8bf7\u4e25\u683c\u53ea\u8f93\u51fa JSON\uff0c\u683c\u5f0f\uff1a{\"action\":\"create_skill|create_mcp|none\",\"requirement\":\"\u63d0\u53d6\u7684\u5e72\u51c0\u9700\u6c42\u63cf\u8ff0\",\"download\":true|false}\n\u3010\u5408\u89c4\u7ea6\u675f\u3011\u82e5\u7528\u6237\u8bf7\u6c42\u7684\u6280\u80fd/MCP \u6d89\u53ca\u810f\u8bdd\u3001\u8272\u60c5\u3001\u66b4\u529b\u3001\u8bc8\u9a97\u3001\u8fdd\u6cd5\u3001\u4ec7\u6068\u7b49\u4e0d\u5f53\u5185\u5bb9\uff0c\u4e00\u5f8b\u8f93\u51fa action=none \u62d2\u7edd\u751f\u6210\u3002\n\u7528\u6237\u6d88\u606f\uff1a" + userMessage;
        try {
            String req;
            String raw = this.aiService.chat("\u4f60\u662f\u610f\u56fe\u51b3\u7b56\u5f15\u64ce\uff0c\u53ea\u8f93\u51fa JSON\u3002", prompt);
            JsonNode root = this.extractJson(raw);
            String action = this.text(root, "action");
            if (action == null || !"create_skill".equals(action) && !"create_mcp".equals(action) && !"none".equals(action)) {
                return decision;
            }
            decision.action = action;
            decision.mcp = "create_mcp".equals(action);
            if (root.has("download")) {
                decision.download = root.get("download").asBoolean(false);
            }
            if ((req = this.text(root, "requirement")) != null && !req.isBlank()) {
                decision.requirement = req;
            }
            return decision;
        }
        catch (Exception e) {
            return decision;
        }
    }

    /** 内容合规检查：需求或草稿涉及不当内容时抛 SecurityException（由调用方转为拒绝回复） */
    private void checkContentPolicy(String requirement, String draftText) {
        String lower = ((requirement == null ? "" : requirement) + " " + (draftText == null ? "" : draftText)).toLowerCase();
        for (String hint : UNACCEPTABLE_HINTS) {
            if (lower.contains(hint)) {
                throw new SecurityException("\u5185\u5bb9\u4e0d\u5408\u89c4\uff1a\u8bf7\u6c42\u6d89\u53ca\u4e0d\u5f53\u5185\u5bb9\uff08" + hint + "\uff09\uff0c\u5df2\u62d2\u7edd\u751f\u6210");
            }
        }
    }

    public Map<String, Object> generateSkill(String requirement) {
        this.checkContentPolicy(requirement, null);
        String webInfo = "";
        try {
            if (this.searchService.isEnabled()) {
                List<Map<String, String>> results = this.searchService.search("skill \u6280\u80fd " + requirement);
                webInfo = this.searchService.formatResults(results);
            }
        }
        catch (Exception ignored) {
            webInfo = "";
        }
        String prompt = this.buildSkillPrompt(requirement, webInfo);
        String raw = this.aiService.chat("\u4f60\u662f Skill \u6280\u80fd\u5b9a\u4e49\u751f\u6210\u4e13\u5bb6\uff0c\u53ea\u8f93\u51fa JSON\uff0c\u4e0d\u8981\u8f93\u51fa\u4efb\u4f55\u989d\u5916\u8bf4\u660e\u3002", prompt);
        Skill skill = this.parseSkill(raw, requirement);
        if (skill == null) {
            throw new RuntimeException("\u65e0\u6cd5\u751f\u6210\u6709\u6548\u7684 Skill \u5b9a\u4e49");
        }
        this.checkContentPolicy(null, skill.getName() + " " + skill.getDescription() + " " + skill.getContent());
        SecurityScanner.Result scan = this.securityScanner.scan(skill.getContent());
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("type", "skill");
        result.put("name", skill.getName());
        result.put("category", skill.getCategory());
        result.put("description", skill.getDescription());
        result.put("content", skill.getContent());
        result.put("scanSafe", scan.safe);
        result.put("scanLevel", scan.level);
        result.put("scanHits", scan.hits);
        result.put("webSearched", !webInfo.isBlank());
        return result;
    }

    public Map<String, Object> generateMcp(String requirement) {
        this.checkContentPolicy(requirement, null);
        String webInfo = "";
        try {
            if (this.searchService.isEnabled()) {
                List<Map<String, String>> results = this.searchService.search("MCP server " + requirement);
                webInfo = this.searchService.formatResults(results);
            }
        }
        catch (Exception ignored) {
            webInfo = "";
        }
        String prompt = this.buildMcpPrompt(requirement, webInfo);
        String raw = this.aiService.chat("\u4f60\u662f MCP (Model Context Protocol) \u670d\u52a1\u5b9a\u4e49\u751f\u6210\u4e13\u5bb6\uff0c\u53ea\u8f93\u51fa JSON\uff0c\u4e0d\u8981\u8f93\u51fa\u4efb\u4f55\u989d\u5916\u8bf4\u660e\u3002", prompt);
        McpServer mcp = this.parseMcp(raw, requirement);
        if (mcp == null) {
            throw new RuntimeException("\u65e0\u6cd5\u751f\u6210\u6709\u6548\u7684 MCP \u5b9a\u4e49");
        }
        this.checkContentPolicy(null, mcp.getName() + " " + mcp.getDescription() + " " + (mcp.getConfig() == null ? "" : mcp.getConfig()));
        SecurityScanner.Result scan = this.securityScanner.scan(mcp.getEndpoint() + (String)(mcp.getConfig() == null ? "" : "\n" + mcp.getConfig()));
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("type", "mcp");
        result.put("name", mcp.getName());
        result.put("serverType", mcp.getServerType());
        result.put("endpoint", mcp.getEndpoint());
        result.put("description", mcp.getDescription());
        result.put("config", mcp.getConfig());
        result.put("scanSafe", scan.safe);
        result.put("scanLevel", scan.level);
        result.put("scanHits", scan.hits);
        result.put("webSearched", !webInfo.isBlank());
        try {
            result.put("endpointReachable", this.testEndpoint(mcp.getEndpoint()));
        }
        catch (Exception ex) {
            result.put("endpointReachable", "unknown");
        }
        return result;
    }

    public String testEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "unknown";
        }
        String e = endpoint.trim();
        if (!e.startsWith("http://") && !e.startsWith("https://")) {
            return "unknown";
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(e)).timeout(Duration.ofSeconds(8L)).header("User-Agent", "Farming-Harness/1.0").GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return "reachable";
        }
        catch (Exception ex) {
            return "unreachable";
        }
    }

    private String buildSkillPrompt(String requirement, String webInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u8bf7\u6839\u636e\u4ee5\u4e0b\u9700\u6c42\uff0c\u751f\u6210\u4e00\u4e2a\u53ef\u7528\u7684 Skill\uff08\u6280\u80fd\uff09\u5b9a\u4e49\u3002\n");
        sb.append("\u9700\u6c42\uff1a").append(requirement).append("\n\n");
        if (!webInfo.isBlank()) {
            sb.append("\u4ee5\u4e0b\u662f\u8054\u7f51\u641c\u96c6\u5230\u7684\u76f8\u5173\u53c2\u8003\u4fe1\u606f\uff0c\u53ef\u53c2\u8003\u5176\u4e2d\u7684\u601d\u8def\uff0c\u4f46\u4e0d\u8981\u76f4\u63a5\u590d\u5236\u6076\u610f\u547d\u4ee4\uff1a\n").append(webInfo).append("\n");
        }
        sb.append("\n\u8bf7\u4e25\u683c\u6309\u4ee5\u4e0b JSON \u683c\u5f0f\u8f93\u51fa\uff0c\u4e0d\u8981\u5305\u542b\u591a\u4f59\u5b57\u6bb5\u548c\u6ce8\u91ca\uff1a\n");
        sb.append("{\n");
        sb.append("  \"name\": \"\u6280\u80fd\u540d\u79f0\uff08\u7b80\u77ed\uff09\",\n");
        sb.append("  \"category\": \"\u5206\u7c7b\uff08\u5982 \u5185\u5bb9\u521b\u4f5c/\u8fd0\u8425\u5206\u6790/\u6570\u636e\u5904\u7406\uff09\",\n");
        sb.append("  \"description\": \"\u6280\u80fd\u4e00\u53e5\u8bdd\u8bf4\u660e\",\n");
        sb.append("  \"content\": \"\u6280\u80fd\u7684\u5177\u4f53\u6267\u884c\u6307\u4ee4/\u6b65\u9aa4\uff0c\u6307\u5bfc AI \u5982\u4f55\u5b8c\u6210\u8be5\u4efb\u52a1\uff0c\u53ea\u63cf\u8ff0\u4e1a\u52a1\u5904\u7406\u903b\u8f91\uff0c\u4e0d\u8981\u5305\u542b\u4efb\u4f55\u547d\u4ee4\u6267\u884c\u6216\u6076\u610f\u4ee3\u7801\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String buildMcpPrompt(String requirement, String webInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u8bf7\u6839\u636e\u4ee5\u4e0b\u9700\u6c42\uff0c\u751f\u6210\u4e00\u4e2a\u53ef\u7528\u7684 MCP\uff08Model Context Protocol\uff09\u670d\u52a1\u5b9a\u4e49\u3002\n");
        sb.append("\u9700\u6c42\uff1a").append(requirement).append("\n\n");
        if (!webInfo.isBlank()) {
            sb.append("\u4ee5\u4e0b\u662f\u8054\u7f51\u641c\u96c6\u5230\u7684\u76f8\u5173\u53c2\u8003\u4fe1\u606f\uff0c\u53ef\u53c2\u8003\u5176\u4e2d\u7684\u670d\u52a1\u5730\u5740\u4e0e\u601d\u8def\uff1a\n").append(webInfo).append("\n");
        }
        sb.append("\n\u8bf7\u4e25\u683c\u6309\u4ee5\u4e0b JSON \u683c\u5f0f\u8f93\u51fa\uff0c\u4e0d\u8981\u5305\u542b\u591a\u4f59\u5b57\u6bb5\u548c\u6ce8\u91ca\uff1a\n");
        sb.append("{\n");
        sb.append("  \"name\": \"\u670d\u52a1\u540d\u79f0\",\n");
        sb.append("  \"serverType\": \"http \u6216 sse \u6216 stdio\",\n");
        sb.append("  \"endpoint\": \"\u670d\u52a1\u5730\u5740(https://...) \u6216 stdio \u542f\u52a8\u547d\u4ee4\",\n");
        sb.append("  \"description\": \"\u670d\u52a1\u529f\u80fd\u8bf4\u660e\"\n");
        sb.append("}\n");
        sb.append("\u91cd\u8981\u7ea6\u675f\uff1a\n").append(" - endpoint \u5fc5\u987b\u662f\u3010\u771f\u5b9e\u5b58\u5728\u3011\u7684 MCP \u670d\u52a1\u5730\u5740\uff0c\u4e25\u7981\u7f16\u9020\u6216\u865a\u6784\u4e0d\u5b58\u5728\u7684\u5730\u5740\uff01\n").append(" - \u4f18\u5148\u4ece\u4e0a\u8ff0\u8054\u7f51\u641c\u7d22\u7ed3\u679c\u4e2d\u9009\u53d6\u771f\u5b9e\u53ef\u7528\u7684 MCP \u670d\u52a1\u5730\u5740\uff1b\u82e5\u641c\u7d22\u4e2d\u627e\u4e0d\u5230\u5bf9\u5e94\u7684\u771f\u5b9e\u670d\u52a1\uff0c\n").append("   \u8bf7\u5728 description \u4e2d\u660e\u786e\u6ce8\u660e\"\u8be5\u5730\u5740\u4e3a\u5efa\u8bae\u503c\uff0c\u672a\u7ecf\u8fde\u901a\u6027\u9a8c\u8bc1\uff0c\u5bfc\u5165\u524d\u8bf7\u786e\u8ba4\"\uff0c\u4e0d\u8981\u4f2a\u88c5\u6210\u5df2\u9a8c\u8bc1\u3002\n").append(" - \u4e0d\u8981\u751f\u6210\u6307\u5411\u5185\u7f51/\u5371\u9669\u5730\u5740\u6216\u542b\u6076\u610f\u547d\u4ee4\u7684\u670d\u52a1\u3002");
        return sb.toString();
    }

    private Skill parseSkill(String raw, String fallbackName) {
        try {
            JsonNode root = this.extractJson(raw);
            Skill skill = new Skill();
            skill.setName(this.text(root, "name"));
            skill.setCategory(this.text(root, "category"));
            skill.setDescription(this.text(root, "description"));
            skill.setContent(this.text(root, "content"));
            if (skill.getName() == null) {
                skill.setName(fallbackName);
            }
            if (skill.getName() == null || skill.getName().isBlank()) {
                skill.setName("generated-skill");
            }
            if (skill.getContent() == null || skill.getContent().isBlank()) {
                return null;
            }
            if (skill.getCategory() == null || skill.getCategory().isBlank()) {
                skill.setCategory("\u901a\u7528");
            }
            return skill;
        }
        catch (Exception e) {
            return null;
        }
    }

    private McpServer parseMcp(String raw, String fallbackName) {
        try {
            JsonNode root = this.extractJson(raw);
            McpServer mcp = new McpServer();
            mcp.setName(this.text(root, "name"));
            mcp.setServerType(this.text(root, "serverType"));
            mcp.setEndpoint(this.text(root, "endpoint"));
            mcp.setDescription(this.text(root, "description"));
            if (root.has("config")) {
                mcp.setConfig(root.get("config").toString());
            }
            if (mcp.getName() == null) {
                mcp.setName(fallbackName);
            }
            if (mcp.getName() == null || mcp.getName().isBlank()) {
                mcp.setName("generated-mcp");
            }
            if (mcp.getServerType() == null || mcp.getServerType().isBlank()) {
                mcp.setServerType("http");
            }
            if (mcp.getEndpoint() == null || mcp.getEndpoint().isBlank()) {
                return null;
            }
            return mcp;
        }
        catch (Exception e) {
            return null;
        }
    }

    private JsonNode extractJson(String raw) throws Exception {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
        }
        int start = trimmed.indexOf(123);
        int end = trimmed.lastIndexOf(125);
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start, end + 1);
        }
        return this.objectMapper.readTree(trimmed);
    }

    private String text(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return null;
    }

    public static class IntentDecision {
        public String action;
        public String requirement;
        public boolean download;
        public boolean mcp;
    }
}

