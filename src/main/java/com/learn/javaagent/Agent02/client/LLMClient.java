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
 * LLM 访问客户端，封装 Chat Completions API。
 *
 * <p>tools 由 {@link AgentConfig#tools()} 提供，来源于 {@link com.learn.javaagent.Agent02.tools.ToolRegistry#openAiTools()}。</p>
 */
public final class LLMClient {

    private static final Gson GSON = new Gson();
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    private static final int READ_TIMEOUT_MS = 600_000;

    private final AgentConfig.RuntimeConfig runtime;

    /**
     * 加载 {@link AgentConfig} 并绑定为本次进程内的 LLM 会话配置。
     *
     * @throws IOException              读取配置文件失败
     * @throws IllegalStateException 缺少必填项（如 {@link AgentConfig#KEY_API_KEY}）
     */
    public LLMClient() throws IOException {
        this(AgentConfig.loadRuntime());
    }

    /**
     * 使用已解析的配置构造（便于测试或外部预先 {@link AgentConfig#load()} 再 {@link AgentConfig#apiBaseUrl} 等场景）。
     */
    public LLMClient(AgentConfig.RuntimeConfig runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /**
     * 组装 system 与多轮 user/assistant/tool 消息，并附带 model、tools、tool_choice、max_tokens，发送 Chat Completions。
     *
     * @param conversationMessages 不含 system 的消息序列（与 OpenAI 多轮约定一致）
     * @return 服务端响应 JSON 字符串
     */
    public String completeChat(JsonArray conversationMessages) throws IOException {
        JsonArray req = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", runtime.getSystemPrompt());
        req.add(sys);
        for (JsonElement e : conversationMessages) {
            req.add(e);
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", runtime.getModelId());
        body.add("messages", req);
        body.add("tools", AgentConfig.tools());
        body.addProperty("tool_choice", AgentConfig.DEFAULT_TOOL_CHOICE);
        body.addProperty("max_tokens", AgentConfig.DEFAULT_MAX_TOKENS);
        return postChatCompletions(body);
    }

    /**
     * 使用已组装的请求体发送 Chat Completions（字段组装见 {@link #completeChat(JsonArray)}）。
     */
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
