/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.mapper;

import com.badmelon.farming.entity.MonitorLog;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MonitorLogMapper {
    public int insert(MonitorLog var1);

    public List<MonitorLog> findLatest(@Param(value="limit") int var1);

    public List<MonitorLog> findBySource(@Param(value="source") String var1, @Param(value="limit") int var2);

    public int clean(@Param(value="keepDays") int var1);
}

