/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.mapper;

import com.badmelon.farming.entity.Setting;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SettingMapper {
    public List<Setting> findAll();

    public Setting findByKey(@Param(value="settingKey") String var1);

    public int insert(Setting var1);

    public int update(Setting var1);

    public int deleteByKey(@Param(value="settingKey") String var1);
}

