/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.mapper;

import com.badmelon.farming.entity.ChatMessage;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatMessageMapper {
    public List<ChatMessage> findBySession(@Param(value="sessionKey") String var1);

    public ChatMessage findById(@Param(value="id") Long var1);

    public int insert(ChatMessage var1);

    public int deleteBySession(@Param(value="sessionKey") String var1);

    public int deleteById(@Param(value="id") Long var1);

    public List<Map<String, Object>> findSessionSummaries();
}

