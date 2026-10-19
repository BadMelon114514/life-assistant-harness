/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.Setting;
import com.badmelon.farming.mapper.SettingMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

@Service
public class SearchService {
    private static final int DEFAULT_TIMEOUT = 15000;
    private static final int DEFAULT_RESULT = 6;
    private final SettingMapper settingMapper;

    public SearchService(SettingMapper settingMapper) {
        this.settingMapper = settingMapper;
    }

    public boolean isEnabled() {
        String v = this.getSetting("search.enabled");
        return v == null || "1".equals(v);
    }

    public String getProvider() {
        String p = this.getSetting("search.provider");
        return p == null || p.isBlank() ? "duckduckgo" : p;
    }

    public List<Map<String, String>> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (!this.isEnabled()) {
            throw new IllegalStateException("\u8054\u7f51\u641c\u7d22\u5df2\u505c\u7528\uff0c\u8bf7\u5728\u8bbe\u7f6e\u4e2d\u542f\u7528");
        }
        try {
            if ("bing".equalsIgnoreCase(this.getProvider())) {
                return this.searchBing(query);
            }
            return this.searchDuckDuckGo(query);
        }
        catch (Exception e) {
            throw new RuntimeException("\u8054\u7f51\u641c\u7d22\u5931\u8d25: " + e.getMessage(), e);
        }
    }

    private List<Map<String, String>> searchDuckDuckGo(String query) throws Exception {
        String url = "https://html.duckduckgo.com/html/?q=" + this.encode(query);
        Document doc = Jsoup.connect((String)url).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36").timeout(15000).get();
        Elements results = doc.select(".result");
        ArrayList<Map<String, String>> list = new ArrayList<Map<String, String>>();
        int count = 0;
        for (Element r : results) {
            if (count >= 6) break;
            Element link = r.selectFirst(".result__a");
            Element snip = r.selectFirst(".result__snippet");
            if (link == null) continue;
            LinkedHashMap<String, String> item = new LinkedHashMap<String, String>();
            item.put("title", link.text());
            item.put("url", link.attr("href"));
            item.put("snippet", snip != null ? snip.text() : "");
            list.add(item);
            ++count;
        }
        return list;
    }

    private List<Map<String, String>> searchBing(String query) throws Exception {
        String url = "https://www.bing.com/search?q=" + this.encode(query) + "&count=6";
        Document doc = Jsoup.connect((String)url).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36").timeout(15000).get();
        Elements results = doc.select("li.b_algo");
        ArrayList<Map<String, String>> list = new ArrayList<Map<String, String>>();
        int count = 0;
        for (Element r : results) {
            if (count >= 6) break;
            Element link = r.selectFirst("h2 a");
            if (link == null) continue;
            Element snip = r.selectFirst(".b_caption p, .b_lineclamp2, .b_lineclamp3");
            LinkedHashMap<String, String> item = new LinkedHashMap<String, String>();
            item.put("title", link.text());
            item.put("url", link.absUrl("href"));
            item.put("snippet", snip != null ? snip.text() : "");
            list.add(item);
            ++count;
        }
        return list;
    }

    public String formatResults(List<Map<String, String>> results) {
        if (results == null || results.isEmpty()) {
            return "\u3010\u8054\u7f51\u641c\u7d22\u7ed3\u679c\u3011\u672a\u627e\u5230\u76f8\u5173\u5185\u5bb9\u3002\n";
        }
        StringBuilder sb = new StringBuilder("\u3010\u8054\u7f51\u641c\u7d22\u7ed3\u679c\u3011\n");
        int i = 1;
        for (Map<String, String> r : results) {
            sb.append(i++).append(". ").append(r.get("title")).append("\n");
            if (r.get("snippet") != null && !r.get("snippet").isBlank()) {
                sb.append("   ").append(r.get("snippet")).append("\n");
            }
            if (r.get("url") == null || r.get("url").isBlank()) continue;
            sb.append("   \u6765\u6e90: ").append(r.get("url")).append("\n");
        }
        return sb.toString();
    }

    private String getSetting(String key) {
        Setting s = this.settingMapper.findByKey(key);
        return s != null ? s.getSettingValue() : null;
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}

