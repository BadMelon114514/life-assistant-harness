/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.KnowledgeDoc;
import com.badmelon.farming.service.AiService;
import com.badmelon.farming.service.KnowledgeService;
import com.badmelon.farming.service.SearchService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    private final AiService aiService;
    private final SearchService searchService;
    private final KnowledgeService knowledgeService;

    public ReportService(AiService aiService, SearchService searchService, KnowledgeService knowledgeService) {
        this.aiService = aiService;
        this.searchService = searchService;
        this.knowledgeService = knowledgeService;
    }

    public Map<String, Object> generateAndSave(String industry, String period) {
        KnowledgeDoc doc;
        LocalDateTime now = LocalDateTime.now();
        String nowStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String nowYm = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String webInfo = "";
        try {
            if (this.searchService.isEnabled()) {
                List<Map<String, String>> results = this.searchService.search(industry + " \u884c\u4e1a \u8d44\u8baf " + period);
                webInfo = this.searchService.formatResults(results);
            }
        }
        catch (Exception e) {
            webInfo = "";
        }
        String lastMonth = now.minusMonths(1L).format(DateTimeFormatter.ofPattern("yyyy\u5e74M\u6708"));
        String curMonth = now.format(DateTimeFormatter.ofPattern("yyyy\u5e74M\u6708"));
        String prompt = this.buildReportPrompt(industry, period, nowStr, nowYm, lastMonth, curMonth, webInfo);
        String report = this.aiService.chat("\u4f60\u662f\u8d44\u6df1\u884c\u4e1a\u5206\u6790\u5e08\uff0c\u8bf7\u57fa\u4e8e\u5f53\u524d\u65f6\u95f4\u4e0e\u641c\u96c6\u5230\u7684\u8d44\u8baf\uff0c\u8f93\u51fa\u4e00\u4efd\u7ed3\u6784\u6e05\u6670\u3001\u6570\u636e\u8be6\u5b9e\u7684\u884c\u4e1a\u62a5\u544a\uff08Markdown \u683c\u5f0f\uff09\u3002", prompt);
        String fileName = nowYm + industry + "\u62a5\u544a.md";
        String tags = "\u884c\u4e1a\u62a5\u544a," + industry;
        try {
            doc = this.knowledgeService.saveText(fileName, report, tags);
        }
        catch (Exception e) {
            throw new RuntimeException("\u62a5\u544a\u4fdd\u5b58\u5230\u77e5\u8bc6\u5e93\u5931\u8d25\uff1a" + e.getMessage());
        }
        return Map.of("report", report, "fileName", fileName, "generatedAt", nowStr, "webSearched", !webInfo.isBlank(), "doc", doc);
    }

    private String buildReportPrompt(String industry, String period, String nowStr, String nowYm, String lastMonth, String curMonth, String webInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u8bf7\u4e3a\u300c").append(industry).append("\u300d\u884c\u4e1a\u751f\u6210\u4e00\u4efd ").append(period).append(" \u7684\u8be6\u7ec6\u884c\u4e1a\u5206\u6790\u62a5\u544a\u3002\n\n");
        sb.append("\u3010\u5f53\u524d\u771f\u5b9e\u65f6\u95f4\u3011").append(nowStr).append("\uff08\u5f53\u524d\u6708\u4efd\uff1a").append(nowYm).append("\uff09\n");
        sb.append("\u8bf7\u4e25\u683c\u4ee5\u5f53\u524d\u65f6\u95f4\u4f5c\u4e3a\u62a5\u544a\u751f\u6210\u65f6\u95f4\uff0c\u62a5\u544a\u5468\u671f\u4e3a ").append(lastMonth).append("\u2014").append(curMonth).append("\u3002\n\n");
        if (!webInfo.isBlank()) {
            sb.append("\u3010\u8054\u7f51\u641c\u96c6\u5230\u7684\u76f8\u5173\u8d44\u8baf\u3011\n").append(webInfo).append("\n\n");
            sb.append("\u8bf7\u4f18\u5148\u53c2\u8003\u4ee5\u4e0a\u771f\u5b9e\u8d44\u8baf\u8fdb\u884c\u6574\u7406\uff0c\u6570\u636e\u6807\u6ce8\u6765\u6e90\uff1b\u5bf9\u4e8e\u65e0\u6cd5\u786e\u8ba4\u7684\u63a8\u65ad\u5185\u5bb9\u8bf7\u660e\u786e\u8bf4\u660e\u3002\n");
        } else {
            sb.append("\u3010\u63d0\u793a\u3011\u8054\u7f51\u641c\u7d22\u672a\u8fd4\u56de\u7ed3\u679c\uff0c\u8bf7\u57fa\u4e8e\u884c\u4e1a\u5e38\u8bc6\u7ec4\u7ec7\u5185\u5bb9\uff0c\u5e76\u6807\u6ce8\u300c\u8be5\u90e8\u5206\u4e3a\u884c\u4e1a\u901a\u8bc6\uff0c\u9700\u4eba\u5de5\u6838\u5b9e\u300d\u3002\n");
        }
        sb.append("\n\u62a5\u544a\u9700\u5305\u542b\u4ee5\u4e0b\u7ae0\u8282\uff08Markdown \u683c\u5f0f\uff09\uff1a\n").append("# ").append(nowYm).append(" ").append(industry).append("\u884c\u4e1a\u62a5\u544a\n").append("> \u62a5\u544a\u5468\u671f\uff1a").append(period).append("\uff08").append(lastMonth).append("\u2014").append(curMonth).append("\uff09\n").append("> \u751f\u6210\u65f6\u95f4\uff1a").append(nowStr).append("\n").append("> \u6570\u636e\u6765\u6e90\uff1a\u884c\u4e1a\u516c\u5f00\u4fe1\u606f\u53ca\u7f51\u7edc\u8d44\u8baf\uff08\u9700\u6838\u5b9e\uff09\n\n").append("## \u4e00\u3001\u5e02\u573a\u6982\u8ff0\n").append("## \u4e8c\u3001\u4ef7\u683c/\u884c\u60c5\u8d70\u52bf\n").append("## \u4e09\u3001\u4f9b\u9700\u5206\u6790\n").append("## \u56db\u3001\u4e3b\u8981\u5382\u5546/\u73a9\u5bb6\u52a8\u6001\n").append("## \u4e94\u3001\u6280\u672f\u8fdb\u5c55\u4e0e\u65b0\u54c1\n").append("## \u516d\u3001\u6e20\u9053/\u5e02\u573a\u8868\u73b0\n").append("## \u4e03\u3001\u8fd0\u8425\u4e0e\u91c7\u8d2d\u5efa\u8bae\n").append("## \u516b\u3001\u98ce\u9669\u9884\u8b66\n");
        return sb.toString();
    }
}

