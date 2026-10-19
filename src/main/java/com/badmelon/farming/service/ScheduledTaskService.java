/*
 * 定时提醒服务：
 * - 应用启动时自动建表（t_reminder，CREATE TABLE IF NOT EXISTS，兼容已部署数据库）
 * - 提供创建/查询/取消提醒
 * - 后台 @Scheduled 轮询到期提醒：先写聊天历史（防浏览器离线丢失），再经 WebSocket
 *   广播 assistant_notify 事件（前端 pushAssistantNotification 插入气泡并展示）
 * 对应 REST 端点（/api/assistant/schedule）：
 *   POST   /api/assistant/schedule       创建提醒 {instruct, delayMinutes, title, session}
 *   GET    /api/assistant/schedule       查询提醒列表（可带 session 过滤）
 *   DELETE /api/assistant/schedule/{id}  取消提醒
 */
package com.badmelon.farming.service;

import com.badmelon.farming.config.MonitorWsHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScheduledTaskService {
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskService.class);

    private final JdbcTemplate jdbcTemplate;
    private final MonitorWsHandler monitorWsHandler;
    private final ChatHistoryService chatHistoryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScheduledTaskService(JdbcTemplate jdbcTemplate, MonitorWsHandler monitorWsHandler,
                                ChatHistoryService chatHistoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.monitorWsHandler = monitorWsHandler;
        this.chatHistoryService = chatHistoryService;
        this.initTable();
    }

    /** 启动时自动建表，兼容未执行新版 schema.sql 的既有数据库 */
    private void initTable() {
        try {
            this.jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_reminder ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "instruct TEXT NOT NULL COMMENT '提醒内容', "
                    + "title VARCHAR(255) DEFAULT '⏰ 定时提醒' COMMENT '提醒标题', "
                    + "delay_minutes INT DEFAULT 0 COMMENT '延迟分钟数', "
                    + "session_key VARCHAR(64) DEFAULT 'default' COMMENT '会话标识', "
                    + "fire_at DATETIME COMMENT '触发时间', "
                    + "status VARCHAR(16) DEFAULT 'pending' COMMENT 'pending/fired/cancelled', "
                    + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                    + "KEY idx_fire (status, fire_at)) "
                    + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时提醒'");
        }
        catch (Exception e) {
            log.warn("初始化定时提醒表失败（数据库可能未就绪）: {}", e.getMessage());
        }
    }

    /** 创建定时提醒，返回 {success, reminder} */
    public Map<String, Object> create(String instruct, int delayMinutes, String title, String sessionKey) {
        HashMap<String, Object> resp = new HashMap<>();
        if (instruct == null || instruct.isBlank()) {
            resp.put("success", false);
            resp.put("error", "缺少提醒内容");
            return resp;
        }
        if (delayMinutes < 0) {
            delayMinutes = 0;
        }
        String sess = sessionKey == null || sessionKey.isBlank() ? "default" : sessionKey;
        String t = title == null || title.isBlank() ? "⏰ 定时提醒" : title;
        LocalDateTime fireAt = LocalDateTime.now().plusMinutes(delayMinutes);
        try {
            this.jdbcTemplate.update(
                    "INSERT INTO t_reminder (instruct, title, delay_minutes, session_key, fire_at, status) VALUES (?,?,?,?,?,?)",
                    instruct, t, delayMinutes, sess, fireAt, "pending");
            Long id = this.jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            HashMap<String, Object> reminder = new HashMap<>();
            reminder.put("id", id);
            reminder.put("instruct", instruct);
            reminder.put("title", t);
            reminder.put("delayMinutes", delayMinutes);
            reminder.put("sessionKey", sess);
            reminder.put("fireAt", fireAt.toString());
            reminder.put("status", "pending");
            resp.put("success", true);
            resp.put("reminder", reminder);
            resp.put("message", "提醒已设定，" + delayMinutes + " 分钟后触发");
            log.info("已设定定时提醒 #{}: {}（{} 分钟后）", id, instruct, delayMinutes);
        }
        catch (Exception e) {
            resp.put("success", false);
            resp.put("error", "创建提醒失败: " + e.getMessage());
        }
        return resp;
    }

    /** 查询提醒列表（可按会话过滤） */
    public List<Map<String, Object>> list(String sessionKey) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<Map<String, Object>> rows;
            if (sessionKey != null && !sessionKey.isBlank()) {
                rows = this.jdbcTemplate.queryForList(
                        "SELECT id, instruct, title, delay_minutes, session_key, fire_at, status, created_at "
                                + "FROM t_reminder WHERE session_key = ? ORDER BY fire_at ASC", sessionKey);
            } else {
                rows = this.jdbcTemplate.queryForList(
                        "SELECT id, instruct, title, delay_minutes, session_key, fire_at, status, created_at "
                                + "FROM t_reminder ORDER BY fire_at ASC");
            }
            for (Map<String, Object> row : rows) {
                HashMap<String, Object> item = new HashMap<>();
                item.put("id", row.get("id"));
                item.put("instruct", row.get("instruct"));
                item.put("title", row.get("title"));
                item.put("delayMinutes", row.get("delay_minutes"));
                item.put("sessionKey", row.get("session_key"));
                item.put("fireAt", row.get("fire_at"));
                item.put("status", row.get("status"));
                item.put("createdAt", row.get("created_at"));
                result.add(item);
            }
        }
        catch (Exception e) {
            log.warn("查询提醒列表失败: {}", e.getMessage());
        }
        return result;
    }

    /** 取消未触发的提醒 */
    public boolean cancel(Long id) {
        if (id == null) {
            return false;
        }
        try {
            int n = this.jdbcTemplate.update(
                    "UPDATE t_reminder SET status='cancelled' WHERE id=? AND status='pending'", id);
            return n > 0;
        }
        catch (Exception e) {
            log.warn("取消提醒失败 #{}: {}", id, e.getMessage());
            return false;
        }
    }

    /** 轮询触发到期的提醒（每 10 秒）；触发顺序：写聊天历史 → WebSocket 广播 assistant_notify */
    @Scheduled(fixedDelay = 10000L, initialDelay = 15000L)
    public void fireDue() {
        try {
            List<Map<String, Object>> due = this.jdbcTemplate.queryForList(
                    "SELECT id, instruct, title, session_key FROM t_reminder "
                            + "WHERE status='pending' AND fire_at <= NOW() LIMIT 50");
            for (Map<String, Object> row : due) {
                Long id = ((Number) row.get("id")).longValue();
                String instruct = String.valueOf(row.get("instruct"));
                String title = String.valueOf(row.get("title"));
                String sess = String.valueOf(row.get("session_key"));
                // 原子抢占：仅本线程成功将 pending 置为 fired 才触发，防止重复推送
                int claimed = this.jdbcTemplate.update(
                        "UPDATE t_reminder SET status='fired' WHERE id=? AND status='pending'", id);
                if (claimed != 1) {
                    continue;
                }
                this.notifyReminder(sess, title, instruct);
            }
            // 顺手清理已结束（fired/cancelled）且超过 7 天的记录
            this.jdbcTemplate.update(
                    "DELETE FROM t_reminder WHERE status IN ('fired','cancelled') AND created_at < NOW() - INTERVAL 7 DAY");
        }
        catch (Exception e) {
            log.warn("定时提醒轮询异常: {}", e.getMessage());
        }
    }

    /** 提醒触发：写入该会话的聊天历史（浏览器离线也不丢），并广播 WebSocket 通知 */
    private void notifyReminder(String sessionKey, String title, String instruct) {
        String content = "⏰ " + title + "\n" + instruct;
        try {
            this.chatHistoryService.add(sessionKey, "assistant", content);
        }
        catch (Exception e) {
            log.warn("提醒历史写入失败: {}", e.getMessage());
        }
        try {
            HashMap<String, Object> payload = new HashMap<>();
            payload.put("type", "assistant_notify");
            payload.put("session", sessionKey);
            payload.put("title", title);
            payload.put("content", instruct);
            this.monitorWsHandler.broadcast(this.objectMapper.writeValueAsString(payload));
        }
        catch (Exception e) {
            log.warn("提醒 WebSocket 广播失败: {}", e.getMessage());
        }
        log.info("定时提醒已触发 [{}]: {}", sessionKey, instruct);
    }
}
