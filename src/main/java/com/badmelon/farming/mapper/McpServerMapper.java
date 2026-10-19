/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.mapper;

import com.badmelon.farming.entity.McpServer;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface McpServerMapper {
    public List<McpServer> findAll();

    public List<McpServer> findActive();

    public McpServer findByName(@Param(value="name") String var1);

    public McpServer findById(@Param(value="id") Long var1);

    public int insert(McpServer var1);

    public int update(McpServer var1);

    public int deleteById(@Param(value="id") Long var1);
}

