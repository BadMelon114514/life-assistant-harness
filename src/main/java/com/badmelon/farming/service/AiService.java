/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.service;

import com.badmelon.farming.entity.AiModelConfig;
import com.badmelon.farming.entity.Setting;
import com.badmelon.farming.mapper.SettingMapper;
import com.badmelon.farming.service.AiModelService;
import com.badmelon.farming.service.MonitorService;
import com.badmelon.farming.util.SecretRedaction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class AiService {
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";
    private static final int MAX_RETRIES = 3;
    private static final int BASE_DELAY_MS = 500;
    private static final int MAX_DELAY_MS = 8000;
    private static final int MAX_CONSECUTIVE_529 = 2;
    private final SettingMapper settingMapper;
    private final AiModelService aiModelService;
    private final MonitorService monitorService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15L)).build();
    private final Random random = new Random();

    public AiService(SettingMapper settingMapper, AiModelService aiModelService, MonitorService monitorService) {
        this.settingMapper = settingMapper;
        this.aiModelService = aiModelService;
        this.monitorService = monitorService;
    }

    private void logLlmRequest(ModelEndpoint ep, ObjectNode body, boolean stream) {
        try {
            String model = ep != null ? ep.model : (body.has("model") ? body.get("model").asText("") : "");
            StringBuilder sb = new StringBuilder();
            sb.append("model=").append(model).append(stream ? ", \u6d41\u5f0f" : ", \u975e\u6d41\u5f0f").append(", messages=[");
            JsonNode messages = body.get("messages");
            int count = 0;
            if (messages != null && messages.isArray()) {
                for (JsonNode m : messages) {
                    ++count;
                    String role = m.path("role").asText("?");
                    String c = m.path("content").asText("");
                    String brief = c.length() > 120 ? c.substring(0, 120) + "\u2026" : c;
                    sb.append(role).append(":").append(brief).append("; ");
                }
            }
            sb.append("] \u5171").append(count).append("\u6761\u6d88\u606f");
            this.monitorService.log("llm", "INFO", SecretRedaction.redact(sb.toString()));
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public AiModelConfig getActiveModel() {
        return this.aiModelService != null ? this.aiModelService.getActive() : null;
    }

    public String getApiKey() {
        AiModelConfig am = this.getActiveModel();
        if (am != null && am.getApiKey() != null && !am.getApiKey().isBlank()) {
            return am.getApiKey();
        }
        Setting key = this.settingMapper.findByKey("api.key");
        return key != null && key.getSettingValue() != null && !key.getSettingValue().isBlank() ? key.getSettingValue() : null;
    }

    public String getBaseUrl() {
        AiModelConfig am = this.getActiveModel();
        if (am != null && am.getBaseUrl() != null && !am.getBaseUrl().isBlank()) {
            String v = am.getBaseUrl();
            return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
        }
        Setting url = this.settingMapper.findByKey("api.baseUrl");
        if (url != null && url.getSettingValue() != null && !url.getSettingValue().isBlank()) {
            String v = url.getSettingValue();
            return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
        }
        return DEFAULT_BASE_URL;
    }

    public String getDefaultModel() {
        AiModelConfig am = this.getActiveModel();
        if (am != null && am.getModel() != null && !am.getModel().isBlank()) {
            return am.getModel();
        }
        Setting model = this.settingMapper.findByKey("api.model");
        return model != null && model.getSettingValue() != null && !model.getSettingValue().isBlank() ? model.getSettingValue() : DEFAULT_MODEL;
    }

    public boolean isConfigured() {
        return this.getApiKey() != null;
    }

    public String chat(String systemPrompt, String userPrompt) {
        return this.chatWithRetry(systemPrompt, null, userPrompt, this.getDefaultModel());
    }

    public String chat(String systemPrompt, String userPrompt, String model) {
        return this.chatWithRetry(systemPrompt, null, userPrompt, model);
    }

    public String chatWithHistory(String systemPrompt, List<Map.Entry<String, String>> history, String userPrompt) {
        return this.chatWithRetry(systemPrompt, history, userPrompt, this.getDefaultModel());
    }

    public String chatWithHistory(String systemPrompt, List<Map.Entry<String, String>> history, String userPrompt, String model) {
        return this.chatWithRetry(systemPrompt, history, userPrompt, model);
    }

    public String streamChatWithHistory(String systemPrompt, List<Map.Entry<String, String>> history, String userPrompt, Consumer<String> consumer) {
        return this.streamChatWithHistory(systemPrompt, history, userPrompt, this.getDefaultModel(), consumer);
    }

    public String streamChatWithHistory(String systemPrompt, List<Map.Entry<String, String>> history, String userPrompt, String model, Consumer<String> consumer) {
        String apiKey = this.getApiKey();
        if (apiKey == null) {
            throw new IllegalStateException("\u5c1a\u672a\u914d\u7f6e API Key\uff0c\u8bf7\u524d\u5f80\u8bbe\u7f6e\u9875\u9762\u586b\u5199");
        }
        ObjectNode body = this.buildRequestBody(systemPrompt, history, userPrompt, model);
        body.put("stream", true);
        String baseUrl = this.getBaseUrl();
        List<ModelEndpoint> candidates = this.buildModelCandidates(model, apiKey, baseUrl);
        String lastError = null;
        StringBuilder full = new StringBuilder();
        block4: for (int ci = 0; ci < candidates.size(); ++ci) {
            ModelEndpoint ep = candidates.get(ci);
            int consecutiveOverload = 0;
            for (int attempt = 0; attempt <= 3; ++attempt) {
                try {
                    full.setLength(0);
                    this.streamExecute(ep, body, chunk -> {
                        full.append((String)chunk);
                        if (consumer != null) {
                            consumer.accept((String)chunk);
                        }
                    });
                    return full.toString();
                }
                catch (RetryableException re) {
                    lastError = re.getMessage();
                    if (re.isOverload() && ++consecutiveOverload >= 2 && ci < candidates.size() - 1) continue block4;
                    if (attempt >= 3) continue;
                    this.sleepBackoff(attempt);
                    continue;
                }
                catch (ContextTooLongException ce) {
                    throw new RuntimeException("AI \u4e0a\u4e0b\u6587\u8fc7\u957f\uff1a" + ce.getMessage() + "\uff0c\u8bf7\u5f00\u542f\u65b0\u5bf9\u8bdd\u6216\u538b\u7f29\u4e0a\u4e0b\u6587\u3002");
                }
                catch (Exception e) {
                    lastError = e.getMessage();
                    continue block4;
                }
            }
        }
        throw new RuntimeException("AI \u8c03\u7528\u5931\u8d25\uff08\u5df2\u91cd\u8bd5\u5e76\u5c1d\u8bd5\u5907\u7528\u6a21\u578b\uff09: " + (lastError == null ? "\u672a\u77e5\u9519\u8bef" : lastError));
    }

    private void streamExecute(ModelEndpoint ep, ObjectNode body, Consumer<String> consumer) throws Exception {
        String line;
        HttpResponse<InputStream> response;
        body.put("model", ep.model);
        this.logLlmRequest(ep, body, true);
        String url = ep.baseUrl + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMinutes(2L)).header("Content-Type", "application/json").header("Authorization", "Bearer " + ep.apiKey).POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        try {
            response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        }
        catch (HttpTimeoutException e) {
            throw new RetryableException("\u8bf7\u6c42\u8d85\u65f6", false);
        }
        catch (IOException e) {
            throw new RetryableException("\u7f51\u7edc\u5f02\u5e38: " + e.getMessage(), false);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("AI \u8c03\u7528\u88ab\u4e2d\u65ad", e);
        }
        int code = response.statusCode();
        if (code != 200) {
            String errBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            if (code == 429 || code == 500 || code == 502 || code == 503 || code == 529) {
                boolean overload = code == 429 || code == 529 || code == 503;
                throw new RetryableException("HTTP " + code + ": " + this.truncate(errBody, 200), overload);
            }
            throw new RuntimeException("HTTP " + code + ": " + this.truncate(errBody, 300));
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
        while ((line = reader.readLine()) != null) {
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if ("[DONE]".equals(data)) break;
            if (data.isEmpty()) continue;
            try {
                JsonNode node = this.objectMapper.readTree(data);
                JsonNode delta = node.path("choices").path(0).path("delta");
                JsonNode content = delta.path("content");
                if (!(content.isMissingNode() || content.isNull() || content.asText().isBlank())) {
                    consumer.accept(content.asText());
                    continue;
                }
                // reasoning_content is the model's chain-of-thought; skip it so it
                // never leaks into the user-facing stream (final answer is in "content").
                JsonNode reasoning = delta.path("reasoning_content");
                if (reasoning.isMissingNode() || reasoning.isNull() || reasoning.asText().isBlank()) continue;
            }
            catch (Exception exception) {}
        }
    }

    private String chatWithRetry(String systemPrompt, List<Map.Entry<String, String>> history, String userPrompt, String model) {
        String apiKey = this.getApiKey();
        if (apiKey == null) {
            throw new IllegalStateException("\u5c1a\u672a\u914d\u7f6e API Key\uff0c\u8bf7\u524d\u5f80\u8bbe\u7f6e\u9875\u9762\u586b\u5199");
        }
        ObjectNode body = this.buildRequestBody(systemPrompt, history, userPrompt, model);
        String baseUrl = this.getBaseUrl();
        List<ModelEndpoint> candidates = this.buildModelCandidates(model, apiKey, baseUrl);
        String lastError = null;
        block4: for (int ci = 0; ci < candidates.size(); ++ci) {
            ModelEndpoint ep = candidates.get(ci);
            int consecutiveOverload = 0;
            for (int attempt = 0; attempt <= 3; ++attempt) {
                try {
                    String result = this.execute(ep, body);
                    return result;
                }
                catch (RetryableException re) {
                    lastError = re.getMessage();
                    if (re.isOverload() && ++consecutiveOverload >= 2 && ci < candidates.size() - 1) continue block4;
                    if (attempt >= 3) continue;
                    this.sleepBackoff(attempt);
                    continue;
                }
                catch (ContextTooLongException ce) {
                    throw new RuntimeException("AI \u4e0a\u4e0b\u6587\u8fc7\u957f\uff1a" + ce.getMessage() + "\uff0c\u8bf7\u5f00\u542f\u65b0\u5bf9\u8bdd\u6216\u538b\u7f29\u4e0a\u4e0b\u6587\u3002");
                }
                catch (Exception e) {
                    lastError = e.getMessage();
                    continue block4;
                }
            }
        }
        throw new RuntimeException("AI \u8c03\u7528\u5931\u8d25\uff08\u5df2\u91cd\u8bd5\u5e76\u5c1d\u8bd5\u5907\u7528\u6a21\u578b\uff09: " + (lastError == null ? "\u672a\u77e5\u9519\u8bef" : lastError));
    }

    private ObjectNode buildRequestBody(String systemPrompt, List<Map.Entry<String, String>> history, String userPrompt, String model) {
        ObjectNode body = this.objectMapper.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
        }
        if (history != null) {
            for (Map.Entry entry : history) {
                ObjectNode m = messages.addObject();
                m.put("role", (String)entry.getKey());
                m.put("content", (String)entry.getValue());
            }
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userPrompt);
        return body;
    }

    private List<ModelEndpoint> buildModelCandidates(String primaryModel, String apiKey, String baseUrl) {
        ArrayList<ModelEndpoint> candidates = new ArrayList<ModelEndpoint>();
        candidates.add(new ModelEndpoint(primaryModel, apiKey, baseUrl));
        try {
            if (this.aiModelService != null) {
                for (AiModelConfig m : this.aiModelService.findAll()) {
                    String modelName = m.getModel();
                    String key = m.getApiKey();
                    String url = m.getBaseUrl();
                    if (modelName == null || key == null || key.isBlank() || url == null || url.isBlank() || modelName.equals(primaryModel)) continue;
                    String normalizedUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
                    candidates.add(new ModelEndpoint(modelName, key, normalizedUrl));
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return candidates;
    }

    private String execute(ModelEndpoint ep, ObjectNode body) throws Exception {
        HttpResponse<String> response;
        body.put("model", ep.model);
        this.logLlmRequest(ep, body, false);
        String url = ep.baseUrl + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(60L)).header("Content-Type", "application/json").header("Authorization", "Bearer " + ep.apiKey).POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        try {
            response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
        catch (HttpTimeoutException e) {
            throw new RetryableException("\u8bf7\u6c42\u8d85\u65f6", false);
        }
        catch (IOException e) {
            throw new RetryableException("\u7f51\u7edc\u5f02\u5e38: " + e.getMessage(), false);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("AI \u8c03\u7528\u88ab\u4e2d\u65ad", e);
        }
        int code = response.statusCode();
        String bodyStr = response.body();
        if (code == 200) {
            JsonNode root = this.objectMapper.readTree(bodyStr);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new RuntimeException("\u54cd\u5e94\u4e2d\u65e0\u5185\u5bb9: " + this.truncate(bodyStr, 300));
            }
            return content.asText();
        }
        if (code == 429 || code == 500 || code == 502 || code == 503 || code == 529) {
            boolean overload = code == 429 || code == 529 || code == 503;
            throw new RetryableException("HTTP " + code + ": " + this.truncate(bodyStr, 200), overload);
        }
        String lower = (bodyStr != null ? bodyStr : "").toLowerCase();
        if (code == 400 && (lower.contains("context_length") || lower.contains("too long") || lower.contains("max context") || lower.contains("token"))) {
            throw new ContextTooLongException(this.truncate(bodyStr, 200));
        }
        throw new RuntimeException("HTTP " + code + ": " + this.truncate(bodyStr, 300));
    }

    private void sleepBackoff(int attempt) {
        long base = Math.min(500L * (1L << attempt), 8000L);
        long jitter = (long)(this.random.nextDouble() * (double)base * 0.25);
        try {
            Thread.sleep(base + jitter);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String summarize(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        String prompt = "\u8bf7\u5bf9\u4ee5\u4e0b\u5185\u5bb9\u751f\u6210\u7b80\u6d01\u6458\u8981\uff0c\u4e0d\u8d85\u8fc7" + maxLen + "\u5b57\uff1a\n\n" + text;
        try {
            return this.chat("\u4f60\u662f\u4e00\u540d\u4e13\u4e1a\u7684\u6587\u672c\u6458\u8981\u52a9\u624b\u3002", prompt);
        }
        catch (Exception e) {
            return text.substring(0, maxLen);
        }
    }

    private String truncate(String s, int len) {
        if (s == null) {
            return "";
        }
        return s.length() <= len ? s : s.substring(0, len);
    }

    private static class ModelEndpoint {
        final String model;
        final String apiKey;
        final String baseUrl;

        ModelEndpoint(String model, String apiKey, String baseUrl) {
            this.model = model;
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
        }
    }

    private static class RetryableException
    extends RuntimeException {
        final boolean overload;

        RetryableException(String message, boolean overload) {
            super(message);
            this.overload = overload;
        }

        boolean isOverload() {
            return this.overload;
        }
    }

    private static class ContextTooLongException
    extends RuntimeException {
        ContextTooLongException(String message) {
            super(message);
        }
    }
}

