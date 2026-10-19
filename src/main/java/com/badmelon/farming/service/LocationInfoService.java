/*
 * 本机时间与地理位置信息服务：
 * - 时间：服务器本机当前时间（日期/星期/时区/时间戳），不依赖外部服务
 * - 定位：经公网 IP 定位城市/区域/坐标（ipapi.co → ipinfo.io → ip-api.com 级联，
 *   与前端 getUserCity 策略一致）；全部失败时优雅降级（仅返回时间，附 locationError）
 * 对应 REST 端点：GET /api/assistant/location
 * 同时作为 http 类型 MCP（内置库 local-geo）的本地自托管端点
 */
package com.badmelon.farming.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LocationInfoService {
    private static final Logger log = LoggerFactory.getLogger(LocationInfoService.class);
    private static final String[] WEEKDAYS_CN = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[][] IP_SERVICES = {
            {"https://ipapi.co/json/", "ipapi.co"},
            {"https://ipinfo.io/json", "ipinfo.io"},
            {"http://ip-api.com/json/?lang=zh-CN", "ip-api.com"}
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** 获取本机时间与地理位置信息（定位失败不影响时间返回） */
    public Map<String, Object> getLocationInfo() {
        HashMap<String, Object> resp = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        String zone = ZoneId.systemDefault().getId();
        HashMap<String, Object> time = new HashMap<>();
        time.put("localTime", now.toString());
        time.put("date", now.toLocalDate().toString());
        time.put("timeText", now.format(FMT) + " " + WEEKDAYS_CN[now.getDayOfWeek().getValue() - 1]);
        time.put("weekday", WEEKDAYS_CN[now.getDayOfWeek().getValue() - 1]);
        time.put("timezone", zone);
        time.put("timestamp", System.currentTimeMillis());
        resp.put("success", true);
        resp.put("time", time);

        Map<String, Object> loc = this.locate();
        if (loc != null) {
            resp.put("location", loc);
        } else {
            resp.put("location", null);
            resp.put("locationError", "定位失败：公网 IP 定位服务均不可达（可稍后重试或直接使用本机时间）");
        }
        return resp;
    }

    /** 公网 IP 定位（多服务级联，任一成功即返回） */
    private Map<String, Object> locate() {
        for (String[] svc : IP_SERVICES) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(svc[0]))
                        .timeout(Duration.ofSeconds(5))
                        .header("Accept", "application/json")
                        .GET().build();
                HttpResponse<String> response = this.httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    continue;
                }
                JsonNode n = this.objectMapper.readTree(response.body());
                HashMap<String, Object> loc = new HashMap<>();
                loc.put("provider", svc[1]);
                loc.put("ip", n.path("ip").asText(n.path("query").asText("")));
                String city = n.path("city").asText("");
                String region = n.path("region").asText("");
                String country = n.path("country_name").asText(n.path("country").asText(""));
                if (city.isEmpty() && region.isEmpty() && country.isEmpty()) {
                    continue; // 空数据视为失败，继续下一服务
                }
                loc.put("city", city);
                loc.put("region", region);
                loc.put("country", country);
                double lat = n.path("latitude").asDouble(n.path("lat").asDouble(0));
                double lon = n.path("longitude").asDouble(n.path("lon").asDouble(0));
                if (lat != 0 || lon != 0) {
                    loc.put("latitude", lat);
                    loc.put("longitude", lon);
                }
                return loc;
            }
            catch (Exception e) {
                log.warn("IP 定位服务 {} 不可用: {}", svc[1], e.getMessage());
            }
        }
        return null;
    }

    /** 供监控日志等复用的简短文本描述 */
    public String describe() {
        try {
            Map<String, Object> info = this.getLocationInfo();
            Object time = info.get("time");
            Object loc = info.get("location");
            if (loc instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) loc;
                return "本机时间: " + String.valueOf(((Map<?, ?>) time).get("timeText"))
                        + "；定位: " + m.get("country") + " " + m.get("region") + " " + m.get("city")
                        + "（" + m.get("ip") + "）";
            }
            return "本机时间: " + String.valueOf(((Map<?, ?>) time).get("timeText")) + "；定位: " + info.get("locationError");
        }
        catch (Exception e) {
            return "本机时间与定位信息获取失败: " + e.getMessage();
        }
    }
}
