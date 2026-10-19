/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.ChatMessage;
import com.badmelon.farming.entity.KnowledgeDoc;
import com.badmelon.farming.entity.McpServer;
import com.badmelon.farming.entity.Skill;
import com.badmelon.farming.mapper.HarnessTaskMapper;
import com.badmelon.farming.mapper.TeammateMapper;
import com.badmelon.farming.service.AiService;
import com.badmelon.farming.service.AssistantStepTracker;
import com.badmelon.farming.service.ChatHistoryService;
import com.badmelon.farming.service.ClawHubService;
import com.badmelon.farming.service.KnowledgeService;
import com.badmelon.farming.service.McpServerService;
import com.badmelon.farming.service.MemoryService;
import com.badmelon.farming.service.MonitorService;
import com.badmelon.farming.service.RemoteImportService;
import com.badmelon.farming.service.ReportService;
import com.badmelon.farming.service.SearchService;
import com.badmelon.farming.service.SecurityScanner;
import com.badmelon.farming.service.SkillMcpGeneratorService;
import com.badmelon.farming.service.SkillService;
import com.badmelon.farming.service.TaskService;
import com.badmelon.farming.service.ToolScheduler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.CallSite;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {
    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final AiService aiService;
    private final MemoryService memoryService;
    private final KnowledgeService knowledgeService;
    private final TaskService taskService;
    private final SearchService searchService;
    private final SkillService skillService;
    private final McpServerService mcpServerService;
    private final SkillMcpGeneratorService skillMcpGeneratorService;
    private final RemoteImportService remoteImportService;
    private final ToolScheduler toolScheduler;
    private final ReportService reportService;
    private final ChatHistoryService chatHistoryService;
    private final ClawHubService clawHubService;
    private final SecurityScanner securityScanner;
    private final TeammateMapper teammateMapper;
    private final HarnessTaskMapper taskMapper;
    private final MonitorService monitorService;

    public AssistantService(AiService aiService, MemoryService memoryService, KnowledgeService knowledgeService, TaskService taskService, SearchService searchService, SkillService skillService, McpServerService mcpServerService, SkillMcpGeneratorService skillMcpGeneratorService, RemoteImportService remoteImportService, ToolScheduler toolScheduler, ReportService reportService, ChatHistoryService chatHistoryService, ClawHubService clawHubService, SecurityScanner securityScanner, TeammateMapper teammateMapper, HarnessTaskMapper taskMapper, MonitorService monitorService) {
        this.aiService = aiService;
        this.memoryService = memoryService;
        this.knowledgeService = knowledgeService;
        this.taskService = taskService;
        this.searchService = searchService;
        this.skillService = skillService;
        this.mcpServerService = mcpServerService;
        this.skillMcpGeneratorService = skillMcpGeneratorService;
        this.remoteImportService = remoteImportService;
        this.toolScheduler = toolScheduler;
        this.reportService = reportService;
        this.chatHistoryService = chatHistoryService;
        this.clawHubService = clawHubService;
        this.securityScanner = securityScanner;
        this.teammateMapper = teammateMapper;
        this.monitorService = monitorService;
        this.taskMapper = taskMapper;
    }

    public Map<String, Object> chat(String userMessage) {
        return this.chat(userMessage, null);
    }

    public Map<String, Object> chat(String userMessage, String session) {
        AssistantStepTracker tracker = new AssistantStepTracker();
        int sIntent = tracker.begin("\u8bc6\u522b\u8bf7\u6c42\u610f\u56fe");
        int sKb = -1;
        int sWeb = -1;
        int sTool = -1;
        int sPrompt = -1;
        int sGen = -1;
        this.monitorLog("INFO", "\u6536\u5230\u7528\u6237\u6d88\u606f\uff1a<" + this.truncate(userMessage, 200) + ">");
        if (!this.aiService.isConfigured()) {
            tracker.finish(sIntent, "AI \u672a\u914d\u7f6e");
            HashMap<String, Object> resp = new HashMap<String, Object>();
            resp.put("reply", "\u7cfb\u7edf\u5c1a\u672a\u914d\u7f6e API Key\u3002\u8bf7\u524d\u5f80\u300c\u8bbe\u7f6e\u300d\u9875\u9762\u586b\u5199\u5bc6\u94a5\u540e\u518d\u4f7f\u7528 AI \u8f85\u52a9\u529f\u80fd\u3002");
            resp.put("needConfig", true);
            resp.put("steps", tracker.getSteps());
            return resp;
        }
        // 多 Agent 协同调度优先：命中多任务意图时，先拆解派发而非走单工具/报告
        ToolScheduler.ToolResult multiResp = this.toolScheduler.tryMultiAgentDispatch(userMessage);
        if (multiResp != null) {
            tracker.finish(sIntent, "\u8bc6\u522b\u4e3a\u591a Agent \u534f\u540c\u8c03\u5ea6\u9700\u6c42");
            Map<String, Object> multiOut = new HashMap<String, Object>();
            multiOut.put("reply", multiResp.builtinResult);
            multiOut.put("toolLog", multiResp.log);
            multiOut.put("multiAgent", true);
            multiOut.put("steps", tracker.getSteps());
            return multiOut;
        }
        Map<String, Object> toolResp = this.handleToolIntent(userMessage);
        if (toolResp != null) {
            tracker.finish(sIntent, "\u8bc6\u522b\u4e3a Skill/MCP \u5de5\u5177\u9700\u6c42");
            toolResp.put("steps", tracker.getSteps());
            return toolResp;
        }
        Map<String, Object> reportResp = this.handleReportIntent(userMessage);
        if (reportResp != null) {
            tracker.finish(sIntent, "\u8bc6\u522b\u4e3a\u884c\u4e1a\u62a5\u544a\u751f\u6210\u9700\u6c42");
            reportResp.put("steps", tracker.getSteps());
            return reportResp;
        }
        Map<String, Object> kbReadResp = this.handleKnowledgeRead(userMessage);
        if (kbReadResp != null) {
            tracker.finish(sIntent, "\u8bc6\u522b\u4e3a\u77e5\u8bc6\u5e93\u6587\u6863\u8bfb\u53d6\u9700\u6c42");
            kbReadResp.put("steps", tracker.getSteps());
            return kbReadResp;
        }
        tracker.finish(sIntent, "\u666e\u901a\u5bf9\u8bdd");
        sKb = tracker.begin("\u68c0\u7d22\u672c\u5730\u77e5\u8bc6\u5e93");
        String kbContext = this.retrieveKnowledge(userMessage);
        tracker.finish(sKb, kbContext.isBlank() ? "\u65e0\u76f8\u5173\u6587\u6863" : "\u5df2\u6ce8\u5165\u77e5\u8bc6\u5e93\u4e0a\u4e0b\u6587");
        sWeb = tracker.begin("\u8054\u7f51\u641c\u7d22\u5224\u65ad");
        String webContext = this.retrieveWeb(userMessage);
        tracker.finish(sWeb, webContext.isBlank() ? "\u65e0\u9700\u8054\u7f51" : "\u5df2\u8054\u7f51\u68c0\u7d22\u8d44\u8baf");
        sTool = tracker.begin("\u5de5\u5177\u8c03\u5ea6(Agent Loop)");
        ToolScheduler.ToolResult toolResult = this.toolScheduler.runAgentLoop(userMessage);
        if (toolResult.needsConfirm) {
            tracker.finish(sTool, "\u5de5\u5177\u6d89\u53ca\u9ad8\u5371\u64cd\u4f5c\uff0c\u7b49\u5f85\u7528\u6237\u786e\u8ba4");
            HashMap<String, Object> confirmResp = new HashMap<String, Object>();
            confirmResp.put("needConfig", false);
            confirmResp.put("needsConfirm", true);
            confirmResp.put("confirmReason", toolResult.pendingReason);
            if (toolResult.pendingOp != null) {
                confirmResp.put("confirmOp", toolResult.pendingOp);
                confirmResp.put("confirmPath", toolResult.pendingPath);
                confirmResp.put("confirmContent", toolResult.pendingContent);
                confirmResp.put("confirmTool", "file_ops");
                confirmResp.put("reply", "\u26a0\ufe0f \u52a9\u624b\u9700\u8981\u6267\u884c**\u6587\u4ef6\u64cd\u4f5c**\uff1a\n\n\ud83d\udcc1 \u64cd\u4f5c\u7c7b\u578b\uff1a" + this.fileOpLabel(toolResult.pendingOp) + "\n\ud83d\udccd \u76ee\u6807\u8def\u5f84\uff1a" + (toolResult.pendingPath == null || toolResult.pendingPath.isBlank() ? "(\u9879\u76ee\u6839\u76ee\u5f55)" : toolResult.pendingPath) + "\n\u26a0\ufe0f \u539f\u56e0\uff1a" + toolResult.pendingReason + "\n\n\u8be5\u64cd\u4f5c\u4f1a\u4fee\u6539\u9879\u76ee\u5e93\u6587\u4ef6\u5939\uff0c\u4e3a\u5b89\u5168\u8d77\u89c1\uff0c\u9700\u8981\u60a8\u786e\u8ba4\u540e\u624d\u6267\u884c\u3002");
            } else {
                confirmResp.put("confirmEndpoint", toolResult.pendingEndpoint);
                confirmResp.put("confirmArgs", toolResult.pendingArgs);
                confirmResp.put("confirmTool", toolResult.mcpName);
                confirmResp.put("reply", "\u26a0\ufe0f \u52a9\u624b\u9700\u8981\u8c03\u7528 MCP \u5de5\u5177\u300c" + toolResult.mcpName + "\u300d\u6267\u884c\u64cd\u4f5c\u3002\n\n\u68c0\u6d4b\u5230\u8be5\u64cd\u4f5c\u6d89\u53ca**\u9ad8\u5371\u52a8\u4f5c**\uff1a" + toolResult.pendingReason + "\n\ud83d\udce1 \u76ee\u6807\uff1a" + toolResult.pendingEndpoint + "\n\n\u4e3a\u5b89\u5168\u8d77\u89c1\uff0c\u9700\u8981\u60a8\u786e\u8ba4\u540e\u624d\u6267\u884c\u3002");
            }
            confirmResp.put("steps", tracker.getSteps());
            return confirmResp;
        }
        StringBuilder toolContext = new StringBuilder();
        boolean usedTool = false;
        if (toolResult.invoked) {
            usedTool = true;
            toolContext.append("\n【工具执行结果说明】以下内容是工具真实返回；若结果为空或包含失败、不可用、未找到等提示，请如实说明原因，不要编造数据。\n");
            if (toolResult.usedMcp && toolResult.mcpResult != null) {
                toolContext.append("\n【MCP 工具「").append(toolResult.mcpName).append("」调用结果】\n").append(toolResult.mcpResult).append("\n");
                this.monitorLog("INFO", "调用工具：MCP「" + toolResult.mcpName + "」，返回：" + this.truncate(toolResult.mcpResult, 200));
            }
            if (toolResult.usedBuiltin && toolResult.builtinResult != null) {
                toolContext.append("\n【内置工具「").append(toolResult.builtinName).append("」返回】\n").append(toolResult.builtinResult).append("\n");
                this.monitorLog("INFO", "调用工具：内置「" + toolResult.builtinName + "」，返回：" + this.truncate(toolResult.builtinResult, 200));
            }
            if (toolResult.usedSkill && toolResult.skillPrompt != null) {
                toolContext.append("\n").append(toolResult.skillPrompt).append("\n");
                this.monitorLog("INFO", "应用 Skill 技能：" + this.truncate(toolResult.skillPrompt, 150));
            }
            StringBuilder toolSummary = new StringBuilder();
            if (toolResult.usedMcp) {
                toolSummary.append("MCP「").append(toolResult.mcpName).append("」");
            }
            if (toolResult.usedBuiltin) {
                if (toolSummary.length() > 0) toolSummary.append(" + ");
                toolSummary.append("内置「").append(toolResult.builtinName).append("」");
            }
            if (toolResult.usedSkill) {
                if (toolSummary.length() > 0) toolSummary.append(" + ");
                toolSummary.append("Skill 技能");
            }
            if (toolSummary.length() == 0) toolSummary.append("工具");
            tracker.finish(sTool, "已调用 " + toolSummary);
        } else {
            tracker.finish(sTool, "无需调用工具");
        }
        sPrompt = tracker.begin("\u7ec4\u88c5\u7cfb\u7edf\u63d0\u793a\u8bcd");
        String systemPrompt = this.buildSystemPrompt(kbContext, webContext) + toolContext.toString();
        tracker.finish(sPrompt, "\u5b8c\u6210\u4e0a\u4e0b\u6587\u7ec4\u88c5");
        try {
            sGen = tracker.begin("\u8c03\u7528 AI \u751f\u6210\u56de\u7b54");
            List<Map.Entry<String, String>> history = this.buildHistoryContext(session);
            String reply = this.aiService.chatWithHistory(systemPrompt, history, userMessage);
            tracker.finish(sGen, "AI \u5df2\u8fd4\u56de\u7ed3\u679c");
            int sVerify = tracker.begin("\u81ea\u9a8c\u8bc1\u7b54\u6848\u8d28\u91cf");
            VerifiedReply verified = this.selfVerify(userMessage, reply, toolContext.toString());
            String verifiedReply = reply;
            if (!verified.ok && verified.revised != null && !verified.revised.isBlank()) {
                tracker.finish(sVerify, "\u5df2\u81ea\u7ea0\u9519: \u5b58\u5728\u504f\u79bb/\u4e0d\u5408\u7406, \u5df2\u4fee\u6b63");
                verifiedReply = verified.revised;
            } else {
                tracker.finish(sVerify, "\u56de\u590d\u5207\u9898\u5408\u7406, \u65e0\u9700\u4fee\u6539");
            }
            HashMap<String, Object> resp = new HashMap<String, Object>();
            verifiedReply = this.ensureChartDiv(verifiedReply, toolResult);
            resp.put("reply", verifiedReply);
            resp.put("needConfig", false);
            resp.put("toolUsed", usedTool);
            resp.put("selfVerified", true);
            this.monitorLog("INFO", "\u52a9\u624b\u56de\u590d\u5b8c\u6210\uff1a<" + this.truncate(verifiedReply, 300) + ">");
            if (!verified.ok) {
                resp.put("selfCorrected", true);
                resp.put("verifyIssue", verified.issue);
            }
            if (toolResult.usedMcp) {
                resp.put("toolLog", "\u5df2\u8c03\u7528 MCP \u5de5\u5177\u300c" + toolResult.mcpName + "\u300d");
            }
            if (toolResult.usedBuiltin) {
                resp.put("toolLog", "\u5df2\u8c03\u7528\u5185\u7f6e\u5de5\u5177\u300c" + toolResult.builtinName + "\u300d");
            }
            if (toolResult.usedSkill) {
                resp.put("toolLog", resp.getOrDefault("toolLog", "") + "\uff0c\u5df2\u5e94\u7528 Skill \u6280\u80fd");
            }
            this.rememberIfUseful(userMessage, verifiedReply);
            resp.put("steps", tracker.getSteps());
            return resp;
        }
        catch (Exception e) {
            if (sGen >= 0) {
                tracker.fail(sGen, e.getMessage());
            }
            this.monitorLog("ERROR", "\u52a9\u624b\u5904\u7406\u5f02\u5e38\uff1a<" + this.truncate(e.getMessage(), 200) + ">");
            HashMap<String, Object> resp = new HashMap<String, Object>();
            resp.put("reply", "AI \u8c03\u7528\u5931\u8d25\uff1a" + e.getMessage());
            resp.put("needConfig", false);
            resp.put("error", true);
            resp.put("steps", tracker.getSteps());
            return resp;
        }
    }

    private String retrieveKnowledge(String query) {
        try {
            List<KnowledgeDoc> docs = this.knowledgeService.search(query);
            if (docs == null || docs.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder("\u3010\u77e5\u8bc6\u5e93\u76f8\u5173\u5185\u5bb9\u3011\n");
            int count = 0;
            for (KnowledgeDoc d : docs) {
                if (count >= 3) break;
                String c = d.getContent();
                if (c == null || c.isBlank()) continue;
                int len = Math.min(c.length(), 800);
                sb.append("\u6587\u6863[").append(d.getFileName()).append("]: ").append(c.substring(0, len)).append("\n\n");
                ++count;
            }
            return sb.toString();
        }
        catch (Exception e) {
            return "";
        }
    }

    private String buildSystemPrompt(String kbContext, String webContext) {
        String skillPrompt;
        StringBuilder sb = new StringBuilder();
        sb.append("\u4f60\u662f\u300c\u751f\u6d3b\u52a9\u7406 Harness\u300d\u7cfb\u7edf\u7684\u667a\u80fd\u52a9\u624b\uff0c\u5e2e\u52a9\u7528\u6237\u5904\u7406\u65e5\u5e38\u751f\u6d3b\u4e8b\u52a1\uff1a\u5929\u6c14\u51fa\u884c\u3001\u8d44\u8baf\u70ed\u70b9\u3001\u65e5\u7a0b\u63d0\u9192\u3001\u5bb6\u5ead\u8d26\u52a1\u3001\u5065\u5eb7\u4e0e\u996e\u98df\u3001\u77e5\u8bc6\u5e93\u67e5\u8be2\u3001\u6570\u636e\u7edf\u8ba1\u3001\u6587\u4ef6\u6574\u7406\u7b49\u3002");
        sb.append("\u4f60\u53ef\u4ee5\u53c2\u8003\u56e2\u961f\u72b6\u6001\u3001\u4efb\u52a1\u8fdb\u5ea6\u3001\u8bb0\u5fc6\u3001\u77e5\u8bc6\u5e93\u3001\u8054\u7f51\u641c\u7d22\u7ed3\u679c\u548c\u4e13\u4e1a\u6280\u80fd\u6765\u7ed9\u51fa\u4e13\u4e1a\u3001\u53ef\u6267\u884c\u7684\u56de\u7b54\u3002\n");
        sb.append("\u3010\u5f53\u524d\u56e2\u961f\u72b6\u6001\u3011\n");
        this.teammateMapper.findAll().forEach(t -> sb.append("- ").append(t.getName()).append(" (").append(t.getRole()).append(") \u72b6\u6001:").append(t.getStatus()).append(" \u5f53\u524d\u4efb\u52a1:").append(t.getCurrentTask() == null ? "\u65e0" : t.getCurrentTask()).append(" \u8fdb\u5ea6:").append(t.getProgress()).append("%\n"));
        sb.append("\u3010\u5f53\u524d\u4efb\u52a1\u6c60\u3011\n");
        this.taskMapper.findAll().forEach(t -> sb.append("- ").append(t.getTitle()).append(" [").append(t.getStatus()).append("] \u8fdb\u5ea6:").append(t.getProgress()).append("%\n"));
        String mem = this.memoryService.buildMemoryPrompt();
        if (!mem.isBlank()) {
            sb.append(mem);
        }
        if (!(skillPrompt = this.skillService.buildSkillPrompt()).isBlank()) {
            sb.append(skillPrompt);
        }
        if (!kbContext.isBlank()) {
            sb.append(kbContext);
        }
        if (!webContext.isBlank()) {
            sb.append(webContext);
        }
        sb.append("\n\u56fe\u8868\u6e32\u67d3\u534f\u8bae\uff1a\u82e5\u4e0a\u9762\u7684\u5de5\u5177\u8fd4\u56de\u5185\u5bb9\u4e2d\u5305\u542b [CHART_JSON]{...}[/CHART_JSON] \u6807\u8bb0\uff0c\u8bf7\u5728\u56de\u590d\u6b63\u6587\u672b\u5c3e\u8f93\u51fa\u56fe\u8868\u5bb9\u5668\uff1a\n<div class=\"chart\" data-option='{...}'></div>\n\u5176\u4e2d {\u2026} \u4e3a\u8be5\u6807\u8bb0\u5185\u7684 JSON \u5185\u5bb9\uff08\u6574\u4f53\u539f\u6837\u4fdd\u7559\uff0c\u7528\u5355\u5f15\u53f7\u5305\u88f9\u5c5e\u6027\u503c\uff0c\u4e0d\u8981\u6539\u52a8\u5176\u4e2d\u7684\u6570\u636e\uff09\u3002\u82e5\u6ca1\u6709\u8be5\u6807\u8bb0\uff0c\u5219\u4e0d\u8981\u8f93\u51fa\u56fe\u8868\u5bb9\u5668\u3002\n");
        sb.append("\n\u3010\u5b9a\u65f6\u63d0\u9192\u534f\u8bae\u3011\u82e5\u7528\u6237\u8bf7\u6c42\u5b9a\u65f6\u63d0\u9192\uff08\u5982\u201cX\u5206\u949f\u540e\u63d0\u9192\u6211\u2026\u201d\u201c\u5b9a\u65f6\u63d0\u9192\u2026\u201d\u201c\u7a0d\u540e\u63d0\u9192\u6211\u2026\u201d\uff09\uff0c\u8bf7\u5728\u56de\u590d\u672b\u5c3e\u9644\u4e0a\u6807\u8bb0\uff1a[[SCHEDULE:instruct=\u63d0\u9192\u5185\u5bb9;delayMinutes=\u5ef6\u8fdf\u5206\u949f\u6570;title=\u63d0\u9192\u6807\u9898]]\uff0c\u7cfb\u7edf\u4f1a\u81ea\u52a8\u5b9a\u65f6\u63d0\u9192\u5e76\u51c6\u65f6\u63a8\u9001\u5230\u5bf9\u8bdd\u3002\u793a\u4f8b\uff1a\u7528\u6237\u8bf4\u201c2\u5206\u949f\u540e\u63d0\u9192\u6211\u559d\u6c34\u201d\uff0c\u56de\u590d\u672b\u5c3e\u9644 [[SCHEDULE:instruct=\u559d\u6c34;delayMinutes=2;title=\u559d\u6c34\u63d0\u9192]]\u3002\u82e5\u7528\u6237\u53ea\u662f\u666e\u901a\u63d0\u9192\u800c\u4e0d\u662f\u5b9a\u65f6\u4efb\u52a1\uff0c\u5219\u4e0d\u8981\u8f93\u51fa\u8be5\u6807\u8bb0\u3002\n");
        sb.append("\n\u8bf7\u7528\u7b80\u6d01\u3001\u4e13\u4e1a\u7684\u4e2d\u6587\u56de\u7b54\u7528\u6237\u95ee\u9898\u3002\u82e5\u63d0\u4f9b\u4e86\u8054\u7f51\u641c\u7d22\u7ed3\u679c\uff0c\u8bf7\u4f18\u5148\u53c2\u8003\u5176\u4e2d\u4fe1\u606f\u5e76\u6807\u6ce8\u6765\u6e90\u3002");
        return sb.toString();
    }

    private String retrieveWeb(String query) {
        String[] howToHints;
        if (query == null || query.isBlank()) {
            return "";
        }
        if (!this.searchService.isEnabled()) {
            return "";
        }
        for (String h : howToHints = new String[]{"\u5982\u4f55", "\u600e\u4e48", "\u600e\u6837", "\u6559\u7a0b", "\u600e\u4e48\u7528", "\u600e\u4e48\u64cd\u4f5c", "\u5982\u4f55\u4f7f\u7528", "\u80fd\u5e72\u5565"}) {
            if (!query.contains(h)) continue;
            String[] infoWords = new String[]{"\u8d44\u8baf", "\u6d88\u606f", "\u4ef7\u683c", "\u65b0\u95fb", "\u884c\u60c5", "\u52a8\u6001", "\u70ed\u70b9", "\u8d8b\u52bf"};
            boolean hasInfoNeed = false;
            for (String w : infoWords) {
                if (!query.contains(w)) continue;
                hasInfoNeed = true;
                break;
            }
            if (hasInfoNeed) continue;
            return "";
        }
        String[] strongHints = new String[]{"\u641c\u7d22", "\u641c\u4e00\u4e0b", "\u67e5\u4e00\u4e0b", "\u67e5\u4e00\u67e5", "\u8054\u7f51", "\u67e5\u4e00\u4e0b\u6700\u65b0", "\u641c\u641c"};
        boolean strong = false;
        for (String h : strongHints) {
            if (!query.contains(h)) continue;
            strong = true;
            break;
        }
        boolean weak = false;
        if (!strong) {
            String[] weakHints;
            for (String h : weakHints = new String[]{"\u4eca\u5929", "\u6700\u65b0", "\u5b9e\u65f6", "\u7f51\u4e0a", "\u6700\u8fd1"}) {
                if (!query.contains(h)) continue;
                weak = true;
                break;
            }
            if (weak) {
                String[] infoWords = new String[]{"\u8d44\u8baf", "\u6d88\u606f", "\u4ef7\u683c", "\u65b0\u95fb", "\u884c\u60c5", "\u52a8\u6001", "\u53d1\u751f\u4e86\u4ec0\u4e48", "\u70ed\u70b9", "\u8d8b\u52bf"};
                weak = false;
                for (String w : infoWords) {
                    if (!query.contains(w)) continue;
                    weak = true;
                    break;
                }
            }
        }
        if (!strong && !weak) {
            return "";
        }
        try {
            List<Map<String, String>> results = this.searchService.search(query);
            return this.searchService.formatResults(results);
        }
        catch (Exception e) {
            return "";
        }
    }

    private Map<String, Object> handleToolIntent(String userMessage) {
        boolean actionWord;
        Map<String, Object> scriptResp;
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        String msg = userMessage.toLowerCase();
        if (!msg.contains("http://") && !msg.contains("https://") && (scriptResp = this.handleScriptFileIntent(userMessage)) != null) {
            return scriptResp;
        }
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("needConfig", false);
        resp.put("toolIntent", true);
        String directUrl = this.extractUrl(userMessage);
        SkillMcpGeneratorService.IntentDecision decision = this.skillMcpGeneratorService.decideIntent(userMessage);
        if ("create_skill".equals(decision.action)) {
            return this.handleSkill(decision.requirement, directUrl, decision.download);
        }
        if ("create_mcp".equals(decision.action)) {
            return this.handleMcp(decision.requirement, directUrl, decision.download);
        }
        boolean skillIntent = msg.contains("skill") || msg.contains("\u6280\u80fd");
        boolean mcpIntent = msg.contains("mcp") || msg.contains("\u6a21\u578b\u4e0a\u4e0b\u6587\u534f\u8bae");
        boolean bl = actionWord = msg.contains("\u627e") || msg.contains("\u641c") || msg.contains("\u641c\u96c6") || msg.contains("\u751f\u6210") || msg.contains("\u7f16\u5199") || msg.contains("\u5199\u4e00\u4e2a") || msg.contains("\u5e2e\u6211\u5199") || msg.contains("\u5236\u4f5c") || msg.contains("\u5bfb\u627e") || msg.contains("\u8981\u4e00\u4e2a") || msg.contains("\u9700\u8981") || msg.contains("\u4e0b\u8f7d") || msg.contains("\u5b89\u88c5") || msg.contains("\u65b0\u589e") || msg.contains("\u589e\u52a0") || msg.contains("\u6269\u5c55") || msg.contains("\u88c5\u4e00\u4e2a") || msg.contains("\u63a5\u5165") || msg.contains("\u88c5\u4e0a") || msg.contains("\u542f\u7528");
        if (!actionWord) {
            return null;
        }
        boolean downloadIntent = msg.contains("\u4e0b\u8f7d") || msg.contains("\u5b89\u88c5") || msg.contains("\u76f4\u63a5\u52a0") || msg.contains("\u88c5\u597d") || msg.contains("\u88c5\u4e00\u4e2a") || msg.contains("\u7ed9\u6211\u88c5") || msg.contains("\u88c5\u4e0a") || msg.contains("\u6269\u5c55") || msg.contains("\u63a5\u5165");
        boolean capabilityRequest = this.isCapabilityRequest(msg);
        boolean llmUnavailable = !this.aiService.isConfigured();
        try {
            if (mcpIntent && !skillIntent) {
                return this.handleMcp(userMessage, directUrl, downloadIntent);
            }
            if (skillIntent || capabilityRequest && llmUnavailable) {
                return this.handleSkill(userMessage, directUrl, downloadIntent);
            }
        }
        catch (Exception e) {
            resp.put("reply", "\u81ea\u52a8\u751f\u6210\u5931\u8d25\uff1a" + e.getMessage());
            return resp;
        }
        return null;
    }

    private Map<String, Object> handleScriptFileIntent(String userMessage) {
        boolean skillWord;
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        String msg = userMessage.toLowerCase();
        boolean mentionScript = msg.contains("\u811a\u672c") || msg.contains(".py") || msg.contains(".js");
        boolean actionWord = msg.contains("\u5199") || msg.contains("\u65b0\u5efa") || msg.contains("\u521b\u5efa") || msg.contains("\u589e\u52a0") || msg.contains("\u65b0\u589e") || msg.contains("\u505a\u4e00\u4e2a") || msg.contains("\u7f16\u5199") || msg.contains("\u751f\u6210") || msg.contains("\u653e\u4e00\u4e2a") || msg.contains("\u5236\u4f5c");
        boolean bl = skillWord = msg.contains("\u6280\u80fd") || msg.contains("skill");
        if (!mentionScript || !actionWord || skillWord) {
            return null;
        }
        if (msg.contains("\u4e0b\u8f7d") || msg.contains("\u5bfc\u5165") || msg.contains("\u5b89\u88c5")) {
            return null;
        }
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("needConfig", false);
        resp.put("toolIntent", true);
        resp.put("type", "script_file");
        resp.put("needsConfirm", true);
        try {
            String raw = this.aiService.chat("\u4f60\u662f\u4fe1\u606f\u62bd\u53d6\u5f15\u64ce\uff0c\u53ea\u8f93\u51fa JSON\u3002", "\u7528\u6237\u60f3\u5728\u9879\u76ee\u5185\u65b0\u589e\u4e00\u4e2a\u5de5\u5177\u811a\u672c\u3002\u8bf7\u4ece\u6d88\u606f\u4e2d\u63d0\u53d6\uff1a1) \u811a\u672c\u76f8\u5bf9\u8def\u5f84\uff08\u9ed8\u8ba4\u653e\u5728 tools/ \u76ee\u5f55\uff0c\u5982 tools/test.py\uff09\uff1b2) \u811a\u672c\u5b8c\u6574\u5185\u5bb9\u3002\u82e5\u6d88\u606f\u672a\u7ed9\u51fa\u811a\u672c\u5185\u5bb9\uff0c\u8bf7\u751f\u6210\u4e00\u4e2a\u5b89\u5168\u3001\u53ef\u76f4\u63a5\u8fd0\u884c\u7684 Python \u5de5\u5177\u811a\u672c\u6846\u67b6\uff0c\u7981\u6b62\u5305\u542b\u4efb\u4f55\u7cfb\u7edf\u547d\u4ee4\uff08os.system/subprocess/exec\uff09\u3001\u6587\u4ef6\u5220\u9664\u7b49\u5371\u9669\u64cd\u4f5c\u3002\u4e25\u683c\u53ea\u8f93\u51fa JSON\uff1a{\"path\":\"...\",\"content\":\"...\"}\u3002\u6d88\u606f\uff1a" + userMessage);
            JsonNode node = this.extractJsonNode(raw);
            String path = node.path("path").asText("").trim();
            String content = node.path("content").asText("");
            if (path.isBlank()) {
                path = "tools/generated_script.py";
            }
            if (path.startsWith("/") || path.contains("..")) {
                path = "tools/generated_script.py";
            }
            resp.put("confirmOp", "write_file");
            resp.put("confirmPath", path);
            resp.put("confirmContent", content);
            resp.put("reply", "\ud83d\udcdd \u52a9\u624b\u51c6\u5907\u5728\u9879\u76ee\u5185**\u65b0\u589e\u5de5\u5177\u811a\u672c**\uff1a\n\n\ud83d\udccd \u8def\u5f84\uff1a" + path + "\n\n\u26a0\ufe0f \u8be5\u64cd\u4f5c\u4f1a\u4fee\u6539\u9879\u76ee\u5e93\u6587\u4ef6\u5939\uff0c\u9700\u8981\u60a8\u786e\u8ba4\u540e\u624d\u6267\u884c\u3002");
            return resp;
        }
        catch (Exception e) {
            resp.put("confirmOp", "write_file");
            resp.put("confirmPath", "tools/generated_script.py");
            resp.put("confirmContent", "");
            resp.put("reply", "\ud83d\udcdd \u52a9\u624b\u51c6\u5907\u5728\u9879\u76ee\u5185\u65b0\u589e\u5de5\u5177\u811a\u672c\uff08\u8def\u5f84\u89e3\u6790\u5931\u8d25\uff0c\u9ed8\u8ba4 tools/generated_script.py\uff09\u3002\n\n\u9700\u8981\u60a8\u786e\u8ba4\u540e\u624d\u6267\u884c\u3002");
            return resp;
        }
    }

    private JsonNode extractJsonNode(String raw) throws Exception {
        String trimmed;
        String string = trimmed = raw == null ? "" : raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
        }
        int s = trimmed.indexOf(123);
        int e = trimmed.lastIndexOf(125);
        if (s >= 0 && e > s) {
            trimmed = trimmed.substring(s, e + 1);
        }
        return new ObjectMapper().readTree(trimmed);
    }

    private List<Map.Entry<String, String>> buildHistoryContext(String session) {
        ArrayList<Map.Entry<String, String>> history = new ArrayList<Map.Entry<String, String>>();
        try {
            List<ChatMessage> messages = this.chatHistoryService.history(session);
            if (messages == null || messages.size() <= 1) {
                return history;
            }
            ArrayList<ChatMessage> usable = new ArrayList<ChatMessage>();
            for (int i = 0; i < messages.size() - 1; ++i) {
                ChatMessage m = messages.get(i);
                if (m.getContent() == null || m.getContent().isBlank()) continue;
                usable.add(m);
            }
            if (usable.isEmpty()) {
                return history;
            }
            if (usable.size() > 40) {
                int start;
                ArrayList<ChatMessage> compressed = new ArrayList<ChatMessage>();
                for (int i = 0; i < 3 && i < usable.size(); ++i) {
                    compressed.add((ChatMessage)usable.get(i));
                }
                int keepTail = 32;
                for (int i = start = Math.max(3, usable.size() - keepTail); i < usable.size(); ++i) {
                    compressed.add((ChatMessage)usable.get(i));
                }
                int snipped = usable.size() - 3 - keepTail;
                if (snipped > 0) {
                    compressed.add(3, new ChatMessage());
                    ((ChatMessage)compressed.get(3)).setRole("user");
                    ((ChatMessage)compressed.get(3)).setContent("[snipped " + snipped + " \u6761\u8f83\u65e9\u6d88\u606f\uff0c\u5df2\u7701\u7565]");
                }
                usable = compressed;
            }
            int totalLen = 0;
            for (ChatMessage m : usable) {
                totalLen += m.getContent() == null ? 0 : m.getContent().length();
            }
            if (totalLen > 12000 && usable.size() > 8) {
                List<ChatMessage> recent = usable.subList(Math.max(0, usable.size() - 6), usable.size());
                List<ChatMessage> early = usable.subList(0, Math.max(0, usable.size() - 6));
                String summary = this.summarizeConversation(early);
                if (summary != null && !summary.isBlank()) {
                    ArrayList<Map.Entry<String, String>> result = new ArrayList<Map.Entry<String, String>>();
                    result.add(new AbstractMap.SimpleEntry<String, String>("user", "[\u8f83\u65e9\u5bf9\u8bdd\u6458\u8981]\n" + summary));
                    for (ChatMessage m : recent) {
                        result.add(new AbstractMap.SimpleEntry<String, String>(m.getRole(), m.getContent()));
                    }
                    return result;
                }
            }
            for (ChatMessage m : usable) {
                history.add(new AbstractMap.SimpleEntry<String, String>(m.getRole(), m.getContent()));
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return history;
    }

    private String summarizeConversation(List<ChatMessage> early) {
        try {
            String summary;
            if (early == null || early.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (ChatMessage m : early) {
                String c = m.getContent();
                if (c == null) continue;
                sb.append(m.getRole()).append(": ").append((String)(c.length() > 200 ? c.substring(0, 200) + "..." : c)).append("\n");
            }
            if (sb.length() > 8000) {
                sb = new StringBuilder(sb.substring(0, 8000));
            }
            return (summary = this.aiService.chat("\u4f60\u662f\u5bf9\u8bdd\u6458\u8981\u52a9\u624b\u3002", "\u5bf9\u4ee5\u4e0b\u8f83\u65e9\u7684\u5bf9\u8bdd\u5185\u5bb9\u751f\u6210\u7b80\u6d01\u6458\u8981\uff0c\u4fdd\u7559\uff1a1.\u5f53\u524d\u76ee\u6807 2.\u5173\u952e\u7ed3\u8bba/\u51b3\u7b56 3.\u7528\u6237\u504f\u597d\u4e0e\u7ea6\u675f 4.\u5f85\u529e\u4e8b\u9879\u3002\u53ea\u8f93\u51fa\u6458\u8981\u6b63\u6587\uff1a\n\n" + String.valueOf(sb))) == null ? null : summary.trim();
        }
        catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> handleReportIntent(String userMessage) {
        boolean saveWord;
        String[] analysisWords;
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        String msg = userMessage.toLowerCase();
        for (String w : analysisWords = new String[]{"\u6570\u636e\u5206\u6790", "\u7edf\u8ba1\u5206\u6790", "\u6570\u636e\u7edf\u8ba1", "\u505a\u7edf\u8ba1", "\u56fe\u8868", "\u6392\u884c", "\u5360\u6bd4", "\u53ef\u89c6\u5316", "\u7ed8\u5236", "\u751f\u6210\u56fe", "\u505a\u4e2a\u56fe", "\u753b\u4e2a\u56fe", "\u67f1\u72b6\u56fe", "\u997c\u56fe", "\u6298\u7ebf\u56fe", "\u8d8b\u52bf\u56fe", "\u751f\u6210\u56fe\u8868", "\u7edf\u8ba1\u4e00\u4e0b", "\u505a\u6570\u636e\u5206\u6790", "\u6570\u636e\u53ef\u89c6\u5316"}) {
            if (!userMessage.contains(w)) continue;
            return null;
        }
        boolean reportWord = msg.contains("\u62a5\u544a") || msg.contains("\u8d44\u8baf") || msg.contains("\u884c\u60c5") || msg.contains("\u884c\u4e1a") || msg.contains("\u5e02\u573a") || msg.contains("\u5206\u6790");
        boolean actionWord = msg.contains("\u641c\u96c6") || msg.contains("\u6574\u7406") || msg.contains("\u751f\u6210") || msg.contains("\u5199") || msg.contains("\u505a\u4e00\u4efd") || msg.contains("\u51fa\u4e00\u4efd") || msg.contains("\u6536\u96c6") || msg.contains("\u6c47\u603b");
        boolean bl = saveWord = msg.contains("\u4fdd\u5b58") || msg.contains("\u5165\u5e93") || msg.contains("\u77e5\u8bc6\u5e93") || msg.contains("\u5b58\u5230") || msg.contains("\u5b58\u5165");
        if (!reportWord || !actionWord) {
            return null;
        }
        String industry = this.extractIndustry(userMessage);
        String period = this.extractReportPeriod(userMessage);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("needConfig", false);
        resp.put("reportIntent", true);
        try {
            Map<String, Object> result = this.reportService.generateAndSave(industry, period);
            resp.put("report", result.get("report"));
            resp.put("fileName", result.get("fileName"));
            resp.put("generatedAt", result.get("generatedAt"));
            resp.put("reportPeriod", period);
            resp.put("saved", true);
            resp.put("savedToKnowledgeBase", true);
            resp.put("reply", "\ud83d\udcca \u5df2\u4e3a\u60a8\u641c\u96c6 " + industry + " \u884c\u4e1a" + period + "\u8d44\u8baf\uff0c\u751f\u6210\u8be6\u7ec6\u62a5\u544a\u5e76**\u81ea\u52a8\u4fdd\u5b58\u5230\u672c\u5730\u77e5\u8bc6\u5e93**\u3002\n\n\ud83d\udcc4 \u6587\u4ef6\u540d\uff1a**" + String.valueOf(result.get("fileName")) + "**\n\ud83d\udd50 \u751f\u6210\u65f6\u95f4\uff1a" + String.valueOf(result.get("generatedAt")) + "\n\ud83d\udcc5 \u62a5\u544a\u5468\u671f\uff1a" + period + "\n" + (Boolean.TRUE.equals(result.get("webSearched")) ? "\ud83d\udd0e \u5df2\u8054\u7f51\u641c\u96c6\u8d44\u8baf\u8f85\u52a9\u751f\u6210\n" : "") + "\n\u62a5\u544a\u5df2\u4fdd\u5b58\uff0c\u53ef\u5728\u300c\ud83d\udcda \u672c\u5730\u77e5\u8bc6\u5e93\u300d\u4e2d\u67e5\u770b\u5b8c\u6574\u5185\u5bb9\u3002");
            return resp;
        }
        catch (Exception e) {
            resp.put("saved", false);
            resp.put("reply", "\u274c \u62a5\u544a\u751f\u6210\u5931\u8d25\uff1a" + e.getMessage());
            return resp;
        }
    }

    private String extractReportPeriod(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "\u8fd1\u4e00\u4e2a\u6708";
        }
        String m = userMessage;
        if (m.contains("\u4e0a\u534a\u5e74")) {
            return "\u8fd1\u534a\u5e74\uff081-6\u6708\uff09";
        }
        if (m.contains("\u4e0b\u534a\u5e74")) {
            return "\u8fd1\u534a\u5e74\uff087-12\u6708\uff09";
        }
        Matcher m1 = Pattern.compile("\u8fd1(?:[\u4e00\u4e8c\u4e24\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341]?\\d?|\u51e0)?\\s*\u5929").matcher(m);
        if (m1.find()) {
            return "\u8fd1\u4e00\u5468";
        }
        Matcher m2 = Pattern.compile("\u6700\u8fd1[\u51e0]?(\\d+)\u5929").matcher(m);
        if (m2.find()) {
            return "\u8fd1" + m2.group(1) + "\u5929";
        }
        if (m.contains("\u8fd1\u4e00\u5468") || m.contains("\u6700\u8fd1\u4e00\u5468") || m.contains("\u8fc7\u53bb\u4e00\u5468")) {
            return "\u8fd1\u4e00\u5468";
        }
        if (m.contains("\u8fd1\u4e24\u5468") || m.contains("\u6700\u8fd1\u4e24\u5468")) {
            return "\u8fd1\u4e24\u5468";
        }
        Matcher m3 = Pattern.compile("\u8fd1[\u51e0]?(\\d+)\\s*\u4e2a?\u6708").matcher(m);
        if (m3.find()) {
            return "\u8fd1" + m3.group(1) + "\u4e2a\u6708";
        }
        Matcher m4 = Pattern.compile("\u6700\u8fd1[\u51e0]?(\\d+)\\s*\u4e2a?\u6708").matcher(m);
        if (m4.find()) {
            return "\u8fd1" + m4.group(1) + "\u4e2a\u6708";
        }
        if (m.contains("\u8fd1\u4e00\u5e74") || m.contains("\u6700\u8fd1\u4e00\u5e74") || m.contains("\u8fc7\u53bb\u4e00\u5e74")) {
            return "\u8fd1\u4e00\u5e74";
        }
        Matcher m5 = Pattern.compile("\u8fd1[\u51e0]?(\\d+)\\s*\u5468").matcher(m);
        if (m5.find()) {
            return "\u8fd1" + m5.group(1) + "\u5468";
        }
        Matcher m6 = Pattern.compile("(20\\d{2})\\s*\u5e74\\s*\u4e0a\\s*\u534a\\s*\u5e74").matcher(m);
        if (m6.find()) {
            return m6.group(1) + "\u5e74\u4e0a\u534a\u5e74";
        }
        Matcher m7 = Pattern.compile("(20\\d{2})\\s*\u5e74\\s*\u4e0b\\s*\u534a\\s*\u5e74").matcher(m);
        if (m7.find()) {
            return m7.group(1) + "\u5e74\u4e0b\u534a\u5e74";
        }
        Matcher m8 = Pattern.compile("(20\\d{2})\\s*\u5e74\\s*Q([1-4])").matcher(m);
        if (m8.find()) {
            return m8.group(1) + "\u5e74Q" + m8.group(2);
        }
        Matcher m9 = Pattern.compile("(20\\d{2})\\s*\u5e74").matcher(m);
        if (m9.find()) {
            return m9.group(1) + "\u5e74\u5168\u5e74";
        }
        return "\u8fd1\u4e00\u4e2a\u6708";
    }

    private Map<String, Object> handleKnowledgeRead(String userMessage) {
        String[] stringArray;
        String[] projectHints;
        boolean trigger;
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        boolean hasFileExt = userMessage.contains(".md") || userMessage.contains(".txt") || userMessage.contains(".pdf") || userMessage.contains(".docx") || userMessage.contains(".xlsx");
        String[] readVerbs = new String[]{"\u8bfb\u53d6", "\u603b\u7ed3", "\u6982\u62ec", "\u5206\u6790", "\u67e5\u770b", "\u7ed9\u6211\u8bb2\u8bb2", "\u68b3\u7406", "\u89e3\u8bfb", "\u63d0\u70bc", "\u8bb2\u4e00\u4e0b"};
        boolean readIntent = false;
        for (String string : readVerbs) {
            if (!userMessage.contains(string)) continue;
            readIntent = true;
            break;
        }
        boolean hasDocWord = userMessage.contains("\u62a5\u544a") || userMessage.contains("\u6587\u6863") || userMessage.contains("\u6587\u4ef6") || userMessage.contains("\u77e5\u8bc6\u5e93\u91cc\u7684") || userMessage.contains("\u77e5\u8bc6\u5e93\u4e2d");
        boolean bl = trigger = hasFileExt || readIntent && hasDocWord;
        if (!trigger) {
            return null;
        }
        if (userMessage.contains("\u641c\u96c6") || userMessage.contains("\u6574\u7406\u6210") || userMessage.contains("\u751f\u6210\u4e00\u4efd")) {
            return null;
        }
        for (String h : projectHints = new String[]{"\u9879\u76ee\u91cc", "\u9879\u76ee\u4e2d", "\u9879\u76ee\u5185", "\u76ee\u5f55", "\u4ee3\u7801", "\u6e90\u7801", "src", ".java", ".py", ".js", ".gradle", ".xml", ".yml", ".properties", "\u811a\u672c"}) {
            if (!userMessage.contains(h)) continue;
            return null;
        }
        for (String w : stringArray = new String[]{"\u6570\u636e\u5206\u6790", "\u7edf\u8ba1\u5206\u6790", "\u6570\u636e\u7edf\u8ba1", "\u505a\u7edf\u8ba1", "\u56fe\u8868", "\u6392\u884c", "\u5360\u6bd4", "\u53ef\u89c6\u5316", "\u7ed8\u5236", "\u751f\u6210\u56fe", "\u505a\u4e2a\u56fe", "\u753b\u4e2a\u56fe", "\u67f1\u72b6\u56fe", "\u997c\u56fe", "\u6298\u7ebf\u56fe", "\u8d8b\u52bf\u56fe"}) {
            if (!userMessage.contains(w)) continue;
            return null;
        }
        String keyword = this.extractDocKeyword(userMessage);
        try {
            String content;
            List<KnowledgeDoc> docs = this.knowledgeService.search(keyword);
            if (docs == null || docs.isEmpty()) {
                docs = this.knowledgeService.findAll();
            }
            if (docs == null || docs.isEmpty()) {
                if (hasFileExt) {
                    return null;
                }
                HashMap<String, Object> resp = new HashMap<String, Object>();
                resp.put("needConfig", false);
                resp.put("reply", "\ud83d\udcc2 \u672c\u5730\u77e5\u8bc6\u5e93\u5f53\u524d\u6ca1\u6709\u53ef\u8bfb\u53d6\u7684\u6587\u6863\u3002\u60a8\u53ef\u4ee5\u5148\u4e0a\u4f20\u6587\u6863\u5230\u300c\ud83d\udcda \u672c\u5730\u77e5\u8bc6\u5e93\u300d\u9875\u9762\uff0c\u518d\u8fdb\u884c\u603b\u7ed3\u5206\u6790\u3002");
                return resp;
            }
            String lowerKeyword = keyword.toLowerCase();
            boolean fileNameMatched = false;
            for (KnowledgeDoc d : docs) {
                String fn = d.getFileName();
                if (fn != null && fn.toLowerCase().contains(lowerKeyword)) {
                    fileNameMatched = true;
                    break;
                }
            }
            if (hasFileExt && !fileNameMatched) {
                return null;
            }
            docs.sort((a, b) -> {
                String fa = a.getFileName() == null ? "" : a.getFileName().toLowerCase();
                String fb = b.getFileName() == null ? "" : b.getFileName().toLowerCase();
                int sa = this.scoreFileNameMatch(fa, lowerKeyword);
                int sb = this.scoreFileNameMatch(fb, lowerKeyword);
                return Integer.compare(sb, sa);
            });
            KnowledgeDoc target = docs.get(0);
            for (KnowledgeDoc d : docs) {
                String fn = d.getFileName();
                if (fn == null || !fn.toLowerCase().contains(lowerKeyword)) continue;
                target = d;
                break;
            }
            if ((content = target.getContent()) == null || content.isBlank()) {
                HashMap<String, Object> resp = new HashMap<String, Object>();
                resp.put("needConfig", false);
                resp.put("reply", "\u26a0\ufe0f \u5df2\u627e\u5230\u6587\u6863\u300c" + target.getFileName() + "\u300d\uff0c\u4f46\u8be5\u6587\u6863\u6682\u65e0\u5185\u5bb9\uff08\u53ef\u80fd\u672a\u89e3\u6790\u6210\u529f\uff09\u3002");
                return resp;
            }
            String systemPrompt = this.buildKnowledgeReadPrompt(target.getFileName(), content, userMessage);
            String reply = this.aiService.chat(systemPrompt, userMessage);
            HashMap<String, Object> resp = new HashMap<String, Object>();
            resp.put("needConfig", false);
            resp.put("reply", "\ud83d\udcc4 \u5df2\u4ece\u672c\u5730\u77e5\u8bc6\u5e93\u8bfb\u53d6\u6587\u6863\u300c" + target.getFileName() + "\u300d\u5e76\u5b8c\u6210\u5206\u6790\uff1a\n\n" + reply);
            resp.put("knowledgeRead", true);
            resp.put("fileName", target.getFileName());
            return resp;
        }
        catch (Exception e) {
            HashMap<String, Object> resp = new HashMap<String, Object>();
            resp.put("needConfig", false);
            resp.put("reply", "\u274c \u8bfb\u53d6\u77e5\u8bc6\u5e93\u6587\u6863\u5931\u8d25\uff1a" + e.getMessage());
            return resp;
        }
    }

    private int scoreFileNameMatch(String fileName, String lowerKeyword) {
        if (fileName == null || fileName.isEmpty()) {
            return 0;
        }
        if (fileName.contains(lowerKeyword)) {
            int score = 50;
            if (lowerKeyword.length() >= 4 && fileName.length() >= lowerKeyword.length() && fileName.indexOf(lowerKeyword) <= fileName.length() / 2) {
                score += 30;
            }
            return score;
        }
        return 0;
    }

    private String buildKnowledgeReadPrompt(String fileName, String content, String userQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u4f60\u662f\u300c\u751f\u6d3b\u52a9\u7406 Harness\u300d\u7cfb\u7edf\u7684\u667a\u80fd\u52a9\u624b\u3002\u7528\u6237\u8981\u6c42\u4f60\u57fa\u4e8e\u672c\u5730\u77e5\u8bc6\u5e93\u4e2d\u7684\u6307\u5b9a\u6587\u6863\u5185\u5bb9\u8fdb\u884c\u5206\u6790\u3002\n\n");
        sb.append("\u3010\u6587\u6863\u540d\u79f0\u3011").append(fileName).append("\n\n");
        sb.append("\u3010\u6587\u6863\u5b8c\u6574\u5185\u5bb9\u3011\n").append(content).append("\n\n");
        sb.append("\u8bf7\u4e25\u683c\u57fa\u4e8e\u4ee5\u4e0a\u6587\u6863\u5185\u5bb9\u56de\u5e94\u7528\u6237\u8bf7\u6c42\uff0c\u4e0d\u8981\u7f16\u9020\u6587\u6863\u4e2d\u4e0d\u5b58\u5728\u7684\u4fe1\u606f\u3002\n");
        sb.append("\u82e5\u6587\u6863\u5185\u5bb9\u4e0d\u8db3\u4ee5\u56de\u7b54\uff0c\u8bf7\u5982\u5b9e\u8bf4\u660e\u3002\n");
        return sb.toString();
    }

    private String extractDocKeyword(String userMessage) {
        try {
            String kw;
            String raw = this.aiService.chat("\u4f60\u662f\u4fe1\u606f\u62bd\u53d6\u5f15\u64ce\u3002", "\u4ece\u4e0b\u9762\u6d88\u606f\u4e2d\u63d0\u53d6\u7528\u6237\u60f3\u8bfb\u53d6/\u603b\u7ed3\u7684\u77e5\u8bc6\u5e93\u6587\u6863\u540d\u5173\u952e\u8bcd\uff08\u5982\"\u4e2a\u4eba\u6d88\u8d39\u7535\u5b50\u62a5\u544a\"\u3001\"\u5e93\u5b58\u7ba1\u7406\u62a5\u544a\"\uff09\u3002\u89c4\u5219\uff1a\u53ea\u8f93\u51fa\u6587\u6863\u540d\u5173\u952e\u8bcd\u672c\u8eab\uff082-20\u5b57\uff09\uff0c\u4e0d\u8981\u8f93\u51fa\u4efb\u4f55\u5176\u4ed6\u6587\u5b57\u3001\u5f15\u53f7\u6216\u89e3\u91ca\uff1b\u82e5\u65e0\u6cd5\u5224\u65ad\u8f93\u51fa\"\u65e0\"\u3002\u6d88\u606f\uff1a" + userMessage);
            String string = kw = raw == null ? "" : raw.trim().replace("\"", "").replace("'", "").replace("\n", "").replace("\r", "").trim();
            if (kw.length() >= 2 && kw.length() <= 20 && !"\u65e0".equals(kw)) {
                return kw;
            }
        }
        catch (Exception raw) {
            // empty catch block
        }
        Matcher m = Pattern.compile("([\\p{IsHan}A-Za-z0-9]{1,30})\\.(md|txt|pdf|docx|xlsx)").matcher(userMessage);
        if (m.find()) {
            return m.group(1);
        }
        Matcher m2 = Pattern.compile("([\\p{IsHan}A-Za-z0-9]{1,6}?)(\u62a5\u544a|\u6587\u6863)").matcher(userMessage);
        if (m2.find()) {
            String[] skips;
            String name = m2.group(1) + m2.group(2);
            for (String s : skips = new String[]{"\u603b\u7ed3", "\u4e00\u4e0b", "\u5e2e\u6211", "\u7ed9\u6211", "\u5206\u6790", "\u67e5\u770b", "\u6574\u7406", "\u8bfb\u53d6", "\u68b3\u7406", "\u6982\u62ec", "\u89e3\u8bfb", "\u63d0\u70bc", "\u5f52\u7eb3", "\u6c47\u62a5", "\u4ecb\u7ecd"}) {
                while (name.startsWith(s)) {
                    name = name.substring(s.length());
                }
            }
            return name.isEmpty() ? m2.group(1) + m2.group(2) : name;
        }
        return userMessage.replace("\"", "").replace("'", "").replace("\u300a", "").replace("\u300b", "").replace("\u8bfb\u53d6", "").replace("\u603b\u7ed3", "").replace("\u5206\u6790", "").replace("\u67e5\u770b", "").replace("\u62a5\u544a", "").trim();
    }

    private String extractIndustry(String userMessage) {
        try {
            String industry;
            String raw = this.aiService.chat("\u4f60\u662f\u4fe1\u606f\u62bd\u53d6\u5f15\u64ce\u3002", "\u4ece\u4e0b\u9762\u6d88\u606f\u4e2d\u63d0\u53d6\u7528\u6237\u60f3\u5206\u6790\u7684\u884c\u4e1a\u540d\u79f0\u3002\u89c4\u5219\uff1a\u53ea\u8f93\u51fa\u884c\u4e1a\u540d\u672c\u8eab\uff0c\u4e0d\u8981\u8f93\u51fa\u4efb\u4f55\u5176\u4ed6\u6587\u5b57\u3001\u5f15\u53f7\u3001JSON \u6216\u89e3\u91ca\u3002\u793a\u4f8b\uff1a\u6d88\u606f\"\u641c\u96c6\u624b\u673a\u884c\u4e1a\u8d44\u8baf\"\u2192\u8f93\u51fa\"\u624b\u673a\"\uff1b\u6d88\u606f\"\u5206\u6790\u4eba\u5de5\u667a\u80fd\u5e02\u573a\"\u2192\u8f93\u51fa\"\u4eba\u5de5\u667a\u80fd\"\u3002\u82e5\u65e0\u6cd5\u5224\u65ad\u884c\u4e1a\uff0c\u8f93\u51fa\"\u901a\u7528\"\u3002\u6d88\u606f\uff1a" + userMessage);
            String string = industry = raw == null ? "" : raw.trim().replace("\"", "").replace("'", "").replace("\n", "").replace("\r", "").trim();
            if (industry.length() >= 1 && industry.length() <= 8) {
                return industry;
            }
        }
        catch (Exception raw) {
            // empty catch block
        }
        String s = this.matchIndustryNear(userMessage, "\u884c\u4e1a");
        if (s != null) {
            return s;
        }
        s = this.matchIndustryNear(userMessage, "\u5e02\u573a");
        if (s != null) {
            return s;
        }
        return "\u901a\u7528";
    }

    private String matchIndustryNear(String text, String suffix) {
        String[] skipWords;
        char c;
        int idx = text.indexOf(suffix);
        if (idx <= 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = idx - 1; i >= 0 && sb.length() < 4 && (c = text.charAt(i)) >= '\u4e00' && c <= '\u9fa5'; --i) {
            sb.insert(0, c);
        }
        String result = sb.toString();
        for (String skip : skipWords = new String[]{"\u5e2e\u6211", "\u7ed9\u6211", "\u641c\u96c6", "\u6574\u7406", "\u751f\u6210", "\u505a\u4e00\u4efd", "\u51fa\u4e00\u4efd", "\u6536\u96c6", "\u6c47\u603b", "\u5206\u6790", "\u67e5\u8be2", "\u67e5\u627e", "\u8fd1\u4e00\u4e2a", "\u4e00\u4e2a\u6708\u7684", "\u4e00\u4e2a", "\u7684"}) {
            if (!result.endsWith(skip)) continue;
            result = result.substring(0, result.length() - skip.length());
        }
        return result.isEmpty() ? null : result;
    }

    private boolean isCapabilityRequest(String msg) {
        String[] needWords = new String[]{"\u9700\u8981", "\u8981\u4e00\u4e2a", "\u6211\u8981", "\u6709\u6ca1\u6709", "\u80fd\u4e0d\u80fd", "\u5e2e\u6211\u505a", "\u505a\u4e2a", "\u505a\u4e00\u4e2a", "\u589e\u52a0", "\u65b0\u589e", "\u6269\u5c55", "\u63d0\u4f9b", "\u652f\u6301"};
        String[] capWords = new String[]{"\u529f\u80fd", "\u80fd\u529b", "\u5de5\u5177", "\u6280\u80fd", "\u6a21\u5757", "\u81ea\u52a8\u5316", "\u83b7\u53d6", "\u67e5\u8be2", "\u8f6c\u6362", "\u751f\u6210", "\u68c0\u6d4b", "\u8bc6\u522b"};
        boolean hasNeed = false;
        for (String n : needWords) {
            if (!msg.contains(n)) continue;
            hasNeed = true;
            break;
        }
        if (!hasNeed) {
            return false;
        }
        for (String c : capWords) {
            if (!msg.contains(c)) continue;
            return true;
        }
        return false;
    }

    private Map<String, Object> handleMcp(String userMessage, String directUrl, boolean downloadIntent) {
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("needConfig", false);
        resp.put("toolIntent", true);
        resp.put("type", "mcp");
        if (directUrl != null) {
            try {
                Map<String, Object> imported = this.remoteImportService.importMcp(directUrl);
                resp.put("downloaded", true);
                resp.put("scanSafe", true);
                resp.put("server", imported.get("server"));
                resp.put("reply", this.buildDownloadedReply("MCP", imported.get("server")));
                return resp;
            }
            catch (SecurityException e) {
                resp.put("downloaded", false);
                resp.put("blocked", true);
                resp.put("reply", "\u26d4 \u5df2\u62d2\u7edd\u5bfc\u5165\uff1a" + e.getMessage());
                return resp;
            }
            catch (Exception e) {
                resp.put("downloaded", false);
                resp.put("reply", "\u274c \u4e0b\u8f7d\u5931\u8d25\uff1a" + e.getMessage());
                return resp;
            }
        }
        Map<String, Object> draft = this.skillMcpGeneratorService.generateMcp(userMessage);
        if (downloadIntent && Boolean.TRUE.equals(draft.get("scanSafe"))) {
            McpServer server = new McpServer();
            server.setName((String)draft.get("name"));
            server.setServerType((String)draft.get("serverType"));
            server.setEndpoint((String)draft.get("endpoint"));
            server.setDescription((String)draft.get("description"));
            server.setConfig((String)draft.get("config"));
            server.setStatus("active");
            McpServer saved = this.mcpServerService.save(server);
            resp.put("downloaded", true);
            resp.put("scanSafe", true);
            resp.put("server", saved);
            resp.put("reply", this.buildDownloadedReply("MCP", saved));
            return resp;
        }
        resp.put("draftMcp", draft);
        resp.put("scanSafe", draft.get("scanSafe"));
        resp.put("reply", this.buildMcpReply(draft));
        return resp;
    }

    private Map<String, Object> handleSkill(String userMessage, String directUrl, boolean downloadIntent) {
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("needConfig", false);
        resp.put("toolIntent", true);
        resp.put("type", "skill");
        if (directUrl != null) {
            try {
                Map<String, Object> imported = this.remoteImportService.importSkill(directUrl);
                resp.put("downloaded", true);
                resp.put("scanSafe", true);
                resp.put("skill", imported.get("skill"));
                resp.put("reply", this.buildDownloadedReply("Skill", imported.get("skill")));
                return resp;
            }
            catch (SecurityException e) {
                resp.put("downloaded", false);
                resp.put("blocked", true);
                resp.put("reply", "\u26d4 \u5df2\u62d2\u7edd\u5bfc\u5165\uff1a" + e.getMessage());
                return resp;
            }
            catch (Exception e) {
                resp.put("downloaded", false);
                resp.put("reply", "\u274c \u4e0b\u8f7d\u5931\u8d25\uff1a" + e.getMessage());
                return resp;
            }
        }
        if (downloadIntent) {
            try {
                Map<String, Object> best;
                List<Map<String, Object>> hubSkills = this.clawHubService.search(userMessage);
                log.info("[ClawHub] handleSkill 搜索词={}, 结果数={}", userMessage, hubSkills == null ? -1 : hubSkills.size());
                if (hubSkills != null && !hubSkills.isEmpty() && (best = this.pickRelevantClawHubSkill(userMessage, hubSkills)) != null) {
                    String slug = (String)best.get("slug");
                    String version = (String)best.getOrDefault("version", "");
                    String name = (String)best.getOrDefault("name", slug);
                    String summary = (String)best.getOrDefault("summary", "");
                    String description = (String)best.getOrDefault("description", "");
                    Object skillContent = this.clawHubService.fetchSkillMd(slug, version);
                    if (skillContent == null || ((String)skillContent).isBlank()
                            || !this.clawHubService.contentMatches((String)skillContent, slug, name)) {
                        // 内容端点不可用或返回错位数据：以搜索元信息兜底，保证技能内容正确
                        skillContent = "\u540d\u79f0: " + name + "\n\u63cf\u8ff0: " + summary + "\n\n" + description + "\n\u6765\u6e90: ClawHub (" + slug + ")\n\u5f53\u7528\u6237\u8bf7\u6c42\u4e0e\u6b64\u6280\u80fd\u76f8\u5173\u65f6\uff0c\u8bf7\u6309\u4ee5\u4e0a\u8bf4\u660e\u5904\u7406\u3002";
                    }
                    Skill skill = new Skill();
                    skill.setName(name);
                    skill.setCategory("ClawHub");
                    // description 列限长 255，截断 ClawHub 超长摘要
                    skill.setDescription(summary != null && summary.length() > 250 ? summary.substring(0, 250) : summary);
                    skill.setContent((String)skillContent);
                    skill.setEnabled(1);
                    SecurityScanner.Result scan = this.securityScanner.scan((String)skillContent);
                    if (scan.safe) {
                        // 同名技能已存在则跳过安装，避免唯一键冲突
                        if (this.skillService.findByName(name) != null) {
                            resp.put("downloaded", true);
                            resp.put("scanSafe", true);
                            resp.put("source", "clawhub");
                            resp.put("alreadyExists", true);
                            resp.put("reply", "\u2705 ClawHub \u6280\u80fd\u300c" + name + "\u300d\uff08" + slug + "\uff09\u4e0e\u7cfb\u7edf\u5df2\u6709\u6280\u80fd\u540c\u540d\uff0c\u5df2\u8df3\u8fc7\u91cd\u590d\u5b89\u88c5\u3002");
                            return resp;
                        }
                        Skill saved = this.skillService.save(skill);
                        resp.put("downloaded", true);
                        resp.put("scanSafe", true);
                        resp.put("source", "clawhub");
                        resp.put("clawhubSlug", slug);
                        resp.put("skill", saved);
                        resp.put("reply", "\u2705 \u5df2\u4ece ClawHub \u627e\u5230\u5e76\u65b0\u589e Skill\u300c" + saved.getName() + "\u300d\uff08\u6765\u6e90: " + slug + "\uff09\u5230\u7cfb\u7edf\uff01\n\u53ef\u901a\u8fc7\u300c\ud83d\udee0\ufe0f Skill \u6280\u80fd\u300d\u9875\u9762\u67e5\u770b\u548c\u7ba1\u7406\u3002");
                        return resp;
                    }
                }
            }
            catch (Exception hubSkills) {
                log.warn("[ClawHub] 安装链路异常（将降级 LLM 生成）: {} {}", hubSkills.getClass().getSimpleName(), hubSkills.getMessage());
                hubSkills.printStackTrace();
            }
        }
        Map<String, Object> draft = this.skillMcpGeneratorService.generateSkill(userMessage);
        if (downloadIntent && Boolean.TRUE.equals(draft.get("scanSafe"))) {
            String draftName = (String)draft.get("name");
            // 同名技能已存在则跳过安装，避免唯一键冲突
            if (draftName != null && this.skillService.findByName(draftName) != null) {
                resp.put("downloaded", true);
                resp.put("scanSafe", true);
                resp.put("alreadyExists", true);
                resp.put("reply", "\u2705 \u6280\u80fd\u300c" + draftName + "\u300d\u5df2\u5b58\u5728\uff0c\u5df2\u8df3\u8fc7\u91cd\u590d\u5b89\u88c5\u3002");
                return resp;
            }
            Skill skill = new Skill();
            skill.setName(draftName);
            skill.setCategory((String)draft.get("category"));
            skill.setDescription((String)draft.get("description"));
            skill.setContent((String)draft.get("content"));
            skill.setEnabled(1);
            Skill saved = this.skillService.save(skill);
            resp.put("downloaded", true);
            resp.put("scanSafe", true);
            resp.put("skill", saved);
            resp.put("reply", this.buildDownloadedReply("Skill", saved));
            return resp;
        }
        resp.put("draftSkill", draft);
        resp.put("scanSafe", draft.get("scanSafe"));
        resp.put("reply", this.buildSkillReply(draft));
        return resp;
    }

    private Map<String, Object> pickRelevantClawHubSkill(String requirement, List<Map<String, Object>> hubSkills) {
        if (hubSkills == null || hubSkills.isEmpty()) {
            return null;
        }
        if (hubSkills.size() == 1) {
            return hubSkills.get(0);
        }
        try {
            // 先做确定性关键词打分（不依赖 LLM 报号，规避编号 off-by-one）
            Map<String, Object> kwBest = this.fallbackRelevantClawHubSkill(requirement, hubSkills);
            List<Map<String, Object>> candidates = hubSkills.size() > 8 ? hubSkills.subList(0, 8) : hubSkills;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < candidates.size(); ++i) {
                Map<String, Object> s = candidates.get(i);
                sb.append(i + 1).append(". ").append(s.getOrDefault("name", "")).append(" \u2014 ").append(s.getOrDefault("summary", "")).append("\n");
            }
            String raw = this.aiService.chat("\u4f60\u662f\u6280\u80fd\u5339\u914d\u4e13\u5bb6\uff0c\u53ea\u8f93\u51fa JSON\u3002", "\u7528\u6237\u9700\u6c42\uff1a" + requirement + "\n\u5019\u9009\u6280\u80fd\u5217\u8868\uff1a\n" + String.valueOf(sb) + "\n\u8bf7\u9009\u62e9\u4e0e\u7528\u6237\u9700\u6c42\u6700\u5339\u914d\u7684\u5019\u9009\u7f16\u53f7\u3002\u89c4\u5219\uff1a\u53ea\u6709\u786e\u5b9e\u5339\u914d\u624d\u9009\u62e9\uff1b\u82e5\u6240\u6709\u5019\u9009\u90fd\u4e0e\u9700\u6c42\u65e0\u5173\uff0c\u8f93\u51fa 0\u3002\u4e25\u683c\u53ea\u8f93\u51fa JSON \u683c\u5f0f\uff1a{\"index\": \u6570\u5b57}");
            int idx = this.extractJsonIndex(raw);
            log.info("[ClawHub] 挑选需求={}, LLM原始={}, 解析idx={}, 候选数={}, 关键词命中={}", requirement, this.truncateForLog(raw), idx, candidates.size(), kwBest == null ? "null" : kwBest.getOrDefault("name", ""));
            if (idx >= 1 && idx <= candidates.size()) {
                Map<String, Object> llmPick = candidates.get(idx - 1);
                // 关键词命中结果优先（确定性），规避 LLM 编号 off-by-one 选错；
                // 仅当关键词完全未命中时才采用 LLM 语义挑选（同义词场景）
                return kwBest != null ? kwBest : llmPick;
            }
            // LLM 判定无匹配（输出 0）或解析失败：以关键词打分结果为准
            return kwBest;
        }
        catch (Exception ignored) {
            log.info("[ClawHub] LLM挑选异常，走关键词兜底: {}", ignored.getMessage());
            return this.fallbackRelevantClawHubSkill(requirement, hubSkills);
        }
    }

    private String truncateForLog(String s) {
        if (s == null) return "";
        return s.length() <= 120 ? s : s.substring(0, 120);
    }

    private Map<String, Object> fallbackRelevantClawHubSkill(String requirement, List<Map<String, Object>> hubSkills) {
        List<String> kws = this.extractSkillKeywords(requirement);
        if (kws.isEmpty()) {
            // 无可用关键词时不盲目安装第一条，交给上层降级 LLM 生成
            return null;
        }
        Map<String, Object> best = null;
        int bestScore = 0;
        for (Map<String, Object> s : hubSkills) {
            String hay = String.valueOf(s.getOrDefault("name", "")) + " " + String.valueOf(s.getOrDefault("summary", "")) + " " + String.valueOf(s.getOrDefault("description", "")) + " " + String.valueOf(s.getOrDefault("topics", "")).toLowerCase();
            int score = 0;
            for (String kw : kws) {
                if (kw.length() < 2 || !hay.toLowerCase().contains(kw.toLowerCase())) continue;
                score += kw.length() >= 3 ? 3 : 2;
            }
            if (score <= bestScore) continue;
            bestScore = score;
            best = s;
        }
        log.info("[ClawHub] 兜底关键词={}, bestScore={}, best={}", kws, bestScore, best == null ? "null" : best.getOrDefault("name", ""));
        return bestScore > 0 ? best : null;
    }

    private List<String> extractSkillKeywords(String requirement) {
        ArrayList<String> kws = new ArrayList<String>();
        if (requirement == null || requirement.isBlank()) {
            return kws;
        }
        String[] skip = new String[]{"\u83b7\u53d6", "\u5e2e\u6211", "\u7ed9\u6211", "\u9700\u8981", "\u4e00\u4e2a", "\u60f3\u8981", "\u65b0\u589e", "\u589e\u52a0", "\u505a\u4e00\u4e2a", "\u521b\u5efa", "\u751f\u6210", "\u8981", "\u7684", "\u548c", "\u53ca", "\u4ee5\u53ca", "\u652f\u6301", "\u529f\u80fd",
                "\u4e0b\u8f7d", "\u5b89\u88c5", "\u88c5\u597d", "\u88c5\u4e00\u4e2a", "\u627e\u4e00\u4e2a", "\u67e5\u627e", "\u641c\u7d22", "\u641c\u4e00\u4e0b", "\u4e3b\u52a8", "\u76f4\u63a5", "\u5e2e\u5fd9", "\u627e\u4e00\u4e0b", "\u6280\u80fd", "skill"};
        String clean = requirement;
        for (String s : skip) {
            clean = clean.replace(s, "");
        }
        Matcher me = Pattern.compile("[a-zA-Z]{3,}").matcher(clean);
        while (me.find()) {
            kws.add(me.group());
        }
        Matcher mh = Pattern.compile("[\\u4e00-\\u9fa5]{2,8}").matcher(clean);
        while (mh.find()) {
            String seg = mh.group();
            if (seg.length() > 4) {
                kws.add(seg.substring(0, 4));
                kws.add(seg.substring(seg.length() - 2));
                continue;
            }
            kws.add(seg);
        }
        LinkedHashSet<String> unique = new LinkedHashSet<String>(kws);
        return new ArrayList<String>(unique);
    }

    private int extractJsonIndex(String raw) {
        if (raw == null) {
            return 0;
        }
        try {
            int s = raw.indexOf(123);
            int e = raw.lastIndexOf(125);
            if (s >= 0 && e > s) {
                JsonNode n = new ObjectMapper().readTree(raw.substring(s, e + 1));
                return n.path("index").asInt(0);
            }
            Matcher m = Pattern.compile("\\d+").matcher(raw);
            if (m.find()) {
                return Integer.parseInt(m.group());
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return 0;
    }

    private String extractUrl(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = Pattern.compile("https?://[^\\s\uff0c\u3002\uff1b\u3001\"']+").matcher(text);
        return m.find() ? m.group() : null;
    }

    private String buildDownloadedReply(String type, Object obj) {
        String name = "-";
        if (obj instanceof Skill) {
            name = ((Skill)obj).getName();
        } else if (obj instanceof McpServer) {
            name = ((McpServer)obj).getName();
        }
        return "\u2705 \u5df2\u4e3b\u52a8\u4e0b\u8f7d\u5e76\u65b0\u589e " + type + "\u300c" + name + "\u300d\u5230\u7cfb\u7edf\uff01\n\u53ef\u901a\u8fc7\u300c" + ("Skill".equals(type) ? "\ud83d\udee0\ufe0f Skill \u6280\u80fd" : "\ud83d\udd0c MCP \u670d\u52a1") + "\u300d\u9875\u9762\u67e5\u770b\u548c\u7ba1\u7406\u3002";
    }

    private String buildSkillReply(Map<String, Object> draft) {
        StringBuilder sb = new StringBuilder();
        sb.append("\ud83e\udd16 \u6211\u5df2\u4e3a\u4f60\u81ea\u52a8\u641c\u96c6\u5e76\u7f16\u5199\u4e86\u4e00\u4e2a Skill \u6280\u80fd\u8349\u7a3f\uff1a\n\n");
        sb.append("\ud83d\udccc \u540d\u79f0\uff1a").append(draft.get("name")).append("\n");
        sb.append("\ud83d\uddc2\ufe0f \u5206\u7c7b\uff1a").append(draft.get("category")).append("\n");
        sb.append("\ud83d\udcdd \u8bf4\u660e\uff1a").append(draft.get("description")).append("\n");
        sb.append("\ud83d\udcc4 \u5185\u5bb9\uff1a").append(draft.get("content")).append("\n");
        if (Boolean.TRUE.equals(draft.get("webSearched"))) {
            sb.append("\n\ud83d\udd0e \u5df2\u8054\u7f51\u641c\u96c6\u76f8\u5173\u8d44\u6599\u8f85\u52a9\u751f\u6210\u3002");
        }
        if (Boolean.TRUE.equals(draft.get("scanSafe"))) {
            sb.append("\n\u2705 \u5df2\u901a\u8fc7\u5b89\u5168\u626b\u63cf\uff0c\u53ef\u5b89\u5168\u5bfc\u5165\u3002");
        } else {
            sb.append("\n\u26a0\ufe0f \u68c0\u6d4b\u5230\u98ce\u9669\u5185\u5bb9\uff0c\u5efa\u8bae\u4e0d\u8981\u5bfc\u5165\u3002");
        }
        sb.append("\n\n\ud83d\udc47 \u70b9\u51fb\u4e0b\u65b9\u300c\u2705 \u4e00\u952e\u5bfc\u5165\u8fd9\u4e2a\u6280\u80fd\u300d\u6309\u94ae\u5373\u53ef\u6dfb\u52a0\u5230\u7cfb\u7edf\uff08\u5c1a\u672a\u5bfc\u5165\uff0c\u9700\u786e\u8ba4\u540e\u624d\u4f1a\u521b\u5efa\uff09\u3002");
        return sb.toString();
    }

    private String buildMcpReply(Map<String, Object> draft) {
        String reach;
        StringBuilder sb = new StringBuilder();
        sb.append("\ud83e\udd16 \u6211\u5df2\u4e3a\u4f60\u81ea\u52a8\u641c\u96c6\u5e76\u7f16\u5199\u4e86\u4e00\u4e2a MCP \u670d\u52a1\u8349\u7a3f\uff1a\n\n");
        sb.append("\ud83d\udccc \u540d\u79f0\uff1a").append(draft.get("name")).append("\n");
        sb.append("\ud83d\udd0c \u7c7b\u578b\uff1a").append(draft.get("serverType")).append("\n");
        sb.append("\ud83d\udd17 \u5730\u5740\uff1a").append(draft.get("endpoint")).append("\n");
        sb.append("\ud83d\udcdd \u8bf4\u660e\uff1a").append(draft.get("description")).append("\n");
        if (Boolean.TRUE.equals(draft.get("webSearched"))) {
            sb.append("\n\ud83d\udd0e \u5df2\u8054\u7f51\u641c\u96c6\u76f8\u5173\u8d44\u6599\u8f85\u52a9\u751f\u6210\u3002");
        }
        String string = reach = draft.get("endpointReachable") == null ? "" : String.valueOf(draft.get("endpointReachable"));
        if ("reachable".equals(reach)) {
            sb.append("\n\u2705 \u5df2\u81ea\u52a8\u6d4b\u8bd5\u8fde\u901a\u6027\uff1a\u670d\u52a1\u5730\u5740\u53ef\u8fbe\u3002");
        } else if ("unreachable".equals(reach)) {
            sb.append("\n\u26a0\ufe0f \u5df2\u81ea\u52a8\u6d4b\u8bd5\u8fde\u901a\u6027\uff1a**\u670d\u52a1\u5730\u5740\u4e0d\u53ef\u8fbe**\uff08\u53ef\u80fd\u4e3a\u865a\u6784\u6216\u672a\u90e8\u7f72\uff09\uff0c\u5bfc\u5165\u524d\u8bf7\u52a1\u5fc5\u786e\u8ba4\u670d\u52a1\u771f\u5b9e\u5b58\u5728\uff01");
        } else if ("unknown".equals(reach)) {
            sb.append("\n\u26a0\ufe0f \u8be5\u5730\u5740\u4e3a\u5efa\u8bae\u503c\uff08stdio \u672c\u5730\u547d\u4ee4\u65e0\u6cd5\u8fdc\u7a0b\u9a8c\u8bc1\uff09\uff0c\u5bfc\u5165\u524d\u8bf7\u786e\u8ba4\u670d\u52a1\u771f\u5b9e\u53ef\u7528\u3002");
        }
        if (Boolean.TRUE.equals(draft.get("scanSafe"))) {
            sb.append("\n\u2705 \u5df2\u901a\u8fc7\u5b89\u5168\u626b\u63cf\uff0c\u53ef\u5b89\u5168\u5bfc\u5165\u3002");
        } else {
            sb.append("\n\u26a0\ufe0f \u68c0\u6d4b\u5230\u98ce\u9669\u5185\u5bb9\uff0c\u5efa\u8bae\u4e0d\u8981\u5bfc\u5165\u3002");
        }
        sb.append("\n\n\ud83d\udc47 \u70b9\u51fb\u4e0b\u65b9\u300c\u2705 \u4e00\u952e\u5bfc\u5165\u8fd9\u4e2a MCP\u300d\u6309\u94ae\u5373\u53ef\u6dfb\u52a0\u5230\u7cfb\u7edf\uff08\u5c1a\u672a\u5bfc\u5165\uff0c\u9700\u786e\u8ba4\u540e\u624d\u4f1a\u521b\u5efa\uff09\u3002");
        return sb.toString();
    }

    private VerifiedReply selfVerify(String userMessage, String reply, String toolContext) {
        VerifiedReply res = new VerifiedReply();
        res.ok = true;
        res.issue = "";
        res.revised = null;
        if (reply == null || reply.isBlank()) {
            return res;
        }
        try {
            String revised;
            String toolSection = (toolContext == null || toolContext.isBlank()) ? "" : "\n\n【工具真实返回（权威依据，AI 回答应与其一致）】" + this.truncate(toolContext, 3000);
            String prompt = "你是质量审查员。请评估 AI 助手对用户问题的回答是否切题合理。\n\n【用户当前问题】" + userMessage + "\n\n【AI 助手回答】" + this.truncate(reply, 2000) + toolSection + "\n\n评估要点：\n1. 回答是否直接回应用户当前问题？（避免答非所问）\n2. 是否混淆了历史对话与当前问题？\n3. 是否包含明显错误或编造的信息？（注意：若上面提供了工具真实返回，凡与工具返回一致的数据都属于事实，不得判定为编造，也不得改写成「无法获取」）\n4. 表达是否清晰自然？\n\n严格输出 JSON：{ok:true 或 false, issue:问题描述, revised:ok=false 时提供修正后的完整回答}";
            String raw = this.aiService.chat("\u4f60\u662f\u4e25\u683c\u8d28\u91cf\u5ba1\u67e5\u5458\uff0c\u53ea\u8f93\u51fa JSON\u3002", prompt);
            int start = raw.indexOf(123);
            int end = raw.lastIndexOf(125);
            if (start < 0 || end <= start) {
                return res;
            }
            String json = raw.substring(start, end + 1);
            JsonNode root = new ObjectMapper().readTree(json);
            res.ok = root.path("ok").asBoolean(true);
            res.issue = root.path("issue").asText("");
            if (!(res.ok || (revised = root.path("revised").asText("")).isBlank() || this.isMetaCognition(revised))) {
                res.revised = revised;
            }
            if (!res.ok && res.revised != null && toolContext != null && !toolContext.isBlank()) {
                boolean refusal = res.revised.contains("\u65e0\u6cd5\u83b7\u53d6") || res.revised.contains("\u65e0\u6cd5\u63d0\u4f9b") || res.revised.contains("\u65e0\u6cd5\u786e\u8ba4") || res.revised.contains("\u4e0d\u80fd\u51c6\u786e") || res.revised.contains("\u6ca1\u6709\u83b7\u53d6\u5230");
                boolean replyRefusal = reply.contains("\u65e0\u6cd5\u83b7\u53d6") || reply.contains("\u65e0\u6cd5\u63d0\u4f9b") || reply.contains("\u65e0\u6cd5\u786e\u8ba4") || reply.contains("\u4e0d\u80fd\u51c6\u786e") || reply.contains("\u6ca1\u6709\u83b7\u53d6\u5230");
                if (refusal && !replyRefusal) {
                    res.ok = true;
                    res.revised = null;
                    res.issue = "";
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return res;
    }

    private boolean isMetaCognition(String text) {
        String[] metaHints;
        String t = text.trim();
        if (t.length() > 400) {
            t = t.substring(0, 400);
        }
        for (String h : metaHints = new String[]{"\u6211\u4eec\u9700\u8981\u7406\u89e3", "\u7528\u6237\u95ee\uff1a", "\u6839\u636e\u7cfb\u7edf\u6307\u793a", "\u6839\u636e\u7cfb\u7edf\u6307\u4ee4", "\u76f4\u63a5\u56de\u7b54", "\u76f4\u63a5\u7528\u4e2d\u6587", "\u6211\u5e94\u8be5", "\u6211\u4eec\u8981\u5148", "\u6211\u7684\u601d\u8003\u662f", "\u9996\u5148\u7406\u89e3", "\u8fd9\u4e2a\u95ee\u9898\u7684\u6838\u5fc3\u662f", "\u7528\u6237\u7684\u610f\u601d\u662f", "\u4f5c\u4e3aAI\u52a9\u624b\uff0c\u6211\u5148"}) {
            if (!t.contains(h)) continue;
            return true;
        }
        return false;
    }

    private String truncate(String s, int len) {
        if (s == null) {
            return "";
        }
        return s.length() <= len ? s : s.substring(0, len);
    }

    private void monitorLog(String level, String message) {
        try {
            this.monitorService.log("assistant", level, message == null ? "" : message);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private String ensureChartDiv(String reply, ToolScheduler.ToolResult toolResult) {
        if (reply == null || toolResult == null) {
            return reply;
        }
        if (reply.contains("<div class=\"chart\"") || reply.contains("<div class='chart'")) {
            return reply;
        }
        String builtinResult = toolResult.builtinResult;
        if (builtinResult == null || builtinResult.isBlank()) {
            return reply;
        }
        int s = builtinResult.indexOf("[CHART_JSON]");
        int e = builtinResult.indexOf("[/CHART_JSON]");
        if (s < 0 || e <= s) {
            return reply;
        }
        String chartJson = builtinResult.substring(s + "[CHART_JSON]".length(), e).trim();
        if (chartJson.isEmpty()) {
            return reply;
        }
        String safeJson = chartJson.replace("'", "&#39;");
        return reply + "\n<div class=\"chart\" data-option='" + safeJson + "'></div>";
    }

    private String fileOpLabel(String op) {
        switch (op == null ? "" : op) {
            case "write_file": {
                return "\u5199\u5165 / \u8986\u76d6\u6587\u4ef6\uff08\u6216\u65b0\u589e\u5de5\u5177\u811a\u672c\uff09";
            }
            case "delete_file": {
                return "\u5220\u9664\u6587\u4ef6 / \u76ee\u5f55";
            }
            case "run_script": {
                return "\u6267\u884c\u9879\u76ee\u5185\u811a\u672c";
            }
            case "mkdir": {
                return "\u521b\u5efa\u76ee\u5f55";
            }
        }
        return op == null ? "\u6587\u4ef6\u64cd\u4f5c" : op;
    }

    private void rememberIfUseful(String userMsg, String reply) {
        if (userMsg == null || userMsg.isBlank()) {
            return;
        }
        if (userMsg.contains("\u8bb0\u4f4f") || userMsg.contains("\u8bb0\u4e0b") || userMsg.contains("\u5907\u5fd8")) {
            String content = reply;
            if (content.length() > 500) {
                content = content.substring(0, 500);
            }
            this.memoryService.remember("auto_" + System.currentTimeMillis() % 100000L, content, 6, "\u5bf9\u8bdd\u5907\u5fd8");
        }
    }

    public String streamChat(String userMessage, String session, Consumer<String> onEvent, Consumer<String> onToken) {
        String reply;
        this.monitorLog("INFO", "\u6536\u5230\u7528\u6237\u6d88\u606f\uff1a<" + this.truncate(userMessage, 200) + ">");
        // 多 Agent 协同调度优先
        ToolScheduler.ToolResult multiResp = this.toolScheduler.tryMultiAgentDispatch(userMessage);
        if (multiResp != null) {
            String multiReply = multiResp.builtinResult;
            if (onToken != null) {
                for (int i = 0; i < multiReply.length(); i += 20) {
                    onToken.accept(multiReply.substring(i, Math.min(i + 20, multiReply.length())));
                }
            }
            if (onEvent != null) {
                onEvent.accept("multiAgent:true");
                onEvent.accept("done:" + multiReply);
            }
            return multiReply;
        }
        // 音视频搜索强意图直调：带歌名/关键词的播放搜索优先于技能匹配
        Map<String, Object> toolResp = this.handleToolIntent(userMessage);
        if (toolResp != null) {
            String draftJson2;
            String reply2 = (String)toolResp.getOrDefault("reply", "");
            if (Boolean.TRUE.equals(toolResp.get("needsConfirm")) && onEvent != null) {
                String op = String.valueOf(toolResp.getOrDefault("confirmOp", ""));
                String path = String.valueOf(toolResp.getOrDefault("confirmPath", ""));
                String content = String.valueOf(toolResp.getOrDefault("confirmContent", ""));
                onEvent.accept("confirm:" + op + "|" + path + "|" + content);
            }
            if (toolResp.get("draftSkill") != null && onEvent != null) {
                try {
                    draftJson2 = new ObjectMapper().writeValueAsString(toolResp.get("draftSkill"));
                    onEvent.accept("skill_draft:" + draftJson2);
                } catch (Exception ignored) {
                    // empty catch block
                }
            }
            if (toolResp.get("draftMcp") != null && onEvent != null) {
                try {
                    draftJson2 = new ObjectMapper().writeValueAsString(toolResp.get("draftMcp"));
                    onEvent.accept("mcp_draft:" + draftJson2);
                } catch (Exception ignored) {
                    // empty catch block
                }
            }
            if (onEvent != null) {
                onEvent.accept("done:" + reply2);
            }
            return reply2;
        }
        Map<String, Object> reportResp = this.handleReportIntent(userMessage);
        if (reportResp != null) {
            String reply3 = (String)reportResp.getOrDefault("reply", "");
            if (onEvent != null) {
                onEvent.accept("done:" + reply3);
            }
            return reply3;
        }
        Map<String, Object> kbReadResp = this.handleKnowledgeRead(userMessage);
        if (kbReadResp != null) {
            String reply4 = (String)kbReadResp.getOrDefault("reply", "");
            if (onEvent != null) {
                onEvent.accept("done:" + reply4);
            }
            return reply4;
        }
        String kbContext = this.retrieveKnowledge(userMessage);
        String webContext = this.retrieveWeb(userMessage);
        ToolScheduler.ToolResult toolResult = this.toolScheduler.runAgentLoop(userMessage);
        if (toolResult.invoked && toolResult.builtinResult != null && !toolResult.builtinResult.isBlank()) {
            this.monitorLog("INFO", "\u8c03\u7528\u5de5\u5177\uff1a\u5185\u7f6e\u300c" + toolResult.builtinName + "\u300d\uff0c\u8fd4\u56de\uff1a" + this.truncate(toolResult.builtinResult, 200));
        }
        if (toolResult.needsConfirm) {
            String confirmData;
            String replyText;
            if (toolResult.pendingOp != null) {
                replyText = "\u26a0\ufe0f \u52a9\u624b\u9700\u8981\u6267\u884c**\u6587\u4ef6\u64cd\u4f5c**\uff1a\n\n\ud83d\udcc1 \u64cd\u4f5c\u7c7b\u578b\uff1a" + this.fileOpLabel(toolResult.pendingOp) + "\n\ud83d\udccd \u76ee\u6807\u8def\u5f84\uff1a" + (toolResult.pendingPath == null || toolResult.pendingPath.isBlank() ? "(\u9879\u76ee\u6839\u76ee\u5f55)" : toolResult.pendingPath) + "\n\u26a0\ufe0f \u539f\u56e0\uff1a" + toolResult.pendingReason + "\n\n\u8be5\u64cd\u4f5c\u4f1a\u4fee\u6539\u9879\u76ee\u5e93\u6587\u4ef6\u5939\uff0c\u4e3a\u5b89\u5168\u8d77\u89c1\uff0c\u9700\u8981\u60a8\u786e\u8ba4\u540e\u624d\u6267\u884c\u3002";
                confirmData = toolResult.pendingOp + "|" + (toolResult.pendingPath == null ? "" : toolResult.pendingPath) + "|" + (toolResult.pendingContent == null ? "" : toolResult.pendingContent);
            } else {
                replyText = "\u26a0\ufe0f \u52a9\u624b\u9700\u8981\u8c03\u7528 MCP \u5de5\u5177\u300c" + toolResult.mcpName + "\u300d\u6267\u884c\u64cd\u4f5c\u3002\n\n\u68c0\u6d4b\u5230\u8be5\u64cd\u4f5c\u6d89\u53ca**\u9ad8\u5371\u52a8\u4f5c**\uff1a" + toolResult.pendingReason + "\n\ud83d\udce1 \u76ee\u6807\uff1a" + toolResult.pendingEndpoint + "\n\n\u4e3a\u5b89\u5168\u8d77\u89c1\uff0c\u9700\u8981\u60a8\u786e\u8ba4\u540e\u624d\u6267\u884c\u3002";
                confirmData = "mcp|" + toolResult.pendingEndpoint + "|" + toolResult.pendingArgs;
            }
            if (onEvent != null) {
                onEvent.accept("confirm:" + confirmData);
            }
            if (onEvent != null) {
                onEvent.accept("done:" + replyText);
            }
            return replyText;
        }
        StringBuilder toolContext = new StringBuilder();
        if (toolResult.invoked) {
            toolContext.append("\n【工具执行结果说明】以下内容是工具真实返回；若结果为空或包含失败、不可用、未找到等提示，请如实说明原因，不要编造数据。\n");
            if (toolResult.usedMcp && toolResult.mcpResult != null) {
                toolContext.append("\n【MCP 工具「").append(toolResult.mcpName).append("」调用结果】\n").append(toolResult.mcpResult).append("\n");
            }
            if (toolResult.usedBuiltin && toolResult.builtinResult != null) {
                toolContext.append("\n【内置工具「").append(toolResult.builtinName).append("」返回】\n").append(toolResult.builtinResult).append("\n");
            }
            if (toolResult.usedSkill && toolResult.skillPrompt != null) {
                toolContext.append("\n").append(toolResult.skillPrompt).append("\n");
            }
        }
        String systemPrompt = this.buildSystemPrompt(kbContext, webContext) + toolContext.toString();
        List<Map.Entry<String, String>> history = this.buildHistoryContext(session);
        StringBuilder full = new StringBuilder();
        reply = this.aiService.streamChatWithHistory(systemPrompt, history, userMessage, chunk -> {
            full.append((String)chunk);
            if (onToken != null) {
                onToken.accept((String)chunk);
            }
        });
        try {
            VerifiedReply verified = this.selfVerify(userMessage, reply, toolContext.toString());
            if (!verified.ok && verified.revised != null && !verified.revised.isBlank()) {
                reply = verified.revised;
            }
        }
        catch (Exception verified) {
            // empty catch block
        }
        this.rememberIfUseful(userMessage, reply);
        if (onEvent != null && reply != null && reply.length() > 250) {
            try {
                String cleanReply = reply.replaceAll("\\[CHART_JSON\\].*?\\[/CHART_JSON\\]", "").replaceAll("<div class=\"?chart\"?[^>]*></div>", "").replaceAll("\u3010\u5185\u7f6e\u5de5\u5177.*?\u8fd4\u56de\u3011", "").trim();
                String summary = this.aiService.chat("\u4f60\u662f\u6458\u8981\u52a9\u624b\uff0c\u53ea\u8f93\u51fa\u603b\u7ed3\u672c\u8eab\u3002", "\u4ee5\u4e0b\u662f\u4e00\u6bb5\u5df2\u5b8c\u6210\u7684\u52a9\u624b\u56de\u590d\uff0c\u8bf7\u7528 500-10000 \u5b57\u6982\u62ec\u5b83\u7684\u6838\u5fc3\u5185\u5bb9\u8981\u70b9\uff08\u8fd9\u662f\u7ed9\u7528\u6237\u770b\u7684\u8be6\u7ec6\u603b\u7ed3\uff0c\u5206\u70b9\u7528\u3001\u5206\u9694\uff0c\u5c3d\u91cf\u5b8c\u6574\u8986\u76d6\u8981\u70b9\uff0c\u4e0d\u8981\u590d\u8ff0\u539f\u6587\uff0c\u4e0d\u8981\u63d0\u53ca\u5de5\u5177\uff0c\u4e0d\u8981\u8f93\u51fa'\u65e0\u6cd5\u63d0\u4f9b'\uff09\uff1a\n\n" + this.truncate(cleanReply.isEmpty() ? reply : cleanReply, 12000));
                if (summary != null && !summary.isBlank()) {
                    Object sm0 = summary.trim().replaceAll("[\\r\\n]+", "\u3001");
                    if (((String)sm0).length() > 10000) {
                        int cut = 10000;
                        int p = -1;
                        for (int i = 0; i < cut && i < ((String)sm0).length(); ++i) {
                            char c = ((String)sm0).charAt(i);
                            if (c != '\u3002' && c != '\uff01' && c != '\uff1f' && c != ';' && c != '\u3001') continue;
                            p = i;
                        }
                        sm0 = p > 4000 ? ((String)sm0).substring(0, p + 1) : ((String)sm0).substring(0, 10000) + "\u2026";
                    }
                    HashMap<String, Object> sm = new HashMap<String, Object>();
                    sm.put("summary", sm0);
                    sm.put("length", reply.length());
                    onEvent.accept("summary:" + new ObjectMapper().writeValueAsString(sm));
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        reply = this.ensureChartDiv(reply, toolResult);
        this.monitorLog("INFO", "\u52a9\u624b\u56de\u590d\u5b8c\u6210\uff1a<" + this.truncate(reply, 300) + ">");
        if (onEvent != null) {
            onEvent.accept("done:" + reply);
        }
        return reply;
    }

    static class VerifiedReply {
        boolean ok;
        String issue;
        String revised;

        VerifiedReply() {
        }
    }
}

