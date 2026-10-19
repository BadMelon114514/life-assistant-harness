/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.mapper;

import com.badmelon.farming.entity.AiModelConfig;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiModelMapper {
    public List<AiModelConfig> findAll();

    public AiModelConfig findById(@Param(value="id") Long var1);

    public AiModelConfig findActive();

    public int insert(AiModelConfig var1);

    public int update(AiModelConfig var1);

    public int deleteById(@Param(value="id") Long var1);

    public int clearActive();
}

