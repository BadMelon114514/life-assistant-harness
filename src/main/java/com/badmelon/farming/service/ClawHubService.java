/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ClawHubService {
    private static final Logger log = LoggerFactory.getLogger(ClawHubService.class);
    private static final String BASE_URL = "https://clawhub.ai";
    private static final Duration TIMEOUT = Duration.ofSeconds(15L);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public List<Map<String, Object>> search(String query) {
        ArrayList<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (query == null || query.isBlank()) {
            return result;
        }
        try {
            String url = "https://clawhub.ai/api/v1/skills?q=" + this.urlEncode(query);
            String body = this.get(url);
            JsonNode root = this.objectMapper.readTree(body);
            JsonNode items = root.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    LinkedHashMap<String, Object> skill = new LinkedHashMap<String, Object>();
                    skill.put("slug", item.path("slug").asText(""));
                    skill.put("name", this.titleOf(item.path("displayName").asText(item.path("slug").asText(""))));
                    skill.put("summary", item.path("summary").asText(""));
                    skill.put("description", this.truncate(item.path("description").asText(""), 300));
                    skill.put("version", item.path("latestVersion").path("version").asText(""));
                    if (item.path("topics").isTextual()) {
                        skill.put("topics", item.path("topics").asText(""));
                    } else if (item.path("topics").isArray()) {
                        skill.put("topics", item.path("topics").toString());
                    } else {
                        skill.put("topics", "");
                    }
                    result.add(skill);
                }
            }
        }
        catch (Exception exception) {
            log.warn("[ClawHub] 搜索失败 q={}, err={}", query, exception.getMessage());
        }
        log.info("[ClawHub] 搜索 q={}, 返回 {} 条", query, result.size());
        return result;
    }

    public String fetchSkillMd(String slug, String version) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        String[] urls = version != null && !version.isBlank() ? new String[]{"https://clawhub.ai/api/v1/skills/" + slug + "/" + version + "/SKILL.md", "https://clawhub.ai/api/v1/skills/" + slug + "/" + version + "/content", "https://clawhub.ai/api/v1/skills/" + slug + "/download?version=" + version} : new String[]{"https://clawhub.ai/api/v1/skills/" + slug + "/SKILL.md", "https://clawhub.ai/api/v1/skills/" + slug + "/latest", "https://clawhub.ai/api/v1/skills/" + slug + "/download"};
        for (String url : urls) {
            try {
                String content;
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(TIMEOUT).header("Accept", "text/plain, text/markdown, application/json").header("User-Agent", "Farming-Harness/1.0").GET().build();
                HttpResponse<String> resp = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() != 200 || (content = resp.body()) == null || !content.contains("name:") && !content.contains("description:") && !content.contains("# ")) continue;
                return content;
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        return null;
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(TIMEOUT).header("Accept", "application/json").header("User-Agent", "Farming-Harness/1.0").GET().build();
        HttpResponse<String> resp = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    /** 校验拉取的技能内容是否与该技能匹配（ClawHub 内容端点可能返回错位数据）。
     *  匹配规则：内容含 slug，或含技能名关键词（英文全名 / 中文前 4 字）。 */
    public boolean contentMatches(String content, String slug, String name) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String c = content.toLowerCase();
        if (slug != null && !slug.isBlank() && c.contains(slug.toLowerCase())) {
            return true;
        }
        if (name != null && !name.isBlank()) {
            String n = name.trim();
            if (n.matches("[a-zA-Z0-9 _\\-]+") && c.contains(n.toLowerCase())) {
                return true;
            }
            Matcher m = Pattern.compile("[\\u4e00-\\u9fa5]{2,}").matcher(n);
            if (m.find()) {
                String head = m.group();
                String key = head.length() >= 4 ? head.substring(0, 4) : head;
                if (c.contains(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 取 displayName 的主标题部分：按常见分隔符（— / - / :）截取第一段并限制长度，
     *  避免 ClawHub 返回的完整长标题被直接当作技能名 */
    private String titleOf(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }
        String t = displayName.trim();
        for (String sep : new String[]{" \u2014 ", " - ", ": ", "\uff1a"}) {
            int i = t.indexOf(sep);
            if (i > 0) {
                t = t.substring(0, i).trim();
                break;
            }
        }
        return t.length() <= 40 ? t : t.substring(0, 40);
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String truncate(String s, int len) {
        if (s == null) {
            return "";
        }
        return s.length() <= len ? s : s.substring(0, len);
    }
}

