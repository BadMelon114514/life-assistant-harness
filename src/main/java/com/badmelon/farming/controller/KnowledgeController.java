/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.controller;

import com.badmelon.farming.entity.KnowledgeDoc;
import com.badmelon.farming.service.KnowledgeService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(value={"/api/knowledge"})
public class KnowledgeController {
    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping
    public List<KnowledgeDoc> list(@RequestParam(required=false) String keyword) {
        return this.knowledgeService.search(keyword);
    }

    @GetMapping(value={"/{key}"})
    public ResponseEntity<KnowledgeDoc> get(@PathVariable String key) {
        KnowledgeDoc doc = this.knowledgeService.findByKey(key);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(doc);
    }

    @PostMapping(value={"/upload"})
    public ResponseEntity<Map<String, Object>> upload(@RequestParam(value="file") MultipartFile file, @RequestParam(required=false) String tags) {
        try {
            KnowledgeDoc doc = this.knowledgeService.upload(file, tags);
            HashMap<String, Object> resp = new HashMap<String, Object>();
            resp.put("success", true);
            resp.put("doc", doc);
            return ResponseEntity.ok(resp);
        }
        catch (Exception e) {
            HashMap<String, Object> resp = new HashMap<String, Object>();
            resp.put("success", false);
            resp.put("error", e.getMessage());
            return ResponseEntity.status((int)500).body(resp);
        }
    }

    @PostMapping(value={"/save-text"})
    public ResponseEntity<Map<String, Object>> saveText(@RequestBody Map<String, String> body) {
        String fileName = body.get("fileName");
        String content = body.get("content");
        String tags = body.get("tags");
        try {
            KnowledgeDoc doc = this.knowledgeService.saveText(fileName, content, tags);
            HashMap<String, Object> resp = new HashMap<String, Object>();
            resp.put("success", true);
            resp.put("doc", doc);
            return ResponseEntity.ok(resp);
        }
        catch (Exception e) {
            HashMap<String, Object> resp = new HashMap<String, Object>();
            resp.put("success", false);
            resp.put("error", e.getMessage());
            return ResponseEntity.status((int)500).body(resp);
        }
    }

    @DeleteMapping(value={"/{key}"})
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String key) {
        try {
            this.knowledgeService.delete(key);
            HashMap<String, Object> resp = new HashMap<String, Object>();
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        }
        catch (Exception e) {
            HashMap<String, Object> resp = new HashMap<String, Object>();
            resp.put("success", false);
            resp.put("error", e.getMessage());
            return ResponseEntity.status((int)500).body(resp);
        }
    }
}

