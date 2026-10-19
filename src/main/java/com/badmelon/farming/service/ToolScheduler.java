/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.HarnessTask;
import com.badmelon.farming.entity.KnowledgeDoc;
import com.badmelon.farming.entity.McpServer;
import com.badmelon.farming.entity.Skill;
import com.badmelon.farming.entity.Teammate;
import com.badmelon.farming.mapper.TeammateMapper;
import com.badmelon.farming.service.AiService;
import com.badmelon.farming.service.DataAnalysisService;
import com.badmelon.farming.service.FileOpsService;
import com.badmelon.farming.service.KnowledgeService;
import com.badmelon.farming.service.McpServerService;
import com.badmelon.farming.service.SearchService;
import com.badmelon.farming.service.OpenclawImportService;
import com.badmelon.farming.service.SettingsService;
import com.badmelon.farming.service.SkillService;
import com.badmelon.farming.service.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ToolScheduler {
    private final AiService aiService;
    private final McpServerService mcpServerService;
    private final SkillService skillService;
    private final SearchService searchService;
    private final FileOpsService fileOpsService;
    private final KnowledgeService knowledgeService;
    private final DataAnalysisService dataAnalysisService;
    private final SettingsService settingsService;
    private final TeammateMapper teammateMapper;
    private final TaskService taskService;
    private final OpenclawImportService openclawImportService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final String[] HIGH_RISK_HINTS = new String[]{"delete", "remove", "drop", "truncate", "clear", "purge", "rm ", " rm", "unlink", "destroy", "wipe", "reset", "shutdown", "restart", "stop", "write", "overwrite", "update", "insert", "create", "upload", "send", "\u8f6c\u8d26", "\u5220\u9664", "\u6e05\u7a7a", "\u79fb\u9664", "\u91cd\u7f6e", "\u8986\u76d6", "\u5199\u5165", "\u53d1\u9001", "\u5173\u95ed", "\u91cd\u542f", "\u505c\u6b62"};
    private final ConcurrentHashMap<String, CachedWeather> weatherCache = new ConcurrentHashMap();

    public ToolScheduler(AiService aiService, McpServerService mcpServerService, SkillService skillService, SearchService searchService, FileOpsService fileOpsService, KnowledgeService knowledgeService, DataAnalysisService dataAnalysisService, SettingsService settingsService, TeammateMapper teammateMapper, TaskService taskService, OpenclawImportService openclawImportService) {
        this.aiService = aiService;
        this.mcpServerService = mcpServerService;
        this.skillService = skillService;
        this.searchService = searchService;
        this.fileOpsService = fileOpsService;
        this.knowledgeService = knowledgeService;
        this.dataAnalysisService = dataAnalysisService;
        this.settingsService = settingsService;
        this.teammateMapper = teammateMapper;
        this.taskService = taskService;
        this.openclawImportService = openclawImportService;
    }

    private boolean isToolAllowed(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        try {
            String deny = this.settingsService.getValue("tool_policy_deny");
            String allow = this.settingsService.getValue("tool_policy_allow");
            boolean denied = this.policyMatches(name, deny);
            boolean whitelistOk = allow == null || allow.isBlank() || this.policyMatches(name, allow);
            return !denied && whitelistOk;
        }
        catch (Exception e) {
            LoggerFactory.getLogger(ToolScheduler.class).warn("ToolPolicy \u8bfb\u53d6\u5931\u8d25: name={}, err={}", (Object)name, (Object)e.getMessage());
            return true;
        }
    }

    private boolean policyMatches(String name, String policy) {
        if (policy == null || policy.isBlank()) {
            return false;
        }
        String n = name.toLowerCase();
        for (String p : policy.split(",")) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            if (t.equals("*")) {
                return true;
            }
            if (t.toLowerCase().equals(n)) {
                return true;
            }
            if (!t.endsWith("*") || !n.startsWith(t.substring(0, t.length() - 1).toLowerCase())) continue;
            return true;
        }
        return false;
    }

    public ToolResult runAgentLoop(String userMessage) {
        ToolResult result = new ToolResult();
        result.invoked = false;
        result.log = "";
        result.loopRounds = 0;
        if (userMessage == null || userMessage.isBlank()) {
            return result;
        }
        if (!this.aiService.isConfigured()) {
            return result;
        }
        int maxRounds = 3;
        StringBuilder loopLog = new StringBuilder();
        StringBuilder observations = new StringBuilder();
        HashSet<String> usedTools = new HashSet<String>();
        String agentContext = "";
        for (int round = 1; round <= maxRounds; ++round) {
            ToolResult roundResult = this.schedule(userMessage, agentContext, usedTools);
            if (!roundResult.invoked) {
                if (round > 1) {
                    loopLog.append("第").append(round).append("轮判断无需继续调用工具；");
                }
                break;
            }
            result.loopRounds = round;
            if (roundResult.needsConfirm) {
                result.invoked = true;
                result.needsConfirm = true;
                result.pendingOp = roundResult.pendingOp;
                result.pendingPath = roundResult.pendingPath;
                result.pendingContent = roundResult.pendingContent;
                result.pendingReason = roundResult.pendingReason;
                result.pendingEndpoint = roundResult.pendingEndpoint;
                result.pendingArgs = roundResult.pendingArgs;
                result.log = "Agent Loop 第" + round + "轮触发高危操作确认";
                return result;
            }
            StringBuilder roundNames = new StringBuilder();
            boolean merged = false;
            if (roundResult.usedBuiltin && roundResult.builtinResult != null) {
                result.invoked = true;
                result.usedBuiltin = true;
                result.builtinName = this.joinText(result.builtinName, roundResult.builtinName);
                result.builtinResult = this.joinText(result.builtinResult, roundResult.builtinResult);
                observations.append("【第").append(round).append("轮】内置工具「").append(roundResult.builtinName).append("」返回：\n").append(this.truncate(roundResult.builtinResult, 4000)).append("\n");
                roundNames.append(roundResult.builtinName);
                merged = true;
            }
            if (roundResult.usedMcp && roundResult.mcpResult != null) {
                result.invoked = true;
                result.usedMcp = true;
                result.mcpName = this.joinText(result.mcpName, roundResult.mcpName);
                result.mcpResult = this.joinText(result.mcpResult, roundResult.mcpResult);
                observations.append("【第").append(round).append("轮】MCP「").append(roundResult.mcpName).append("」返回：\n").append(this.truncate(roundResult.mcpResult, 4000)).append("\n");
                if (roundNames.length() > 0) {
                    roundNames.append(", ");
                }
                roundNames.append(roundResult.mcpName);
                merged = true;
            }
            if (roundResult.usedSkill && roundResult.skillPrompt != null) {
                result.invoked = true;
                result.usedSkill = true;
                result.skillPrompt = this.joinText(result.skillPrompt, roundResult.skillPrompt);
                observations.append("【第").append(round).append("轮】技能「").append(roundResult.skillName).append("」已应用\n");
                if (roundNames.length() > 0) {
                    roundNames.append(", ");
                }
                roundNames.append(roundResult.skillName);
                merged = true;
            }
            usedTools.addAll(roundResult.usedCalls);
            if (!merged) {
                break;
            }
            loopLog.append("第").append(round).append("轮调用工具 ").append(roundNames).append("；");
            if (round >= maxRounds) {
                break;
            }
            if (!this.mayNeedMoreTools(userMessage, observations.toString())) {
                break;
            }
            agentContext = observations.toString();
        }
        result.log = loopLog.length() == 0 ? "Agent Loop 无工具调用" : "Agent Loop: " + loopLog.toString();
        return result;
    }

    private String joinText(String oldText, String addText) {
        if (addText == null || addText.isBlank()) {
            return oldText == null ? "" : oldText;
        }
        return oldText == null || oldText.isBlank() ? addText : oldText + "\n" + addText;
    }

    /**
     * 调用签名：工具名 + 关键参数，用于区分“同一工具的不同参数调用”。
     * 仅按工具名去重会把“读取 A 文件”与“读取 B 文件”误判为重复。
     */
    private String callSignature(String toolName, JsonNode node) {
        String base = toolName == null ? "" : toolName.trim().toLowerCase();
        if (node == null) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        String[] fields = new String[]{"path", "file", "command", "args2", "doc", "operation", "col1", "col2", "topN", "query", "url", "city", "name", "id"};
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v == null || v.isNull()) continue;
            String text = v.asText("");
            if (text.isBlank()) continue;
            sb.append("|").append(f).append("=").append(text.trim());
        }
        JsonNode args = node.get("args");
        if (args != null && !args.isNull()) {
            String text = args.toString().trim();
            if (!text.isBlank() && !"{}".equals(text)) {
                sb.append("|args=").append(text);
            }
        }
        return sb.toString();
    }

    private String usedDisplay(Set<String> usedTools) {
        ArrayList<String> names = new ArrayList<String>();
        for (String u : usedTools) {
            if (u == null || u.isBlank()) continue;
            int p = u.indexOf(0x7c);
            String d = p > 0 ? u.substring(0, p) : u;
            if (!names.contains(d)) names.add(d);
        }
        return String.join(", ", names);
    }

    /**
     * 只在存在明确的多步信号（顺序词/失败需要补救）时才进入下一轮，
     * 避免单步工具请求每轮都多付一次“是否继续”的模型调用。
     */
    private boolean mayNeedMoreTools(String userMessage, String observations) {
        if (userMessage == null) {
            return false;
        }
        String[] sequenceHints = new String[]{"然后", "接着", "之后", "最后", "并且", "同时", "以及", "顺便", "另外", "分别", "依次", "步骤"};
        for (String hint : sequenceHints) {
            if (userMessage.contains(hint)) {
                return true;
            }
        }
        if (userMessage.contains("先") && userMessage.contains("再")) {
            return true;
        }
        if (observations == null) {
            return false;
        }
        String[] failureHints = new String[]{"调用失败", "不可用", "HTTP 4", "HTTP 5", "超时", "未找到", "返回为空", "失败"};
        for (String hint : failureHints) {
            if (observations.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 供 AssistantService 优先调用的多 Agent 调度入口。
     * 若命中多 Agent 意图则返回调度 ToolResult，否则返回 null。
     */
    public ToolResult tryMultiAgentDispatch(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        // 数据分析类请求（含"统计/生成图表"等动作词）应走 analyze_data，避免被误判为多任务拆解
        if (this.hasDataAnalysisIntent(userMessage)) {
            return null;
        }
        if (!this.hasMultiAgentIntent(userMessage) || !this.isToolAllowed("dispatch_teammates")) {
            return null;
        }
        try {
            String out = this.dispatchTeammates(userMessage);
            if (out == null) {
                return null;
            }
            ToolResult result = new ToolResult();
            result.usedBuiltin = true;
            result.builtinName = "dispatch_teammates";
            result.builtinResult = out;
            result.invoked = true;
            result.log = "\u5df2\u8c03\u7528\u591a Agent \u8c03\u5ea6\u5de5\u5177\u300cdispatch_teammates\u300d";
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    public ToolResult schedule(String userMessage) {
        return this.schedule(userMessage, "", new HashSet<String>());
    }

    public ToolResult schedule(String userMessage, String agentContext, Set<String> usedTools) {
        ToolResult result = new ToolResult();
        result.invoked = false;
        result.log = "";
        if (userMessage == null || userMessage.isBlank()) {
            return result;
        }
        if (!this.aiService.isConfigured()) {
            return result;
        }
        Set<String> used = usedTools == null ? new HashSet<String>() : usedTools;
        List<McpServer> activeMcp = this.mcpServerService.findActive();
        List<Skill> enabledSkills = this.skillService.findEnabled();
        List<McpServer> callableMcp = activeMcp.stream().filter(m -> m.getServerType() != null && ("http".equalsIgnoreCase(m.getServerType()) || "sse".equalsIgnoreCase(m.getServerType()))).toList();
        if (!used.contains("get_current_time") && this.hasTimeIntent(userMessage) && !this.hasGeoIntent(userMessage) && this.isToolAllowed("get_current_time")) {
            try {
                String timeOut = this.invokeBuiltin("get_current_time");
                if (timeOut != null && !timeOut.isBlank()) {
                    result.usedBuiltin = true;
                    result.builtinName = "get_current_time";
                    result.builtinResult = timeOut;
                    result.invoked = true;
                    result.log = "已调用内置工具「get_current_time」（时间强意图直调）";
                    result.usedCalls.add("get_current_time");
                    return result;
                }
            }
            catch (Exception ignored) {
                // ignore
            }
        }
        if (!used.contains("local-geo") && this.hasGeoIntent(userMessage) && this.isToolAllowed("local-geo")) {
            McpServer geoMcp = callableMcp.stream().filter(m -> "local-geo".equalsIgnoreCase(m.getName())).findFirst().orElse(null);
            if (geoMcp != null) {
                try {
                    String geoOut = this.callMcp(geoMcp, "{}");
                    if (geoOut != null && !geoOut.isBlank()) {
                        result.usedMcp = true;
                        result.mcpName = geoMcp.getName();
                        result.mcpResult = geoOut;
                        result.invoked = true;
                        result.log = "已调用 MCP 工具「local-geo」（定位强意图直调，自动获取本机时间与地理位置）";
                        result.usedCalls.add("local-geo");
                        return result;
                    }
                }
                catch (Exception e) {
                    result.log = "local-geo 调用异常: " + e.getMessage();
                }
            }
        }
        if (!used.contains("analyze_data") && this.hasDataAnalysisIntent(userMessage) && this.isToolAllowed("analyze_data")) {
            try {
                String doc = this.extractAnalysisDoc(userMessage);
                String toolJson = "{\"tool\":\"analyze_data\",\"doc\":\"" + this.escapeJson(doc) + "\"}";
                String out = this.tryDataAnalysis(this.objectMapper.readTree(toolJson), "analyze_data");
                if (out != null) {
                    result.usedBuiltin = true;
                    result.builtinName = "analyze_data";
                    result.builtinResult = out;
                    result.invoked = true;
                    result.log = "已调用内置工具「analyze_data」（强意图直调）";
                    result.usedCalls.add("analyze_data");
                    return result;
                }
            }
            catch (Exception ignored) {
                // ignore
            }
        }
        if (!used.contains("dispatch_teammates") && this.hasMultiAgentIntent(userMessage) && this.isToolAllowed("dispatch_teammates")) {
            try {
                String out = this.dispatchTeammates(userMessage);
                if (out != null) {
                    result.usedBuiltin = true;
                    result.builtinName = "dispatch_teammates";
                    result.builtinResult = out;
                    result.invoked = true;
                    result.log = "已调用内置工具「dispatch_teammates」";
                    result.usedCalls.add("dispatch_teammates");
                    return result;
                }
            }
            catch (Exception ignored) {
                // 调度失败时回退到普通工具调度
            }
        }
        String decisionPrompt = this.buildDecisionPrompt(userMessage, callableMcp, enabledSkills, agentContext, used);
        String raw;
        try {
            raw = this.aiService.chat("你是 Agent 工具调度引擎，只输出 JSON。", decisionPrompt);
        }
        catch (Exception e) {
            return result;
        }
        JsonNode decision;
        try {
            decision = this.extractJson(raw);
        }
        catch (Exception e) {
            return result;
        }
        if (!decision.path("call").asBoolean(false)) {
            return result;
        }
        String toolType = decision.path("toolType").asText("");
        if ("builtin".equals(toolType)) {
            ArrayList<JsonNode> tools = new ArrayList<JsonNode>();
            if (decision.has("tools") && decision.path("tools").isArray()) {
                decision.path("tools").forEach(tools::add);
            }
            if (tools.isEmpty() && !decision.path("tool").asText("").isBlank()) {
                tools.add(decision);
            }
            StringBuilder results = new StringBuilder();
            ArrayList<String> names = new ArrayList<String>();
            for (JsonNode t : tools) {
                if (t == null || t.path("tool").asText("").isBlank()) {
                    continue;
                }
                String toolName = t.path("tool").asText("");
                String callSig = this.callSignature(toolName, t);
                if (used.contains(callSig)) {
                    results.append("【工具「").append(toolName).append("」本轮已执行过相同调用，跳过重复执行】\n");
                    continue;
                }
                if (!this.isToolAllowed(toolName)) {
                    results.append("【工具「").append(toolName).append("」已被系统策略禁用，无法执行。请如实告知用户该工具不可用，不要编造执行结果或替代方案】\n");
                    names.add(toolName);
                    result.usedCalls.add(callSig);
                    continue;
                }
                BuiltinOutcome fo = this.tryFileOperation(t, toolName);
                if (fo != null) {
                    if (fo.needConfirm) {
                        result.usedBuiltin = true;
                        result.needsConfirm = true;
                        result.pendingOp = fo.op;
                        result.pendingPath = fo.path;
                        result.pendingContent = fo.content;
                        result.pendingReason = fo.reason;
                        result.log = "内置工具「" + toolName + "」涉及" + fo.reason + "，等待用户确认";
                        return result;
                    }
                    results.append("【内置工具「").append(toolName).append("」返回】\n").append(fo.output).append("\n");
                    names.add(toolName);
                    result.usedCalls.add(callSig);
                    continue;
                }
                String analysis = this.tryDataAnalysis(t, toolName);
                if (analysis != null) {
                    results.append("【内置工具「").append(toolName).append("」返回】\n").append(analysis).append("\n");
                    names.add(toolName);
                    result.usedCalls.add(callSig);
                    continue;
                }
                String resolvedName = toolName;
                if ((toolName.contains("weather") || toolName.contains("天气")) && !toolName.contains(",")) {
                    String city = this.extractCityFromMessage(userMessage);
                    boolean hasInlineCity = toolName.trim().indexOf(' ') > 0;
                    if (!hasInlineCity && city != null && !toolName.contains(city)) {
                        resolvedName = toolName + " " + city;
                    }
                }
                String builtin = this.invokeBuiltin(resolvedName);
                if (builtin == null) {
                    results.append("【工具「").append(toolName).append("」调用失败或暂不可用】\n");
                    names.add(toolName);
                    result.usedCalls.add(callSig);
                    continue;
                }
                results.append("【内置工具「").append(toolName).append("」返回】\n").append(builtin).append("\n");
                names.add(toolName);
            }
            if (names.isEmpty()) {
                result.log = "本轮所有工具均已执行过，跳过重复调用";
                return result;
            }
            result.usedBuiltin = true;
            result.builtinName = String.join(", ", names);
            result.builtinResult = results.toString();
            result.invoked = true;
            result.log = "已调用内置工具「" + String.join(", ", names) + "」";
            return result;
        }
        if ("mcp".equals(toolType)) {
            String name = decision.path("tool").asText("");
            String args = decision.has("args") ? decision.path("args").toString() : "{}";
            if (name.isBlank()) {
                result.log = "MCP 决策缺少工具名";
                return result;
            }
            String callSig = this.callSignature(name, decision);
            if (used.contains(callSig)) {
                result.log = "MCP 工具「" + name + "」本轮已执行过相同调用，跳过重复调用";
                return result;
            }
            if (!this.isToolAllowed(name)) {
                result.usedMcp = true;
                result.mcpName = name;
                result.mcpResult = "【MCP 工具「" + name + "」已被系统策略禁用，无法执行。请如实告知用户该工具不可用，不要编造执行结果】";
                result.invoked = true;
                result.log = "MCP 工具「" + name + "」被工具策略禁用";
                result.usedCalls.add(callSig);
                return result;
            }
            McpServer matched = null;
            for (McpServer m : callableMcp) {
                if (!name.equalsIgnoreCase(m.getName())) continue;
                matched = m;
                break;
            }
            if (matched == null) {
                StringBuilder available = new StringBuilder();
                for (McpServer m : callableMcp) {
                    if (available.length() > 0) {
                        available.append(", ");
                    }
                    available.append(m.getName());
                }
                result.usedMcp = true;
                result.mcpName = name;
                result.mcpResult = "【未找到名为「" + name + "」的 MCP 工具。可用 MCP 工具：" + (available.length() == 0 ? "无" : available.toString()) + "。请改用正确的工具名，或直接回答用户】";
                result.invoked = true;
                result.log = "MCP 工具「" + name + "」不存在，未执行任何调用";
                result.usedCalls.add(callSig);
                return result;
            }
            String risk = this.detectHighRisk(matched.getEndpoint(), args);
            result.usedMcp = true;
            result.mcpName = matched.getName();
            if (risk != null) {
                result.needsConfirm = true;
                result.invoked = true;
                result.pendingEndpoint = matched.getEndpoint();
                result.pendingArgs = args;
                result.pendingReason = risk;
                result.log = "MCP 工具「" + matched.getName() + "」涉及高危操作，等待用户确认";
                result.usedCalls.add(callSig);
                return result;
            }
            String out = this.callMcp(matched, args);
            result.usedCalls.add(callSig);
            result.mcpResult = out;
            result.invoked = out != null && !out.isBlank();
            result.log = "已调用 MCP 工具「" + matched.getName() + "」";
            return result;
        }
        if ("skill".equals(toolType)) {
            String name = decision.path("tool").asText("");
            String skillSig = "skill|" + name.trim().toLowerCase();
            if (used.contains(skillSig)) {
                result.log = "Skill「" + name + "」本轮已应用过，跳过重复应用";
                return result;
            }
            for (Skill s : enabledSkills) {
                if (!name.equalsIgnoreCase(s.getName())) continue;
                result.usedSkill = true;
                result.skillName = s.getName();
                result.skillPrompt = "请使用技能「" + s.getName() + "」处理：\n" + s.getContent();
                result.invoked = true;
                result.usedCalls.add(skillSig);
                result.log = "已应用 Skill「" + s.getName() + "」";
                return result;
            }
            result.log = "未找到名为「" + name + "」的已启用 Skill，未应用任何技能";
        }
        return result;
    }

    private String invokeBuiltin(String name) {
        if (name == null) {
            return null;
        }
        String n = name.toLowerCase();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter fmtDate = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter fmtWeek = DateTimeFormatter.ofPattern("EEEE");
        if (n.contains("time") || n.contains("\u65f6\u95f4") || n.contains("\u51e0\u70b9") || n.contains("\u51e0\u70b9\u4e86")) {
            return "\u5f53\u524d\u672c\u5730\u65f6\u95f4\uff1a" + now.format(fmt) + "\uff08" + now.format(fmtDate) + " " + now.format(fmtWeek) + "\uff09";
        }
        if (n.contains("date") || n.contains("\u65e5\u671f")) {
            return "\u5f53\u524d\u65e5\u671f\uff1a" + now.format(fmtDate) + "\uff08" + now.format(fmtWeek) + "\uff09";
        }
        if (n.contains("utc") || n.contains("\u96f6\u65f6")) {
            return "\u5f53\u524d UTC \u65f6\u95f4\uff1a" + ZonedDateTime.now(ZoneId.of("UTC")).format(fmt);
        }
        if (n.contains("week") || n.contains("\u661f\u671f") || n.contains("\u5468\u51e0")) {
            return "\u4eca\u5929\u662f\uff1a" + now.format(fmtWeek);
        }
        if (n.contains("timestamp") || n.contains("\u65f6\u95f4\u6233")) {
            return "\u5f53\u524d\u65f6\u95f4\u6233\uff08\u6beb\u79d2\uff09\uff1a" + System.currentTimeMillis();
        }
        if (n.contains("system") || n.contains("\u7cfb\u7edf\u72b6\u6001")) {
            Runtime r = Runtime.getRuntime();
            return "\u7cfb\u7edf\u72b6\u6001\uff1aCPU\u6838\u6570=" + r.availableProcessors() + "\uff0c\u53ef\u7528\u5185\u5b58=" + r.freeMemory() / 1024L / 1024L + "MB\uff0c\u603b\u5185\u5b58=" + r.totalMemory() / 1024L / 1024L + "MB\uff0c\u6700\u5927\u5185\u5b58=" + r.maxMemory() / 1024L / 1024L + "MB";
        }
        if (n.contains("weather") || n.contains("\u5929\u6c14") || n.contains("\u6c14\u6e29") || n.contains("\u6e7f\u5ea6") || n.contains("\u9884\u62a5")) {
            return this.fetchWeather(n);
        }
        if (n.contains("hot") || n.contains("\u70ed\u70b9") || n.contains("\u70ed\u641c") || n.contains("\u4eca\u65e5\u65b0\u95fb") || n.contains("\u8d44\u8baf\u5934\u6761")) {
            return this.fetchHotTopics();
        }
        if (n.contains("import_library") || n.contains("\u5bfc\u5165\u5185\u7f6e\u5e93") || n.contains("\u5bfc\u5165\u5185\u7f6e\u6280\u80fd") || n.contains("\u5bfc\u5165\u5185\u7f6emcp")) {
            return this.importFromLibrary(n);
        }
        return null;
    }

    /** 从项目内置库导入 Skill / MCP，返回执行结果文本 */
    private String importFromLibrary(String hint) {
        boolean wantSkill = hint.contains("import_library_skill") || hint.contains("\u5bfc\u5165\u5185\u7f6e\u6280\u80fd") || (hint.contains("skill") && !hint.contains("mcp"));
        boolean wantMcp = hint.contains("import_library_mcp") || hint.contains("\u5bfc\u5165\u5185\u7f6emcp") || (hint.contains("mcp") && !hint.contains("skill"));
        boolean wantAll = hint.contains("import_library") || (!wantSkill && !wantMcp);
        StringBuilder out = new StringBuilder("\u3010\u5185\u7f6e\u5e93\u5bfc\u5165\u3011\n");
        try {
            if (wantAll || wantSkill) {
                Map<String, Object> r = this.openclawImportService.importSkills();
                out.append("\u2022 \u6280\u80fd\u5e93\uff1a\u5bfc\u5165 ").append(r.get("importedCount")).append(" \u4e2a\uff0c\u8df3\u8fc7 ").append(r.get("skippedCount")).append(" \u4e2a\n");
            }
            if (wantAll || wantMcp) {
                Map<String, Object> r = this.openclawImportService.importMcpServers();
                out.append("\u2022 MCP \u5e93\uff1a\u5bfc\u5165 ").append(r.get("importedCount")).append(" \u4e2a\uff0c\u8df3\u8fc7 ").append(r.get("skippedCount")).append(" \u4e2a\n");
            }
            out.append("\u53ef\u524d\u5f80\u300c\u53e3 \u6280\u80fd\u7ba1\u7406\u300d\u300c\ud83d\udd0c MCP \u670d\u52a1\u7ba1\u7406\u300d\u9875\u9762\u67e5\u770b\u5e76\u542f\u7528\u3002");
            return out.toString();
        } catch (Exception e) {
            return "\u3010\u5185\u7f6e\u5e93\u5bfc\u5165\u5931\u8d25\u3011" + e.getMessage();
        }
    }

    /**
     * 判断用户请求是否需要多 Agent 协同调度。
     * 命中多任务关键词，或消息中包含多个动作词时返回 true。
     */
    private boolean hasMultiAgentIntent(String msg) {
        if (msg == null || msg.isBlank()) {
            return false;
        }
        String[] hints = new String[]{
            "\u540c\u65f6", "\u4e00\u8d77", "\u5e76\u884c", "\u5206\u5de5", "\u534f\u540c", "\u534f\u4f5c",
            "\u591a\u4e2a\u4efb\u52a1", "\u591a\u9879\u4efb\u52a1", "\u5e2e\u6211\u505a\u51e0\u4ef6\u4e8b",
            "\u5206\u522b", "\u5404\u81ea", "\u5206\u5934", "\u914d\u5408", "\u4ea4\u7ed9\u4e0d\u540c",
            "\u8ba9\u2026\u2026\u8ba9\u2026\u2026", "\u5148\u2026\u2026\u7136\u540e\u2026\u2026",
            "\u90e8\u7f72\u7ed9", "\u6d3e\u53d1\u7ed9", "\u5206\u6d3e", "\u4ea4\u4ed8\u7ed9",
        };
        for (String h : hints) {
            if (msg.contains(h)) {
                return true;
            }
        }
        // 含两个及以上动作词 → 视为多任务
        String[] actions = new String[]{"\u67e5", "\u5199", "\u5206\u6790", "\u6574\u7406", "\u641c\u96c6", "\u751f\u6210", "\u7edf\u8ba1", "\u6c47\u603b", "\u8ba1\u7b97", "\u89c4\u5212", "\u9884\u7b97", "\u5236\u5b9a"};
        int actionCount = 0;
        for (String a : actions) {
            if (msg.contains(a)) {
                actionCount++;
            }
        }
        return actionCount >= 2;
    }

    /**
     * 多 Agent 协同调度：
     * 1. 获取当前可用 Teammate 列表
     * 2. 让 LLM 将用户请求拆解为多个子任务
     * 3. 创建 HarnessTask，后台 Teammate 守护线程自动认领执行
     */
    private String dispatchTeammates(String userMessage) {
        try {
            List<Teammate> mates = this.teammateMapper.findAll();
            StringBuilder mateInfo = new StringBuilder();
            if (mates == null || mates.isEmpty()) {
                mateInfo.append("- \u5f53\u524d\u65e0\u6ce8\u518c Teammate\uff0c\u5c06\u4e0d\u6307\u5b9a owner\uff0c\u7531\u4efb\u610f\u7a7a\u95f2\u5de5\u4f5c\u4ee3\u7406\u81ea\u52a8\u8ba4\u9886\u3002");
            } else {
                for (Teammate t : mates) {
                    mateInfo.append("- ").append(t.getName())
                            .append(" (").append(t.getRole() == null ? "" : t.getRole()).append(")")
                            .append(" \u72b6\u6001:").append(t.getStatus())
                            .append(" \u8fdb\u5ea6:").append(t.getProgress() == null ? 0 : t.getProgress()).append("%\n");
                }
            }

            String decomposePrompt =
                "\u4f60\u662f\u591a Agent \u4efb\u52a1\u8c03\u5ea6\u5668\u3002\u6839\u636e\u7528\u6237\u8bf7\u6c42\u548c\u5f53\u524d\u53ef\u7528 Teammate \u5217\u8868\uff0c\u5c06\u4efb\u52a1\u62c6\u89e3\u4e3a\u5b50\u4efb\u52a1\u3002\n\n"
                + "\u3010\u7528\u6237\u8bf7\u6c42\u3011\n" + userMessage + "\n\n"
                + "\u3010\u53ef\u7528 Teammate\u3011\n" + mateInfo.toString() + "\n"
                + "\u3010\u62c6\u89e3\u89c4\u5219\u3011\n"
                + "1. \u6bcf\u4e2a\u5b50\u4efb\u52a1\u5fc5\u987b\u6307\u5b9a title\uff08\u77ed\u6807\u9898\uff09\u548c description\uff08\u8be6\u7ec6\u63cf\u8ff0\uff0c\u7528\u5206\u53f7\u5206\u9694\u6b65\u9aa4\uff09\u3002\n"
                + "2. owner \u4e3a\u6700\u9002\u5408\u7684 Teammate \u540d\u79f0\uff08\u4ece\u4e0a\u8ff0\u5217\u8868\u4e2d\u9009\uff09\uff1b\u82e5\u5217\u8868\u4e3a\u7a7a\u5219\u586b\u7a7a\u5b57\u7b26\u4e32\u3002\n"
                + "3. \u82e5\u6709\u4f9d\u8d56\u5173\u7cfb\uff0c\u5728 blockedBy \u4e2d\u586b\u5199\u524d\u5e8f\u4efb\u52a1\u7684 title\uff08\u4ee5\u52a0\u6570\u53f7\u5206\u9694\uff09\uff0c\u65e0\u5219\u586b\u7a7a\u5b57\u7b26\u4e32\u3002\n"
                + "4. \u82e5\u7528\u6237\u8bf7\u6c42\u7b80\u5355\uff08\u5982\u4ec5\u67e5\u8be2\uff09\uff0c\u8fd4\u56de\u7a7a\u6570\u7ec4\u3002\n"
                + "5. \u5206\u5de5\u5408\u7406\uff1a\u4e0d\u540c\u7684\u5b50\u4efb\u52a1\u5206\u914d\u7ed9\u4e0d\u540c\u5339\u914d\u89d2\u8272\u7684 Teammate\uff0c\u4fdd\u8bc1\u5e76\u884c\u6267\u884c\u3002\n"
                + "\u4e25\u683c\u53ea\u8f93\u51fa JSON \u6570\u7ec4\uff1a\n"
                + "[{\"title\":\"...\",\"description\":\"...\",\"owner\":\"...\",\"blockedBy\":\"\"}]";

            String raw = this.aiService.chat("\u4f60\u662f\u591a Agent \u4efb\u52a1\u8c03\u5ea6\u5668\uff0c\u53ea\u8f93\u51fa JSON \u6570\u7ec4\u3002", decomposePrompt);

            JsonNode tasks = this.extractJson(raw);
            if (tasks == null || !tasks.isArray() || tasks.size() == 0) {
                // LLM 判断无需拆解时，若用户请求含多个动作词，回退为规则拆解，保证产生子任务
                List<Map<String, String>> fallback = this.decomposeByRules(userMessage);
                if (fallback.isEmpty()) {
                    return "\u3010\u591a Agent \u8c03\u5ea6\u3011\u5f53\u524d\u8bf7\u6c42\u65e0\u9700\u62c6\u89e3\u4e3a\u591a\u4e2a\u5b50\u4efb\u52a1\uff0c\u53ef\u76f4\u63a5\u56de\u590d\u7528\u6237\u3002";
                }
                // 用回退结果构建临时 JSON 数组
                StringBuilder fb = new StringBuilder("[");
                for (int i = 0; i < fallback.size(); i++) {
                    Map<String, String> m = fallback.get(i);
                    if (i > 0) fb.append(",");
                    fb.append("{\"title\":\"").append(this.escapeJson(m.get("title")))
                      .append("\",\"description\":\"").append(this.escapeJson(m.get("desc")))
                      .append("\",\"owner\":\"").append(this.escapeJson(m.get("owner")))
                      .append("\",\"blockedBy\":\"\"}");
                }
                fb.append("]");
                tasks = this.objectMapper.readTree(fb.toString());
            }

            StringBuilder result = new StringBuilder();
            result.append("\u3010\u591a Agent \u8c03\u5ea6\u3011\u5df2\u5c06\u60a8\u7684\u8bf7\u6c42\u62c6\u89e3\u4e3a ").append(tasks.size()).append(" \u4e2a\u5b50\u4efb\u52a1\uff0c\u5df2\u8fdb\u5165\u4efb\u52a1\u770b\u677f\uff1a\n\n");

            for (int i = 0; i < tasks.size(); i++) {
                JsonNode t = tasks.get(i);
                String title = t.path("title").asText("\u5b50\u4efb\u52a1" + (i + 1));
                String desc = t.path("description").asText("");
                String owner = t.path("owner").asText("");
                String blockedBy = t.path("blockedBy").asText("");

                HarnessTask task = new HarnessTask();
                task.setTaskKey("mate-" + System.currentTimeMillis() % 1000000L + "-" + i);
                task.setTitle(title);
                task.setDescription(desc);
                task.setStatus("pending");
                task.setOwner(owner.isBlank() ? null : owner);
                task.setBlockedBy(blockedBy.isBlank() ? null : blockedBy);
                task.setProgress(0);
                this.taskService.create(task);

                result.append(i + 1).append(". **").append(title).append("**");
                result.append(" \u2192 \u5206\u914d\u7ed9 ").append(owner.isBlank() ? "\u81ea\u52a8\u5206\u914d" : owner);
                if (!blockedBy.isBlank()) {
                    result.append(" \uff08\u4f9d\u8d56: ").append(blockedBy).append("\uff09");
                }
                if (!desc.isBlank()) {
                    result.append("\n   ").append(desc);
                }
                result.append("\n\n");
            }

            result.append("---\n");
            result.append("\u2705 \u5171\u521b\u5efa ").append(tasks.size()).append(" \u4e2a\u5b50\u4efb\u52a1\uff0c\u5df2\u8fdb\u5165\u4efb\u52a1\u770b\u677f\u3002\n");
            result.append("\u540e\u53f0 Teammate \u5b88\u62a4\u8fdb\u7a0b\u5c06\u81ea\u52a8\u8ba4\u9886\u5e76\u6267\u884c\u3002\n");
            result.append("\u53ef\u5728\u300c\ud83d\udcca \u8fdb\u7a0b\u76d1\u63a7\u300d\u548c\u300c\ud83d\udccb \u4efb\u52a1\u770b\u677f\u300d\u9875\u9762\u5b9e\u65f6\u67e5\u770b\u8fdb\u5ea6\u3002");

            return result.toString();
        } catch (Exception e) {
            return "\u3010\u591a Agent \u8c03\u5ea6\u5931\u8d25\u3011" + e.getMessage() + "\u3002\u5c06\u4f7f\u7528\u666e\u901a\u6a21\u5f0f\u56de\u590d\u3002";
        }
    }

    /**
     * 基于动作词的规则拆解：当 LLM 未给出拆解结果时，按请求中的动作词拆分出子任务。
     * 返回 [{title, desc, owner}]，owner 为空串表示自动分配。
     */
    private List<Map<String, String>> decomposeByRules(String msg) {
        List<Map<String, String>> out = new ArrayList<>();
        if (msg == null || msg.isBlank()) {
            return out;
        }
        // 拆分用户请求中的多个子句（按逗号、分号、"和"、"与"、"、"等分隔）
        String[] parts = msg.split("[,\uff0c;\uff1b\u3001\u548c\u4e0e]");
        for (String part : parts) {
            String p = part.trim();
            if (p.length() < 2) {
                continue;
            }
            // 去除常见引导词
            p = p.replaceAll("^(\u8bf7|\u5e2e\u6211|\u9700\u8981|\u8981|\u540c\u65f6|\u5206\u5934|\u5206\u522b|\u8ba9|\u5148|\u518d|\u7136\u540e|\u7ed9\u6211|\u505a|\u7684)", "");
            p = p.trim();
            if (p.length() < 2) {
                continue;
            }
            String title = p.length() > 14 ? p.substring(0, 14) : p;
            Map<String, String> m = new HashMap<>();
            m.put("title", title);
            m.put("desc", p + "\uff1b\u6b65\u9aa4\uff1a\u5206\u6790\u9700\u6c42\u2192\u6267\u884c\u2192\u6c47\u62a5\u7ed3\u679c");
            m.put("owner", "");
            out.add(m);
        }
        // 动作词至少 2 个才认定为多任务
        return out.size() >= 2 ? out : new ArrayList<>();
    }

    /** 时间强意图：询问当前时间/日期/星期（触发 get_current_time 直调） */
    private boolean hasTimeIntent(String msg) {
        if (msg == null) {
            return false;
        }
        String[] words = new String[]{"\u73b0\u5728\u51e0\u70b9", "\u5f53\u524d\u65f6\u95f4", "\u73b0\u5728\u65f6\u95f4", "\u51e0\u70b9\u4e86",
                "\u73b0\u5728\u65e5\u671f", "\u4eca\u5929\u51e0\u53f7", "\u4eca\u5929\u65e5\u671f", "\u4eca\u5929\u661f\u671f\u51e0", "\u661f\u671f\u51e0",
                "\u4eca\u5929\u662f\u51e0", "\u73b0\u5728\u662f\u4ec0\u4e48\u65f6\u95f4", "\u51c6\u786e\u65f6\u95f4", "\u7cbe\u786e\u65f6\u95f4", "\u51e0\u70b9\u949f"};
        for (String w : words) {
            if (msg.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** 定位强意图：询问本机位置/当前城市/获取定位等（触发 local-geo MCP 直调） */
    private boolean hasGeoIntent(String msg) {
        if (msg == null) {
            return false;
        }
        String[] words = new String[]{"\u6211\u5728\u54ea\u91cc", "\u6211\u5728\u54ea", "\u6211\u7684\u4f4d\u7f6e", "\u6211\u7684\u5b9a\u4f4d",
                "\u6211\u5f53\u524d\u4f4d\u7f6e", "\u6211\u73b0\u5728\u7684\u4f4d\u7f6e", "\u6211\u5728\u4ec0\u4e48\u4f4d\u7f6e",
                "\u5f53\u524d\u4f4d\u7f6e", "\u672c\u673a\u4f4d\u7f6e", "\u5730\u7406\u4f4d\u7f6e", "\u83b7\u53d6\u5b9a\u4f4d",
                "\u54ea\u4e2a\u57ce\u5e02", "\u4ec0\u4e48\u57ce\u5e02", "\u5f53\u524d\u57ce\u5e02", "\u7ecf\u7eac\u5ea6",
                "\u672c\u5730\u4f4d\u7f6e", "\u67e5\u4e00\u4e0b\u6211\u5728\u54ea"};
        for (String w : words) {
            if (msg.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDataAnalysisIntent(String msg) {
        String[] strong;
        if (msg == null) {
            return false;
        }
        for (String w : strong = new String[]{"\u6570\u636e\u5206\u6790", "\u6570\u636e\u7edf\u8ba1", "\u7edf\u8ba1\u5206\u6790", "\u505a\u6570\u636e", "\u56fe\u8868", "\u6392\u884c", "\u5360\u6bd4", "\u751f\u6210\u56fe", "\u505a\u4e2a\u56fe", "\u753b\u4e2a\u56fe", "\u67f1\u72b6\u56fe", "\u997c\u56fe", "\u6298\u7ebf\u56fe", "\u8d8b\u52bf\u56fe", "\u6570\u636e\u53ef\u89c6\u5316", "\u7edf\u8ba1\u4e00\u4e0b", "\u505a\u4e2a\u5206\u6790"}) {
            if (!msg.contains(w)) continue;
            return true;
        }
        return false;
    }

    private String extractAnalysisDoc(String msg) {
        if (msg == null) {
            return "";
        }
        String clean = msg.replaceAll("^(\u8bf7|\u5e2e\u6211|\u7528|\u505a|\u5bf9|\u7ed9|\u628a|\u5e2e|\u5206\u6790|\u7edf\u8ba1|\u67e5\u770b|\u770b\u770b|\u5173\u4e8e|\u9488\u5bf9|\u5c31|\u5c06|\u628a|\u56f4\u7ed5|\u57fa\u4e8e)", "");
        Matcher m = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]{2,12}?(?:\u62a5\u544a|\u6587\u6863|\u6570\u636e|\u8868))").matcher(clean);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private List<KnowledgeDoc> searchDocLoose(String doc) {
        if (doc == null || doc.isBlank()) {
            return this.knowledgeService.findAll();
        }
        String keyword = doc.trim();
        for (int i = 0; i <= keyword.length() - 2; i++) {
            List<KnowledgeDoc> r = this.knowledgeService.search(keyword.substring(i));
            if (r != null && !r.isEmpty()) {
                return r;
            }
        }
        return this.knowledgeService.search(keyword);
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extractCityFromMessage(String msg) {
        if (msg == null) {
            return null;
        }
        // 用户明确说出的城市优先于前端自动附加的定位标签（例如「北京天气」+【用户当前所在城市：东莞】应以北京为准）
        String explicit = this.extractExplicitCity(msg);
        if (explicit != null) {
            return explicit;
        }
        String tag = "【用户当前所在城市：";
        int s = msg.indexOf(tag);
        if (s >= 0) {
            int e = msg.indexOf("】", s);
            if (e > s) {
                String city = msg.substring(s + tag.length(), e).trim();
                if (!city.isEmpty()) {
                    return city;
                }
            }
        }
        return null;
    }

    private String extractExplicitCity(String msg) {
        String cleaned = msg.replaceAll("(帮我|请|查一下|查询|查查|看看|告诉我|我想知道|请问|今天|明天|后天|现在|当前|今日|最近|这里|那里|当地|本地)", "");
        cleaned = cleaned.replaceAll("^[\\s，,。:：]*(?:先|再|然后|接着|之后|最后|顺便|另外|同时|并且|以及|分别|依次|步骤)+", "");
        Matcher m = Pattern.compile("([一-龥]{2,6}?)(?:市)?(?:的)?(?:天气|气温|温度)").matcher(cleaned);
        String[] stop = new String[]{"今天", "明天", "后天", "现在", "当前", "今日", "最近", "这里", "那里", "当地", "本地", "查询", "查看", "查一下", "帮我", "请问", "看看", "一下", "天气", "气温", "温度", "适合", "穿"};
        while (m.find()) {
            String city = m.group(1);
            boolean bad = false;
            for (String s : stop) {
                if (!city.contains(s)) continue;
                bad = true;
                break;
            }
            if (!bad) {
                return city;
            }
        }
        return null;
    }

    private String fetchWeather(String hint) {
        String body;
        String cacheKey;
        block8: try {
            String result;
            String city = hint.replace("get_", "").replace("fetch_", "").replace("weather", "").replace("\u5929\u6c14", "").replace("\u3010\u7528\u6237\u5f53\u524d\u6240\u5728\u57ce\u5e02\uff1a", "").replace("\u3011", "").trim();
            cacheKey = city.isEmpty() ? "__default__" : city;
            CachedWeather cached = this.weatherCache.get(cacheKey);
            if (cached != null && System.currentTimeMillis() - cached.ts < 600000L) {
                return cached.value;
            }
            Object url = city.isEmpty() ? "https://wttr.in/?format=3&lang=zh" : (city.contains(",") ? "https://wttr.in/" + city.replace(" ", "") + "?format=j1" : "https://wttr.in/" + URLEncoder.encode(city, "UTF-8") + "?format=j1");
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create((String)url)).timeout(Duration.ofSeconds(15L)).header("Accept", "application/json").header("User-Agent", "curl/7.0").GET().build();
            HttpResponse<String> resp = this.httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return "\u26a0\ufe0f \u5929\u6c14\u670d\u52a1\u6682\u4e0d\u53ef\u7528\uff08HTTP " + resp.statusCode() + "\uff09\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5";
            }
            body = resp.body();
            if (!((String)url).contains("format=3")) break block8;
            String text = body == null ? "" : body.trim();
            result = text.isEmpty() ? "\u26a0\ufe0f \u5929\u6c14\u670d\u52a1\u8fd4\u56de\u4e3a\u7a7a\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5" : "\u5f53\u524d\u5929\u6c14\uff1a" + text;
            if (!result.startsWith("\u26a0\ufe0f")) {
                this.weatherCache.put(cacheKey, new CachedWeather(result, System.currentTimeMillis()));
            }
            return result;
        } catch (Exception e) {
            String reason = e.getClass().getSimpleName();
            return "\u26a0\ufe0f \u5929\u6c14\u67e5\u8be2\u6682\u4e0d\u53ef\u7528\uff08" + reason + "\uff09\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5";
        }
        try {
            JsonNode root = this.objectMapper.readTree(body);
            JsonNode current = root.path("current_condition").path(0);
            JsonNode area = root.path("nearest_area").path(0);
            String areaName = area.path("areaName").path(0).path("value").asText("\u5f53\u524d\u4f4d\u7f6e");
            String tempC = current.path("temp_C").asText("?");
            String desc = current.path("lang_zh").path(0).path("value").asText(current.path("weatherDesc").path(0).path("value").asText(""));
            String humidity = current.path("humidity").asText("?");
            String feelsLike = current.path("FeelsLikeC").asText("?");
            String result = "\ud83c\udf24\ufe0f " + areaName + " \u5f53\u524d\u5929\u6c14\uff1a\n\u6e29\u5ea6\uff1a" + tempC + "\u00b0C\uff08\u4f53\u611f " + feelsLike + "\u00b0C\uff09\n\u5929\u6c14\uff1a" + desc + "\n\u6e7f\u5ea6\uff1a" + humidity + "%";
            this.weatherCache.put(cacheKey, new CachedWeather(result, System.currentTimeMillis()));
            return result;
        }
        catch (Exception ex) {
            return "\u26a0\ufe0f \u5929\u6c14\u6570\u636e\u89e3\u6790\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5";
        }
    }

    private String fetchHotTopics() {
        try {
            if (!this.searchService.isEnabled()) {
                return "\u26a0\ufe0f \u8054\u7f51\u641c\u7d22\u672a\u542f\u7528\uff0c\u65e0\u6cd5\u83b7\u53d6\u4eca\u65e5\u70ed\u70b9\u3002\u53ef\u524d\u5f80\u300c\u8bbe\u7f6e\u300d\u5f00\u542f\u8054\u7f51\u641c\u7d22\u3002";
            }
            List<Map<String, String>> results = this.searchService.search("\u4eca\u65e5\u70ed\u70b9\u65b0\u95fb \u70ed\u641c\u8bdd\u9898");
            if (results == null || results.isEmpty()) {
                return "\u26a0\ufe0f \u4eca\u65e5\u70ed\u70b9\u68c0\u7d22\u65e0\u7ed3\u679c\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5";
            }
            return this.searchService.formatResults(results);
        }
        catch (Exception e) {
            return "\u26a0\ufe0f \u4eca\u65e5\u70ed\u70b9\u83b7\u53d6\u5931\u8d25\uff08" + e.getClass().getSimpleName() + "\uff09\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5";
        }
    }

    private BuiltinOutcome tryFileOperation(JsonNode toolNode, String name) {
        String t = name.toLowerCase();
        String path = toolNode.has("path") ? toolNode.path("path").asText("") : toolNode.path("args").path("path").asText("");
        String content = toolNode.has("content") ? toolNode.path("content").asText("") : toolNode.path("args").path("content").asText("");
        try {
            if (t.contains("list_dir") || t.contains("list_files") || t.contains("\u6d4f\u89c8\u76ee\u5f55") || t.contains("ls_dir")) {
                return new BuiltinOutcome("list", path, null, this.fileOpsService.list(path).toString(), null, false);
            }
            if (t.contains("read_file") || t.contains("cat_file") || t.contains("\u67e5\u770b\u6587\u4ef6")) {
                String rel = path;
                if (rel.isBlank()) {
                    rel = toolNode.path("args").path("file").asText("");
                }
                return new BuiltinOutcome("read", rel, null, this.fileOpsService.read(rel), null, false);
            }
            if (t.contains("describe_workspace") || t.contains("\u9879\u76ee\u6982\u89c8") || t.contains("workspace")) {
                return new BuiltinOutcome("read", null, null, this.fileOpsService.describeWorkspace(), null, false);
            }
            if (t.contains("write_file") || t.contains("save_file") || t.contains("\u5199\u5165\u6587\u4ef6") || t.contains("\u65b0\u589e\u811a\u672c") || t.contains("\u521b\u5efa\u6587\u4ef6") || t.contains("\u5199\u6587\u4ef6")) {
                if (path.isBlank()) {
                    return new BuiltinOutcome("write_file", path, content, null, "\u5199\u5165/\u8986\u76d6\u6587\u4ef6\u4f46\u7f3a\u5c11\u76ee\u6807\u8def\u5f84", true);
                }
                return new BuiltinOutcome("write_file", path, content, null, "\u5199\u5165/\u8986\u76d6\u6587\u4ef6\uff08\u4fee\u6539\u9879\u76ee\u76ee\u5f55\uff09", true);
            }
            if (t.contains("delete_file") || t.contains("remove_file") || t.contains("\u5220\u9664\u6587\u4ef6") || t.contains("\u5220\u9664\u76ee\u5f55") || t.contains("\u5220\u9664\u811a\u672c")) {
                if (path.isBlank()) {
                    return new BuiltinOutcome("delete_file", path, null, null, "\u5220\u9664\u6587\u4ef6/\u76ee\u5f55\u4f46\u7f3a\u5c11\u76ee\u6807\u8def\u5f84", true);
                }
                return new BuiltinOutcome("delete_file", path, null, null, "\u5220\u9664\u6587\u4ef6/\u76ee\u5f55\uff08\u4e0d\u53ef\u6062\u590d\uff09", true);
            }
            if (t.contains("run_script") || t.contains("\u6267\u884c\u811a\u672c") || t.contains("\u8fd0\u884c\u811a\u672c")) {
                if (path.isBlank()) {
                    return new BuiltinOutcome("run_script", path, content, null, "\u6267\u884c\u9879\u76ee\u5185\u811a\u672c\u4f46\u7f3a\u5c11\u811a\u672c\u8def\u5f84", true);
                }
                return new BuiltinOutcome("run_script", path, content, null, "\u6267\u884c\u9879\u76ee\u5185\u811a\u672c\uff08\u53ef\u80fd\u4ea7\u751f\u526f\u4f5c\u7528\uff09", true);
            }
            if (t.contains("mkdir") || t.contains("\u521b\u5efa\u76ee\u5f55") || t.contains("\u65b0\u5efa\u6587\u4ef6\u5939")) {
                if (path.isBlank()) {
                    return new BuiltinOutcome("mkdir", path, null, null, "\u521b\u5efa\u76ee\u5f55\u4f46\u7f3a\u5c11\u76ee\u6807\u8def\u5f84", true);
                }
                return new BuiltinOutcome("mkdir", path, null, null, "\u521b\u5efa\u76ee\u5f55\uff08\u4fee\u6539\u9879\u76ee\u76ee\u5f55\u7ed3\u6784\uff09", true);
            }
            if (t.contains("run_cli") || t.contains("\u547d\u4ee4\u884c") || t.contains("\u4f4e\u98ce\u9669\u547d\u4ee4")) {
                String cmd = toolNode.has("command") ? toolNode.path("command").asText("")
                        : toolNode.path("args").path("command").asText("");
                String cliArgs = toolNode.has("args2") ? toolNode.path("args2").asText("")
                        : toolNode.path("args").path("args").asText("");
                if (cmd.isBlank()) {
                    return new BuiltinOutcome("run_cli", null, null, null, "\u6267\u884c\u547d\u4ee4\u4f46\u7f3a\u5c11 command \u53c2\u6570", true);
                }
                return new BuiltinOutcome("run_cli", cmd, cliArgs, this.fileOpsService.runCli(cmd, cliArgs), null, false);
            }
        }
        catch (Exception e) {
            return new BuiltinOutcome("read", path, null, "\u26a0\ufe0f \u6587\u4ef6\u64cd\u4f5c\u5931\u8d25\uff1a" + e.getMessage(), null, false);
        }
        return null;
    }

    private String tryDataAnalysis(JsonNode toolNode, String name) {
        boolean isAnalyze;
        String t = name.toLowerCase();
        boolean bl = isAnalyze = t.contains("analyze_data") || t.contains("data_analysis") || t.contains("\u6570\u636e\u5206\u6790") || t.contains("\u7edf\u8ba1\u5206\u6790") || t.contains("\u5206\u6790\u6570\u636e");
        if (!isAnalyze) {
            return null;
        }
        try {
            Object chart;
            List<KnowledgeDoc> docs;
            int topN;
            String doc = toolNode.has("doc") ? toolNode.path("doc").asText("") : toolNode.path("args").path("doc").asText("");
            String operation = toolNode.has("operation") ? toolNode.path("operation").asText("") : toolNode.path("args").path("operation").asText("");
            String col1 = toolNode.has("col1") ? toolNode.path("col1").asText("") : toolNode.path("args").path("col1").asText("");
            String col2 = toolNode.has("col2") ? toolNode.path("col2").asText("") : toolNode.path("args").path("col2").asText("");
            int n = topN = toolNode.has("topN") ? toolNode.path("topN").asInt(5) : toolNode.path("args").path("topN").asInt(5);
            if (doc == null || doc.isBlank()) {
                docs = this.knowledgeService.findAll();
                if (docs == null || docs.isEmpty()) {
                    return "\u26a0\ufe0f \u77e5\u8bc6\u5e93\u4e3a\u7a7a\uff0c\u8bf7\u5148\u4e0a\u4f20\u6570\u636e\u6587\u4ef6\u540e\u518d\u8fdb\u884c\u6570\u636e\u5206\u6790\u3002";
                }
            } else {
                docs = this.searchDocLoose(doc);
                if (docs == null || docs.isEmpty()) {
                    return "\u26a0\ufe0f \u77e5\u8bc6\u5e93\u4e2d\u672a\u627e\u5230\u4e0e\u300c" + doc + "\u300d\u76f8\u5173\u7684\u6587\u6863\uff0c\u53ef\u5148\u4e0a\u4f20\u6570\u636e\u6587\u4ef6\u5230\u77e5\u8bc6\u5e93\u3002";
                }
            }
            if (docs == null || docs.isEmpty()) {
                return "\u26a0\ufe0f \u77e5\u8bc6\u5e93\u4e2d\u672a\u627e\u5230\u4e0e\u300c" + doc + "\u300d\u76f8\u5173\u7684\u6587\u6863\uff0c\u53ef\u5148\u4e0a\u4f20\u6570\u636e\u6587\u4ef6\u5230\u77e5\u8bc6\u5e93\u3002";
            }
            KnowledgeDoc target = null;
            String lower = doc == null ? "" : doc.toLowerCase();
            KnowledgeDoc bestNameMatch = null;
            int bestScore = -1;
            for (KnowledgeDoc d : docs) {
                int len;
                boolean nameMatch = d.getFileName() != null && d.getFileName().toLowerCase().contains(lower);
                boolean analyzable = this.dataAnalysisService.hasAnalyzableTable(d.getContent());
                if (nameMatch && bestNameMatch == null) {
                    bestNameMatch = d;
                }
                if (!nameMatch || !analyzable || (len = d.getContent() == null ? 0 : d.getContent().length()) <= bestScore) continue;
                bestScore = len;
                target = d;
            }
            if (target == null && bestNameMatch != null) {
                target = bestNameMatch;
            }
            if (target == null && !docs.isEmpty()) {
                for (KnowledgeDoc d : docs) {
                    if (!this.dataAnalysisService.hasAnalyzableTable(d.getContent())) continue;
                    target = d;
                    break;
                }
                if (target == null) {
                    target = docs.get(0);
                }
            }
            Map<String, Object> r = this.dataAnalysisService.analyze(target, operation, col1, col2, topN);
            StringBuilder out = new StringBuilder();
            out.append("\u5df2\u5bf9\u77e5\u8bc6\u5e93\u6587\u6863\u300c").append(target.getFileName()).append("\u300d\u5b8c\u6210\u6570\u636e\u5206\u6790\u3002\n");
            if (r.get("summary") != null) {
                out.append(r.get("summary"));
            }
            if ((chart = r.get("chart")) != null) {
                out.append("\n[CHART_JSON]").append(this.objectMapper.writeValueAsString(chart)).append("[/CHART_JSON]");
            }
            return out.toString();
        }
        catch (Exception e) {
            return "\u26a0\ufe0f \u6570\u636e\u5206\u6790\u6267\u884c\u5931\u8d25\uff1a" + e.getMessage();
        }
    }

    public String executeFileOp(String op, String path, String content) {
        try {
            switch (op == null ? "" : op) {
                case "write_file": {
                    this.fileOpsService.write(path, content);
                    return "\u2705 \u5df2\u5199\u5165/\u8986\u76d6\u6587\u4ef6\uff1a" + path;
                }
                case "delete_file": {
                    this.fileOpsService.delete(path);
                    return "\u2705 \u5df2\u5220\u9664\uff1a" + path;
                }
                case "run_script": {
                    return "\u2705 \u811a\u672c\u6267\u884c\u7ed3\u679c\uff1a\n" + this.fileOpsService.runScript(path, content);
                }
                case "mkdir": {
                    this.fileOpsService.mkdir(path);
                    return "\u2705 \u5df2\u521b\u5efa\u76ee\u5f55\uff1a" + path;
                }
            }
            return "(\u672a\u77e5\u6587\u4ef6\u64cd\u4f5c: " + op + ")";
        }
        catch (Exception e) {
            return "\u274c \u6587\u4ef6\u64cd\u4f5c\u5931\u8d25\uff1a" + e.getMessage();
        }
    }

    private String detectHighRisk(String endpoint, String args) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        String combined = endpoint.toLowerCase() + " " + (args == null ? "" : args.toLowerCase());
        for (String hint : HIGH_RISK_HINTS) {
            if (!combined.contains(hint)) continue;
            return "\u68c0\u6d4b\u5230\u654f\u611f\u64cd\u4f5c\u7279\u5f81\u300c" + hint + "\u300d";
        }
        if (combined.matches(".*(delete|remove|drop|truncate|clear|purge|reset).*")) {
            return "\u68c0\u6d4b\u5230\u6570\u636e\u5220\u9664/\u6e05\u7a7a\u7c7b\u64cd\u4f5c";
        }
        return null;
    }

    public String executeConfirmed(String endpoint, String args) {
        McpServer server = new McpServer();
        server.setEndpoint(endpoint);
        return this.callMcp(server, args);
    }

    private String callMcp(McpServer server, String args) {
        try {
            String endpoint = server.getEndpoint();
            if (endpoint == null || endpoint.isBlank()) {
                return "(\u5de5\u5177\u5730\u5740\u4e3a\u7a7a)";
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(15L)).header("Content-Type", "application/json");
            if (args != null && !"{}".equals(args) && args.length() > 4) {
                builder.uri(URI.create(endpoint)).header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(args));
            } else {
                builder.uri(URI.create(endpoint)).GET();
            }
            HttpResponse<String> response = this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return "(HTTP " + response.statusCode() + ")";
            }
            String body = response.body();
            return body == null || body.isBlank() ? "(\u65e0\u8fd4\u56de\u5185\u5bb9)" : this.truncate(body, 2000);
        }
        catch (Exception e) {
            return "(\u8c03\u7528\u5931\u8d25: " + e.getMessage() + ")";
        }
    }

    private String buildDecisionPrompt(String userMessage, List<McpServer> mcps, List<Skill> skills, String agentContext, Set<String> usedTools) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u4f60\u662f Agent \u5de5\u5177\u8c03\u5ea6\u5f15\u64ce\u3002\u5224\u65ad\u662f\u5426\u9700\u8981\u8c03\u7528\u5de5\u5177\u6765\u5b8c\u6210\u7528\u6237\u8bf7\u6c42\u3002\n\n");
        sb.append("\u3010\u5185\u7f6e\u57fa\u7840\u5de5\u5177\uff08\u59cb\u7ec8\u53ef\u7528\uff09\u3011\n").append("- get_current_time: \u83b7\u53d6\u5f53\u524d\u672c\u5730\u65f6\u95f4\uff08\u542b\u65e5\u671f\u4e0e\u661f\u671f\uff09\n").append("- get_current_date: \u83b7\u53d6\u5f53\u524d\u65e5\u671f\uff08yyyy-MM-dd\uff09\n").append("- get_current_time_utc: \u83b7\u53d6\u5f53\u524d UTC \u65f6\u95f4\n").append("- get_current_weekday: \u83b7\u53d6\u4eca\u5929\u662f\u661f\u671f\u51e0\n").append("- get_current_timestamp: \u83b7\u53d6\u5f53\u524d\u65f6\u95f4\u6233\uff08\u6beb\u79d2\uff09\n").append("- get_system_status: \u83b7\u53d6\u7cfb\u7edf\u72b6\u6001\uff08CPU/\u5185\u5b58\uff09\n").append("- get_weather: \u83b7\u53d6\u771f\u5b9e\u5929\u6c14\uff08\u8c03 wttr.in\uff1b\u53ef\u9644\u57ce\u5e02\u540d\uff0c\u5982 get_weather \u5317\u4eac\uff1b\u82e5\u7528\u6237\u6d88\u606f\u4e2d\u542b\u3010\u7528\u6237\u5f53\u524d\u6240\u5728\u57ce\u5e02\uff1aXX\u3011\uff0c\u5fc5\u987b\u7528\u8be5\u57ce\u5e02\u67e5\u8be2\uff1b\u5426\u5219\u53d6\u5f53\u524d\u4f4d\u7f6e\uff09\n").append("- get_hot_topics: \u83b7\u53d6\u4eca\u65e5\u70ed\u70b9/\u70ed\u641c\u8d44\u8baf\uff08\u8054\u7f51\u68c0\u7d22\uff0c\u4f9d\u8d56\u641c\u7d22\u5f00\u5173\uff09\n\n").append("\u3010\u9879\u76ee\u5e93\u6587\u4ef6\u5939\u5de5\u5177\uff08\u4ec5\u9650\u9879\u76ee\u6839\u76ee\u5f55 d:/jvav/farming \u5185\uff09\u3011\n").append("- describe_workspace: \u67e5\u770b\u9879\u76ee\u6839\u76ee\u5f55\u7ed3\u6784\u6982\u89c8\n").append("- list_dir: \u6d4f\u89c8\u9879\u76ee\u5185\u76ee\u5f55\uff0c\u53c2\u6570 path=\u76f8\u5bf9\u8def\u5f84\uff08\u5982 src/main/java\uff09\n").append("- read_file: \u8bfb\u53d6\u9879\u76ee\u5185\u6587\u4ef6\u5185\u5bb9\uff0c\u53c2\u6570 path=\u76f8\u5bf9\u8def\u5f84\n").append("- write_file: \u5199\u5165/\u8986\u76d6\u9879\u76ee\u5185\u6587\u4ef6\u6216\u65b0\u589e\u5de5\u5177\u811a\u672c\uff0c\u53c2\u6570 path=\u76f8\u5bf9\u8def\u5f84, content=\u6587\u4ef6\u5185\u5bb9\uff08\u5371\u9669\u64cd\u4f5c\uff0c\u9700\u7528\u6237\u786e\u8ba4\uff09\n").append("- delete_file: \u5220\u9664\u9879\u76ee\u5185\u6587\u4ef6/\u76ee\u5f55\uff0c\u53c2\u6570 path=\u76f8\u5bf9\u8def\u5f84\uff08\u5371\u9669\u64cd\u4f5c\uff0c\u9700\u7528\u6237\u786e\u8ba4\uff09\n").append("- run_script: \u6267\u884c\u9879\u76ee\u5185 .py/.js \u5de5\u5177\u811a\u672c\uff0c\u53c2\u6570 path=\u811a\u672c\u76f8\u5bf9\u8def\u5f84, content=\u53ef\u9009\u53c2\u6570\uff08\u5371\u9669\u64cd\u4f5c\uff0c\u9700\u7528\u6237\u786e\u8ba4\uff09\n").append("- mkdir: \u521b\u5efa\u9879\u76ee\u5185\u76ee\u5f55\uff0c\u53c2\u6570 path=\u76f8\u5bf9\u8def\u5f84\uff08\u5371\u9669\u64cd\u4f5c\uff0c\u9700\u7528\u6237\u786e\u8ba4\uff09\n\n").append("\u3010\u4f4e\u98ce\u9669\u547d\u4ee4\u884c\u5de5\u5177\uff08\u767d\u540d\u5355\uff0c\u53ea\u8bfb\uff09\u3011\n").append("- run_cli: \u6267\u884c\u4f4e\u98ce\u9669\u53ea\u8bfb\u547d\u4ee4\u884c\u5de5\u5177\u3002\u53c2\u6570\uff1acommand=\u547d\u4ee4\u540d\uff08\u767d\u540d\u5355\uff1aipconfig / ping / netstat / systeminfo / tasklist / whoami / ver / hostname / date / time / echo / dir / path / vol / nslookup / tracert / driverquery\uff09, args=\u53ef\u9009\u53c2\u6570\uff08\u7981\u6b62 & | ; > < $ ` \u7b49 shell \u5143\u5b57\u7b26\uff09\u3002\n").append("  \u4f8b\u5982\uff1a{\"tool\":\"run_cli\",\"command\":\"ipconfig\"}\uff1b{\"tool\":\"run_cli\",\"command\":\"ping\",\"args\":\"-n 3 127.0.0.1\"}\n\n").append("\u3010\u5185\u7f6e\u5e93\u5bfc\u5165\u5de5\u5177\u3011\n").append("- import_library: \u4ece\u9879\u76ee\u5185\u7f6e\u5e93\u5bfc\u5165 openclaw \u751f\u6001\u7cbe\u9009\u7684\u6280\u80fd\u4e0e\u5e38\u7528 MCP \u670d\u52a1\u5668\u3002\n").append("  \u53c2\u6570\uff1atool=import_library\u6280\u80fd\u5219\u8bf4\u660e\u662f\u6280\u80fd\u8fd8\u662f MCP\uff08\u9ed8\u8ba4\u90fd\u5bfc\u5165\uff09\u3002\n").append("  \u9002\u7528\u573a\u666f\uff1a\u7528\u6237\u8bf4\"\u5bfc\u5165\u5185\u7f6e\u6280\u80fd\u5e93\"\"\u52a0\u4e0a\u5e38\u7528\u7684MCP\u670d\u52a1\"\uff08\u5bfc\u5165\u540e\u9ed8\u8ba4\u505c\u7528\uff0c\u53ef\u5728\u7ba1\u7406\u9875\u542f\u7528\uff09\u3002\n\n").append("\u3010\u6570\u636e\u5206\u6790\u5de5\u5177\uff08pandas \u98ce\u683c\uff0c\u53ea\u8bfb\uff09\u3011\n").append("- analyze_data: \u5bf9\u77e5\u8bc6\u5e93\u6587\u6863\u505a\u6570\u636e\u5206\u6790\u5e76\u751f\u6210\u56fe\u8868\u3002\u53c2\u6570\uff1adoc=\u6587\u6863\u540d\u5173\u952e\u8bcd\uff08\u5fc5\u586b\uff09, operation=describe|top|trend|distribution, col1/col2=\u5217\u540d, topN=\u6570\u91cf\u3002\n").append("  \u793a\u4f8b\uff1a{\"tool\":\"analyze_data\",\"doc\":\"\u6d88\u8d39\u7535\u5b50\u62a5\u544a\",\"operation\":\"top\",\"col1\":\"\u54c1\u7c7b\",\"col2\":\"\u51fa\u8d27\u91cf\",\"topN\":5}\n\n").append("\u3010\u591a Agent \u534f\u540c\u8c03\u5ea6\u5de5\u5177\u3011\n").append("- dispatch_teammates: \u591a Agent \u534f\u540c\u8c03\u5ea6 \u2014\u2014 \u5c06\u590d\u6742\u8bf7\u6c42\u62c6\u89e3\u4e3a\u591a\u4e2a\u5b50\u4efb\u52a1\uff0c\u6d3e\u53d1\u7ed9\u540e\u53f0 Teammate \u5b88\u62a4\u8fdb\u7a0b\u5e76\u884c\u6267\u884c\u3002\n").append("  \u9002\u7528\u573a\u666f\uff1a\u7528\u6237\u540c\u65f6\u63d0\u51fa\u591a\u9879\u4efb\u52a1\uff08\u5982\"\u5e2e\u6211\u67e5\u5929\u6c14\u3001\u5199\u5468\u62a5\u3001\u5206\u6790\u6570\u636e\"\uff09\uff0c\u6216\u9700\u8981\u591a\u4e2a\u89d2\u8272\u5206\u5de5\u534f\u4f5c\u7684\u590d\u6742\u8bf7\u6c42\u3002\n").append("  \u7cfb\u7edf\u4f1a\u81ea\u52a8\u521b\u5efa HarnessTask \u5e76\u5206\u914d\u5230\u4efb\u52a1\u770b\u677f\uff0cTeammate \u5b88\u62a4\u7ebf\u7a0b\u5c06\u8ba4\u9886\u5e76\u9010\u6b65\u6267\u884c\u3002\n\n");
        if (!mcps.isEmpty()) {
            sb.append("\u3010\u53ef\u8c03\u7528\u7684 MCP \u5de5\u5177\u3011\n");
            for (McpServer m : mcps) {
                sb.append("- \u540d\u79f0: ").append(m.getName()).append(", \u8bf4\u660e: ").append(m.getDescription() == null ? "" : m.getDescription()).append(", \u7c7b\u578b: ").append(m.getServerType()).append(", \u5730\u5740: ").append(m.getEndpoint()).append("\n");
            }
        }
        if (!skills.isEmpty()) {
            sb.append("\u3010\u53ef\u5e94\u7528\u7684 Skill \u6280\u80fd\u3011\n");
            for (Skill s : skills) {
                sb.append("- \u540d\u79f0: ").append(s.getName()).append(", \u8bf4\u660e: ").append(s.getDescription() == null ? "" : s.getDescription()).append(", \u5185\u5bb9: ").append(this.truncate(s.getContent(), 120)).append("\n");
            }
        }
        sb.append("\n\u5224\u65ad\u89c4\u5219\uff1a\n").append(" - \u82e5\u7528\u6237\u8be2\u95ee\u65f6\u95f4/\u65e5\u671f/\u661f\u671f/\u65f6\u95f4\u6233\u7b49\uff0c\u4f7f\u7528\u5bf9\u5e94\u7684\u5185\u7f6e\u5de5\u5177\u3002\n").append(" - \u82e5\u7528\u6237\u8be2\u95ee\u5929\u6c14\uff0c\u7528 get_weather\uff1b\u8be2\u95ee\u4eca\u65e5\u70ed\u70b9/\u70ed\u641c/\u65b0\u95fb\uff0c\u7528 get_hot_topics\u3002\n").append(" - \u82e5\u7528\u6237\u8981\u6c42\u5bf9\u77e5\u8bc6\u5e93\u6587\u6863/\u6570\u636e\u505a\u7edf\u8ba1\u5206\u6790\u3001\u6c47\u603b\u3001\u6392\u884c\u3001\u8d8b\u52bf\u3001\u5360\u6bd4\u7b49\uff0c\u7528 analyze_data\u3002\u5176\u4e2d doc \u53c2\u6570\u3010\u5fc5\u987b\u3011\u586b\u5199\u4ece\u7528\u6237\u8bf7\u6c42\u4e2d\u63d0\u53d6\u7684\u6587\u6863\u540d\u5173\u952e\u8bcd\uff08\u5982\"\u6d88\u8d39\u7535\u5b50\u62a5\u544a\"\uff09\uff0coperation \u9ed8\u8ba4\u4e3a describe\uff0c\u6392\u884c\u7528 top\u3001\u8d8b\u52bf\u7528 trend\u3001\u5360\u6bd4\u7528 distribution\uff0c\u53ef\u9009\u7684 col1/col2 \u4e3a\u5217\u540d\u3001topN \u4e3a\u6570\u91cf\u3002\n").append(" - \u82e5\u7528\u6237\u540c\u65f6\u63d0\u51fa\u591a\u9879\u4efb\u52a1\uff0c\u6216\u8bf7\u6c42\u6d89\u53ca\u591a\u4e2a\u89d2\u8272\u7684\u5206\u5de5\u534f\u4f5c\uff08\u5982\"\u5e2e\u6211\u67e5\u5929\u6c14\u3001\u5199\u5468\u62a5\u3001\u5206\u6790\u6570\u636e\"\uff09\uff0c\u4f7f\u7528 dispatch_teammates \u8fdb\u884c\u591a Agent \u8c03\u5ea6\u3002\n").append(" - \u82e5\u7528\u6237\u540c\u65f6\u8be2\u95ee\u591a\u9879\u4fe1\u606f\uff08\u5982\"\u83b7\u53d6\u5929\u6c14 \u4eca\u65e5\u70ed\u70b9\"\uff09\uff0c\u53ef\u5728 tools \u6570\u7ec4\u4e2d\u4e00\u6b21\u5217\u51fa\u591a\u4e2a\u5185\u7f6e\u5de5\u5177\uff1a{\"call\":true,\"toolType\":\"builtin\",\"tools\":[{\"tool\":\"get_weather\"},{\"tool\":\"get_hot_topics\"}]}\n").append(" - \u82e5\u7528\u6237\u8981\u6c42\u9605\u8bfb/\u67e5\u770b\u9879\u76ee\u5185\u6587\u4ef6\u6216\u76ee\u5f55\uff0c\u7528 list_dir / read_file / describe_workspace\uff08\u53c2\u6570\u542b path\uff09\u3002\n").append(" - \u82e5\u7528\u6237\u8981\u6c42\u4fee\u6539\u9879\u76ee\u6587\u4ef6\u3001\u65b0\u589e\u5de5\u5177\u811a\u672c\u3001\u5220\u9664\u6587\u4ef6\u3001\u6267\u884c\u811a\u672c\uff0c\u7528 write_file / delete_file / run_script / mkdir\uff08\u53c2\u6570\u542b path\uff0c\u5fc5\u8981\u65f6\u542b content\uff09\u3002\u8fd9\u4e9b\u662f\u4fee\u6539\u7c7b\u64cd\u4f5c\uff0c\u7cfb\u7edf\u4f1a\u81ea\u52a8\u8bf7\u6c42\u7528\u6237\u786e\u8ba4\u3002\n").append(" - \u82e5\u7528\u6237\u8bf7\u6c42\u67e5\u770b\u7cfb\u7edf/\u7f51\u7edc\u72b6\u6001\u3001\u8bca\u65ad\u7f51\u7edc\u3001\u67e5\u7cfb\u7edf\u4fe1\u606f\u7b49\uff0c\u4e14\u9700\u8981\u6267\u884c\u547d\u4ee4\u884c\uff0c\u7528 run_cli\uff08\u53c2\u6570\u542b command\uff0c\u53ef\u9009 args\uff0c\u4ec5\u767d\u540d\u5355\u547d\u4ee4\uff09\u3002\n").append(" - \u82e5\u7528\u6237\u8bf7\u6c42\u641c\u96c6\u89c6\u9891/\u97f3\u4e50/\u6b4c\u66f2\u5e76\u5e0c\u671b\u76f4\u63a5\u64ad\u653e\uff0c\u7528 search_media\uff08\u89c6\u9891\uff09\u6216 search_media_audio\uff08\u97f3\u4e50\uff09\uff0c\u5173\u952e\u8bcd\u4f5c\u4e3a\u540e\u7f00\u3002\n").append(" - \u82e5\u7528\u6237\u8bf7\u6c42\u64ad\u653e/\u6682\u505c/\u5207\u6b4c/\u67e5\u72b6\u6001\u6216\u63a7\u5236\u5f53\u524d\u97f3\u4e50\uff08\u5982\u64ad\u653e\u97f3\u4e50\u3001\u4e0b\u4e00\u9996\u3001\u6682\u505c\u64ad\u653e\u3001\u73b0\u5728\u64ad\u653e\u4ec0\u4e48\u6b4c\u3001\u97f3\u91cf\u8c03\u5927\u4e9b\uff09\uff0c\u4f7f\u7528 lx_music\uff08\u9ed8\u8ba4\u97f3\u4e50\u8c03\u5ea6\uff09\uff1b\u82e5\u6d1b\u96ea\u672a\u542f\u52a8\u5219\u56de\u9000\u5230 search_media\u3002\n").append(" - \u82e5\u7528\u6237\u8bf7\u6c42\u5bfc\u5165\u9879\u76ee\u5185\u7f6e\u7684\u6280\u80fd\u5e93\u6216 MCP \u670d\u52a1\u5e93\uff0c\u7528 import_library\u3002\n").append(" - \u82e5\u7528\u6237\u8981\u6c42\u5b9a\u65f6/\u5ef6\u65f6\u63d0\u9192\uff08\u5982\u201c2\u5206\u949f\u540e\u63d0\u9192\u6211\u559d\u6c34\u201d\u201c\u5b9a\u65f6\u63d0\u9192\u2026\u201d\uff09\uff0c\u4e14\u6709\u540d\u4e3a reminder \u7684 MCP \u5de5\u5177\u53ef\u7528\uff0c\u5219 call=true, toolType=\"mcp\", tool=\"reminder\", args={\"instruct\":\"\u63d0\u9192\u5185\u5bb9\",\"delayMinutes\":\u5206\u949f\u6570,\"title\":\"\u63d0\u9192\u6807\u9898\"}\u3002\u53d6\u6d88\u63d0\u9192\u65f6 args={\"cancelId\":\u63d0\u9192ID}\u3002\n").append(" - \u82e5\u7528\u6237\u8be2\u95ee\u672c\u673a\u65f6\u95f4/\u5f53\u524d\u65f6\u95f4/\u6211\u5728\u54ea\u91cc/\u5f53\u524d\u4f4d\u7f6e/\u5b9a\u4f4d/\u5730\u7406\u4f4d\u7f6e\uff0c\u4e14\u6709\u540d\u4e3a local-geo \u7684 MCP \u5de5\u5177\u53ef\u7528\uff0c\u5219 call=true, toolType=\"mcp\", tool=\"local-geo\", args={}\uff08\u81ea\u52a8\u83b7\u53d6\u672c\u673a\u65f6\u95f4\u4e0e\u5b9a\u4f4d\uff09\u3002\n").append(" - \u82e5\u7528\u6237\u8bf7\u6c42\u9700\u8981\u5916\u90e8\u6570\u636e/\u64cd\u4f5c\uff0c\u4e14\u6709\u5339\u914d\u7684 MCP \u5de5\u5177\uff0c\u5219 call=true, toolType=\"mcp\", tool=\u5de5\u5177\u540d\u79f0, args=\u53c2\u6570JSON\u3002\n").append(" - \u82e5\u7528\u6237\u8bf7\u6c42\u9002\u5408\u7528\u67d0\u4e2a Skill \u6280\u80fd\u5904\u7406\uff0c\u5219 call=true, toolType=\"skill\", tool=\u6280\u80fd\u540d\u79f0\u3002\n").append(" - \u82e5\u7528\u6237\u8bf7\u6c42\u662f\u57fa\u672c\u5de5\u5177\u80fd\u5904\u7406\u7684\uff08\u5982\u95ee\u65f6\u95f4\uff09\uff0c\u5219 call=true, toolType=\"builtin\", tool=get_current_time \u7b49\u3002\n").append(" - \u82e5\u4e0d\u9700\u8981\u4efb\u4f55\u5de5\u5177\uff0c\u5219 call=false\u3002\n").append("\u53ea\u8f93\u51fa JSON\uff1a{\"call\":true|false,\"toolType\":\"mcp\"|\"skill\"|\"builtin\"|\"\",\"tool\":\"\u540d\u79f0\",\"tools\":[{\"tool\":\"\u540d\u79f0\",\"path\":\"...\",\"content\":\"...\"}],\"args\":{...}}\n").append("\u7528\u6237\u8bf7\u6c42\uff1a" + userMessage);
        sb.append("\n - 若用户要求保存/加载长期上下文、跨会话记忆、多 Agent 共享上下文，且有 context-store MCP 可用，则 call=true, toolType=\"mcp\", tool=\"context-store\", args={\"op\":\"context_load\",\"room\":\"default\",\"budget\":4000}；保存上下文用 context_append，黑板用 blackboard_put/get，快照用 snapshot_commit/rollback，文档检索用 doc_search/doc_read。\n");
        if (usedTools != null && !usedTools.isEmpty()) {
            sb.append("\n【本轮已执行过的工具】\n").append(this.usedDisplay(usedTools)).append("\n禁止重复调用上述工具；同一工具如需不同参数（如读取另一个文件）可以继续调用；若信息已足够，直接 call=false。\n");
        }
        if (agentContext != null && !agentContext.isBlank()) {
            sb.append("\n【前序轮次工具结果】\n").append(this.truncate(agentContext, 6000)).append("\n请基于这些结果判断是否还需要调用工具；已获得的信息不要重复查询。\n");
        }
        sb.append("\n - 若用户明确说出城市名（如「北京今天天气」），必须把该城市加入 get_weather 的 tool 参数（如 get_weather 北京），不得使用默认定位。\n");
        return sb.toString();
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

    private String truncate(String s, int len) {
        if (s == null) {
            return "";
        }
        return s.length() <= len ? s : s.substring(0, len);
    }

    public static class ToolResult {
        public boolean invoked;
        public boolean usedMcp;
        public boolean usedSkill;
        public boolean usedBuiltin;
        public String mcpName;
        public String mcpResult;
        public String skillPrompt;
        public String builtinName;
        public String builtinResult;
        public String log;
        public boolean needsConfirm;
        public String pendingEndpoint;
        public String pendingArgs;
        public String pendingOp;
        public String pendingPath;
        public String pendingContent;
        public String pendingReason;
        public List<String> usedCalls = new ArrayList<String>();
        public int loopRounds = 0;
        public String skillName;
    }

    private static class BuiltinOutcome {
        String op;
        String path;
        String content;
        String output;
        String reason;
        boolean needConfirm;

        BuiltinOutcome(String op, String path, String content, String output, String reason, boolean needConfirm) {
            this.op = op;
            this.path = path;
            this.content = content;
            this.output = output;
            this.reason = reason;
            this.needConfirm = needConfirm;
        }
    }

    private static class CachedWeather {
        String value;
        long ts;

        CachedWeather(String value, long ts) {
            this.value = value;
            this.ts = ts;
        }
    }
}

