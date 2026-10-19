/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.Setting;
import com.badmelon.farming.mapper.SettingMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {
    private final SettingMapper settingMapper;

    public SettingsService(SettingMapper settingMapper) {
        this.settingMapper = settingMapper;
    }

    public List<Setting> getAll() {
        List<Setting> list = this.settingMapper.findAll();
        list.forEach(s -> {
            if (s.getIsSecret() != null && s.getIsSecret() == 1 && s.getSettingValue() != null && !s.getSettingValue().isBlank()) {
                s.setSettingValue(this.mask(s.getSettingValue()));
            }
        });
        return list;
    }

    public Setting get(String key) {
        return this.settingMapper.findByKey(key);
    }

    public String getValue(String key) {
        Setting s = this.settingMapper.findByKey(key);
        return s != null ? s.getSettingValue() : null;
    }

    public Setting save(String key, String value, String description, Integer isSecret) {
        Setting existing = this.settingMapper.findByKey(key);
        if (isSecret != null && isSecret == 1 && existing != null && value != null && value.startsWith("******")) {
            value = existing.getSettingValue();
        }
        if (existing == null) {
            Setting s = new Setting();
            s.setSettingKey(key);
            s.setSettingValue(value);
            s.setDescription(description);
            s.setIsSecret(isSecret != null ? isSecret : 0);
            this.settingMapper.insert(s);
            return s;
        }
        existing.setSettingValue(value);
        if (description != null) {
            existing.setDescription(description);
        }
        if (isSecret != null) {
            existing.setIsSecret(isSecret);
        }
        this.settingMapper.update(existing);
        return existing;
    }

    public void delete(String key) {
        this.settingMapper.deleteByKey(key);
    }

    private String mask(String value) {
        if (value.length() <= 8) {
            return "******";
        }
        return "******" + value.substring(value.length() - 4);
    }
}

