/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.controller;

import com.badmelon.farming.entity.AiModelConfig;
import com.badmelon.farming.service.AiModelService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/models"})
public class AiModelController {
    private final AiModelService aiModelService;

    public AiModelController(AiModelService aiModelService) {
        this.aiModelService = aiModelService;
    }

    @GetMapping
    public List<AiModelConfig> list() {
        return this.aiModelService.findAll();
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody AiModelConfig model) {
        AiModelConfig saved = this.aiModelService.save(model);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        resp.put("model", saved);
        return resp;
    }

    @PostMapping(value={"/{id}/activate"})
    public Map<String, Object> activate(@PathVariable Long id) {
        AiModelConfig model = this.aiModelService.activate(id);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", model != null);
        resp.put("model", model);
        return resp;
    }

    @DeleteMapping(value={"/{id}"})
    public Map<String, Object> delete(@PathVariable Long id) {
        this.aiModelService.delete(id);
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("success", true);
        return resp;
    }
}

