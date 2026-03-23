package com.learn.javaagent.Agent02.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.learn.javaagent.Agent02.config.AgentConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * LLM 访问客户端，封装 Chat Completions API 通信。
 *
 * <p>与 Agent01 的 LLMClient 职责一致，工具声明来自 {@link AgentConfig#tools()}，
 * 其内容由 {@link com.learn.javaagent.Agent02.tools.ToolRegistry#openAiTools()} 统一生成，
 * 与运行期工具分发保持一致。</p>
 */
public final class LLMClient {

    private static final Gson GSON = new Gson();
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 600_000;

    private final AgentConfig.RuntimeConfig runtime;

    /**
     * 从 agent.properties 或环境变量加载配置。
     *
     * @throws IOException            配置文件读取失败
     * @throws IllegalStateException 缺少 API_KEY 或 MODEL_ID
     */
    public LLMClient() throws IOException {
        this(AgentConfig.loadRuntime());
    }

    /**
     * 使用已有配置构造（便于测试）。
     *
     * @param runtime 运行期配置，不能为 null
     */
    public LLMClient(AgentConfig.RuntimeConfig runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /**
     * 发送 Chat Completions 请求：system + 历史 + tools + tool_choice + max_tokens。
     *
     * @param conversationMessages 多轮对话（不含 system）
     * @return 服务端 JSON 响应字符串
     */
    public String completeChat(JsonArray conversationMessages) throws IOException {
        JsonArray messages = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", runtime.getSystemPrompt());
        messages.add(sys);
        for (JsonElement e : conversationMessages) {
            messages.add(e);
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", runtime.getModelId());
        body.add("messages", messages);
        body.add("tools", AgentConfig.tools());
        body.addProperty("tool_choice", AgentConfig.DEFAULT_TOOL_CHOICE);
        body.addProperty("max_tokens", AgentConfig.DEFAULT_MAX_TOKENS);

        return postChatCompletions(body);
    }

    private String postChatCompletions(JsonObject requestBody) throws IOException {
        return postJson(runtime.getApiBaseUrl(), runtime.getApiKey(), GSON.toJson(requestBody));
    }

    private static String postJson(String baseUrl, String bearerToken, String jsonBody) throws IOException {
        String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setDoOutput(true);

        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String text = readFully(stream);
        if (code != 200) {
            throw new IllegalStateException("HTTP " + code + ": " + text);
        }
        return text;
    }

    private static String readFully(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = stream.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }
}
