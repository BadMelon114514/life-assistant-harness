/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.MemoryItem;
import com.badmelon.farming.mapper.MemoryMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MemoryService {
    private final MemoryMapper memoryMapper;

    public MemoryService(MemoryMapper memoryMapper) {
        this.memoryMapper = memoryMapper;
    }

    public List<MemoryItem> findAll() {
        return this.memoryMapper.findAll();
    }

    public MemoryItem remember(String key, String content, int importance, String category) {
        MemoryItem existing = this.memoryMapper.findByKey(key);
        if (existing != null) {
            existing.setContent(content);
            existing.setImportance(importance);
            existing.setCategory(category);
            this.memoryMapper.update(existing);
            return existing;
        }
        MemoryItem item = new MemoryItem();
        item.setMemoryKey(key);
        item.setContent(content);
        item.setImportance(importance);
        item.setCategory(category);
        this.memoryMapper.insert(item);
        return item;
    }

    public void forget(String key) {
        this.memoryMapper.deleteByKey(key);
    }

    public String buildMemoryPrompt() {
        List<MemoryItem> items = this.memoryMapper.findAll();
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\u3010\u5386\u53f2\u8bb0\u5fc6\u3011\n");
        for (MemoryItem it : items) {
            sb.append("- ").append(it.getMemoryKey()).append(": ").append(it.getContent()).append("\n");
        }
        return sb.toString();
    }
}

