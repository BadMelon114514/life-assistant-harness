/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.AiModelConfig;
import com.badmelon.farming.mapper.AiModelMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiModelService {
    private final AiModelMapper aiModelMapper;

    public AiModelService(AiModelMapper aiModelMapper) {
        this.aiModelMapper = aiModelMapper;
    }

    public List<AiModelConfig> findAll() {
        return this.aiModelMapper.findAll();
    }

    public AiModelConfig getActive() {
        return this.aiModelMapper.findActive();
    }

    public AiModelConfig findById(Long id) {
        return this.aiModelMapper.findById(id);
    }

    public AiModelConfig save(AiModelConfig model) {
        AiModelConfig existing;
        AiModelConfig aiModelConfig = existing = model.getId() != null ? this.aiModelMapper.findById(model.getId()) : null;
        if (existing != null) {
            if (model.getActive() == null) {
                model.setActive(existing.getActive());
            }
            this.aiModelMapper.update(model);
            return model;
        }
        if (this.aiModelMapper.findAll().isEmpty()) {
            model.setActive(1);
        } else if (model.getActive() == null) {
            model.setActive(0);
        }
        this.aiModelMapper.insert(model);
        return model;
    }

    public AiModelConfig activate(Long id) {
        AiModelConfig model = this.aiModelMapper.findById(id);
        if (model == null) {
            return null;
        }
        this.aiModelMapper.clearActive();
        model.setActive(1);
        this.aiModelMapper.update(model);
        return model;
    }

    public void delete(Long id) {
        List<AiModelConfig> rest;
        AiModelConfig model = this.aiModelMapper.findById(id);
        this.aiModelMapper.deleteById(id);
        if (model != null && Integer.valueOf(1).equals(model.getActive()) && !(rest = this.aiModelMapper.findAll()).isEmpty()) {
            this.activate(rest.get(0).getId());
        }
    }
}

