/*
 * 前端「关闭后端服务」入口。
 * 前端设置页/侧栏按钮调用 POST /api/system/shutdown：
 *   1. 校验请求来自本机且携带 X-Shutdown-Confirm 头（防第三方网页 CSRF 关闭服务）；
 *   2. 通过 WebSocket 通知所有已连接页面「服务即将关闭」；
 *   3. 延迟 600ms 后先请求 context-store(8765) 关闭，再优雅关闭 Spring 上下文并退出进程。
 */
package com.badmelon.farming.controller;

import com.badmelon.farming.config.MonitorWsHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/system"})
public class SystemController {
    private static final Logger log = LoggerFactory.getLogger(SystemController.class);
    private static final String CTX_SHUTDOWN_URL = "http://127.0.0.1:8765/shutdown";
    private final ConfigurableApplicationContext context;
    private final MonitorWsHandler wsHandler;

    public SystemController(ConfigurableApplicationContext context, MonitorWsHandler wsHandler) {
        this.context = context;
        this.wsHandler = wsHandler;
    }

    @PostMapping(value={"/shutdown"})
    public ResponseEntity<Map<String, Object>> shutdown(HttpServletRequest request) {
        Map<String, Object> resp = new LinkedHashMap<>();
        String ip = request.getRemoteAddr();
        boolean local = "127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
        if (!local) {
            resp.put("success", false);
            resp.put("message", "仅允许本机操作");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(resp);
        }
        if (!"yes".equalsIgnoreCase(request.getHeader("X-Shutdown-Confirm"))) {
            resp.put("success", false);
            resp.put("message", "缺少关闭确认头 X-Shutdown-Confirm");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(resp);
        }
        resp.put("success", true);
        resp.put("message", "后端服务正在关闭");
        try {
            this.wsHandler.broadcast("{\"type\":\"server_shutdown\",\"message\":\"后端服务即将关闭\"}");
        } catch (Exception e) {
            log.warn("广播关闭通知失败: {}", e.getMessage());
        }
        Thread t = new Thread(this::doShutdown, "graceful-shutdown");
        t.setDaemon(true);
        t.start();
        log.info("收到前端关闭指令，600ms 后停止服务");
        return ResponseEntity.ok(resp);
    }

    private void doShutdown() {
        try {
            Thread.sleep(600L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(800L)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(CTX_SHUTDOWN_URL))
                    .timeout(Duration.ofMillis(1200L))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            client.send(req, HttpResponse.BodyHandlers.discarding());
            log.info("上下文存储服务(context-store:8765)已请求关闭");
        } catch (Exception e) {
            log.info("上下文存储服务关闭请求未成功（可能未运行）: {}", e.getMessage());
        }
        try {
            this.context.close();
            log.info("Spring 应用上下文已关闭");
        } catch (Exception e) {
            log.warn("关闭 Spring 上下文异常: {}", e.getMessage());
        }
        System.exit(0);
    }
}
