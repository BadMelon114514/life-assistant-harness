/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.mapper;

import com.badmelon.farming.entity.Skill;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SkillMapper {
    public List<Skill> findAll();

    public List<Skill> findEnabled();

    public Skill findByName(@Param(value="name") String var1);

    public Skill findById(@Param(value="id") Long var1);

    public int insert(Skill var1);

    public int update(Skill var1);

    public int deleteById(@Param(value="id") Long var1);
}

