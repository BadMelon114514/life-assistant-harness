/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.controller;

import com.badmelon.farming.service.SearchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/search"})
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public Map<String, Object> search(@RequestParam(value="q") String q) {
        HashMap<String, Object> resp = new HashMap<String, Object>();
        try {
            List<Map<String, String>> results = this.searchService.search(q);
            resp.put("success", true);
            resp.put("provider", this.searchService.getProvider());
            resp.put("results", results);
            resp.put("formatted", this.searchService.formatResults(results));
        }
        catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return resp;
    }

    @GetMapping(value={"/status"})
    public Map<String, Object> status() {
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("enabled", this.searchService.isEnabled());
        resp.put("provider", this.searchService.getProvider());
        return resp;
    }
}

