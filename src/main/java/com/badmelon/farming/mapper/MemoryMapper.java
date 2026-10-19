/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.mapper;

import com.badmelon.farming.entity.MemoryItem;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MemoryMapper {
    public List<MemoryItem> findAll();

    public MemoryItem findByKey(@Param(value="memoryKey") String var1);

    public List<MemoryItem> findByCategory(@Param(value="category") String var1);

    public int insert(MemoryItem var1);

    public int update(MemoryItem var1);

    public int deleteByKey(@Param(value="memoryKey") String var1);
}

