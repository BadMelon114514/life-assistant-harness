/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.Skill;
import com.badmelon.farming.mapper.SkillMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SkillService {
    private final SkillMapper skillMapper;

    public SkillService(SkillMapper skillMapper) {
        this.skillMapper = skillMapper;
    }

    public List<Skill> findAll() {
        return this.skillMapper.findAll();
    }

    public List<Skill> findEnabled() {
        return this.skillMapper.findEnabled();
    }

    public Skill findById(Long id) {
        return this.skillMapper.findById(id);
    }

    public Skill findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return this.skillMapper.findByName(name);
    }

    /** 列限长保护：description 超出列宽时截断，避免 Data too long 落库失败 */
    private static String cut(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    public Skill save(Skill skill) {
        skill.setName(cut(skill.getName(), 100));
        skill.setDescription(cut(skill.getDescription(), 255));
        Skill existing;
        Skill skill2 = existing = skill.getId() != null ? this.skillMapper.findById(skill.getId()) : null;
        if (skill.getEnabled() == null) {
            skill.setEnabled(1);
        }
        if (existing == null) {
            this.skillMapper.insert(skill);
        } else {
            this.skillMapper.update(skill);
        }
        return skill;
    }

    public void delete(Long id) {
        this.skillMapper.deleteById(id);
    }

    public Skill toggle(Long id) {
        Skill skill = this.skillMapper.findById(id);
        if (skill == null) {
            return null;
        }
        skill.setEnabled(skill.getEnabled() != null && skill.getEnabled() == 1 ? 0 : 1);
        this.skillMapper.update(skill);
        return skill;
    }

    public String buildSkillPrompt() {
        List<Skill> skills = this.findEnabled();
        if (skills.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\u3010\u53ef\u7528\u7684\u4e13\u4e1a\u6280\u80fd\u3011\n");
        for (Skill s : skills) {
            sb.append("- \u6280\u80fd\u300c").append(s.getName()).append("\u300d");
            if (s.getDescription() != null && !s.getDescription().isBlank()) {
                sb.append("(").append(s.getDescription()).append(")");
            }
            sb.append("\uff1a\n  ").append(s.getContent()).append("\n");
        }
        sb.append("\u5f53\u7528\u6237\u8bf7\u6c42\u4e0e\u4e0a\u8ff0\u6280\u80fd\u76f8\u5173\u65f6\uff0c\u8bf7\u4f18\u5148\u4f7f\u7528\u5bf9\u5e94\u6280\u80fd\u6765\u5904\u7406\u3002\n");
        return sb.toString();
    }
}

