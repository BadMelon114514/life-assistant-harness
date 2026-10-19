/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.mapper;

import com.badmelon.farming.entity.Teammate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TeammateMapper {
    public List<Teammate> findAll();

    public Teammate findByName(@Param(value="name") String var1);

    public int insert(Teammate var1);

    public int update(Teammate var1);

    public int updateStatus(@Param(value="name") String var1, @Param(value="status") String var2);

    public int deleteByName(@Param(value="name") String var1);
}

