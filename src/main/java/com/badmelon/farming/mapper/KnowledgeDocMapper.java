/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.mapper;

import com.badmelon.farming.entity.KnowledgeDoc;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeDocMapper {
    public List<KnowledgeDoc> findAll();

    public KnowledgeDoc findByKey(@Param(value="docKey") String var1);

    public List<KnowledgeDoc> search(@Param(value="keyword") String var1);

    public int insert(KnowledgeDoc var1);

    public int update(KnowledgeDoc var1);

    public int deleteByKey(@Param(value="docKey") String var1);
}

