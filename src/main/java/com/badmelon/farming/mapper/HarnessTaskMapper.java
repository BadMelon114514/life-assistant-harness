/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.mapper;

import com.badmelon.farming.entity.HarnessTask;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HarnessTaskMapper {
    public List<HarnessTask> findAll();

    public List<HarnessTask> findByStatus(@Param(value="status") String var1);

    public List<HarnessTask> findByOwner(@Param(value="owner") String var1);

    public HarnessTask findByKey(@Param(value="taskKey") String var1);

    public int insert(HarnessTask var1);

    public int update(HarnessTask var1);

    public int deleteByKey(@Param(value="taskKey") String var1);
}

